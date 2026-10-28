package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class ScrollDownTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    private fun notes(ids: IntRange) = ids.map { MessageItem(it, "note $it", out = it % 2 == 0, date = now - 1000 + it) }

    @Test
    fun scrolledUpGoesDown() {
        compose.setContent {
            MioTheme { ChatScreen("Notes", notes(1..60), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}) }
        }
        compose.onNodeWithTag("message_60").assertIsDisplayed()
        compose.onNodeWithTag("scrollDown").assertDoesNotExist()

        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_5"))
        compose.onNodeWithContentDescription("Go to the newest messages").assertIsDisplayed()
        compose.onNodeWithTag("scrollDown").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("message_60").assertIsDisplayed()
        compose.onNodeWithTag("scrollDown").assertDoesNotExist()
    }

    @Test
    fun afterAJumpTheNewestPageLoadsFirst() {
        var messages by mutableStateOf(notes(1..30))
        var newer by mutableStateOf(true)
        var latest by mutableStateOf<Int?>(null)
        var asked = 0
        var shown = 0
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Notes", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    newerAvailable = newer,
                    latest = latest,
                    onLatestShown = {
                        shown++
                        latest = null
                    },
                    onShowLatest = { asked++ },
                )
            }
        }
        compose.onNodeWithTag("message_30").assertIsDisplayed()
        compose.onNodeWithTag("scrollDown").performClick()
        assertEquals(1, asked)

        latest = 80
        newer = false
        compose.waitForIdle()
        assertEquals(0, shown)

        messages = notes(20..80)
        compose.waitUntil(5_000) { shown == 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("message_80").assertIsDisplayed()
        compose.onNodeWithTag("scrollDown").assertDoesNotExist()
    }
}
