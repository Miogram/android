package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.contacts.ContactsActions
import cn.ellerotta.core.ui.components.contacts.ContactsPageState
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContactsTest {
    @get:Rule
    val compose = createComposeRule()

    private val erin = ContactItem("u6", 6, "Erin Online", Presence.Online, mutual = true)
    private val bob = ContactItem("u2", 2, "Bob", Presence.LastWeek, username = "bobby")
    private val carol = ContactItem("u3", 3, "Carol Writer", Presence.Recently, contact = false)

    @Test
    fun pageRowsSearchAddAndDelete() {
        var state by mutableStateOf(ContactsPageState(contacts = listOf(erin, bob)))
        val opened = mutableListOf<String>()
        val searched = mutableListOf<String>()
        val added = mutableListOf<List<String>>()
        val deleted = mutableListOf<String>()
        var shown = 0
        compose.setContent {
            MioTheme {
                ChatsScreen(
                    tabs = listOf(FolderTab(ChatFolder.all(""), listOf(DialogItem("u9", "Alice")))),
                    archived = emptyList(),
                    avatars = emptyMap(),
                    me = null,
                    status = ChatsStatus.Ready,
                    refreshing = false,
                    onOpenChat = {},
                    onOpenArchive = {},
                    onRefresh = {},
                    onAction = { _, _ -> },
                    contacts = state,
                    contactsActions = ContactsActions(
                        onOpen = { opened += it.key },
                        onSearch = { searched += it },
                        onAdd = { phone, first, last -> added += listOf(phone, first, last) },
                        onDelete = { deleted += it.key },
                        onShown = { shown++ },
                    ),
                )
            }
        }

        compose.onNodeWithTag("openContacts").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("contact_u6").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Contacts").assertExists()
        assertEquals(1, shown)
        compose.onNodeWithTag("contact_u6").assertTextContains("online", substring = true)
        compose.onNodeWithContentDescription("Mutual contact").assertExists()
        compose.onNodeWithTag("contact_u2").assertTextContains("last seen within a week", substring = true, ignoreCase = true)
        compose.onNodeWithTag("contact_u6").performClick()
        assertEquals(listOf("u6"), opened)

        compose.onNodeWithTag("contactsSearch").performTextInput("bob")
        assertEquals("bob", searched.last())
        compose.waitUntil { compose.onAllNodesWithTag("contact_u6").fetchSemanticsNodes().isEmpty() }
        state = state.copy(globalResults = listOf(carol))
        compose.onNodeWithTag("contactsGlobalTitle").assertExists()
        compose.onNodeWithTag("contact_u3").assertExists()

        compose.onNodeWithTag("addContact").performClick()
        compose.onNodeWithTag("contactPhone").performTextInput("+1 555 01")
        compose.onNodeWithTag("contactFirstName").performTextInput("Dan")
        compose.onNodeWithTag("confirmAddContact").performClick()
        assertEquals(listOf(listOf("+1 555 01", "Dan", "")), added)
        state = state.copy(notFound = true)
        compose.onNodeWithText("No Telegram account has this phone number").assertExists()
        state = state.copy(notFound = false, added = 1)
        compose.waitUntil { compose.onAllNodesWithTag("contactPhone").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithTag("contact_u2").performTouchInput { longClick() }
        compose.onNodeWithTag("confirmDeleteContact").performClick()
        assertEquals(listOf("u2"), deleted)
        assertTrue(compose.onAllNodesWithTag("confirmDeleteContact").fetchSemanticsNodes().isEmpty())
    }
}
