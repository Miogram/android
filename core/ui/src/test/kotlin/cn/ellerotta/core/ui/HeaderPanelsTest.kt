package cn.ellerotta.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PinnedMessages
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeaderPanelsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val messages = (1..3).map { MessageItem(it, "message $it", out = false, date = now - 100 + it) }
    private var pinned by mutableStateOf(PinnedMessages())
    private var busy by mutableStateOf(false)
    private var playing by mutableStateOf(false)

    private fun show() {
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen(
                    title = "Alice", messages = messages, downloads = emptyMap(), busy = busy, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("u7", "Alice"), pinned = pinned,
                    player = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("player")) }, playerShown = playing,
                )
            }
        }
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(96)
    }

    private fun header(): Dp = compose.onNodeWithTag("chatHeader").getBoundsInRoot().let { it.bottom - it.top }

    private fun assertAnimates(tag: String, open: () -> Unit, close: () -> Unit) {
        val base = header()
        compose.onNodeWithTag(tag).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        open()
        settle()
        val partway = header()
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(3_000)
        val full = header()
        assertTrue(base < partway && partway < full, "opening: $base $partway $full")
        compose.onNodeWithTag(tag).assertExists()

        compose.mainClock.autoAdvance = false
        close()
        settle()
        val closing = header()
        compose.onNodeWithTag(tag).assertExists()
        assertTrue(base < closing && closing < full, "closing: $base $closing $full")
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(base.value, header().value, 0.5f)
        compose.onNodeWithTag(tag).assertDoesNotExist()
    }

    @Test
    fun theMiniPlayerOpensAndClosesWithAnAnimatedHeight() {
        show()
        assertAnimates("player", open = { playing = true }, close = { playing = false })
    }

    @Test
    fun thePinnedBarOpensAndClosesWithAnAnimatedHeightAndKeepsItsMessageWhileClosing() {
        show()
        assertAnimates("pinnedBar", open = { pinned = PinnedMessages(listOf(messages[0])) }, close = { pinned = PinnedMessages() })
    }

    @Test
    fun theBusyLineOpensAndClosesWithAnAnimatedHeight() {
        show()
        val base = header()
        compose.mainClock.autoAdvance = false
        busy = true
        settle()
        val partway = header()
        compose.mainClock.advanceTimeBy(3_000)
        val full = header()
        assertTrue(base < partway && partway < full, "opening: $base $partway $full")
        busy = false
        settle()
        val closing = header()
        assertTrue(base < closing && closing < full, "closing: $base $closing $full")
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(base.value, header().value, 0.5f)
    }
}
