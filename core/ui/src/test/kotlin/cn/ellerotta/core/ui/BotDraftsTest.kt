package cn.ellerotta.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.components.chat.ChatEntry
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.entries
import cn.ellerotta.core.ui.theme.MioTheme
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BotDraftsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    @Test
    fun aStreamedDraftHasNoActionsAndCanBeStopped() {
        var stops = 0
        val messages = listOf(
            MessageItem(1, "what is kotlin?", out = true, date = now - 5),
            MessageItem(-1, "Kotlin is", out = false, date = now, senderId = 504, streaming = true),
        )
        compose.setContent {
            MioTheme {
                ChatScreen("Helper", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onForward = {}, onStopDraft = { stops++ })
            }
        }
        compose.onNodeWithText("Kotlin is").assertIsDisplayed()

        compose.onNodeWithTag("message_-1").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        assertTrue(compose.onAllNodes(hasTestTag("actionReply")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("message_1").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionReply").assertIsDisplayed()
        compose.onNodeWithTag("actionReply").performClick()

        compose.onNodeWithTag("stopDraft").performClick()
        assertEquals(1, stops)
    }

    @Test
    fun theBotsMessageKeepsTheDraftsRow() {
        val rows = entries(listOf(MessageItem(9, "Kotlin is a language", out = false, date = now, replacedDraft = -1)), ZoneOffset.UTC)
        assertEquals("m-1", rows.filterIsInstance<ChatEntry.Bubble>().single().key)
        assertEquals("m9", entries(listOf(MessageItem(9, "plain", out = false, date = now)), ZoneOffset.UTC).filterIsInstance<ChatEntry.Bubble>().single().key)
    }
}
