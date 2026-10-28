package app.miogram

import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.rowPreviewOf
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.ktproto.api.types.MessageActionChatAddUser
import cn.ellerotta.ktproto.api.types.MessageActionChatJoinedByLink
import cn.ellerotta.ktproto.api.types.MessageActionChatJoinedByRequest
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class AddedMembersTest {
    @Test
    fun addsNameTheAddedAndJoinsStayJoins(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("9996624242")
                val me = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "22222")).profile.id
                fake.selfId = me
                val alice = 501L
                val bob = 502L
                fake.addAddUserMessage(alice, listOf(bob))
                fake.addAddUserMessage(alice, listOf(alice))
                fake.addAddUserMessage(alice, listOf(bob, me))
                fake.addAddUserMessage(me, listOf(bob), out = true)
                fake.addAddUserMessage(alice, listOf(me))
                fake.addAddUserMessage(alice, listOf(bob, me, alice, 999))

                val items = repos.messages.history("self").filter { it.service != null }.sortedBy { it.id }
                assertEquals(
                    listOf(
                        ServiceKind.Added to listOf("Bob"),
                        ServiceKind.Joined to emptyList(),
                        ServiceKind.Added to listOf("Bob", ""),
                        ServiceKind.Added to listOf("Bob"),
                        ServiceKind.Added to listOf(""),
                        ServiceKind.Added to listOf("Bob", "", "Alice"),
                    ),
                    items.map { it.service to it.added },
                )
                assertEquals(listOf("Alice", "Alice", "Alice", "Alice", "Alice"), items.filterNot { it.out }.map { it.senderName })
                assertEquals(listOf(false, false, false, true, false, false), items.map { it.out })

                val peers = repos.telegram.peers
                fun row(users: List<Long>, from: Long = alice) = peers.rowPreviewOf(
                    MessageService(id = 1, fromId = PeerUser(from), peerId = PeerChat(5), date = 0, action = MessageActionChatAddUser(users)),
                )
                assertEquals(ServiceKind.Added to listOf("Bob"), row(listOf(bob)).let { it.service to it.added })
                assertEquals(ServiceKind.Joined, row(listOf(alice)).service)
                assertEquals(listOf(""), row(listOf(me)).added)
                assertEquals(ServiceKind.Added to emptyList(), row(listOf(999L)).let { it.service to it.added })
                for (action in listOf(MessageActionChatJoinedByLink(inviterId = bob), MessageActionChatJoinedByRequest)) {
                    assertEquals(ServiceKind.Joined, peers.rowPreviewOf(MessageService(id = 2, fromId = PeerUser(alice), peerId = PeerChat(5), date = 0, action = action)).service)
                }
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
