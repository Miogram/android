package cn.ellerotta.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.EPHEMERAL_IDS
import cn.ellerotta.core.model.EphemeralInfo
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Reaction
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
class EphemeralTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val heart: Reaction = Reaction.Emoji("\u2764\uFE0F")
    private val fromBot = EPHEMERAL_IDS + 1
    private val toBot = EPHEMERAL_IDS + 2
    private val toUnknown = EPHEMERAL_IDS + 3

    private fun gone(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()

    @Test
    fun ephemeralMessagesSayWhoSeesThemAndOnlyAnswerCopyAndDelete() {
        val reacted = mutableListOf<Int>()
        val deleted = mutableListOf<Pair<Int, Boolean>>()
        val replies = mutableListOf<Pair<String, Int>>()
        val messages = listOf(
            MessageItem(5, "a message of the chat", out = false, date = now - 10),
            MessageItem(fromBot, "only for you", out = false, date = now - 5, senderId = 504, ephemeral = EphemeralInfo(1, "u504", "helperbot")),
            MessageItem(toBot, "/secret", out = true, date = now - 4, ephemeral = EphemeralInfo(2, "u504", "helperbot")),
            MessageItem(toUnknown, "/secret again", out = true, date = now - 3, ephemeral = EphemeralInfo(3, "u504")),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Helper", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("u504", "Helper", kind = PeerKind.Bot),
                    onSendReply = { text, to -> replies += text.text to to },
                    onEdit = { _, _ -> },
                    onDelete = { id, revoke -> deleted += id to revoke },
                    onForward = {},
                    reactions = ChatReactionState(listOf(heart), default = heart),
                    onReact = { id, _, _, _ -> reacted += id },
                )
            }
        }

        compose.onNodeWithTag("ephemeral_$fromBot", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("visible only to you").assertExists()
        compose.onNodeWithText("visible to @helperbot").assertExists()
        compose.onNodeWithText("visible to the bot").assertExists()
        assertTrue(compose.onAllNodes(hasTestTag("ephemeral_5"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("message_$fromBot").performTouchInput { doubleClick(Offset(centerX, height - 4f)) }
        compose.waitForIdle()
        assertTrue(reacted.isEmpty())

        compose.onNodeWithTag("message_$toBot").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionReply").assertExists()
        compose.onNodeWithTag("actionCopy").assertExists()
        compose.onNodeWithTag("actionDelete").assertExists()
        assertTrue(gone("actionForward") && gone("actionSelect") && gone("actionEdit") && gone("reactionPicker"))

        compose.onNodeWithTag("actionDelete").performClick()
        compose.onNodeWithTag("confirmDelete").assertExists()
        assertTrue(gone("revoke"))
        compose.onNodeWithTag("confirmDelete").performClick()
        assertEquals(listOf(toBot to false), deleted)

        compose.onNodeWithTag("message_$fromBot").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionReply").performClick()
        compose.onNodeWithTag("messageInput").performTextInput("thanks")
        compose.onNodeWithTag("send").performClick()
        assertEquals(listOf("thanks" to fromBot), replies)

        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_5"))
        compose.onNodeWithTag("message_5").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionForward").assertExists()
        compose.onNodeWithTag("reactionPicker").assertExists()
    }

    @Test
    @Config(qualifiers = "ru")
    fun theHeaderInRussian() {
        val messages = listOf(
            MessageItem(fromBot, "только вам", out = false, date = now, ephemeral = EphemeralInfo(1, "u504", "helperbot")),
            MessageItem(toBot, "/secret", out = true, date = now, ephemeral = EphemeralInfo(2, "u504", "helperbot")),
        )
        compose.setContent {
            MioTheme {
                ChatScreen("Helper", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        compose.onNodeWithText("видно только вам").assertExists()
        compose.onNodeWithText("видно @helperbot").assertExists()
    }
}
