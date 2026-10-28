package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ThreadTitle
import cn.ellerotta.core.ui.components.chat.ChatSearchActions
import cn.ellerotta.core.ui.components.chats.ChatAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.core.model.RepliesInfo
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
class ThreadsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    @Test
    fun commentsAndRepliesBarsOpenTheirThreads() {
        val opened = mutableListOf<Int>()
        val messages = listOf(
            MessageItem(1, "post without comments", out = false, date = now - 300, replies = RepliesInfo(0, comments = true)),
            MessageItem(
                2, "post with comments", out = false, date = now - 200,
                replies = RepliesInfo(5, comments = true, recent = listOf(Reactor(7, "Alice")), maxId = 30, readMaxId = 20),
            ),
            MessageItem(3, "a question", out = false, date = now - 100, senderName = "Bob", replies = RepliesInfo(1, maxId = 40, readMaxId = 40)),
        )
        compose.setContent {
            MioTheme { ChatScreen(title = "Kotlin blog", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, onOpenReplies = { opened += it }) }
        }
        compose.onNodeWithText("Leave a comment").assertExists()
        compose.onNodeWithText("5 comments").assertExists()
        compose.onNodeWithText("1 reply").assertExists()
        compose.onNodeWithTag("repliesUnread_2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("repliesUnread_3", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("replies_2", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("replies_3", useUnmergedTree = true).performClick()
        assertEquals(listOf(2, 3), opened)
    }

    @Test
    fun aThreadTitleCountsAndOpensNothing() {
        var thread by mutableStateOf(ThreadTitle(comments = true, count = null))
        var profiles = 0
        val actions = mutableListOf<ChatAction>()
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Comments", messages = emptyList(), downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    onOpenProfile = { profiles++ }, dialog = DialogItem("c1", "Hikers", kind = PeerKind.Group), onDialogAction = { actions += it }, thread = thread,
                    searchActions = ChatSearchActions(onQuery = {}, onMore = {}, onClose = {}),
                )
            }
        }
        compose.onNodeWithText("Comments", useUnmergedTree = true).assertExists()
        thread = ThreadTitle(comments = true, count = 12)
        compose.waitForIdle()
        compose.onNodeWithText("12 Comments", useUnmergedTree = true).assertExists()
        thread = ThreadTitle(comments = true, count = 13)
        compose.waitForIdle()
        compose.onNodeWithText("13 Comments", useUnmergedTree = true).assertExists()
        thread = ThreadTitle(comments = false, count = 1)
        compose.waitForIdle()
        compose.onNodeWithText("1 Reply", useUnmergedTree = true).assertExists()
        thread = ThreadTitle(comments = false, count = 4)
        compose.waitForIdle()
        compose.onNodeWithText("4 Replies", useUnmergedTree = true).assertExists()
        thread = ThreadTitle(comments = false, count = 0)
        compose.waitForIdle()
        compose.onNodeWithText("Replies", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("threadTitle", useUnmergedTree = true).assertDoesNotExist()

        thread = ThreadTitle(comments = true, count = 2)
        compose.waitForIdle()
        compose.onNodeWithTag("title").assertHasNoClickAction()
        compose.onNodeWithTag("title").performTouchInput { click() }
        assertEquals(0, profiles)
        compose.onNodeWithTag("title").performTouchInput { longClick() }
        compose.onNodeWithTag("searchField").assertExists()
        assertEquals(emptyList(), actions)
    }

    @Test
    fun aThreadMarksItsFirstUnreadReply() {
        val messages = listOf(
            MessageItem(100, "post 3", out = false, date = now - 300),
            MessageItem(101, "read comment", out = false, date = now - 200, threadId = 100),
            MessageItem(102, "own comment", out = true, date = now - 150, threadId = 100),
            MessageItem(103, "new comment", out = false, date = now - 100, threadId = 100),
        )
        compose.setContent {
            MioTheme { ChatScreen(title = "Comments", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, unreadAfter = 101) }
        }
        compose.onNodeWithTag("unreadLine", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Unread messages").assertExists()
        compose.onNodeWithTag("replies_100", useUnmergedTree = true).assertDoesNotExist()
    }
}
