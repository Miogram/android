package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsNodeInteraction
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.ForwardInfo
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ForwardOptions
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
class ForwardTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    @Test
    fun selectForwardAndJumpToReplies() {
        val forwarded = mutableListOf<List<Int>>()
        val opened = mutableListOf<String>()
        val shown = mutableListOf<Int>()
        val messages = listOf(
            MessageItem(1, "first note", out = false, date = now - 50),
            MessageItem(2, "second note", out = true, date = now - 40),
            MessageItem(3, "post 5", out = false, date = now - 30, forward = ForwardInfo("Kotlin News", "https://t.me/kotlinnews/5")),
            MessageItem(4, "about the first", out = true, date = now - 20, replyTo = 1),
            MessageItem(5, "about an old one", out = true, date = now - 10, replyTo = 99),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Saved", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    onOpenLink = { opened += it }, onForward = { forwarded += it.ids }, onShowMessage = { shown += it },
                    forwardTargets = listOf(DialogItem("u9", "Bob")),
                )
            }
        }
        compose.onNodeWithText("Kotlin News", useUnmergedTree = true).assertExists()
        show("forward_3").performClick()
        assertEquals(listOf("https://t.me/kotlinnews/5"), opened)

        show("message_4")
        compose.onNode(hasTestTag("replyQuote") and hasAnyAncestor(hasTestTag("message_4"))).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("highlight_1")).fetchSemanticsNodes().isNotEmpty() }
        show("message_5")
        compose.onNode(hasTestTag("replyQuote") and hasAnyAncestor(hasTestTag("message_5"))).performClick()
        assertEquals(listOf(99), shown)

        show("message_3").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionForward").performClick()
        compose.onNodeWithTag("forwardTo_u9").performClick()
        assertEquals(listOf(listOf(3)), forwarded)

        show("message_2").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionSelect").performClick()
        compose.onNodeWithTag("selectedCount").assertTextEquals("1 selected")
        show("select_1").performClick()
        compose.onNodeWithTag("selectedCount").assertTextEquals("2 selected")
        show("select_3").performClick()
        compose.onNodeWithTag("selectedCount").assertTextEquals("3 selected")
        show("select_3").performClick()
        assertEquals(1, opened.size)
        compose.onNodeWithTag("forwardSelected").performClick()
        compose.onNodeWithTag("forwardTo_u9").performClick()
        assertEquals(listOf(1, 2), forwarded.last())
        assertTrue(compose.onAllNodes(hasTestTag("selectedCount")).fetchSemanticsNodes().isEmpty())
    }

    private fun show(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    @Test
    fun hidingCaptionsHidesTheSenderToo() {
        var dropAuthor by mutableStateOf(false)
        var dropCaptions by mutableStateOf(false)
        compose.setContent { MioTheme { ForwardOptions(dropAuthor, dropCaptions, { dropAuthor = it }, { dropCaptions = it }) } }
        compose.onNodeWithTag("dropAuthor").assertIsOff().performClick()
        assertTrue(dropAuthor)
        compose.onNodeWithTag("dropAuthor").performClick()
        compose.onNodeWithTag("dropCaptions").performClick()
        compose.onNodeWithTag("dropCaptions").assertIsOn()
        compose.onNodeWithTag("dropAuthor").assertIsOn().assertIsNotEnabled()
    }
}
