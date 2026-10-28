package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.ui.components.chats.ArchivedChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val tabs = listOf(FolderTab(ChatFolder.all(""), listOf(DialogItem("u1", "Ada", peerId = 1))))

    private fun peer(id: Long, name: String, read: Boolean = false) =
        PeerStories("u$id", name, peerId = id, stories = listOf(StoryItem(id = 5, date = 0, expireDate = 0)), maxReadId = if (read) 5 else 0)

    private val others = listOf(peer(2, "Bob"), peer(3, "Carol"), peer(4, "Dan"), peer(5, "Eve", read = true), peer(6, "Fay", read = true))

    private fun show(
        stories: List<PeerStories>,
        myStories: PeerStories? = null,
        onOpenStories: (String) -> Unit = {},
        onAddStory: (() -> Unit)? = null,
        onHideStories: (String) -> Unit = {},
    ) = compose.setContent {
        MioTheme {
            ChatsScreen(
                tabs = tabs, archived = emptyList(), avatars = emptyMap(), me = null, status = ChatsStatus.Ready, refreshing = false,
                onOpenChat = {}, onOpenArchive = {}, onRefresh = {}, onAction = { _, _ -> },
                stories = stories, myStories = myStories, onOpenStories = onOpenStories, onAddStory = onAddStory, onHideStories = onHideStories,
            )
        }
    }

    @Test
    fun noStoriesNoAvatars() {
        show(emptyList(), onAddStory = {})
        compose.onNodeWithTag("storiesCollapsed").assertDoesNotExist()
        compose.onNodeWithText("Chats").assertIsDisplayed()
    }

    @Test
    fun collapsedAvatarsExpandIntoCards() {
        val opened = mutableListOf<String>()
        var added = 0
        show(others, onOpenStories = { opened += it }, onAddStory = { added++ })
        compose.onNodeWithTag("storiesCollapsed").assertIsDisplayed()
        compose.onNodeWithTag("storyAvatar_u2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storyAvatar_u4", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storyAvatar_u5", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("+2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("story_u2").assertDoesNotExist()

        compose.onNodeWithTag("storiesCollapsed").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("storiesExpanded").assertIsDisplayed()
        compose.onNodeWithTag("story_u2").assertIsDisplayed()
        compose.onNode(hasTestTag("story_u2") and hasText("Bob")).assertExists()
        compose.onNodeWithTag("addStory").assertIsDisplayed()
        compose.onNodeWithText("My story").assertIsDisplayed()

        compose.onNodeWithTag("addStory").performClick()
        assertEquals(1, added)
        compose.onNodeWithTag("story_u2").performClick()
        assertEquals(listOf("u2"), opened)
    }

    @Test
    fun longPressHidesAPeersStories() {
        val hidden = mutableListOf<String>()
        show(others, onHideStories = { hidden += it })
        compose.onNodeWithTag("storiesCollapsed").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("addStory").assertDoesNotExist()
        compose.onNodeWithTag("story_u3").performTouchInput { longClick() }
        compose.onNodeWithText("Hide stories").assertIsDisplayed()
        compose.onNodeWithTag("storyMenuItem").performClick()
        assertEquals(listOf("u3"), hidden)
    }

    @Test
    fun ownStoriesOpenFromMyStoryAndThePlusAddsOne() {
        val opened = mutableListOf<String>()
        var added = 0
        val me = PeerStories("u100", "Me", peerId = 100, stories = listOf(StoryItem(id = 1, date = 0, expireDate = 0, out = true)), self = true)
        show(others, myStories = me, onOpenStories = { opened += it }, onAddStory = { added++ })
        compose.onNodeWithTag("storiesCollapsed").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("addStory").performClick()
        assertEquals(listOf("u100"), opened)
        compose.onNodeWithTag("addStoryPlus").performClick()
        assertEquals(1, added)
    }

    @Test
    fun theArchiveShowsHiddenStories() {
        val opened = mutableListOf<String>()
        val unhidden = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                ArchivedChatsScreen(
                    dialogs = listOf(DialogItem("u9", "Old", archived = true)), avatars = emptyMap(), refreshing = false,
                    onBack = {}, onOpenChat = {}, onAction = { _, _ -> },
                    onOpenStories = { opened += it },
                    hiddenStories = listOf(peer(7, "Gus").copy(hidden = true)),
                    onUnhideStories = { unhidden += it },
                )
            }
        }
        compose.onNodeWithTag("hiddenStories").assertIsDisplayed()
        compose.onNodeWithTag("story_u7").performClick()
        assertEquals(listOf("u7"), opened)
        compose.onNodeWithTag("story_u7").performTouchInput { longClick() }
        compose.onNodeWithText("Show in chats").assertIsDisplayed()
        compose.onNodeWithTag("storyMenuItem").performClick()
        assertEquals(listOf("u7"), unhidden)
    }

    @Test
    fun theArchiveWithoutHiddenStoriesHasNoRow() {
        compose.setContent {
            MioTheme {
                ArchivedChatsScreen(
                    dialogs = listOf(DialogItem("u9", "Old", archived = true)), avatars = emptyMap(), refreshing = false,
                    onBack = {}, onOpenChat = {}, onAction = { _, _ -> },
                )
            }
        }
        compose.onNodeWithTag("hiddenStories").assertDoesNotExist()
        compose.onNodeWithTag("dialog_u9").assertIsDisplayed()
    }
}
