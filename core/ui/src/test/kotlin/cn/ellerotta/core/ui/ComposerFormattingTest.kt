package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.linkUrl
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposerFormattingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aSelectionGetsFormattedAndSentWithItsEntities() {
        val sent = mutableListOf<FormattedText>()
        compose.setContent { MioTheme { ChatScreen("Saved", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = { sent += it }, onDownload = {}) } }
        val input = compose.onNodeWithTag("messageInput")
        input.performTextInput("\uD83D\uDE00 hello bold world, see docs ")
        compose.onNodeWithTag("formattingBar").assertDoesNotExist()

        input.performTextInputSelection(TextRange(9, 13))
        compose.onNodeWithTag("formattingBar").assertIsDisplayed()
        compose.onNodeWithTag("formatBold").performScrollTo().performClick()
        input.performTextInputSelection(TextRange(9, 19))
        compose.onNodeWithTag("formatItalic").performScrollTo().performClick()
        input.performTextInputSelection(TextRange(3, 8))
        compose.onNodeWithTag("formatSpoiler").performScrollTo().performClick()
        compose.onNodeWithTag("formatClear").performScrollTo().performClick()
        input.performTextInputSelection(TextRange(25, 29))
        compose.onNodeWithTag("formatLink").performScrollTo().performClick()
        compose.onNodeWithTag("linkUrl").performTextInput("core.telegram.org/api/entities")
        compose.onNodeWithTag("confirmLink").performClick()

        input.performTextInputSelection(TextRange(11))
        input.performTextInput("l")
        compose.onNodeWithTag("send").performClick()
        assertEquals(
            listOf(
                FormattedText(
                    "\uD83D\uDE00 hello bolld world, see docs",
                    listOf(
                        TextEntity(9, 11, EntityType.Italic),
                        TextEntity(9, 5, EntityType.Bold),
                        TextEntity(26, 4, EntityType.TextUrl("https://core.telegram.org/api/entities")),
                    ),
                ),
            ),
            sent,
        )
    }

    @Test
    fun quotesCodeAndRepeatedStylesToggle() {
        val sent = mutableListOf<FormattedText>()
        compose.setContent { MioTheme { ChatScreen("Saved", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = { sent += it }, onDownload = {}) } }
        val input = compose.onNodeWithTag("messageInput")
        input.performTextInput("quote me\nrun this")
        input.performTextInputSelection(TextRange(0, 8))
        compose.onNodeWithTag("formatQuote").performScrollTo().performClick()
        input.performTextInputSelection(TextRange(9, 17))
        compose.onNodeWithTag("formatCode").performScrollTo().performClick()
        compose.onNodeWithTag("formatUnderline").performScrollTo().performClick()
        compose.onNodeWithTag("formatStrike").performScrollTo().performClick()
        compose.onNodeWithTag("formatUnderline").performScrollTo().performClick()
        compose.onNodeWithTag("send").performClick()
        assertEquals(
            listOf(FormattedText("quote me\nrun this", listOf(TextEntity(0, 8, EntityType.Blockquote()), TextEntity(9, 8, EntityType.Code), TextEntity(9, 8, EntityType.Strike)))),
            sent,
        )
    }

    @Test
    fun editingKeepsTheEntitiesOfTheMessage() {
        val edits = mutableListOf<Pair<Int, FormattedText>>()
        val message = MessageItem(4, "keep bold here", out = true, date = 1_790_000_000, entities = listOf(TextEntity(5, 4, EntityType.Bold)))
        compose.setContent {
            MioTheme {
                ChatScreen("Saved", listOf(message), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onEdit = { id, text -> edits += id to text })
            }
        }
        compose.onNodeWithTag("message_4").performTouchInput { longClick() }
        compose.onNodeWithTag("actionEdit").performClick()
        compose.onNodeWithTag("messageInput").performTextInputSelection(TextRange(0))
        compose.onNodeWithTag("messageInput").performTextInput("now ")
        compose.onNodeWithTag("send").performClick()
        assertEquals(listOf(4 to FormattedText("now keep bold here", listOf(TextEntity(9, 4, EntityType.Bold)))), edits)
    }

    @Test
    fun aLinkCanBeChangedAndRemoved() {
        val sent = mutableListOf<FormattedText>()
        compose.setContent { MioTheme { ChatScreen("Saved", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = { sent += it }, onDownload = {}) } }
        val input = compose.onNodeWithTag("messageInput")
        input.performTextInput("open this")
        input.performTextInputSelection(TextRange(5, 9))
        compose.onNodeWithTag("formatLink").performScrollTo().performClick()
        compose.onNodeWithTag("linkUrl").performTextInput("tg://resolve?domain=carol_writer")
        compose.onNodeWithTag("confirmLink").performClick()
        compose.onNodeWithTag("formatLink").performScrollTo().performClick()
        compose.onNodeWithTag("linkUrl").performTextReplacement("https://t.me/carol_writer")
        compose.onNodeWithTag("confirmLink").performClick()
        compose.onNodeWithTag("send").performClick()
        input.performTextInput("drop it")
        input.performTextInputSelection(TextRange(0, 4))
        compose.onNodeWithTag("formatLink").performScrollTo().performClick()
        compose.onNodeWithTag("linkUrl").performTextInput("example.com")
        compose.onNodeWithTag("confirmLink").performClick()
        compose.onNodeWithTag("formatLink").performScrollTo().performClick()
        compose.onNodeWithTag("removeLink").performClick()
        compose.onNodeWithTag("send").performClick()
        assertEquals(
            listOf(FormattedText("open this", listOf(TextEntity(5, 4, EntityType.TextUrl("https://t.me/carol_writer")))), FormattedText("drop it")),
            sent,
        )
        assertEquals("mailto:a@b.c", linkUrl(" mailto:a@b.c "))
    }
}
