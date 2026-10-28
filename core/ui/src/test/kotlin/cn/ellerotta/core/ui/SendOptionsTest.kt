package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.AttachmentDraft
import cn.ellerotta.core.model.AttachmentItem
import cn.ellerotta.core.model.AttachmentKind
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.ui.components.chat.AttachmentContent
import cn.ellerotta.core.ui.components.chat.ChatAttachmentActions
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatSendActions
import cn.ellerotta.core.ui.components.chat.ScheduleDialog
import cn.ellerotta.core.ui.components.chat.ScheduledActions
import cn.ellerotta.core.ui.components.chat.ScheduledScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SendOptionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val photo = AttachmentItem(1, "cat.jpg", AttachmentKind.Photo, 2_000)
    private val video = AttachmentItem(2, "clip.mp4", AttachmentKind.Video, 9_000)
    private val song = AttachmentItem(3, "song.mp3", AttachmentKind.Audio, 4_000)
    private val notes = AttachmentItem(4, "notes.pdf", AttachmentKind.File, 1_000)

    private val calls = mutableListOf<String>()
    private val sent = mutableListOf<Pair<FormattedText, SendOptions>>()
    private val actions = ChatAttachmentActions(
        onAsFile = { calls += "asFile $it" },
        onSpoiler = { calls += "spoiler $it" },
        onRemove = { calls += "remove $it" },
        onSend = { caption, options -> sent += caption to options },
        onDismiss = { calls += "dismiss" },
    )

    private fun content(draft: AttachmentDraft, whenOnline: Boolean = false) {
        compose.setContent { MioTheme { AttachmentContent(draft, actions, whenOnline) } }
    }

    @Test
    fun theSheetNamesTheFilesAndOffersTheSpoilerOnlyForPhotosAndVideos() {
        content(AttachmentDraft(listOf(photo, video, song)))
        compose.onNodeWithTag("attachmentTitle").assertIsDisplayed()
        compose.onNodeWithText("Send 3 items").assertIsDisplayed()
        compose.onNodeWithTag("attachmentTile_1").assertIsDisplayed()
        compose.onNodeWithTag("attachmentTile_3").assertIsDisplayed()
        compose.onNodeWithTag("attachmentSpoiler").assertIsDisplayed()
        compose.onNodeWithTag("attachmentAsFile").assertIsDisplayed()
    }

    @Test
    fun noSpoilerForFilesOrWhenEverythingGoesAsFiles() {
        content(AttachmentDraft(listOf(song, notes)))
        compose.onNodeWithTag("attachmentSpoiler").assertDoesNotExist()
        compose.onNodeWithText("Send 2 items").assertIsDisplayed()
    }

    @Test
    fun asFileHidesTheSpoilerAndTheTogglesReport() {
        var draft by mutableStateOf(AttachmentDraft(listOf(photo)))
        compose.setContent {
            MioTheme {
                AttachmentContent(
                    draft,
                    ChatAttachmentActions({ draft = draft.copy(asFile = it) }, { draft = draft.copy(spoiler = it) }, {}, { _, _ -> }, {}),
                    whenOnline = false,
                )
            }
        }
        compose.onNodeWithText("Send 1 item").assertIsDisplayed()
        compose.onNodeWithTag("attachmentSpoiler").performClick()
        assertTrue(draft.spoiler)
        compose.onNodeWithTag("attachmentHidden_1").assertIsDisplayed()
        compose.onNodeWithTag("attachmentAsFile").performClick()
        assertTrue(draft.asFile)
        compose.onNodeWithTag("attachmentSpoiler").assertDoesNotExist()
        compose.onNodeWithTag("attachmentHidden_1").assertDoesNotExist()
    }

    @Test
    fun aCrossTakesAFileOut() {
        content(AttachmentDraft(listOf(photo, notes)))
        compose.onNodeWithTag("attachmentRemove_4").performClick()
        assertEquals(listOf("remove 4"), calls)
    }

    @Test
    fun theCaptionGoesOutWithItsFormatting() {
        content(AttachmentDraft(listOf(photo)))
        compose.onNodeWithTag("attachmentCaption").performTextInput("  look  ")
        compose.onNodeWithTag("attachmentSend").performClick()
        assertEquals(listOf(FormattedText("look") to SendOptions.None), sent)
    }

    @Test
    fun aLongPressOnSendSendsWithoutSound() {
        content(AttachmentDraft(listOf(photo)))
        compose.onNodeWithTag("attachmentCaption").performTextInput("psst")
        compose.onNodeWithTag("attachmentSend").performTouchInput { longClick() }
        compose.onNodeWithTag("sendSilent").assertIsDisplayed()
        compose.onNodeWithTag("sendWhenOnline").assertDoesNotExist()
        compose.onNodeWithTag("sendSilent").performClick()
        assertEquals(listOf(FormattedText("psst") to SendOptions(silent = true)), sent)
    }

    @Test
    fun sendWhenOnlineIsOfferedWhereTheRecipientHasALastSeen() {
        content(AttachmentDraft(listOf(photo)), whenOnline = true)
        compose.onNodeWithTag("attachmentSend").performTouchInput { longClick() }
        compose.onNodeWithTag("sendWhenOnline").performClick()
        assertEquals(listOf(FormattedText("") to SendOptions(scheduleDate = SendOptions.WHEN_ONLINE)), sent)
    }

    @Test
    fun scheduleAsksForTheDayThenTheTimeAndSendsForThen() {
        content(AttachmentDraft(listOf(photo)))
        val before = (System.currentTimeMillis() / 1000).toInt()
        compose.onNodeWithTag("attachmentSend").performTouchInput { longClick() }
        compose.onNodeWithTag("sendSchedule").performClick()
        compose.onNodeWithTag("scheduleDateOk").performClick()
        compose.onNodeWithTag("scheduleTimeOk").assertIsEnabled()
        compose.onNodeWithTag("scheduleTimeOk").performClick()
        val (_, options) = sent.single()
        val date = assertNotNull(options.scheduleDate)
        assertTrue(date in before + 3_500..before + 3_700, "scheduled for $date, now $before")
        assertTrue(!options.silent)
    }

    @Test
    fun aTimeLessThanAMinuteAheadCannotBePicked() {
        val now = 1_900_000_000L
        var confirmed: Int? = null
        compose.setContent { MioTheme { ScheduleDialog(onConfirm = { confirmed = it }, onDismiss = {}, initial = now + 30, now = { now }) } }
        compose.onNodeWithTag("scheduleDateOk").performClick()
        compose.onNodeWithTag("schedulePast").assertIsDisplayed()
        compose.onNodeWithTag("scheduleTimeOk").assertIsNotEnabled()
        org.junit.Assert.assertNull(confirmed)
    }

    @Test
    fun aTimeAMinuteAheadIsConfirmedAsPicked() {
        val now = 1_900_000_000L
        var confirmed: Int? = null
        compose.setContent { MioTheme { ScheduleDialog(onConfirm = { confirmed = it }, onDismiss = {}, initial = now + 7_200, now = { now }) } }
        compose.onNodeWithTag("scheduleDateOk").performClick()
        compose.onNodeWithTag("schedulePast").assertDoesNotExist()
        compose.onNodeWithTag("scheduleTimeOk").performClick()
        assertEquals(((now + 7_200) / 60 * 60).toInt(), confirmed)
    }

    private val user = DialogItem("u7", "Alice", kind = PeerKind.User, presence = Presence.Offline(1_700_000_000))

    @Test
    fun theChatsLongPressOnSendAnswersTheRepliedMessageAndTellsTheScreen() {
        val replying = mutableListOf<Int?>()
        val texts = mutableListOf<Triple<FormattedText, Int?, SendOptions>>()
        val messages = listOf(MessageItem(id = 1, text = "question", out = false, date = 1_700_000_000))
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Alice", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = user,
                    sending = ChatSendActions({ text, replyTo, options -> texts += Triple(text, replyTo, options) }, {}),
                    onReplying = { replying += it },
                )
            }
        }
        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.onNodeWithTag("actionReply").performClick()
        compose.onNodeWithTag("messageInput").performTextInput("sure")
        compose.waitForIdle()
        assertEquals(listOf<Int?>(null, 1), replying)

        compose.onNodeWithTag("send").performTouchInput { longClick() }
        compose.onNodeWithTag("sendWhenOnline").assertIsDisplayed()
        compose.onNodeWithTag("sendSilent").performClick()
        assertEquals(listOf(Triple<FormattedText, Int?, SendOptions>(FormattedText("sure"), 1, SendOptions(silent = true))), texts)
        compose.waitForIdle()
        compose.onNodeWithTag("composerStrip").assertDoesNotExist()
        assertEquals(listOf<Int?>(null, 1, null), replying)
    }

    @Test
    fun noWhenOnlineForAChatWithoutALastSeen() {
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Alice", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("u7", "Alice", kind = PeerKind.User, presence = Presence.Recently),
                    sending = ChatSendActions({ _, _, _ -> }, {}),
                )
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("hi")
        compose.onNodeWithTag("send").performTouchInput { longClick() }
        compose.onNodeWithTag("sendSilent").assertIsDisplayed()
        compose.onNodeWithTag("sendSchedule").assertIsDisplayed()
        compose.onNodeWithTag("sendWhenOnline").assertDoesNotExist()
    }

    @Test
    fun aTapOnSendStillSendsPlainly() {
        val plain = mutableListOf<FormattedText>()
        val options = mutableListOf<SendOptions>()
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Alice", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = { plain += it }, onDownload = {},
                    dialog = user,
                    sending = ChatSendActions({ _, _, o -> options += o }, {}),
                )
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("hi")
        compose.onNodeWithTag("send").performClick()
        assertEquals(listOf(FormattedText("hi")), plain)
        assertTrue(options.isEmpty())
    }

    @Test
    fun theBarCountsTheScheduledMessagesAndOpensThem() {
        var opened = 0
        var count by mutableStateOf(0)
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Alice", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = user, scheduledCount = count, onOpenScheduled = { opened++ },
                )
            }
        }
        compose.onNodeWithTag("scheduledBar").assertDoesNotExist()
        count = 2
        compose.waitForIdle()
        compose.onNodeWithText("2 scheduled messages").assertIsDisplayed()
        compose.onNodeWithTag("scheduledBar").performClick()
        assertEquals(1, opened)
        count = 1
        compose.waitForIdle()
        compose.onNodeWithText("1 scheduled message").assertIsDisplayed()
    }

    private val soon = MessageItem(id = 11, text = "soon", out = true, date = 1_900_000_000)
    private val later = MessageItem(id = 12, text = "later", out = true, date = 1_900_100_000, media = MediaItem("doc1", "report.pdf", 100, 2, isPhoto = false))
    private val online = MessageItem(id = 13, text = "when you are around", out = true, date = SendOptions.WHEN_ONLINE)

    private val scheduledCalls = mutableListOf<String>()
    private val scheduledActions = ScheduledActions(
        onSendNow = { scheduledCalls += "now ${it.id}" },
        onReschedule = { message, date -> scheduledCalls += "move ${message.id} $date" },
        onEdit = { message, text -> scheduledCalls += "edit ${message.id} ${text.text}" },
        onDelete = { scheduledCalls += "delete ${it.id}" },
    )

    private fun screen(messages: List<MessageItem>, loaded: Boolean = true) {
        compose.setContent { MioTheme { ScheduledScreen("Scheduled messages", messages, loaded, scheduledActions, onBack = {}) } }
    }

    @Test
    fun scheduledMessagesShowTheirDateTextAndFile() {
        screen(listOf(soon, later, online))
        compose.onNodeWithTag("scheduled_11").assertIsDisplayed()
        compose.onNodeWithText("soon").assertIsDisplayed()
        compose.onNodeWithText("report.pdf").assertIsDisplayed()
        compose.onNodeWithText("Sends when the recipient is online").assertIsDisplayed()
        compose.onNodeWithTag("scheduledEmpty").assertDoesNotExist()
    }

    @Test
    fun anEmptyListSaysSoOnlyOnceLoaded() {
        screen(emptyList(), loaded = false)
        compose.onNodeWithTag("scheduledEmpty").assertDoesNotExist()
    }

    @Test
    fun aLoadedEmptyListSaysSo() {
        screen(emptyList())
        compose.onNodeWithTag("scheduledEmpty").assertIsDisplayed()
    }

    @Test
    fun aTapOffersSendNowRescheduleEditAndDelete() {
        screen(listOf(soon, later))
        compose.onNodeWithTag("scheduled_11").performClick()
        compose.onNodeWithTag("scheduledSendNow").performClick()
        assertEquals(listOf("now 11"), scheduledCalls)

        compose.onNodeWithTag("scheduled_12").performClick()
        compose.onNodeWithTag("scheduledEdit").performClick()
        compose.onNodeWithTag("scheduledEditField").performTextInput(" again")
        compose.onNodeWithTag("scheduledEditSave").performClick()
        assertEquals("edit 12 later again", scheduledCalls.last())
    }

    @Test
    fun rescheduleStartsAtTheMessagesDateAndMovesIt() {
        screen(listOf(soon))
        compose.onNodeWithTag("scheduled_11").performClick()
        compose.onNodeWithTag("scheduledReschedule").performClick()
        compose.onNodeWithTag("scheduleDateOk").performClick()
        compose.onNodeWithTag("schedulePast").assertDoesNotExist()
        compose.onNodeWithTag("scheduleTimeOk").performClick()
        assertEquals(listOf("move 11 ${1_900_000_000 / 60 * 60}"), scheduledCalls)
    }

    @Test
    fun deletingAsksFirst() {
        screen(listOf(soon, later))
        compose.onNodeWithTag("scheduled_12").performClick()
        compose.onNodeWithTag("scheduledDelete").performClick()
        assertEquals(emptyList(), scheduledCalls)
        compose.onNodeWithTag("confirmDeleteScheduled").performClick()
        assertEquals(listOf("delete 12"), scheduledCalls)
    }
}
