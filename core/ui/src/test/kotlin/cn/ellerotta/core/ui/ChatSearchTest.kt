package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.LinkAnnotation
import cn.ellerotta.core.model.ChatSearchState
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatSearchActions
import cn.ellerotta.core.ui.components.chat.highlighted
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
class ChatSearchTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    private fun field(text: String) = SemanticsMatcher("search field $text") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == text }

    @Test
    fun resultsHighlightAndJump() {
        val queries = mutableListOf<String>()
        var more = 0
        var closed = 0
        val shown = mutableListOf<Int>()
        val messages = listOf(
            MessageItem(1, "Kotlin is fun", out = false, date = now - 50, senderName = "Alice"),
            MessageItem(2, "lunch?", out = true, date = now - 40),
            MessageItem(3, "more kotlin today", out = true, date = now - 30),
        )
        var state by mutableStateOf(ChatSearchState())
        val actions = ChatSearchActions(onQuery = { queries += it }, onMore = { more++ }, onClose = { closed++ })
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Saved", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    onShowMessage = { shown += it }, search = state, searchActions = actions,
                )
            }
        }

        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("chatSearch").performClick()
        compose.onNodeWithTag("searchField").performTextInput("kotlin")
        assertEquals("kotlin", queries.last())
        val older = MessageItem(99, "kotlin long ago", out = false, date = now - 86_400 * 30)
        state = ChatSearchState("kotlin", listOf(messages[2], messages[0], older), total = 3)
        compose.waitForIdle()
        compose.onNodeWithTag("searchCounter").assertTextEquals("3 messages found")

        val text = compose.onNodeWithTag("searchText_1", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val marked = text.spanStyles.single { it.item.background != Color.Unspecified }
        assertEquals("Kotlin", text.text.substring(marked.start, marked.end))

        compose.onNodeWithTag("searchResult_1").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("highlight_1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("searchCounter").assertTextEquals("2 of 3")
        assertTrue(compose.onAllNodes(hasTestTag("searchResults")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("searchNewer").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("highlight_3")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("searchCounter").assertTextEquals("1 of 3")
        compose.onNodeWithTag("searchOlder").performClick()
        compose.onNodeWithTag("searchOlder").performClick()
        assertEquals(listOf(99), shown)
        assertTrue(more > 0)
        compose.onNodeWithTag("searchCounter").assertTextEquals("3 of 3")

        compose.onNodeWithTag("searchCounter").performClick()
        compose.onNodeWithTag("searchResults").assertExists()
        compose.onNodeWithTag("closeSearch").performClick()
        assertEquals(1, closed)
        compose.onNodeWithTag("messageInput").assertExists()
    }

    @Test
    fun aHashtagOpensTheSearch() {
        val queries = mutableListOf<String>()
        val text = "news #kotlin weekly"
        val messages = listOf(MessageItem(1, text, out = false, date = now, entities = listOf(TextEntity(5, 7, EntityType.Hashtag))))
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Saved", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    searchActions = ChatSearchActions(onQuery = { queries += it }, onMore = {}, onClose = {}),
                )
            }
        }
        compose.onNode(hasText(text), useUnmergedTree = true).performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "#kotlin" }
        compose.waitForIdle()
        assertEquals(listOf("#kotlin"), queries)
        compose.onNode(field("#kotlin")).assertExists()
    }

    @Test
    fun highlightsEveryMatch() {
        val marked = highlighted("Kotlin and kotlin", "KOTLIN", Color.Yellow)
        assertEquals(listOf(0 to 6, 11 to 17), marked.spanStyles.map { it.start to it.end })
        assertEquals("plain", highlighted("plain", " ", Color.Yellow).text)
    }
}
