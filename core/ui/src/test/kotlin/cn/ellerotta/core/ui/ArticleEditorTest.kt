package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleCaption
import cn.ellerotta.core.model.ArticleCell
import cn.ellerotta.core.model.ArticleGate
import cn.ellerotta.core.model.ArticleLimits
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticlePosting
import cn.ellerotta.core.model.ArticleRow
import cn.ellerotta.core.model.ArticleRowKind
import cn.ellerotta.core.model.ArticleStyle
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.ArticleUpload
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.toRows
import cn.ellerotta.core.ui.components.chat.ArticleEditorContent
import cn.ellerotta.core.ui.components.chat.ChatArticleActions
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArticleEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private fun p(text: String) = ArticleText.Plain(text)

    private fun item(text: String, checkbox: Boolean = false, checked: Boolean = false) = ArticleListItem(listOf(ArticleBlock.Paragraph(p(text))), checkbox, checked)

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)
    }

    private fun addRow() {
        compose.onNodeWithTag("articleAddEnd").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun theComposersTextOpensAndCommandsMakeBlocks() {
        val sent = mutableListOf<Pair<ArticleMessage, Int?>>()
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Saved", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    articles = ChatArticleActions(ArticleGate(ArticlePosting.Enabled), onSend = { article, reply -> sent += article to reply }),
                )
            }
        }
        compose.onNodeWithTag("messageInput").performTextInput("hello")
        compose.onNodeWithTag("articleOpen").performClick()
        compose.onNodeWithTag("articleText_0").assertTextEquals("hello")

        addRow()
        type("articleText_1", "/h1 Title")
        addRow()
        type("articleText_2", "/list one")
        type("articleText_2", "\ntwo")
        addRow()
        type("articleText_4", "/code ")
        type("articleText_4", "val x = 1")
        type("articleLanguage_4", "kotlin")
        addRow()
        type("articleText_5", "/math E = mc^2")
        addRow()
        type("articleText_6", "---")
        compose.onNodeWithTag("articleDivider_6").performScrollTo()
        type("articleText_7", "/quote wise words")
        compose.onNodeWithTag("articleSend").assertIsEnabled().performClick()

        assertEquals(
            listOf(
                ArticleBlock.Paragraph(p("hello")),
                ArticleBlock.Heading(1, p("Title")),
                ArticleBlock.Items(listOf(item("one"), item("two"))),
                ArticleBlock.Preformatted(p("val x = 1"), "kotlin"),
                ArticleBlock.Math("E = mc^2"),
                ArticleBlock.Divider,
                ArticleBlock.Quote(p("wise words")),
            ),
            sent.single().first.blocks,
        )
        compose.onNodeWithTag("articleEditor").assertDoesNotExist()
        assertEquals("", compose.onNodeWithTag("messageInput").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test
    fun theButtonShowsOnlyWhereRichMessagesAreAllowed() {
        var gate by mutableStateOf(ArticleGate(ArticlePosting.Disabled))
        val mine = MessageItem(1, "mine", out = true, date = (System.currentTimeMillis() / 1000).toInt())
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Saved", listOf(mine), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    onEdit = { _, _ -> },
                    articles = ChatArticleActions(gate, onSend = { _, _ -> }),
                )
            }
        }
        compose.onNodeWithTag("articleOpen").assertDoesNotExist()
        gate = ArticleGate(ArticlePosting.Premium)
        compose.waitForIdle()
        compose.onNodeWithTag("articleOpen").assertExists()
    }

    @Test
    fun stylesKindsMovesAndChecks() {
        val sent = mutableListOf<ArticleMessage>()
        val initial = listOf(
            ArticleRow(ArticleRowKind.Paragraph, FormattedText("first words"), id = 0),
            ArticleRow(ArticleRowKind.Check, FormattedText("todo"), id = 1),
            ArticleRow(ArticleRowKind.Paragraph, FormattedText("gone"), id = 2),
        )
        compose.setContent { MioTheme { ArticleEditorContent(initial, ArticleGate(ArticlePosting.Enabled), onSend = { sent += it }, onClose = {}) } }
        compose.onNodeWithTag("articleText_0").performTextInputSelection(TextRange(6, 11))
        compose.onNodeWithTag("formatBold").performScrollTo().performClick()
        compose.onNodeWithTag("articleText_0").performTextInputSelection(TextRange(0))
        compose.onNodeWithTag("articleKind").performClick()
        compose.onNodeWithTag("articleKind_Heading2").performClick()
        compose.onNodeWithTag("articleDown").performClick()
        compose.onNodeWithTag("articleCheck_1").performClick()
        compose.onNodeWithTag("articleText_2").performTextInputSelection(TextRange(0))
        compose.onNodeWithTag("articleRemove").performClick()
        compose.onNodeWithTag("articleRow_2").assertDoesNotExist()
        compose.onNodeWithTag("articleSend").performClick()
        assertEquals(
            listOf(
                ArticleBlock.Items(listOf(item("todo", checkbox = true, checked = true))),
                ArticleBlock.Heading(2, ArticleText.Concat(listOf(p("first "), ArticleText.Styled(ArticleStyle.Bold, p("words"))))),
            ),
            sent.single().blocks,
        )
    }

    @Test
    fun limitsAndPremiumBlockSending() {
        val closed = mutableListOf<ArticleMessage>()
        val initial = listOf(ArticleRow(ArticleRowKind.Paragraph, FormattedText("1234567890"), id = 0))
        val gate = ArticleGate(ArticlePosting.Premium, premium = false, ArticleLimits(length = 10))
        compose.setContent { MioTheme { ArticleEditorContent(initial, gate, onSend = {}, onClose = { closed += it }) } }
        compose.onNodeWithTag("articleCounter").assertTextEquals("10 / 10")
        compose.onNodeWithTag("articleSend").assertIsEnabled()
        compose.onNodeWithTag("articlePremium").assertDoesNotExist()
        type("articleText_0", "!")
        compose.onNodeWithTag("articleCounter").assertTextEquals("11 / 10")
        compose.onNodeWithTag("articleOver_Length").assertExists()
        compose.onNodeWithTag("articleSend").assertIsNotEnabled()
        compose.onNodeWithTag("articleText_0").performTextInputSelection(TextRange(0))
        compose.onNodeWithTag("articleKind").performClick()
        compose.onNodeWithTag("articleKind_Heading1").performClick()
        compose.onNodeWithTag("articlePremium").assertExists()
        compose.onNodeWithTag("articleClose").performClick()
        assertEquals(listOf(ArticleBlock.Heading(1, p("1234567890!"))), closed.single().blocks)
    }

    @Test
    fun tablesAreWrittenCellByCell() {
        val sent = mutableListOf<ArticleMessage>()
        val gate = ArticleGate(ArticlePosting.Enabled, limits = ArticleLimits(tableColumns = 3))
        compose.setContent { MioTheme { ArticleEditorContent(listOf(ArticleRow(ArticleRowKind.Paragraph, id = 0)), gate, onSend = { sent += it }, onClose = {}) } }
        type("articleText_0", "/table Name")
        compose.onNodeWithTag("articleCell_0_0_0").assertTextEquals("Name")
        type("articleCell_0_0_1", "Points")
        type("articleCell_0_1_0", "Alice")
        type("articleCell_0_1_1", "3")
        compose.onNodeWithTag("articleCell_0_1_0").performTextInputSelection(TextRange(0, 5))
        compose.onNodeWithTag("formatBold").performClick()
        compose.onNodeWithTag("articleTableAddColumn_0").performScrollTo().performClick()
        compose.onNodeWithTag("articleTableAddColumn_0").assertIsNotEnabled()
        type("articleCell_0_0_2", "Rank")
        compose.onNodeWithTag("articleTableAddRow_0").performScrollTo().performClick()
        type("articleCell_0_2_0", "gone")
        compose.onNodeWithTag("articleTableRemoveRow_0").performScrollTo().performClick()
        compose.onNodeWithTag("articleCell_0_2_0").assertDoesNotExist()
        compose.onNodeWithTag("articleCell_0_0_2").performTextInputSelection(TextRange(0))
        compose.onNodeWithTag("articleTableRemoveColumn_0").performScrollTo().performClick()
        compose.onNodeWithTag("articleCell_0_0_2").assertDoesNotExist()
        compose.onNodeWithTag("articleTableStriped_0").performScrollTo().performClick()
        compose.onNodeWithTag("articleSend").performClick()
        assertEquals(
            ArticleBlock.Table(
                ArticleText.Empty,
                listOf(
                    listOf(ArticleCell(p("Name"), header = true), ArticleCell(p("Points"), header = true)),
                    listOf(ArticleCell(ArticleText.Styled(ArticleStyle.Bold, p("Alice"))), ArticleCell(p("3"))),
                ),
                bordered = true,
                striped = true,
            ),
            sent.single().blocks.single(),
        )
    }

    @Test
    fun aTableWiderThanTheLimitBlocksSending() {
        val wide = ArticleBlock.Table(ArticleText.Empty, listOf(List(3) { ArticleCell(p("x")) }))
        val gate = ArticleGate(ArticlePosting.Enabled, limits = ArticleLimits(tableColumns = 2))
        compose.setContent { MioTheme { ArticleEditorContent(ArticleMessage(listOf(wide)).toRows(), gate, onSend = {}, onClose = {}) } }
        compose.onNodeWithTag("articleCell_0_0_2").assertExists()
        compose.onNodeWithTag("articleOver_TableColumns").assertExists()
        compose.onNodeWithTag("articleSend").assertIsNotEnabled()
        compose.onNodeWithTag("articleTableAddColumn_0").assertIsNotEnabled()
    }

    @Test
    fun mediaUploadsAsRowsAndHoldsSendingBack() {
        val sent = mutableListOf<ArticleMessage>()
        val picked = mutableListOf<ArticleMediaKind>()
        val retried = mutableListOf<Long>()
        val removed = mutableListOf<Long>()
        var uploads by mutableStateOf(mapOf<Long, ArticleUpload>())
        val gate = ArticleGate(ArticlePosting.Enabled, limits = ArticleLimits(media = 2))
        compose.setContent {
            MioTheme {
                ArticleEditorContent(
                    listOf(ArticleRow(ArticleRowKind.Paragraph, FormattedText("intro"), id = 0)), gate, onSend = { sent += it }, onClose = {},
                    uploads = uploads, onPickMedia = { picked += it }, onRetryUpload = { retried += it }, onRemoveUpload = { removed += it },
                )
            }
        }
        compose.onNodeWithTag("articleText_0").performTextInputSelection(TextRange(0))
        compose.onNodeWithTag("articleMedia").performClick()
        compose.onNodeWithTag("articleMedia_Photo").performClick()
        addRow()
        type("articleText_1", "/video ")
        assertEquals(listOf(ArticleMediaKind.Photo, ArticleMediaKind.Video), picked)
        assertEquals("", compose.onNodeWithTag("articleText_1").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        uploads = mapOf(1L to ArticleUpload(1, ArticleMediaKind.Photo, "cat.jpg", 50, 100))
        compose.waitForIdle()
        compose.onNodeWithTag("articleUploadProgress_2").performScrollTo()
        compose.onNodeWithTag("articleUploading").assertExists()
        compose.onNodeWithTag("articleSend").assertIsNotEnabled()
        compose.onNodeWithTag("articleText_2").performTextInputSelection(TextRange(0))
        uploads = uploads + (2L to ArticleUpload(2, ArticleMediaKind.Video, "clip.mp4", failed = true)) + (3L to ArticleUpload(3, ArticleMediaKind.File, "big.bin", 0, 10))
        compose.waitForIdle()
        compose.onNodeWithTag("articleUploadFailed_3").performScrollTo()
        compose.onNodeWithTag("articleUploadRetry_3").performClick()
        assertEquals(listOf(2L), retried)
        compose.onNodeWithTag("articleOver_Media").assertExists()
        compose.onNodeWithTag("articleMediaRemove_4").performScrollTo().performClick()
        assertEquals(listOf(3L), removed)
        compose.onNodeWithTag("articleRow_4").assertDoesNotExist()
        compose.onNodeWithTag("articleOver_Media").assertDoesNotExist()
        uploads = mapOf(
            1L to ArticleUpload(1, ArticleMediaKind.Photo, "cat.jpg", 100, 100, MediaItem("photo1", "cat.jpg", 100, 2, isPhoto = true)),
            2L to ArticleUpload(2, ArticleMediaKind.Video, "clip.mp4", 10, 10, MediaItem("doc2", "clip.mp4", 10, 2, isPhoto = false)),
            3L to ArticleUpload(3, ArticleMediaKind.File, "big.bin", 0, 10),
        )
        compose.waitForIdle()
        compose.onNodeWithTag("articleRow_4").assertDoesNotExist()
        compose.onNodeWithTag("articleUploading").assertDoesNotExist()
        type("articleText_2", "a cat")
        compose.onNodeWithTag("articleSend").assertIsEnabled().performClick()
        assertEquals(
            listOf(
                ArticleBlock.Paragraph(p("intro")),
                ArticleBlock.Photo(uploads.getValue(1L).media, ArticleCaption(p("a cat"))),
                ArticleBlock.Video(uploads.getValue(2L).media),
            ),
            sent.single().blocks,
        )
    }

    @Test
    fun premiumAccountsSendHeadings() {
        val initial = listOf(ArticleRow(ArticleRowKind.Heading1, FormattedText("Title"), id = 0))
        compose.setContent { MioTheme { ArticleEditorContent(initial, ArticleGate(ArticlePosting.Premium, premium = true), onSend = {}, onClose = {}) } }
        compose.onNodeWithTag("articlePremium").assertDoesNotExist()
        compose.onNodeWithTag("articleSend").assertIsEnabled()
    }
}
