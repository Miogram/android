package cn.ellerotta.core.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import cn.ellerotta.core.model.CallEnd
import cn.ellerotta.core.model.CallInfo
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.ui.components.chat.CallLength
import cn.ellerotta.core.ui.components.chat.ChatEntry
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.callLength
import cn.ellerotta.core.ui.components.chat.callTitle
import cn.ellerotta.core.ui.components.chat.callWentThrough
import cn.ellerotta.core.ui.components.chat.entries
import cn.ellerotta.core.ui.theme.MioTheme
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class CallsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    private fun call(id: Int, out: Boolean, call: CallInfo, date: Int = now - 100 + id) =
        MessageItem(id, "", out = out, date = date, service = ServiceKind.Call, call = call)

    @Test
    fun titlesFollowTheDirectionKindAndEnd() {
        assertEquals(R.string.chat_call_incoming, callTitle(CallInfo(end = CallEnd.Hangup, duration = 70), out = false))
        assertEquals(R.string.chat_call_outgoing, callTitle(CallInfo(end = CallEnd.Hangup, duration = 70), out = true))
        assertEquals(R.string.chat_call_incoming_video, callTitle(CallInfo(video = true, duration = 5), out = false))
        assertEquals(R.string.chat_call_outgoing_video, callTitle(CallInfo(video = true, duration = 5), out = true))
        assertEquals(R.string.chat_call_missed, callTitle(CallInfo(end = CallEnd.Missed), out = false))
        assertEquals(R.string.chat_call_missed, callTitle(CallInfo(video = true, end = CallEnd.Busy), out = false))
        assertEquals(R.string.chat_call_cancelled, callTitle(CallInfo(end = CallEnd.Missed), out = true))
        assertEquals(R.string.chat_call_cancelled, callTitle(CallInfo(end = CallEnd.Busy), out = true))
        assertEquals(R.string.chat_call_cancelled, callTitle(CallInfo(end = CallEnd.Disconnect), out = false))
        assertEquals(R.string.chat_call_cancelled, callTitle(CallInfo(end = CallEnd.Disconnect), out = true))
        assertEquals(R.string.chat_call_incoming, callTitle(CallInfo(end = CallEnd.Disconnect, duration = 30), out = false))
        assertEquals(R.string.chat_call_outgoing, callTitle(CallInfo(end = CallEnd.Hangup), out = true))
    }

    @Test
    fun onlyCallsThatWentThroughAreGreen() {
        assertTrue(callWentThrough(CallInfo(end = CallEnd.Hangup)))
        assertTrue(callWentThrough(CallInfo(end = CallEnd.Disconnect, duration = 3)))
        assertTrue(callWentThrough(CallInfo()))
        assertFalse(callWentThrough(CallInfo(end = CallEnd.Missed)))
        assertFalse(callWentThrough(CallInfo(end = CallEnd.Busy)))
        assertFalse(callWentThrough(CallInfo(end = CallEnd.Disconnect)))
    }

    @Test
    fun lengthsAreSecondsUnderAMinuteThenMinutes() {
        assertNull(callLength(0))
        assertNull(callLength(1))
        assertEquals(CallLength(R.plurals.chat_call_seconds, 2), callLength(2))
        assertEquals(CallLength(R.plurals.chat_call_seconds, 59), callLength(59))
        assertEquals(CallLength(R.plurals.chat_call_minutes, 1), callLength(60))
        assertEquals(CallLength(R.plurals.chat_call_minutes, 2), callLength(179))
    }

    @Test
    fun callsRunWithTheirSidesMessages() {
        val messages = listOf(
            MessageItem(3, "after", out = false, date = now - 30),
            call(2, out = false, CallInfo(end = CallEnd.Hangup, duration = 75), date = now - 60),
            MessageItem(1, "before", out = false, date = now - 90),
        )
        val bubbles = entries(messages, ZoneId.systemDefault()).filterIsInstance<ChatEntry.Bubble>().associateBy { it.message.id }
        assertFalse(bubbles.getValue(2).position.first)
        assertFalse(bubbles.getValue(2).position.last)
    }

    @Test
    fun aCallShowsAsABubbleWithItsTitleTimeAndLength() {
        val messages = listOf(
            call(2, out = true, CallInfo(video = true, end = CallEnd.Busy)),
            call(1, out = false, CallInfo(end = CallEnd.Hangup, duration = 75)),
        )
        compose.setContent {
            MioTheme { ChatScreen(title = "Calls", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}) }
        }
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_1"))
        compose.onNodeWithText("Incoming call").assertExists()
        compose.onNodeWithText("(1 minute)", substring = true).assertExists()
        compose.onNodeWithTag("callArrow_1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("message_1").performClick()
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_2"))
        compose.onNodeWithText("Cancelled call").assertExists()
        compose.onNodeWithTag("callTitle_2", useUnmergedTree = true).assertExists()
    }
}
