package cn.ellerotta.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import cn.ellerotta.core.model.ARTICLE_DRAFT_PREVIEW_MAX
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.ChatSearchState
import cn.ellerotta.core.model.Draft
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatSearchResults
import cn.ellerotta.core.ui.components.chats.previewText
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
class ArticlePreviewTest {
    @get:Rule
    val compose = createComposeRule()

    private val formula = ArticleMessage(listOf(ArticleBlock.Math("x^2")))
    private val text = ArticleMessage(
        listOf(
            ArticleBlock.Heading(2, ArticleText.Plain("News")),
            ArticleBlock.Paragraph(ArticleText.Concat(listOf(ArticleText.Plain("see "), ArticleText.Hashtag(ArticleText.Plain("#kotlin")), ArticleText.Math("y")))),
            ArticleBlock.Photo(null),
        ),
    )

    @Test
    fun theListShowsBlocksAndMediaLikeMessagesInTheAccent() {
        var single: AnnotatedString? = null
        var mixed: AnnotatedString? = null
        var draft: AnnotatedString? = null
        var primary = Color.Unspecified
        val long = ArticleMessage(listOf(ArticleBlock.Paragraph(ArticleText.Plain("w".repeat(400)))))
        compose.setContent {
            MioTheme {
                primary = MaterialTheme.colorScheme.primary
                single = previewText(Preview(article = formula, sender = ""), PeerKind.Group)
                mixed = previewText(Preview(article = text), PeerKind.User)
                draft = previewText(Draft(FormattedText(""), article = long).let { Preview(text = it.text.text, draft = true, article = it.article) }, PeerKind.User)
            }
        }
        compose.waitForIdle()
        val one = single!!
        assertEquals("You: Formula", one.text)
        assertTrue(one.spanStyles.any { it.item.color == primary && one.text.substring(it.start, it.end) == "Formula" })
        val two = mixed!!
        assertEquals("News  see #kotlinFormula  Photo", two.text)
        assertTrue(two.spanStyles.none { it.item.color == primary })
        assertEquals("Draft: ".length + ARTICLE_DRAFT_PREVIEW_MAX, draft!!.text.length)
    }

    @Test
    fun repliesAndSearchResultsQuoteThePreview() {
        val messages = listOf(
            MessageItem(1, "", out = false, date = 1, article = text),
            MessageItem(2, "about it", out = true, date = 2, replyTo = 1),
        )
        compose.setContent {
            MioTheme {
                ChatScreen("Saved", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        compose.onNode(hasTestTag("replyQuote") and hasAnyAncestor(hasTestTag("message_2"))).assertExists()
        compose.onNode(hasText("News  see #kotlinFormula  Photo") and hasAnyAncestor(hasTestTag("message_2")), useUnmergedTree = true).assertExists()
    }

    @Test
    fun searchResultsShowThePreview() {
        compose.setContent {
            MioTheme {
                ChatSearchResults(ChatSearchState("kotlin", listOf(MessageItem(1, "", out = false, date = 1, article = text)), total = 1), "Saved", onOpen = {}, onMore = {})
            }
        }
        compose.onNode(hasText("News  see #kotlinFormula  Photo") and hasTestTag("searchText_1"), useUnmergedTree = true).assertExists()
    }
}
