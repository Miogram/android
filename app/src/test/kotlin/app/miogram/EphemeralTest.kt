package app.miogram

import app.miogram.data.EphemeralStore
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.EPHEMERAL_IDS
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputReplyToEphemeralMessage
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.PeerChannel
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class EphemeralTest {
    @Test
    fun ephemeralMessagesArriveChangeGoAndAreAnswered(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            fake.groups.inDialogs = true
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                repos.dialogs.dialogs()
                val key = assertIs<LinkTarget.Chat>(repos.links.open("tg://resolve?domain=helperbot")).dialog.key
                val group = "ch${FakeGroups.DEVS_ID}"
                val store = EphemeralStore(repos.updates, scope)
                store.start()
                delay(300)
                suspend fun until(check: (Map<String, List<MessageItem>>) -> Boolean) = withTimeout(10_000) { store.state.first(check) }

                val id = fake.ephemeral.botSays("Only you see this", button = "Reveal")
                val first = until { it[key]?.size == 1 }.getValue(key).single()
                assertEquals(EPHEMERAL_IDS + id, first.id)
                assertEquals(id, first.ephemeral?.id)
                assertEquals(key, first.ephemeral?.botKey)
                assertEquals("helperbot", first.ephemeral?.botUsername)
                assertTrue(!first.out && first.buttons.isNotEmpty() && first.reactions.isEmpty())

                val inGroup = fake.ephemeral.botSays("Group secret", peer = PeerChannel(FakeGroups.DEVS_ID), replyTo = id)
                val groupMessage = until { it[group]?.size == 1 }.getValue(group).single()
                assertEquals(EPHEMERAL_IDS + inGroup, groupMessage.id)
                assertEquals(EPHEMERAL_IDS + id, groupMessage.replyTo)

                fake.ephemeral.edit(id, "Only you see this, edited")
                assertEquals(EPHEMERAL_IDS + id, until { it[key]?.singleOrNull()?.text == "Only you see this, edited" }.getValue(key).single().id)

                repos.ephemeral.send(key, key, "thanks", replyToEphemeral = id)
                val sent = fake.ephemeral.sendCalls.last()
                assertNull(sent.peer)
                assertEquals(fake.helperBot.id, (sent.receiverId as InputUser).userId)
                assertEquals(id, (sent.replyTo as InputReplyToEphemeralMessage).id)
                val own = until { state -> state[key].orEmpty().any { it.out } }.getValue(key).single { it.out }
                assertEquals("thanks", own.text)
                assertEquals(EPHEMERAL_IDS + id, own.replyTo)
                assertEquals(key, own.ephemeral?.botKey)

                repos.ephemeral.send(group, key, "/secret@helperbot")
                assertEquals(FakeGroups.DEVS_ID, (fake.ephemeral.sendCalls.last().peer as InputPeerChannel).channelId)
                assertNull(fake.ephemeral.sendCalls.last().replyTo)

                assertEquals("Secret pong", repos.ephemeral.callback(key, id, "secret".toByteArray()).message)
                val pressed = fake.ephemeral.callbackCalls.single()
                assertEquals(fake.helperBot.id, (pressed.peer as InputPeerUser).userId)
                assertEquals(id, pressed.id)
                assertNull(repos.ephemeral.callback(key, id, "slow".toByteArray()).message)
                assertTrue(fake.bots.callbackCalls.isEmpty())

                repos.ephemeral.delete(key, first.ephemeral!!, out = false)
                assertEquals(InputUserSelf, fake.ephemeral.deleteCalls.last().receiverId)
                assertEquals(id, fake.ephemeral.deleteCalls.last().id)
                repos.ephemeral.delete(key, own.ephemeral!!, out = true)
                assertEquals(fake.helperBot.id, (fake.ephemeral.deleteCalls.last().receiverId as InputUser).userId)

                fake.ephemeral.delete(listOf(id))
                until { state -> state[key].orEmpty().none { it.ephemeral?.id == id } }
                assertEquals(1, store.state.value[group]?.size)
                store.clear()
                assertTrue(store.state.value.isEmpty())
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
