package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.contacts.ContactsPageState
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatsToContactsTest {
    @get:Rule
    val compose = createComposeRule()

    private val bot = DialogItem("u4", "Helper", peerId = 4, kind = PeerKind.Bot)
    private val tabs = listOf(
        FolderTab(ChatFolder.all(""), listOf(DialogItem("u2", "Alice", peerId = 2), bot)),
        FolderTab(ChatFolder(7, "Bots", bots = true), listOf(bot)),
    )
    private val bob = ContactItem("u9", 9, "Bob")

    private fun top(tag: String): Dp = compose.onNodeWithTag(tag).getBoundsInRoot().top

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs((actual - expected).value) <= 8.5f, "$what at $actual, the rows started at $expected")

    @Test
    fun theListsKeepTheirPlaceOnTheWayToTheContacts() {
        var contacts by mutableStateOf(ContactsPageState(contacts = listOf(bob), loading = true))
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = tabs, archived = emptyList(), avatars = emptyMap(), me = null, status = ChatsStatus.Ready,
                    refreshing = false, onOpenChat = {}, onOpenArchive = {}, onRefresh = {}, onAction = { _, _ -> },
                    contacts = contacts,
                )
            }
        }
        compose.onNodeWithTag("folder_7").performClick()
        compose.waitForIdle()
        val rows = top("dialog_u4")

        compose.onRoot().performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(-width * 0.025f, 0f)) }
        }
        compose.waitForIdle()
        assertNear(rows, top("dialog_u4"), "the folder's first row")
        assertNear(rows, top("contact_u9"), "the first contact")

        compose.onRoot().performTouchInput {
            repeat(10) { moveBy(Offset(-width * 0.025f, 0f)) }
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("folders").assertDoesNotExist()
        compose.onNodeWithTag("contactsLoading").assertDoesNotExist()
        assertNear(rows, top("contact_u9"), "the first contact")

        contacts = ContactsPageState(loading = true)
        compose.waitForIdle()
        compose.onNodeWithTag("contactsLoading").assertExists()
    }
}
