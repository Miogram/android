package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.ForwardPreview
import cn.ellerotta.core.model.MessageItem
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
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class ComposerCardsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    @Test
    fun aWaitingForwardGoesOutWithTheCommentOrIsCancelled() {
        val picked = MessageItem(5, "the minutes", out = false, date = now - 60, senderName = "Bob")
        var forwarding by mutableStateOf<ForwardPreview?>(ForwardPreview(listOf(picked), "Team"))
        val sent = mutableListOf<Pair<FormattedText, Int?>>()
        var cancelled = 0
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Alice", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onVoiceStart = {},
                    forwarding = forwarding,
                    onSendForward = { text, replyTo -> sent += text to replyTo },
                    onCancelForward = {
                        cancelled++
                        forwarding = null
                    },
                )
            }
        }
        compose.onNodeWithTag("forwardStrip").assertIsDisplayed()
        compose.onNodeWithTag("send").assertIsDisplayed()
        compose.onNodeWithTag("messageInput").performTextInput(" look ")
        compose.onNodeWithTag("send").performClick()
        assertEquals(listOf<Pair<FormattedText, Int?>>(FormattedText("look") to null), sent)

        compose.onNodeWithTag("closeForward").performClick()
        assertEquals(1, cancelled)
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("forwardStrip")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("mic").assertIsDisplayed()
    }
}
