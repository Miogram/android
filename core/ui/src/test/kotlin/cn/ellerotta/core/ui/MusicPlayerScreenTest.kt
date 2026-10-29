package cn.ellerotta.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import kotlin.test.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.onNodeWithText
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicRepeat
import cn.ellerotta.core.model.MusicSleep
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.ui.components.music.MusicActions
import cn.ellerotta.core.ui.components.music.MusicPlayerContent
import cn.ellerotta.core.ui.components.music.MusicPlayerOverlay
import cn.ellerotta.core.ui.components.music.MusicQueueContent
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class MusicPlayerScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun song(n: Int) = MediaItem("doc$n", "song$n.mp3", 4_000_000, 2, isPhoto = false, kind = MediaKind.Music, duration = 185, mimeType = "audio/mpeg", title = "Song $n", performer = "Band")
    private val queue = (1..4).map { MusicTrack("self", it, song(it)) }
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
        onOrder = { calls += "order $it" },
        onMove = { from, to -> calls += "move $from $to" },
        onPlayNext = { calls += "next $it" },
        onRemove = { calls += "remove $it" },
        onSleep = { calls += "sleep $it" },
        onSleepAtEnd = { calls += "sleep at end" },
        onGoToMessage = { calls += "go to message" },
        onSaveToProfile = { calls += "save" },
    )

    private fun player(state: MusicState) = compose.setContent { MioTheme { MusicPlayerContent(state, actions, onCollapse = { calls += "collapse" }) } }

    @Test
    fun theControlsReachTheirActionsAndTheQueueOpens() {
        player(MusicState(queue, index = 1, playing = true, durationMs = 185_000, order = MusicOrder.Shuffle, repeat = MusicRepeat.One))
        compose.onNodeWithTag("sheetTitle").assertTextEquals("Song 2")
        compose.onNodeWithTag("sheetPerformer").assertTextEquals("Band")
        compose.onNodeWithText("2 of 4").assertExists()
        compose.onNodeWithTag("sheetToggle").assertContentDescriptionEquals("Pause")
        compose.onNodeWithTag("sheetToggle").assertIsOn()
        compose.onNodeWithTag("sheetRepeat").assertContentDescriptionEquals("Repeat one")
        compose.onNodeWithTag("sheetRepeat").assertIsOn()
        compose.onNodeWithTag("sheetShuffle").assertContentDescriptionEquals("Shuffle")
        compose.onNodeWithTag("sheetShuffle").assertIsOn()
        compose.onNodeWithTag("sheetPrevious").performClick()
        compose.onNodeWithTag("sheetToggle").performClick()
        compose.onNodeWithTag("sheetNext").performClick()
        compose.onNodeWithTag("sheetShuffle").performClick()
        compose.onNodeWithTag("sheetRepeat").performClick()
        compose.onNodeWithTag("sheetCollapse").performClick()
        assertEquals(listOf("previous", "toggle", "next", "shuffle", "repeat", "collapse"), calls)

        compose.onNodeWithTag("sheetQueue").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("queue_2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("queue_2").performClick()
        assertEquals("play 2", calls.last())
    }

    @Test
    fun aTapShufflesALongPressChoosesTheOrder() {
        player(MusicState(queue, index = 0, playing = false, durationMs = 185_000))
        compose.onNodeWithTag("sheetShuffle").assertIsOff()
        compose.onNodeWithTag("sheetShuffle").performClick()
        assertEquals(listOf("shuffle"), calls)

        compose.onNodeWithTag("sheetShuffle").performTouchInput { longClick() }
        compose.waitUntil { compose.onAllNodesWithTag("order_Reverse").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("shuffle"), calls)
        compose.onNodeWithTag("order_Reverse").performClick()
        assertEquals(listOf("shuffle", "order Reverse"), calls)
        compose.waitUntil { compose.onAllNodesWithTag("orderMenu").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun newestFirstShowsOnTheButtonAndTalkbackHasTheLongPress() {
        player(MusicState(queue, index = 0, playing = true, durationMs = 185_000, order = MusicOrder.Reverse))
        compose.onNodeWithTag("sheetShuffle").assertIsOn()
        compose.onNodeWithTag("sheetShuffle").assertContentDescriptionEquals("Newest first")
        compose.onNodeWithTag("sheetShuffle").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitUntil { compose.onAllNodesWithTag("order_Chat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("order_Chat").performClick()
        assertEquals(listOf("order Chat"), calls)
    }

    @Test
    fun theTimesSitUnderTheWaveTheModesKeepTheirSizeAndTheCoverSwipesToSkip() {
        player(MusicState(queue, index = 1, playing = true, positionMs = 30_000, durationMs = 185_000, lyrics = "la la la"))
        compose.onNodeWithTag("sheetPosition").assertTextEquals("0:30")
        compose.onNodeWithTag("sheetLength").assertTextEquals("3:05")
        compose.onNodeWithTag("sheetSeek").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0:30 of 3:05"))
        val seek = compose.onNodeWithTag("sheetSeek").getUnclippedBoundsInRoot()
        val time = compose.onNodeWithTag("sheetPosition").getUnclippedBoundsInRoot()
        val wave = (seek.top + seek.bottom) / 2
        assertTrue(time.top - wave in 15.dp..17.dp, "the time starts ${time.top - wave} under the wave")
        listOf("sheetShuffle", "sheetRepeat", "sheetLyricsToggle", "sheetSleep").forEach { tag ->
            compose.onNodeWithTag(tag).assertWidthIsEqualTo(52.dp)
        }
        compose.onNodeWithTag("sheetToggle").assertHeightIsEqualTo(96.dp)

        compose.onNodeWithTag("sheetCover").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("sheetCover").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf("next", "previous"), calls)
    }

    @Test
    fun aSeekHoldsItsPlaceUntilThePlayerGetsThere() {
        var state by mutableStateOf(MusicState(queue, index = 0, playing = true, positionMs = 30_000, durationMs = 185_000))
        compose.setContent { MioTheme { MusicPlayerContent(state, actions, onCollapse = {}) } }
        compose.onNodeWithTag("sheetSeek").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals("seek 92500", calls.last())
        compose.onNodeWithTag("sheetPosition").assertTextEquals("1:32")
        state = state.copy(positionMs = 30_500)
        compose.onNodeWithTag("sheetPosition").assertTextEquals("1:32")
        state = state.copy(positionMs = 92_600)
        compose.waitForIdle()
        state = state.copy(positionMs = 95_000)
        compose.onNodeWithTag("sheetPosition").assertTextEquals("1:35")

        compose.onNodeWithTag("sheetSeek").performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
        assertEquals("seek 46250", calls.last())
        compose.onNodeWithTag("sheetPosition").assertTextEquals("0:46")
        compose.mainClock.advanceTimeBy(2_100)
        compose.onNodeWithTag("sheetPosition").assertTextEquals("1:35")
    }

    @Test
    fun aVoiceMessageHasItsSpeedInsteadOfTheOrderAndRepeat() {
        val voices = (1..2).map { n ->
            MusicTrack("u501", 10 + n, MediaItem("voice$n", "voice.ogg", 20_000, 2, isPhoto = false, kind = MediaKind.Voice, duration = 3), senderName = "Alice", senderId = 501, avatarKey = "u501@55")
        }
        player(MusicState(voices, index = 1, playing = true, durationMs = 3_000, speed = 2f))
        compose.onNodeWithTag("sheetTitle").assertTextEquals("Alice")
        compose.onNodeWithTag("sheetShuffle").assertDoesNotExist()
        compose.onNodeWithTag("sheetRepeat").assertDoesNotExist()
        compose.onNodeWithTag("sheetSaveToProfile").assertDoesNotExist()
        compose.onNodeWithTag("sheetSpeed").assertContentDescriptionEquals("Playback speed 2x")
        compose.onNodeWithTag("sheetSpeed").performClick()
        assertEquals(listOf("speed"), calls)
    }

    @Test
    fun theQueueIsReorderedEditedAndChoosesTheOrder() {
        val state = MusicState(queue, index = 1, playing = true, durationMs = 185_000)
        compose.setContent { MioTheme { MusicQueueContent(state, actions) } }
        compose.onNodeWithText("4 tracks").assertExists()
        compose.onNodeWithTag("queueMenu_1").assertDoesNotExist()
        compose.onNode(hasContentDescription("Now playing"), useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("queueMenu_3").performClick()
        compose.onNodeWithTag("queuePlayNext").performClick()
        compose.onNodeWithTag("queueMenu_0").performClick()
        compose.onNodeWithTag("queueRemove").performClick()
        compose.onNodeWithTag("queue_2").performCustomAccessibilityActionWithLabel("Move up")
        compose.onNodeWithTag("queue_0").performCustomAccessibilityActionWithLabel("Move down")
        assertEquals(listOf("next 3", "remove 0", "move 2 1", "move 0 1"), calls)

        val handle = compose.onNodeWithTag("queueHandle_self/1#doc1", useUnmergedTree = true)
        handle.performTouchInput { down(center) }
        val step = compose.onNodeWithTag("queue_0").fetchSemanticsNode().size.height * 2f / 20
        repeat(20) {
            handle.performTouchInput { moveBy(Offset(0f, step)) }
            compose.waitForIdle()
        }
        handle.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("move 0 2", calls.last())

        compose.onNodeWithTag("queueOrder_Reverse").performClick()
        assertEquals("order Reverse", calls.last())
        compose.onNodeWithTag("queueOrder_Chat").assertIsOn()
    }

    @Test
    fun theOverlayOpensAndClosesWithBack() {
        var open by mutableStateOf(false)
        val state = MusicState(queue, index = 0, playing = true, durationMs = 185_000)
        compose.setContent { MioTheme { MusicPlayerOverlay(state, open, actions, onDismiss = { open = false }) } }
        compose.onNodeWithTag("musicSheet").assertDoesNotExist()
        open = true
        compose.waitUntil { compose.onAllNodesWithTag("sheetTitle").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheetTitle").assertTextEquals("Song 1")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil { compose.onAllNodesWithTag("musicSheet").fetchSemanticsNodes().isEmpty() }
        assertEquals(false, open)
        open = true
        compose.waitUntil { compose.onAllNodesWithTag("sheetCollapse").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheetCollapse").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("musicSheet").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theWordsTakeTheCoversPlaceInTheOverlay() {
        val state = MusicState(queue, index = 0, playing = true, durationMs = 185_000, lyrics = "la la la")
        compose.setContent { MioTheme { MusicPlayerOverlay(state, open = true, actions, onDismiss = {}) } }
        compose.waitUntil { compose.onAllNodesWithTag("sheetCover").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheetLyricsToggle").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("sheetCover").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("sheetLyrics").assertTextEquals("la la la")
        compose.onNodeWithTag("sheetLyricsToggle").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("sheetLyrics").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("sheetCover").assertExists()
    }

    @Test
    fun theSleepTimerTheWordsTheSpeedOfALongSongAndTheMenu() {
        val long = MusicTrack("self", 9, song(9).copy(duration = 40 * 60))
        player(MusicState(listOf(long), index = 0, playing = true, durationMs = 2_400_000, speed = 1.5f, sleep = MusicSleep(leftMs = 14 * 60_000L + 52_000), lyrics = "la la la"))
        compose.onNodeWithTag("sheetStatus").assertTextEquals("Stops in 14:52")
        compose.onNodeWithTag("sheetSleep").assertIsOn()
        compose.onNodeWithTag("sheetSleep").performClick()
        compose.onNodeWithTag("sleep_30").performClick()
        compose.onNodeWithTag("sheetSleep").performClick()
        compose.onNodeWithTag("sleepAtEnd").performClick()
        compose.onNodeWithTag("sheetSleep").performClick()
        compose.onNodeWithTag("sleepOff").performClick()

        compose.onNodeWithTag("sheetSpeed").assertContentDescriptionEquals("Playback speed 1.5x")
        compose.onNodeWithTag("sheetSpeed").performClick()
        compose.onNodeWithTag("sheetLyrics").assertDoesNotExist()
        compose.onNodeWithTag("sheetLyricsToggle").performClick()
        compose.onNodeWithTag("sheetLyrics").assertTextEquals("la la la")

        compose.onNodeWithTag("sheetMore").performClick()
        compose.onNodeWithTag("sheetGoToMessage").performClick()
        compose.onNodeWithTag("sheetSaveToProfile").assertContentDescriptionEquals("Add to my profile")
        compose.onNodeWithTag("sheetSaveToProfile").performClick()
        assertEquals(listOf("sleep 30", "sleep at end", "sleep null", "speed", "go to message", "save"), calls)
    }

    @Test
    fun withoutWordsOrALongSongTheirButtonsStayAway() {
        player(MusicState(queue, index = 0, playing = true, durationMs = 185_000, sleep = MusicSleep(endOfTrack = true)))
        compose.onNodeWithTag("sheetStatus").assertTextEquals("Stops after this track")
        compose.onNodeWithTag("sheetSpeed").assertDoesNotExist()
        compose.onNodeWithTag("sheetLyricsToggle").assertDoesNotExist()
        compose.onNodeWithTag("sheetSaveToProfile").assertExists()
    }
}
