package app.miogram

import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.data.telegram.UnknownPeerException
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputPeerUserFromMessage
import cn.ellerotta.ktproto.client.FilePeerStorage
import cn.ellerotta.ktproto.client.InMemorySessionStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class PeerCacheFlowTest {
    private suspend fun TestRepositories.logIn(fake: FakeTelegram) {
        telegram.connect(12345, "hash")
        val request = auth.sendCode("9996624242")
        fake.selfId = assertIs<SignInResult.LoggedIn>(auth.signIn(request, "22222")).profile.id
    }

    @Test
    fun peersSurviveARestartThroughTheCacheFile(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val session = InMemorySessionStorage()
            val file = File(dir, "test.peers")
            fun holder() = TelegramClientHolder(dir, peerStorage = { FilePeerStorage(file) }) { id, hash, logger -> fake.config(id, hash, logger, session) }
            withTimeout(90_000) {
                val first = TestRepositories(dir, fake, holder())
                first.logIn(fake)
                first.dialogs.dialogs()
                assertEquals(9L, assertIs<InputPeerUser>(first.telegram.peers.inputPeer("u501")).accessHash)
                assertNull(first.telegram.peers.inputPeer("u503"))
                assertFailsWith<UnknownPeerException> { first.messages.send("u503", "hi") }
                first.telegram.close()
                assertTrue(file.length() > 0)

                val second = TestRepositories(dir, fake, holder())
                assertTrue(second.telegram.prepare(12345, "hash"))
                assertEquals(9L, assertIs<InputPeerUser>(second.telegram.peers.inputPeer("u501")).accessHash)
                second.dialogs.setMuted("u501", true)
                assertTrue(501L in fake.muted)
                second.telegram.close()
            }
            dir.deleteRecursively()
        }
    }

    @Test
    fun aMinUserFromAGroupMessageIsAddressedThroughThatMessage(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.logIn(fake)
                fake.joinedChannels += FakeTelegram.BOOK_CLUB_ID
                val club = assertIs<LinkTarget.Chat>(repos.links.open("https://t.me/+${FakeTelegram.BOOK_CLUB_INVITE}")).dialog
                assertEquals("ch${FakeTelegram.BOOK_CLUB_ID}", club.key)
                val messages = repos.messages.history(club.key)
                assertEquals(listOf("chapter two tonight"), messages.map { it.text })

                val dave = assertIs<InputPeerUserFromMessage>(repos.telegram.peers.inputPeer("u505"))
                assertEquals(FakeTelegram.BOOK_CLUB_ID, assertIs<InputPeerChannel>(dave.peer).channelId)
                assertEquals(3 to 505L, dave.msgId to dave.userId)

                assertEquals("Dave", repos.profiles.profile("u505").name)
                assertEquals(FakeTelegram.DAVE_HASH, assertIs<InputPeerUser>(repos.telegram.peers.inputPeer("u505")).accessHash)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
