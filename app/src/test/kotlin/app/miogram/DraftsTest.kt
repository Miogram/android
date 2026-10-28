package app.miogram

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SignInResult
import app.miogram.feature.chat.ChatDraft
import cn.ellerotta.core.model.Draft
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputReplyToMessage
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class DraftsTest {
    private val soon = Draft(FormattedText("see you soon", listOf(TextEntity(8, 4, EntityType.Bold))), replyTo = 1)

    @Test
    fun savesRestoresAndFollowsDrafts(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(60_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("+999 66 1 4242"), "11111")).profile.id
                repos.dialogs.dialogs()

                repos.messages.saveDraft("u501", soon)
                val call = fake.saveDraftCalls.last()
                assertEquals(501L, (call.peer as InputPeerUser).userId)
                assertEquals("see you soon", call.message)
                assertEquals(listOf(MessageEntityBold(8, 4)), call.entities)
                assertEquals(1, (call.replyTo as InputReplyToMessage).replyToMsgId)

                val alice = repos.dialogs.dialogs().dialogs.single { it.key == "u501" }
                assertEquals(soon, alice.draft)
                assertTrue(alice.listPreview.draft)
                assertEquals("see you soon", alice.listPreview.text)
                assertEquals("hi from alice", alice.preview.text)

                repos.messages.saveDraft("u501", Draft(FormattedText("  "), replyTo = 1))
                val cleared = fake.saveDraftCalls.last()
                assertEquals("", cleared.message)
                assertNull(cleared.entities)
                assertNull(cleared.replyTo)
                assertNull(repos.dialogs.dialogs().dialogs.single { it.key == "u501" }.draft)
                repos.messages.saveDraft("u501", soon)
                repos.messages.send("u501", "hello")
                assertTrue(fake.lastSendMessage!!.clearDraft)
                assertNull(repos.dialogs.dialogs().dialogs.single { it.key == "u501" }.draft)

                val live = Channel<ChatEvent>(Channel.UNLIMITED)
                val listening = launch { repos.updates.events().collect { live.send(it) } }
                delay(300)
                fake.pushDraft(501, "from the laptop")
                val pushed = withTimeout(10_000) { nextDraft(live) }
                assertEquals("u501", pushed.key)
                assertEquals(Draft(FormattedText("from the laptop")), pushed.draft)
                assertEquals(FakeTelegram.NOW + 60, pushed.date)
                fake.pushDraft(501, null)
                assertNull(withTimeout(10_000) { nextDraft(live) }.draft)
                listening.cancel()
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun aDraftIsSavedAfterAPauseAndWhenLeaving(): Unit = runBlocking {
        val saves = CopyOnWriteArrayList<Draft>()
        val draft = ChatDraft(this, this, Draft.EMPTY, pause = 200) { saves += it }

        draft.remote(soon)
        assertEquals(soon, draft.restore.value)
        draft.restored()
        draft.changed(soon)
        draft.changed(Draft(FormattedText("see")))
        draft.changed(Draft(FormattedText("see you")))
        delay(600)
        assertEquals(listOf(Draft(FormattedText("see you"))), saves)

        draft.changed(Draft(FormattedText("see you there")))
        draft.remote(Draft(FormattedText("other device")))
        assertNull(draft.restore.value)
        draft.leave()
        delay(200)
        assertEquals(Draft(FormattedText("see you there")), saves.last())
        draft.remote(Draft(FormattedText("other device")))
        assertEquals(Draft(FormattedText("other device")), draft.restore.value)

        draft.restored()
        draft.changed(Draft(FormattedText("")))
        delay(600)
        assertEquals(Draft.EMPTY, saves.last())
        draft.changed(Draft(FormattedText("sent")))
        draft.sent()
        draft.changed(Draft.EMPTY)
        draft.leave()
        delay(600)
        assertEquals(3, saves.size)
    }

    @Test
    fun aRichDraftStaysUntilSomethingIsTyped(): Unit = runBlocking {
        val saves = CopyOnWriteArrayList<Draft>()
        val rich = Draft(FormattedText(""), article = ArticleMessage(listOf(ArticleBlock.Paragraph(ArticleText.Plain("rich")))))
        val draft = ChatDraft(this, this, rich, pause = 100) { saves += it }
        draft.changed(Draft.EMPTY)
        draft.leave()
        delay(300)
        assertEquals(emptyList(), saves)
        draft.changed(Draft(FormattedText("plain")))
        delay(300)
        assertEquals(listOf(Draft(FormattedText("plain"))), saves)
    }
}

private suspend fun nextDraft(events: Channel<ChatEvent>): ChatEvent.Draft {
    while (true) {
        val event = events.receive()
        if (event is ChatEvent.Draft) return event
    }
}
