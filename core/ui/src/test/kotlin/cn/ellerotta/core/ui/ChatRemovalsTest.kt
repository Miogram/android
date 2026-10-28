package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PendingRemoval
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.chats.RemovalSnackbar
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatRemovalsTest {
    @get:Rule
    val compose = createComposeRule()

    private val alice = DialogItem("u2", "Alice", Preview(text = "see you"), peerId = 2, date = 100, contact = true)
    private val bot = DialogItem("u4", "Helper", Preview(text = "hi"), peerId = 4, kind = PeerKind.Bot, date = 90)
    private val owned = DialogItem("c5", "Family", Preview(text = "dinner"), peerId = 5, kind = PeerKind.Group, date = 80, owner = true)
    private val tabs = listOf(FolderTab(ChatFolder.all(""), listOf(alice, bot, owned)))

    private fun screen(removed: MutableList<Triple<String, ChatRemoval, Boolean>>, withRemoval: Boolean = true) = compose.setContent {
        MioTheme {
            ChatsScreen(
                tabs = tabs,
                archived = emptyList(),
                avatars = emptyMap(),
                me = null,
                status = ChatsStatus.Ready,
                refreshing = false,
                onOpenChat = {},
                onOpenArchive = {},
                onRefresh = {},
                onAction = { _, _ -> },
                onRemove = if (withRemoval) ({ dialog, removal, revoke -> removed += Triple(dialog.key, removal, revoke) }) else null,
            )
        }
    }

    @Test
    fun aPrivateChatAsksAndTheBoxTakesItsMessagesFromTheOtherSideToo() {
        val removed = mutableListOf<Triple<String, ChatRemoval, Boolean>>()
        screen(removed)
        compose.onNodeWithTag("dialog_u2").performTouchInput { longClick() }
        compose.onNodeWithTag("removal_Clear").assertExists()
        compose.onNodeWithTag("removal_DeleteForEveryone").assertDoesNotExist()
        compose.onNodeWithTag("removal_Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete the chat with Alice? Its messages are deleted for you.").assertIsDisplayed()
        compose.onNodeWithText("Also delete for Alice").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), removed)

        compose.onNodeWithTag("removal_Delete").performScrollTo().performClick()
        compose.onNodeWithTag("removalRevoke").performClick()
        compose.onNodeWithTag("confirmRemoval").performClick()
        assertEquals(listOf(Triple("u2", ChatRemoval.Delete, true)), removed)
    }

    @Test
    fun aBotChatHasNoBoxAndAGroupsOwnerCanDeleteItForEveryone() {
        val removed = mutableListOf<Triple<String, ChatRemoval, Boolean>>()
        screen(removed)
        compose.onNodeWithTag("dialog_u4").performTouchInput { longClick() }
        compose.onNodeWithTag("removal_Clear").performScrollTo().performClick()
        compose.onNodeWithText("All messages in Helper will be deleted for you.").assertIsDisplayed()
        compose.onNodeWithTag("removalRevoke").assertDoesNotExist()
        compose.onNodeWithTag("confirmRemoval").performClick()
        assertEquals(listOf(Triple("u4", ChatRemoval.Clear, false)), removed)

        compose.onNodeWithTag("dialog_c5").performTouchInput { longClick() }
        compose.onNodeWithText("Delete and leave group").assertExists()
        compose.onNodeWithTag("removal_DeleteForEveryone").performScrollTo().performClick()
        compose.onNodeWithText("Delete Family for all its members? This cannot be undone.").assertIsDisplayed()
        compose.onNodeWithTag("removalRevoke").assertDoesNotExist()
        compose.onNodeWithTag("confirmRemoval").performClick()
        assertEquals(Triple("c5", ChatRemoval.DeleteForEveryone, false), removed.last())
    }

    @Test
    fun withoutARemovalCallbackTheMenuOffersNone() {
        screen(mutableListOf(), withRemoval = false)
        compose.onNodeWithTag("dialog_u2").performTouchInput { longClick() }
        compose.onNodeWithTag("action_MarkUnread").assertExists()
        compose.onNodeWithTag("removal_Delete").assertDoesNotExist()
        compose.onNodeWithTag("removal_Clear").assertDoesNotExist()
    }

    @Test
    fun theSnackbarSaysWhatIsAboutToHappenAndUndoes() {
        var undone = 0
        compose.setContent {
            MioTheme { RemovalSnackbar(PendingRemoval(owned, ChatRemoval.Delete), onUndo = { undone++ }) }
        }
        compose.onNodeWithText("You left Family").assertIsDisplayed()
        compose.onNodeWithTag("undoRemoval").performClick()
        assertEquals(1, undone)
    }

    @Test
    fun theChatMenuClearsAndDeletesAfterAsking() {
        val removed = mutableListOf<Pair<ChatRemoval, Boolean>>()
        compose.setContent {
            MioTheme {
                ChatScreen("Alice", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = alice, onRemove = { removal, revoke -> removed += removal to revoke })
            }
        }
        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_removal_Clear").performClick()
        compose.onNodeWithText("All messages in Alice will be deleted for you.").assertIsDisplayed()
        compose.onNodeWithTag("confirmRemoval").performClick()
        assertEquals(listOf(ChatRemoval.Clear to false), removed)

        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_removal_Delete").performClick()
        compose.onNodeWithTag("removalRevoke").performClick()
        compose.onNodeWithTag("confirmRemoval").performClick()
        assertEquals(ChatRemoval.Delete to true, removed.last())
    }

    @Test
    fun aGroupsOwnLeaveLineStaysAndTheMenuAddsDeletingItForEveryone() {
        compose.setContent {
            MioTheme {
                ChatScreen("Family", emptyList(), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {}, dialog = owned, onLeave = {}, onRemove = { _, _ -> })
            }
        }
        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_leave").assertExists()
        compose.onNodeWithTag("menu_removal_Clear").assertExists()
        compose.onNodeWithTag("menu_removal_DeleteForEveryone").assertExists()
        compose.onNodeWithTag("menu_removal_Delete").assertDoesNotExist()
    }
}
