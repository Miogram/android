package app.miogram

import app.miogram.data.BotDraftsState
import app.miogram.data.BotDraftsStore
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.ChatAction
import cn.ellerotta.core.model.plainText
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.SendMessageStopDraftAction
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class StreamingTest {
    @Test
    fun appConfigIsKeptWithItsHash(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.streams.draftTtl = 12
                val time = TestTimeSource()
                repos.appConfig.timeSource = time

                assertEquals(12, repos.appConfig.config().typingDraftTtl)
                assertEquals(12, repos.appConfig.config().typingDraftTtl)
                assertEquals(listOf(0), fake.streams.appConfigHashes)

                time += 2.hours
                assertEquals(12, repos.appConfig.config().typingDraftTtl)
                assertEquals(2, fake.streams.appConfigHashes.size)
                assertTrue(fake.streams.appConfigHashes.last() != 0)

                fake.streams.draftTtl = 7
                time += 2.hours
                assertEquals(7, repos.appConfig.config().typingDraftTtl)
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun draftsStreamStopExpireAndGiveWayToTheBotsMessage(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                val profile = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile
                fake.selfId = profile.id
                repos.dialogs.dialogs()
                val time = TestTimeSource()
                repos.appConfig.timeSource = time
                val store = BotDraftsStore(repos.updates, repos.appConfig, scope)
                store.start()
                delay(300)
                val bot = fake.helperBot.id
                val key = "u$bot"
                suspend fun until(check: (BotDraftsState) -> Boolean) = withTimeout(10_000) { store.state.first(check) }

                fake.streams.text(bot, 1, "Thinking", canStop = true)
                val first = until { it.drafts.singleOrNull()?.message?.text == "Thinking" }.drafts.single()
                assertEquals(key, first.key)
                assertTrue(first.message.streaming && first.message.id < 0 && first.canStop)
                fake.streams.text(bot, 1, "Thinking harder", canStop = true)
                val grown = until { it.drafts.singleOrNull()?.message?.text == "Thinking harder" }.drafts.single()
                assertEquals(first.message.id, grown.message.id)

                fake.streams.rich(bot, 2, "Rich so far")
                val rich = until { state -> state.drafts.any { it.randomId == 2L } }.drafts.single()
                assertEquals("Rich so far", rich.message.article?.plainText())
                assertTrue(rich.message.id != first.message.id)
                fake.streams.inGroup(9_003, bot, 3, "in the group")
                assertEquals(setOf(2L, 3L), until { it.drafts.size == 2 }.drafts.map { it.randomId }.toSet())
                assertEquals("ch9003", store.state.value.drafts.first { it.randomId == 3L }.key)

                fake.streams.text(bot, 4, "Stop me", canStop = true)
                until { state -> state.drafts.any { it.randomId == 4L } }
                assertEquals(4L, store.stop(4)?.randomId)
                assertTrue(store.state.value.drafts.none { it.randomId == 4L })
                fake.streams.text(bot, 4, "ignored", canStop = true)
                fake.streams.inGroup(9_003, bot, 5, "after the stop")
                until { state -> state.drafts.any { it.randomId == 5L } }
                assertTrue(store.state.value.drafts.none { it.randomId == 4L })

                fake.streams.text(bot, 6, "Kept", canStop = true, keepOnStop = true)
                until { state -> state.drafts.any { it.randomId == 6L } }
                assertNotNull(store.stop(6))
                val kept = store.state.value.drafts.single { it.randomId == 6L }
                assertEquals(false, kept.canStop)

                val final = fake.cluster.updates.message(bot, "Kept and done")
                val replaced = until { state -> state.drafts.any { it.replacedBy == final.id } }
                assertEquals(kept.message.id, replaced.replaced[key]?.get(final.id))

                fake.streams.draftTtl = 2
                time += 2.hours
                fake.streams.text(bot, 7, "Soon gone")
                until { state -> state.drafts.any { it.randomId == 7L } }
                until { state -> state.drafts.none { it.randomId == 7L } }

                repos.messages.setTyping(key, ChatAction.StopDraft(6))
                val call = fake.typingCalls.last()
                assertEquals(bot, (call.peer as InputPeerUser).userId)
                assertEquals(6L, assertIs<SendMessageStopDraftAction>(call.action).randomId)
                assertNull(call.topMsgId)
                store.clear()
            }
            scope.cancel()
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }
}
