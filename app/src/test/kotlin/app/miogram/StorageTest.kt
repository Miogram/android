package app.miogram

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.StorageRepository
import app.miogram.data.telegram.SignInResult
import app.miogram.music.MusicCache
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.StorageCategory
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.last
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
class StorageTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private val repos = TestRepositories(dir, fake)
    private val music = MusicCache(ApplicationProvider.getApplicationContext(), StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext()))
    private val storage = StorageRepository(repos.media, music)

    @After
    fun tearDown() {
        runBlocking {
            storage.clear(StorageCategory.Music)
            repos.telegram.close()
        }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun eachCacheIsMeasuredAndClearedOnItsOwn(): Unit = runBlocking {
        withTimeout(90_000) {
            repos.telegram.connect(12345, "hash")
            fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
            assertEquals(0, storage.usage().total)

            fake.addVideoMessage()
            val video = assertNotNull(repos.messages.history("self").last().media)
            repos.media.prefetch(video, 4096)
            val file = assertIs<DownloadState.Done>(repos.media.download(video).last()).file
            fake.addStickerMessage()
            val sticker = assertNotNull(repos.messages.history("self").last().media)
            assertNotNull(repos.media.sticker(sticker.key))
            val part = File(file.parentFile, "other.bin.part").apply { writeBytes(ByteArray(10)) }

            val usage = storage.usage()
            assertEquals(file.length() + 10, usage.bytes[StorageCategory.Media])
            assertTrue(usage.bytes.getValue(StorageCategory.Streams) >= 4096)
            assertTrue(usage.bytes.getValue(StorageCategory.Stickers) > 0)
            assertEquals(0, usage.bytes[StorageCategory.Music])
            assertTrue(usage.free != null)

            storage.clear(StorageCategory.Media)
            assertFalse(file.exists())
            assertTrue(part.exists(), "a file being written stays")
            val after = storage.usage()
            assertEquals(10, after.bytes[StorageCategory.Media])
            assertEquals(usage.bytes[StorageCategory.Stickers], after.bytes[StorageCategory.Stickers], "the other caches stay")

            assertIs<DownloadState.Progress>(repos.media.download(video).toList().first())
            assertTrue(file.exists())

            storage.clear(null)
            val cleared = storage.usage()
            assertEquals(10, cleared.total, "only the part being written is left")
        }
    }

    @Test
    fun theMusicCacheIsMeasuredAndCleared(): Unit = runBlocking {
        val source = CacheDataSource(music.cache, ByteArrayDataSource(ByteArray(5_000)))
        CacheWriter(source, DataSpec.Builder().setUri("tg://music/1").setKey("song").build(), null, null).cache()
        assertEquals(5_000, storage.usage().bytes[StorageCategory.Music])
        storage.clear(StorageCategory.Music)
        assertEquals(0, storage.usage().bytes[StorageCategory.Music])
    }
}
