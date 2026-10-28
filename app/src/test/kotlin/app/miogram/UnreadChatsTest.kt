package app.miogram

import app.miogram.data.DialogsState
import cn.ellerotta.core.model.DialogItem
import kotlin.test.assertEquals
import org.junit.Test

class UnreadChatsTest {
    @Test
    fun countsChatsNotMessagesWithoutTheOpenOneOrMutedOnes() {
        val state = DialogsState(
            dialogs = listOf(
                DialogItem("u1", "open", unread = 4),
                DialogItem("u2", "a", unread = 30),
                DialogItem("u3", "marked", unreadMark = true),
                DialogItem("u4", "read"),
                DialogItem("u5", "muted", unread = 2, muted = true),
            ),
            archived = listOf(DialogItem("u6", "archived", unread = 1), DialogItem("u7", "archived muted", unread = 9, muted = true)),
        )
        assertEquals(3, state.unreadChatsBesides("u1"))
        assertEquals(4, state.unreadChatsBesides("u4"))
        assertEquals(0, DialogsState().unreadChatsBesides("u1"))
    }
}
