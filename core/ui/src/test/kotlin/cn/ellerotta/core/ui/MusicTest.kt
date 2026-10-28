package cn.ellerotta.core.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicRepeat
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.music.MiniPlayer
import cn.ellerotta.core.ui.components.music.MusicActions
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicTest {
    @get:Rule
    val compose = createComposeRule()

    private fun song(n: Int) = MediaItem("doc$n", "song$n.mp3", 4_000_000, 2, isPhoto = false, kind = MediaKind.Music, duration = 185, mimeType = "audio/mpeg", title = "Song $n", performer = "Band")
    private val queue = (1..3).map { MusicTrack("self", it, song(it)) }
    private val calls = mutableListOf<String>()
    private val actions = MusicActions(
        onToggle = { calls += "toggle" },
        onNext = { calls += "next" },
        onPrevious = { calls += "previous" },
        onSeek = { calls += "seek $it" },
        onShuffle = { calls += "shuffle" },
        onRepeat = { calls += "repeat" },
        onPlayAt = { calls += "play $it" },
        onClose = { calls += "close" },
        onSpeed = { calls += "speed" },
    )

    private val voices = (1..2).map { n ->
        MusicTrack("u501", 10 + n, MediaItem("voice$n", "voice.ogg", 20_000, 2, isPhoto = false, kind = MediaKind.Voice, duration = 3), senderName = "Alice", senderId = 501, avatarKey = "u501@55")
    }

    @Test
    fun aVoiceMessageInThePlayerShowsItsSenderAndSpeed() {
        val state = MusicState(voices, index = 0, playing = true, positionMs = 1_000, durationMs = 3_000, speed = 1.5f)
        compose.setContent { MioTheme { MiniPlayer(state, onOpen = {}, actions = actions) } }
        compose.onNodeWithTag("miniTitle", useUnmergedTree = true).assertTextEquals("Alice")
        compose.onNodeWithTag("miniPerformer", useUnmergedTree = true).assertTextEquals("Voice message")
        compose.onNodeWithTag("miniSpeed").assertContentDescriptionEquals("Playback speed 1.5x")
        compose.onNodeWithTag("miniSpeed").performClick()
        assertEquals(listOf("speed"), calls)
    }

    @Test
    fun aSongBubbleShowsTitlePerformerLengthAndPlays() {
        val played = mutableListOf<Int>()
        val unnamed = MediaItem("doc9", "night drive.mp3", 1_000, 2, isPhoto = false, kind = MediaKind.Music, duration = 61)
        val messages = listOf(MessageItem(1, "", out = false, date = 1_700_000_000, media = song(1)), MessageItem(2, "", out = true, date = 1_700_000_001, media = unnamed))
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onPlayMusic = { played += it.id })
            }
        }
        compose.onNodeWithText("Song 1").assertIsDisplayed()
        compose.onNodeWithText("Band").assertIsDisplayed()
        compose.onNodeWithText("3:05").assertIsDisplayed()
        compose.onNodeWithText("night drive").assertIsDisplayed()
        compose.onNodeWithText("Unknown artist").assertIsDisplayed()
        compose.onNodeWithTag("musicToggle_doc1").assertContentDescriptionEquals("Play")
        compose.onNodeWithTag("musicToggle_doc1").performClick()
        assertEquals(listOf(1), played)
    }

    @Test
    fun thePlayersSongShowsPauseAndItsTime() {
        val messages = listOf(MessageItem(2, "", out = false, date = 1_700_000_000, media = song(2)))
        val state = MusicState(queue, index = 1, playing = true, positionMs = 30_000, durationMs = 185_000)
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, music = state, onPlayMusic = {})
            }
        }
        compose.onNodeWithTag("musicToggle_doc2").assertContentDescriptionEquals("Pause")
        compose.onNodeWithTag("musicTime", useUnmergedTree = true).assertTextEquals("0:30 / 3:05")
    }

    @Test
    fun theMiniPlayerShowsTheSongAndHandsOutItsButtons() {
        var opened = 0
        val state = MusicState(queue, index = 0, playing = false, positionMs = 92_500, durationMs = 185_000)
        compose.setContent { MioTheme { MiniPlayer(state, onOpen = { opened++ }, actions = actions) } }
        compose.onNodeWithTag("miniTitle", useUnmergedTree = true).assertTextEquals("Song 1")
        compose.onNodeWithTag("miniPerformer", useUnmergedTree = true).assertTextEquals("Band")
        compose.onNodeWithTag("miniToggle").assertContentDescriptionEquals("Play")
        compose.onNodeWithTag("miniToggle").performClick()
        compose.onNodeWithTag("miniClose").performClick()
        compose.onNodeWithTag("miniTitle", useUnmergedTree = true).performClick()
        assertEquals(listOf("toggle", "close"), calls)
        assertEquals(1, opened)
    }

    @Test
    fun theMiniPlayerSitsOverTheChats() {
        val state = MusicState(queue, index = 2, playing = true)
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = listOf(FolderTab(ChatFolder.all("All"), listOf(DialogItem("u1", "Kotlin club")))), archived = emptyList(), avatars = emptyMap(), me = null, status = ChatsStatus.Ready, refreshing = false,
                    onOpenChat = {}, onOpenArchive = {}, onRefresh = {}, onAction = { _, _ -> },
                    player = { MiniPlayer(state, onOpen = {}, actions = actions) },
                )
            }
        }
        compose.onNodeWithTag("miniPlayer").assertIsDisplayed()
        compose.onNodeWithTag("miniTitle", useUnmergedTree = true).assertTextEquals("Song 3")
    }

    @Test
    fun theMiniPlayerSwipesToSkipAndShowsThePositionAsARing() {
        val state = MusicState(queue, index = 1, playing = true, positionMs = 92_500, durationMs = 185_000)
        compose.setContent { MioTheme { MiniPlayer(state, onOpen = { calls += "open" }, actions = actions) } }
        compose.onNodeWithTag("miniProgress", useUnmergedTree = true).assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f)))
        compose.onNodeWithTag("miniPlayer").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("miniPlayer").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf("next", "previous"), calls)
        compose.onNodeWithTag("miniPlayer").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 30f) }
        compose.waitForIdle()
        assertEquals(listOf("next", "previous"), calls)
        val bar = compose.onNode(hasClickAction() and hasAnyDescendant(hasTestTag("miniTitle")), useUnmergedTree = true)
        bar.performCustomAccessibilityActionWithLabel("Next")
        bar.performCustomAccessibilityActionWithLabel("Previous")
        bar.performClick()
        assertEquals(listOf("next", "previous", "next", "previous", "open"), calls)
    }
}
