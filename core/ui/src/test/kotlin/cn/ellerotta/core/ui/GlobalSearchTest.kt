package cn.ellerotta.core.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.MessageHit
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.SearchFilter
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.chats.GlobalSearchActions
import cn.ellerotta.core.ui.components.chats.GlobalSearchState
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlobalSearchTest {
    @get:Rule
    val compose = createComposeRule()

    private val alice = DialogItem("u2", "Alice", Preview(text = "hi"), peerId = 2, date = 100)
    private val hit = MessageHit(alice.copy(preview = Preview(text = "lunch at one"), date = 90), 41)

    private fun screen(state: GlobalSearchState, actions: GlobalSearchActions) = compose.setContent {
        MioTheme {
            ChatsScreen(
                tabs = listOf(FolderTab(ChatFolder.all(""), listOf(alice))),
                archived = emptyList(),
                avatars = emptyMap(),
                me = null,
                status = ChatsStatus.Ready,
                refreshing = false,
                onOpenChat = {},
                onOpenArchive = {},
                onRefresh = {},
                onAction = { _, _ -> },
                search = state,
                searchActions = actions,
            )
        }
    }

    private fun typeQuery(text: String) {
        compose.onNodeWithTag("openSearch").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("search").performTextInput(text)
        compose.waitForIdle()
    }

    @Test
    fun hitsShowUnderMessagesAndOpenAtTheirMessageRememberingTheQuery() {
        val opened = mutableListOf<Int>()
        val remembered = mutableListOf<String>()
        screen(GlobalSearchState(hits = listOf(hit)), GlobalSearchActions(onOpenHit = { opened += it.messageId }, onOpened = { remembered += it }))
        typeQuery("lunch")
        compose.onNodeWithTag("messagesTitle").assertExists()
        compose.onNodeWithTag("hit_u2_41").performClick()
        assertEquals(listOf(41), opened)
        assertEquals(listOf("lunch"), remembered)
    }

    @Test
    fun aChipChoosesAKindAndTheChosenOneGoesBack() {
        val kinds = mutableListOf<SearchFilter>()
        screen(GlobalSearchState(), GlobalSearchActions(onFilter = { kinds += it }))
        compose.onNodeWithTag("openSearch").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("searchFilter_Links").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("searchFilter_Links").performClick()
        assertEquals(listOf(SearchFilter.Links, SearchFilter.All), kinds)
    }

    @Test
    fun anEmptyQueryShowsPeopleAndRecentSearchesThatCanBeUsedAndForgotten() {
        val forgotten = mutableListOf<String>()
        var cleared = 0
        screen(GlobalSearchState(recent = listOf("lunch", "#kotlin"), topPeers = listOf(alice)), GlobalSearchActions(onRemoveRecent = { forgotten += it }, onClearRecent = { cleared++ }))
        compose.onNodeWithTag("openSearch").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("search").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("topPeer_u2").assertExists()
        compose.onNodeWithTag("removeRecent_#kotlin").performClick()
        compose.onNodeWithTag("clearRecent").performClick()
        assertEquals(listOf("#kotlin"), forgotten)
        assertEquals(1, cleared)
        compose.onNodeWithTag("recent_lunch").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recent_lunch").assertDoesNotExist()
    }
}
