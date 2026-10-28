package app.miogram

import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.OutgoingMedia
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import cn.ellerotta.core.database.ChatDatabase
import cn.ellerotta.ktproto.api.functions.messages.SendMedia
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import cn.ellerotta.ktproto.client.UPLOAD_PART_SIZE
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.session.RpcException
import cn.ellerotta.ktproto.testing.FakeAnswer
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutgoingQueueTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun start(dir: File, fake: FakeTelegram, session: InMemorySessionStorage, database: ChatDatabase): TestRepositories =
        TestRepositories(dir, fake, TelegramClientHolder(dir, database = database) { id, hash, logger -> fake.config(id, hash, logger, session) })

    private suspend fun TestRepositories.logIn(fake: FakeTelegram) {
        telegram.connect(12345, "hash")
        fake.selfId = assertIs<SignInResult.LoggedIn>(auth.signIn(auth.sendCode("+999 66 1 4242"), "11111")).profile.id
    }

    private fun media(dir: File, bytes: ByteArray) = OutgoingMedia("report.bin", "application/octet-stream", UploadSource.of(File(dir, "picked.bin").apply { writeBytes(bytes) }))

    @Test
    fun aRestartMidUploadSendsItOnceWithTheSameRandomId(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val file = File(dir, "chats.db")
        val session = InMemorySessionStorage()
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                var database = ChatDatabase.open(context, file)
                val first = start(dir, fake, session, database)
                first.logIn(fake)
                val saved = "u${fake.selfId}"

                val bytes = Random(4).nextBytes(UPLOAD_PART_SIZE * 2 + 2_000)
                fake.cluster.uploadPartErrors[1] = ConcurrentLinkedQueue(listOf(FakeAnswer.Error(420, "FLOOD_PREMIUM_WAIT_30")))
                val randomId = 0x5EED_0001L
                val batch = first.messages.queueFile(saved, media(dir, bytes), randomId = randomId)
                val key = first.messages.outgoingKeys(batch, 1).single()
                first.messages.outgoingProgress.first { (it[key]?.downloaded ?: 0) > 0 }
                assertEquals(1, first.messages.outgoing(saved, null).first().size)
                first.telegram.close()
                database.close()
                assertEquals(0, fake.sendMediaCalls)
                fake.cluster.uploadPartErrors.clear()

                database = ChatDatabase.open(context, file)
                val second = start(dir, fake, session, database)
                assertTrue(second.telegram.prepare(12345, "hash"))
                val bubble = second.messages.outgoing(saved, null).first { it.isNotEmpty() }.single()
                assertEquals(Int.MAX_VALUE, bubble.id)
                assertTrue(bubble.pending)
                second.messages.outgoing(saved, null).first { it.isEmpty() }
                val sent = assertIs<SendMedia>(fake.lastSendMedia)
                assertEquals(randomId, sent.randomId)
                assertContentEquals(bytes, fake.lastUpload)
                assertEquals(1, fake.sendMediaCalls)
                assertEquals(emptyList(), database.outgoing().rows(fake.selfId))
                assertEquals(emptyList(), File(dir, "outgoing").listFiles().orEmpty().toList())
                delay(500)
                assertEquals(1, fake.sendMediaCalls)
                second.telegram.close()
                database.close()
            }
        }
        dir.deleteRecursively()
    }

    @Test
    fun aFailureStaysFailedAcrossARestartUntilItIsRetried(): Unit = runBlocking {
        val dir = Files.createTempDirectory("poc").toFile()
        val file = File(dir, "chats.db")
        val session = InMemorySessionStorage()
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                var database = ChatDatabase.open(context, file)
                val first = start(dir, fake, session, database)
                first.logIn(fake)
                val saved = "u${fake.selfId}"

                val bytes = Random(5).nextBytes(20_000)
                fake.failMediaSends = 1
                val batch = first.messages.queueFile(saved, media(dir, bytes))
                val error = assertFailsWith<RpcException> { first.messages.awaitOutgoing(batch) }
                assertEquals("CHAT_SEND_MEDIA_FORBIDDEN", error.errorMessage)
                val failed = first.messages.outgoing(saved, null).first().single()
                assertTrue(failed.failed)
                assertEquals("CHAT_SEND_MEDIA_FORBIDDEN", database.outgoing().rows(fake.selfId).single().failed)
                first.telegram.close()
                database.close()

                database = ChatDatabase.open(context, file)
                val second = start(dir, fake, session, database)
                assertTrue(second.telegram.prepare(12345, "hash"))
                val shown = second.messages.outgoing(saved, null).first { it.isNotEmpty() }.single()
                assertTrue(shown.failed)
                delay(1_000)
                assertEquals(1, fake.sendMediaCalls)

                val again = assertNotNull(second.messages.retryOutgoing(saved, shown.id))
                second.messages.awaitOutgoing(again)
                assertEquals(2, fake.sendMediaCalls)
                assertContentEquals(bytes, fake.lastUpload)
                assertEquals(emptyList(), second.messages.outgoing(saved, null).first())
                second.telegram.close()
                database.close()
            }
        }
        dir.deleteRecursively()
    }
}
