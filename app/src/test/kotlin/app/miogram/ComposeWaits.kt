package app.miogram

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.getOrNull
import org.robolectric.Shadows.shadowOf

fun ComposeTestRule.waitUntilIdle(condition: () -> Boolean) = waitUntil(30_000) {
    shadowOf(Looper.getMainLooper()).idle()
    condition()
}

fun ComposeTestRule.waitForTag(tag: String, unmerged: Boolean = false) = waitUntilIdle { onAllNodes(hasTestTag(tag), unmerged).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.waitForText(text: String) = waitUntilIdle { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.scrollToMessageNode(matcher: SemanticsMatcher) {
    val messages = onNodeWithTag("messages")
    messages.performScrollToNode(matcher)
    val offset = onNodeWithTag("chatHeader").fetchSemanticsNode().boundsInRoot.bottom - onNode(matcher).fetchSemanticsNode().positionInRoot.y
    if (offset != 0f) messages.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, offset) }
    waitForIdle()
}

fun ComposeTestRule.logIn() {
    waitForTag("start")
    onNodeWithTag("start").performClick()
    waitForTag("phone")
    onNodeWithTag("phone").performTextClearance()
    onNodeWithTag("phone").performTextInput("6627777")
    onNodeWithTag("sendCode").performClick()
    waitForTag("confirmPhone")
    onNodeWithTag("confirmPhone").performClick()
    waitForTag("code")
    onNodeWithTag("code").performTextInput("22222")
    waitForTag("openChats")
    waitForText("Alice Example")
}

fun ComposeTestRule.openSavedMessages(selfId: Long) {
    onNodeWithTag("openChats").performClick()
    waitUntilIdle { runCatching { onNodeWithTag("dialogs").performScrollToKey("u$selfId") }.isSuccess }
    onNodeWithTag("dialog_u$selfId").performClick()
}

fun editableText(text: String) = SemanticsMatcher("editable text \"$text\"") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == text }
