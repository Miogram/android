package cn.ellerotta.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MessageTranslation
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatTranslateActions
import cn.ellerotta.core.ui.components.common.errorText
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranslationTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val translated = mutableListOf<Int>()
    private val originals = mutableListOf<Int>()
    private val actions = ChatTranslateActions(onTranslate = { translated += it }, onShowOriginal = { originals += it })

    private fun show(messages: List<MessageItem>) {
        compose.setContent {
            MioTheme { ChatScreen(title = "Chat", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, translations = actions) }
        }
    }

    @Test
    fun theActionsSheetTranslatesAndATranslationShowsInPlace() {
        val original = MessageItem(1, "guten tag", out = false, date = now - 60)
        val shown = MessageItem(2, "wie geht's", out = false, date = now, translation = MessageTranslation("en", FormattedText("how are you")))
        show(listOf(original, shown))
        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.onNodeWithTag("actionTranslate").performClick()
        assertEquals(listOf(1), translated)

        compose.onNodeWithText("how are you", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("wie geht's", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Translated · EN", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("showOriginal_2", useUnmergedTree = true).performClick()
        assertEquals(listOf(2), originals)
        compose.onNodeWithTag("message_2").performTouchInput { longClick(Offset(centerX, 8f)) }
        compose.onNodeWithTag("actionShowOriginal").performClick()
        assertEquals(listOf(2, 2), originals)
    }

    @Test
    fun messagesWithoutTextAndLocalOnesOfferNoTranslation() {
        show(listOf(MessageItem(1, "on its way", out = true, date = now, pending = true), MessageItem(2, "", out = false, date = now)))
        compose.onNodeWithTag("message_2").performTouchInput { longClick() }
        compose.onNodeWithTag("actionReply").assertExists()
        compose.onNodeWithTag("actionTranslate").assertDoesNotExist()
    }

    @Test
    fun translationRefusalsReadAsText() {
        compose.setContent {
            MioTheme {
                Text(errorText(UiError.Rpc(400, "TO_LANG_INVALID")))
                Text(errorText(UiError.Rpc(400, "QUOTA_EXCEEDED")))
            }
        }
        compose.onNodeWithText("Messages cannot be translated into this language").assertExists()
        compose.onNodeWithText("Too many translations for now, try again later").assertExists()
    }
}
