package app.miogram

import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import app.miogram.data.FileCache
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.DownloadState
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStreamTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private val repos = TestRepositories(dir, fake)

    @After
    fun tearDown() {
        runBlocking { repos.telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun seekingReadsFromTheRightOffsets(): Unit = runBlocking {
        withTimeout(90_000) {
            login()
            fake.addVideoMessage()
            val video = assertNotNull(repos.messages.history("self").last().media)
            val stream = repos.media.stream(video)
            assertEquals("tg", stream.uri.scheme)
            val source = stream.factory.createDataSource()
            val bytes = fake.cluster.fileBytes

            assertEquals(bytes.size - 700_000L, source.open(DataSpec.Builder().setUri(stream.uri).setPosition(700_000).build()))
            assertContentEquals(bytes.copyOfRange(700_000, 900_000), source.readFully(200_000))
            assertEquals(512L * 1024, fake.fileOffsets.min())
            assertTrue(0L !in fake.fileOffsets)
            source.close()

            fake.fileOffsets.clear()
            source.open(DataSpec.Builder().setUri(stream.uri).setPosition(1_100_000).build())
            assertContentEquals(bytes.copyOfRange(1_100_000, bytes.size), source.readFully(bytes.size - 1_100_000))
            assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(16), 0, 16))
            assertEquals(listOf(1024L * 1024), fake.fileOffsets.toList())
            source.close()

            fake.fileOffsets.clear()
            source.open(DataSpec.Builder().setUri(stream.uri).setPosition(0).setLength(1000).build())
            assertContentEquals(bytes.copyOfRange(0, 1000), source.readFully(1000))
            assertEquals(0L, fake.fileOffsets.min())
            source.close()
        }
    }

    @Test
    fun anExpiredFileReferenceIsFetchedAgainFromTheMessage(): Unit = runBlocking {
        withTimeout(90_000) {
            login()
            fake.addVideoMessage()
            val video = assertNotNull(repos.messages.history("self").last().media)
            fake.expireVideoReference()
            val done = assertIs<DownloadState.Done>(repos.media.download(video).toList().last())
            assertContentEquals(fake.cluster.fileBytes, done.file.readBytes())
            assertTrue(repos.telegram.log.value.any { it.text.contains("file reference of ${video.key} refreshed") })
        }
    }

    @Test
    fun theCacheDropsTheLeastRecentlyUsedFiles() {
        val cache = FileCache(dir.resolve("cache"), maxBytes = 2_500)
        val files = (1..3).map { n -> cache.file("f$n").apply { writeBytes(ByteArray(1_000)); setLastModified(1_000L * n) } }
        assertNotNull(cache.hit(files[0], 1_000))
        cache.trim(keep = files[2])
        assertTrue(files[0].exists() && files[2].exists())
        assertTrue(!files[1].exists())
        assertEquals(2_000, cache.size())
    }

    private suspend fun login() {
        repos.telegram.connect(12345, "hash")
        val request = repos.auth.sendCode("9996624242")
        fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id
    }
}

private fun DataSource.readFully(count: Int): ByteArray {
    val out = ByteArray(count)
    var at = 0
    while (at < count) {
        val read = read(out, at, count - at)
        if (read == C.RESULT_END_OF_INPUT) break
        at += read
    }
    assertEquals(count, at)
    return out
}
