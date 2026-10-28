package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.ktproto.api.types.InputMessagesFilterEmpty
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ChatSearchRepositoryTest {
    @Test
    fun searchesPageByPage(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                fake.addSavedMessages(60)
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key

                val first = repos.messages.search(saved, "note 1", limit = 5)
                val call = fake.searchCalls.last()
                assertEquals(InputPeerSelf, call.peer)
                assertEquals("note 1", call.q)
                assertEquals(InputMessagesFilterEmpty, call.filter)
                assertEquals(listOf(0, 0, 5), listOf(call.offsetId, call.addOffset, call.limit))
                assertEquals(listOf(0, 0, 0, 0), listOf(call.minDate, call.maxDate, call.maxId, call.minId))
                assertEquals(listOf(29, 28, 27, 26, 25), first.messages.map { it.id })
                assertEquals(11, first.total)

                val second = repos.messages.search(saved, "note 1", offsetId = first.messages.last().id, limit = 5)
                assertEquals(25, fake.searchCalls.last().offsetId)
                assertEquals(listOf(24, 23, 22, 21, 20), second.messages.map { it.id })
                val third = repos.messages.search(saved, "note 1", offsetId = second.messages.last().id, limit = 5)
                assertEquals(listOf(11), third.messages.map { it.id })
                assertEquals("note 1", third.messages.single().text)

                assertEquals(0, repos.messages.search(saved, "nothing like this").total)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
