package cn.ellerotta.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.ForwardInfo
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.model.VoiceRecording
import cn.ellerotta.core.ui.components.chat.BubblePosition
import cn.ellerotta.core.ui.components.chat.ChatEntry
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.bubbleShape
import cn.ellerotta.core.ui.components.chat.entries
import cn.ellerotta.core.ui.components.chat.voiceTime
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import java.time.ZoneOffset
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    @Test
    fun bubblesStickersAndComposer() {
        val sent = mutableListOf<FormattedText>()
        var profiles = 0
        val messages = listOf(
            MessageItem(1, "", out = false, date = now - 100, senderName = "Bob", service = ServiceKind.Joined),
            MessageItem(2, "hello there", out = false, date = now - 90, senderName = "Bob", senderId = 7),
            MessageItem(3, "second in a run", out = false, date = now - 80, senderName = "Bob", senderId = 7),
            MessageItem(4, "", out = true, date = now - 70, media = MediaItem("doc9", "s.tgs", 10, 2, false, kind = MediaKind.Sticker, emoji = "🐱", animated = true)),
            MessageItem(5, "mine", out = true, date = now - 60, read = true),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Team", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = { sent += it }, onDownload = {},
                    onOpenProfile = { profiles++ }, dialog = DialogItem("c5", "Team", kind = PeerKind.Group, typing = "Bob"),
                )
            }
        }
        compose.onNodeWithText("Bob is typing").assertIsDisplayed()
        compose.onNodeWithText("🐱").assertIsDisplayed()
        compose.onNodeWithTag("messages").performScrollToNode(hasText("hello there"))
        compose.onNodeWithText("Bob").assertIsDisplayed()
        compose.onNodeWithTag("messages").performScrollToNode(hasText("Today"))
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Bob joined the group").assertIsDisplayed()
        compose.onNodeWithTag("send").assertDoesNotExist()
        compose.onNodeWithTag("messageInput").performTextInput("  hi  ")
        compose.onNodeWithTag("send").performClick()
        assertEquals(listOf(FormattedText("hi")), sent)
        compose.onNodeWithTag("title").performClick()
        assertEquals(1, profiles)
    }

    @Test
    fun runsGetTightInnerCorners() {
        assertEquals(RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 20.dp), bubbleShape(out = true, BubblePosition(first = false, last = false)))
        assertEquals(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp), bubbleShape(out = false, BubblePosition(first = true, last = false)))
    }

    @Test
    fun runsEndAfterTenMinutesAnInlineKeyboardOrAnotherForward() {
        fun positions(vararg messages: MessageItem) =
            entries(messages.toList(), ZoneOffset.UTC).filterIsInstance<ChatEntry.Bubble>().reversed().map { it.position.first to it.position.last }
        val base = 1_700_000_000
        assertEquals(
            listOf(true to false, false to true),
            positions(MessageItem(1, "a", out = false, date = base, senderId = 7), MessageItem(2, "b", out = false, date = base + 540, senderId = 7)),
        )
        assertEquals(
            listOf(true to true, true to true),
            positions(MessageItem(1, "a", out = false, date = base, senderId = 7), MessageItem(2, "b", out = false, date = base + 660, senderId = 7)),
        )
        assertEquals(
            listOf(true to true, true to true),
            positions(MessageItem(1, "a", out = false, date = base, senderId = 7, buttons = listOf(listOf(BotButton.Callback("Go", byteArrayOf(1))))), MessageItem(2, "b", out = false, date = base + 5, senderId = 7)),
        )
        assertEquals(
            listOf(true to true, true to true),
            positions(MessageItem(1, "a", out = false, date = base, senderId = 7), MessageItem(2, "b", out = false, date = base + 5, senderId = 7, forward = ForwardInfo("News"))),
        )
    }

    @Test
    fun groupsShowTheSendersPhotoAtTheEndOfEachRun() {
        val messages = listOf(
            MessageItem(1, "first", out = false, date = now - 30, senderName = "Bob", senderId = 7),
            MessageItem(2, "second", out = false, date = now - 20, senderName = "Bob", senderId = 7),
            MessageItem(3, "mine", out = true, date = now - 10),
        )
        var kind by mutableStateOf(PeerKind.Group)
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Team", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("c5", "Team", kind = kind),
                )
            }
        }
        compose.onNodeWithTag("senderAvatar_2").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("senderAvatar_1")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("senderAvatar_3")).fetchSemanticsNodes().isEmpty())
        kind = PeerKind.User
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("senderAvatar_2")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theSendersPhotoFloatsAlongALongRunAndOpensTheirProfile() {
        val messages = (1..30).map { MessageItem(it, "line $it", out = false, date = now - 300 + it, senderName = "Bob", senderId = 7, senderKey = "u7") }
        val opened = mutableListOf<String?>()
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Team", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("c5", "Team", kind = PeerKind.Group), onOpenSender = { opened += it.senderKey },
                )
            }
        }
        compose.onNodeWithTag("senderAvatar_30").assertIsDisplayed()
        compose.onNodeWithTag("messages").performScrollToNode(hasText("line 5"))
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasText("line 30")).fetchSemanticsNodes().isEmpty())
        val avatar = compose.onNodeWithTag("senderAvatar_30").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("messages").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithTag("messageInput").fetchSemanticsNode().boundsInRoot
        assertTrue(avatar.bottom <= input.top && avatar.bottom > list.bottom - 3 * input.height - avatar.height, "photo at ${avatar.bottom}, list ends at ${list.bottom}, field at ${input.top}")
        compose.onNodeWithTag("senderAvatar_30").performClick()
        assertEquals(listOf<String?>("u7"), opened)
    }

    @Test
    fun filesAndSongsOfAnAlbumShareOneBubble() {
        fun file(id: Int, name: String, caption: String = "", kind: MediaKind = MediaKind.File) =
            MessageItem(id, caption, out = false, date = now - 50, senderId = 7, groupedId = 9, media = MediaItem("doc$id", name, 1000, 2, false, kind = kind))
        val grouped = entries(listOf(file(1, "a.pdf"), file(2, "b.pdf"), file(3, "c.mp3", kind = MediaKind.Music)), ZoneOffset.UTC).filterIsInstance<ChatEntry.Bubble>()
        assertEquals(listOf(listOf(3), listOf(1, 2)), grouped.map { row -> row.album.ifEmpty { listOf(row.message) }.map { it.id } })
        val messages = listOf(file(1, "report.pdf", "the report"), file(2, "slides.pdf", "and the slides"))
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Bob", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        compose.onNodeWithTag("documents_1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("message_1").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("message_2")).fetchSemanticsNodes().isEmpty())
        for (text in listOf("report.pdf", "the report", "slides.pdf", "and the slides")) {
            compose.onNode(hasText(text, substring = true), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun albumsGrowWiderThan310OnWideScreens() {
        val photos = (1..3).map { MessageItem(it, "", out = true, date = now - 50, groupedId = 5, media = MediaItem("p$it", "p$it.jpg", 1000, 2, true, kind = MediaKind.Photo, width = 1600, height = 1200)) }
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Bob", messages = photos, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        val album = compose.onNodeWithTag("album", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        val width = with(album.layoutInfo.density) { album.size.width.toDp() }
        assertTrue(width > 500.dp, "album $width wide")
    }

    @Test
    fun theDayStaysAtTheTopWhileScrolling() {
        val yesterday = (1..40).map { MessageItem(it, "old $it", out = it % 2 == 0, date = now - 86_400 + it) }
        val today = (41..80).map { MessageItem(it, "new $it", out = it % 2 == 0, date = now - 80 + it) }
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Bob", messages = yesterday + today, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("stickyDay")).fetchSemanticsNodes().isEmpty())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("messages").performTouchInput { swipeDown(startY = centerY - 100f, endY = centerY + 100f, durationMillis = 400) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNode(hasText("Today") and hasAnyAncestor(hasTestTag("stickyDay")), useUnmergedTree = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(5_000)
        assertTrue(compose.onAllNodes(hasTestTag("stickyDay")).fetchSemanticsNodes().isEmpty())
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun attachMenuOffersMediaAndFiles() {
        val picked = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    onAttachMedia = { picked += "media" }, onAttachFile = { picked += "file" },
                )
            }
        }
        compose.onNodeWithTag("attach").performClick()
        compose.onNodeWithTag("attachMedia").performClick()
        compose.onNodeWithTag("attach").performClick()
        compose.onNodeWithTag("attachFile").performClick()
        assertEquals(listOf("media", "file"), picked)
    }

    @Test
    fun scrollingUpAsksForOlderMessages() {
        var asked by mutableIntStateOf(0)
        val messages = List(60) { MessageItem(id = it + 1, text = "message $it", out = it % 2 == 0, date = 1_700_000_000 + it * 60) }
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    loadingOlder = asked > 0, onLoadOlder = { asked++ },
                )
            }
        }
        compose.waitForIdle()
        assertEquals(0, asked)
        compose.onNodeWithTag("messages").performScrollToNode(hasText("message 0"))
        compose.waitUntil(5_000) { asked > 0 }
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("loadingOlder"))
    }

    @Test
    fun anOwnMessageScrollsDownEvenWhenScrolledUp() {
        var messages by mutableStateOf(List(40) { MessageItem(id = it + 1, text = "message $it", out = false, date = 1_700_000_000 + it * 60) })
        compose.setContent {
            MioTheme { ChatScreen(title = "Alice", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}) }
        }
        compose.onNodeWithTag("messages").performScrollToNode(hasText("message 0"))
        messages = messages + MessageItem(41, "from alice", out = false, date = 1_700_100_000)
        compose.waitForIdle()
        compose.onNodeWithText("message 0").assertIsDisplayed()
        compose.onNodeWithText("from alice").assertDoesNotExist()
        messages = messages + MessageItem(42, "my reply", out = true, date = 1_700_100_060, pending = true)
        compose.waitForIdle()
        compose.onNodeWithText("my reply").assertIsDisplayed()
        compose.onNodeWithText("message 0").assertDoesNotExist()
    }

    @Test
    fun longPressRepliesEditsAndDeletes() {
        val replies = mutableListOf<Pair<FormattedText, Int>>()
        val edits = mutableListOf<Pair<Int, FormattedText>>()
        val deletes = mutableListOf<Pair<Int, Boolean>>()
        val messages = listOf(
            MessageItem(id = 1, text = "question", out = false, date = 1_700_000_000),
            MessageItem(id = 2, text = "my answer", out = true, date = 1_700_000_060, replyTo = 1),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Alice", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("u7", "Alice", kind = PeerKind.User),
                    onSendReply = { text, id -> replies += text to id },
                    onEdit = { id, text -> edits += id to text },
                    onDelete = { id, revoke -> deletes += id to revoke },
                )
            }
        }
        compose.onNodeWithTag("replyQuote", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.onNodeWithTag("actionReply").performClick()
        compose.onNodeWithTag("composerStrip").assertIsDisplayed()
        compose.onNodeWithTag("messageInput").performTextInput("sure")
        compose.onNodeWithTag("send").performClick()
        assertEquals(listOf(FormattedText("sure") to 1), replies)

        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.onNodeWithTag("actionEdit").assertDoesNotExist()
        compose.onNodeWithTag("actionDelete").performClick()
        compose.onNodeWithText("Also delete for Alice").assertIsDisplayed()
        compose.onNodeWithTag("confirmDelete").performClick()
        assertEquals(listOf(1 to true), deletes)

        compose.onNodeWithTag("messageText_2", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("actionEdit").performClick()
        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("messageInput").performTextInput("my better answer")
        compose.onNodeWithTag("send").performClick()
        assertEquals(listOf(2 to FormattedText("my better answer")), edits)
    }

    @Test
    fun photosShowTheirThumbnailWhileLoading() {
        val photo = MediaItem("photo1", "photo.jpg", 1000, 2, isPhoto = true, kind = MediaKind.Photo, thumb = ByteArray(0), width = 800, height = 600)
        val messages = listOf(MessageItem(id = 1, text = "", out = false, date = 1_700_000_000, media = photo))
        var state by mutableStateOf<DownloadState?>(DownloadState.Progress(100, 1000))
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = messages, downloads = state?.let { mapOf("photo1" to it) } ?: emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        compose.onNodeWithTag("photo_photo1").assertIsDisplayed()
        state = null
        compose.onNodeWithContentDescription("download").assertIsDisplayed()
    }

    @Test
    fun emptyFieldShowsTheMicrophone() {
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onVoiceStart = {})
            }
        }
        compose.onNodeWithTag("mic").assertIsDisplayed()
        compose.onNodeWithTag("send").assertDoesNotExist()
        compose.onNodeWithTag("messageInput").performTextInput("hi")
        compose.onNodeWithTag("send").assertIsDisplayed()
        compose.onNodeWithTag("mic").assertDoesNotExist()
        compose.onNodeWithTag("messageInput").performTextClearance()
        compose.onNodeWithTag("mic").assertIsDisplayed()
    }

    @Test
    fun holdRecordsReleaseSendsSlideCancels() {
        var starts = 0
        val stops = mutableListOf<Boolean>()
        var recording by mutableStateOf<VoiceRecording?>(null)
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    recording = recording,
                    onVoiceStart = {
                        starts++
                        recording = VoiceRecording(7_000, 0.5f)
                    },
                    onVoiceStop = {
                        stops += it
                        recording = null
                    },
                )
            }
        }
        compose.onNodeWithTag("mic").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("voiceTimer").assertTextEquals("0:07")
        compose.onNodeWithTag("lockHint", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Slide to cancel").assertIsDisplayed()
        compose.onNodeWithTag("mic").performTouchInput {
            advanceEventTime(1_000)
            up()
        }
        assertEquals(1, starts)
        assertEquals(listOf(true), stops)
        compose.onNodeWithTag("recording").assertDoesNotExist()

        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(-200.dp.toPx(), 0f))
        }
        assertEquals(listOf(true, false), stops)
        compose.onNodeWithTag("mic").performTouchInput { up() }
        assertEquals(listOf(true, false), stops)

        compose.onNodeWithTag("mic").performClick()
        assertEquals(2, starts)
        assertEquals(listOf(true, false), stops)
        compose.onNodeWithTag("voiceHint", useUnmergedTree = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("voiceHint", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(500)
            up()
        }
        assertEquals(3, starts)
        assertEquals(listOf(true, false, false), stops)

        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(300)
            moveBy(Offset(0f, -100.dp.toPx()))
            up()
        }
        assertEquals(listOf(true, false, false), stops)
        compose.onNodeWithTag("discardVoice").assertIsDisplayed()
        compose.onNodeWithTag("sendVoice").performClick()
        assertEquals(listOf(true, false, false, true), stops)
        compose.onNodeWithTag("mic").performTouchInput {
            down(center)
            advanceEventTime(300)
            moveBy(Offset(0f, -100.dp.toPx()))
            up()
        }
        compose.onNodeWithTag("discardVoice").performClick()
        assertEquals(listOf(true, false, false, true, false), stops)
        compose.onNodeWithTag("recording").assertDoesNotExist()
        compose.onNodeWithTag("mic").assertIsDisplayed()
    }

    @Test
    fun voiceBubbleShowsDurationAndTogglesPlayback() {
        val voice = MediaItem("doc5", "voice_5.ogg", 4_000, 2, isPhoto = false, kind = MediaKind.Voice, duration = 12, waveform = List(100) { it % 32 })
        val messages = listOf(MessageItem(id = 1, text = "", out = false, date = 1_700_000_000, media = voice))
        val toggled = mutableListOf<String>()
        var playback by mutableStateOf<VoicePlayback?>(null)
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Alice", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    playback = playback, onToggleVoice = { toggled += it.key },
                )
            }
        }
        compose.onNodeWithTag("voiceWave", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("voiceTime", useUnmergedTree = true).assertTextEquals("0:12")
        compose.onNodeWithContentDescription("Play").performClick()
        assertEquals(listOf("doc5"), toggled)
        playback = VoicePlayback("doc5", playing = true, positionMs = 3_000, durationMs = 12_000)
        compose.onNodeWithTag("voiceTime", useUnmergedTree = true).assertTextEquals("0:03 / 0:12")
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals(listOf("doc5", "doc5"), toggled)
        playback = VoicePlayback("doc9", playing = true, positionMs = 1_000, durationMs = 5_000)
        compose.onNodeWithTag("voiceTime", useUnmergedTree = true).assertTextEquals("0:12")
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun theVoiceInThePlayerShowsItsPositionAndSpeed() {
        val voice = MediaItem("doc5", "voice_5.ogg", 4_000, 2, isPhoto = false, kind = MediaKind.Voice, duration = 12, waveform = List(100) { it % 32 })
        val messages = listOf(MessageItem(id = 1, text = "", out = false, date = 1_700_000_000, media = voice))
        var speeds = 0
        var playback by mutableStateOf<VoicePlayback?>(null)
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Alice", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    playback = playback, onVoiceSpeed = { speeds++ },
                )
            }
        }
        compose.onNodeWithTag("voiceSpeed_doc5", useUnmergedTree = true).assertDoesNotExist()
        playback = VoicePlayback("doc5", playing = true, positionMs = 6_000, durationMs = 12_000, speed = 1.5f)
        compose.onNodeWithTag("voiceTime", useUnmergedTree = true).assertTextEquals("0:06 / 0:12")
        compose.onNodeWithTag("voiceSpeed_doc5").assertContentDescriptionEquals("Playback speed 1.5x")
        compose.onNodeWithTag("voiceSpeed_doc5").performClick()
        assertEquals(1, speeds)
        playback = VoicePlayback("doc5", playing = true, positionMs = 9_000, durationMs = 12_000, speed = 2f)
        compose.onNodeWithTag("voiceTime", useUnmergedTree = true).assertTextEquals("0:09 / 0:12")
        compose.onNodeWithText("2x", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun voiceTimeIsMinutesAndSeconds() {
        assertEquals("0:00", voiceTime(0))
        assertEquals("0:07", voiceTime(7_900))
        assertEquals("2:05", voiceTime(125_000))
    }
}
