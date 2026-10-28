package app.miogram

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.ktproto.api.types.UpdatePinnedChannelMessages
import cn.ellerotta.ktproto.api.types.UpdateShort
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class PinsRepositoryTest {
    @Test
    fun pinsListPinUnpinUnpinAllAndFollow(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                fake.addSavedMessages(4)
                val saved = repos.dialogs.dialogs().dialogs.first { it.isSaved }.key
                val ids = repos.messages.history(saved).filter { it.text.startsWith("note ") }.map { it.id }.sorted()
                fake.pins.pinned += listOf(ids[0], ids[2])

                assertEquals(listOf(ids[2] to "note 2", ids[0] to "note 0"), repos.pins.pinned(saved).map { it.id to it.text })

                coroutineScope {
                    val live = Channel<ChatEvent>(Channel.UNLIMITED)
                    val listening = launch { repos.updates.events().collect { live.send(it) } }
                    delay(300)

                    repos.pins.pin(saved, ids[3], silent = true, forBoth = false)
                    val call = fake.pins.pinCalls.last()
                    assertEquals(Triple(true, true, false), Triple(call.silent, call.pmOneside, call.unpin))
                    assertEquals(ChatEvent.MessagesPinned(saved, listOf(ids[3]), pinned = true), withTimeout(10_000) { nextPin(live) })
                    assertEquals(listOf(ids[3], ids[2], ids[0]), repos.pins.pinned(saved).map { it.id })

                    repos.pins.pin(saved, ids[1])
                    assertEquals(Triple(false, false, false), fake.pins.pinCalls.last().let { Triple(it.silent, it.pmOneside, it.unpin) })
                    withTimeout(10_000) { nextPin(live) }

                    repos.pins.unpin(saved, ids[2])
                    assertTrue(fake.pins.pinCalls.last().unpin)
                    assertEquals(ChatEvent.MessagesPinned(saved, listOf(ids[2]), pinned = false), withTimeout(10_000) { nextPin(live) })

                    fake.pins.pushPinned(listOf(ids[2]), pinned = true)
                    assertEquals(ChatEvent.MessagesPinned(saved, listOf(ids[2]), pinned = true), withTimeout(10_000) { nextPin(live) })

                    fake.cluster.updates.push(UpdateShort(UpdatePinnedChannelMessages(pinned = true, channelId = 4242, messages = listOf(7), pts = 3, ptsCount = 1), FakeTelegram.NOW))
                    assertEquals(ChatEvent.MessagesPinned("ch4242", listOf(7), pinned = true), withTimeout(10_000) { nextPin(live) })
                    listening.cancel()
                }

                fake.pins.unpinAllRounds = 2
                repos.pins.unpinAll(saved)
                assertEquals(2, fake.pins.unpinAllCalls.size)
                assertFalse(fake.pins.unpinAllCalls.any { it.topMsgId != null })
                assertTrue(repos.pins.pinned(saved).isEmpty())
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}

private suspend fun nextPin(events: Channel<ChatEvent>): ChatEvent.MessagesPinned {
    while (true) {
        val event = events.receive()
        if (event is ChatEvent.MessagesPinned) return event
    }
}
