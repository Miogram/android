package app.miogram

import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.SearchFilter
import cn.ellerotta.ktproto.api.types.InputMessagesFilterEmpty
import cn.ellerotta.ktproto.api.types.InputMessagesFilterUrl
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerUser
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class GlobalSearchTest {
    private fun scenario(test: suspend (FakeTelegram, TestRepositories) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                test(fake, repos)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun hitsArePagedByTheLastMessageAndOpenAsTheirChats() = scenario { fake, repos ->
        val s = fake.search
        s.messages[InputMessagesFilterEmpty] = (1..5).map { s.message(it, PeerUser(fake.alice.id), "lunch $it", age = 100 - it) } +
            s.message(9, PeerChannel(DEVS_ID), "lunch in devs", age = 1, from = fake.alice.id) +
            s.message(10, PeerUser(fake.bob.id), "dinner", age = 0)
        val first = repos.dialogs.searchMessages("lunch", limit = 4)
        assertEquals("lunch", s.calls.last().q)
        assertEquals(InputMessagesFilterEmpty, s.calls.last().filter)
        assertEquals(InputPeerEmpty, s.calls.last().offsetPeer)
        assertEquals(listOf("ch$DEVS_ID", "u501", "u501", "u501"), first.hits.map { it.dialog.key })
        assertEquals(listOf(9, 5, 4, 3), first.hits.map { it.messageId })
        assertEquals("lunch in devs", first.hits.first().dialog.preview.text)
        assertEquals("Alice", first.hits.first().dialog.preview.sender)
        val cursor = first.next!!
        assertEquals("u501", cursor.peerKey)
        assertEquals(3, cursor.messageId)

        val second = repos.dialogs.searchMessages("lunch", after = cursor, limit = 4)
        assertEquals(3, s.calls.last().offsetId)
        assertEquals(InputPeerUser(fake.alice.id, fake.alice.accessHash!!), s.calls.last().offsetPeer)
        assertEquals(listOf(2, 1), second.hits.map { it.messageId })
        assertNull(second.next)
    }

    @Test
    fun aKindOfMessageIsAskedForWithItsFilterAndAHashtagAsTyped() = scenario { fake, repos ->
        val s = fake.search
        s.messages[InputMessagesFilterUrl] = listOf(s.message(7, PeerUser(fake.bob.id), "see https://kotlinlang.org #kotlin"))
        val links = repos.dialogs.searchMessages("", SearchFilter.Links)
        assertEquals(InputMessagesFilterUrl, s.calls.last().filter)
        assertEquals("", s.calls.last().q)
        assertEquals(listOf("u502"), links.hits.map { it.dialog.key })
        repos.dialogs.searchMessages("#kotlin", SearchFilter.Links)
        assertEquals("#kotlin", s.calls.last().q)
        assertEquals(1, repos.dialogs.searchMessages("#kotlin", SearchFilter.Links).hits.size)
        assertEquals(0, repos.dialogs.searchMessages("#swift", SearchFilter.Links).hits.size)
    }

    @Test
    fun topPeersAreTheCorrespondentsWithoutTheOwnUser() = scenario { fake, repos ->
        assertEquals(listOf("u502", "u501"), repos.dialogs.topPeers().map { it.key })
    }
}
