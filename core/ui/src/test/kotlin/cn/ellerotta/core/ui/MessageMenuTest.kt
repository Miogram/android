package cn.ellerotta.core.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ForwardChoice
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()
    private val dialog = DialogItem("u1", "Ada Lovelace", kind = PeerKind.User, presence = Presence.Recently)
    private val allowed = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83C\uDF89", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F").map { Reaction.Emoji(it) }
    private val messages = listOf(
        MessageItem(1, "the engine weaves algebraic patterns", out = false, date = now - 600, senderName = "Ada Lovelace"),
        MessageItem(2, "just as the loom weaves flowers and leaves", out = true, date = now - 300),
        MessageItem(3, "see note g", out = false, date = now - 60, senderName = "Ada Lovelace"),
    )

    private fun show(dark: Boolean = false, onAction: (String) -> Unit = {}, onForward: (ForwardChoice) -> Unit = {}) {
        compose.setContent {
            MioTheme(darkTheme = dark, useDynamicColor = false) {
                ChatScreen(
                    "Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = dialog,
                    onAttachMedia = {}, onVoiceStart = {},
                    onEdit = { _, _ -> onAction("edit") },
                    onDelete = { _, _ -> onAction("delete") },
                    reactions = ChatReactionState(allowed, default = allowed.first()),
                    onReact = { id, reaction, _, _ -> onAction("react $id ${(reaction as Reaction.Emoji).emoji}") },
                    onForward = onForward,
                    forwardTargets = listOf(DialogItem("u7", "Charles Babbage"), DialogItem("u0", "Saved", isSaved = true, kind = PeerKind.Saved)),
                )
            }
        }
    }

    @Test
    fun aLongPressLiftsTheMessageWithReactionsAndActions() {
        val done = mutableListOf<String>()
        show(onAction = { done += it })
        compose.onNodeWithTag("message_2").performTouchInput { longClick() }
        compose.onNodeWithTag("messageMenu").assertExists()
        listOf("actionReply", "actionCopy", "actionEdit", "actionDelete", "actionForward", "actionSelect").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        compose.onNodeWithTag("react_\uD83D\uDE02").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("react_\uD83D\uDE4F")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("moreReactions").performClick()
        compose.onNodeWithTag("react_\uD83D\uDE4F").performScrollTo().performClick()
        assertEquals(listOf("react 2 \uD83D\uDE4F"), done)
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("messageMenu")).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("message_2").performTouchInput { longClick() }
        compose.onNodeWithTag("messageMenu").performTouchInput { click(center.copy(y = 20f)) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("messageMenu")).fetchSemanticsNodes().isEmpty())
        assertEquals(1, done.size)
    }

    @Test
    fun selectingShowsChecksAndTheForwardBar() {
        val forwards = mutableListOf<ForwardChoice>()
        show(onForward = { forwards += it })
        compose.onNodeWithTag("message_1").performTouchInput { longClick() }
        compose.onNodeWithTag("actionSelect").performClick()
        compose.onNodeWithTag("select_1").assertIsOn()
        compose.onNodeWithTag("select_3").assertIsOff().performClick()
        compose.onNodeWithTag("select_3").assertIsOn()
        compose.onNodeWithTag("selectedCount").assertTextEquals("2 selected")
        screenshot("chat_selection", dark = false)

        compose.onNodeWithTag("forwardSelected").performClick()
        compose.onNodeWithTag("dropAuthor").performClick()
        compose.onNodeWithTag("forwardTo_u7").performClick()
        assertEquals(listOf(ForwardChoice(listOf(1, 3), DialogItem("u7", "Charles Babbage"), dropAuthor = true, dropCaptions = false)), forwards)
        assertTrue(compose.onAllNodes(hasTestTag("selectedCount")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun menuScreenshotLight() {
        show()
        compose.onNodeWithTag("message_2").performTouchInput { longClick() }
        compose.waitForIdle()
        screenshot("chat_menu", dark = false)
    }

    @Test
    fun menuScreenshotDark() {
        show(dark = true)
        compose.onNodeWithTag("message_2").performTouchInput { longClick() }
        compose.waitForIdle()
        screenshot("chat_menu", dark = true)
    }

    private fun screenshot(name: String, dark: Boolean) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width > 0)
        System.getenv("MIOGRAM_SCREENSHOTS")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "${name}_${if (dark) "dark" else "light"}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
