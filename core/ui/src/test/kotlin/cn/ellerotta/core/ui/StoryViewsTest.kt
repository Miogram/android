package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.model.StoryViewer
import cn.ellerotta.core.model.StoryViewersPage
import cn.ellerotta.core.model.StoryViews
import cn.ellerotta.core.ui.components.stories.StoryOwnMenu
import cn.ellerotta.core.ui.components.stories.StoryViewersSheet
import cn.ellerotta.core.ui.components.stories.StoryViewsBar
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
class StoryViewsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    @Test
    fun theBarCountsViewsAndReactionsAndOpensTheList() {
        var opened = 0
        val story = StoryItem(1, date = now, expireDate = now + 86_400, out = true, views = StoryViews(views = 12, reactions = 3, recent = listOf("a1", "a2")))
        compose.setContent { MioTheme { StoryViewsBar(story, avatars = emptyMap(), onOpen = { opened++ }) } }
        compose.onNodeWithTag("storyViewsCount", useUnmergedTree = true).assertTextEquals("12 views")
        compose.onNodeWithTag("storyReactionsCount", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storyViews").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theBarHidesReactionsWithoutAny() {
        val story = StoryItem(1, date = now, expireDate = now + 86_400, out = true, views = StoryViews(views = 1))
        compose.setContent { MioTheme { StoryViewsBar(story, avatars = emptyMap(), onOpen = {}) } }
        compose.onNodeWithTag("storyViewsCount", useUnmergedTree = true).assertTextEquals("1 view")
        compose.onNodeWithTag("storyReactionsCount", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSheetListsViewersWithTheirReactionsAndOpensThem() {
        val opened = mutableListOf<String>()
        val page = StoryViewersPage(
            viewers = listOf(
                StoryViewer("u7", "Alice", peerId = 7, date = now - 60, reaction = Reaction.Emoji("\uD83D\uDD25")),
                StoryViewer("u8", "Bob", peerId = 8, date = now - 120),
            ),
            views = 2,
            reactions = 1,
            nextOffset = null,
        )
        compose.setContent { MioTheme { StoryViewersSheet(page, emptyMap(), loading = false, onMore = {}, onOpenPeer = { opened += it }, onDismiss = {}) } }
        compose.waitForIdle()
        compose.onNode(hasText("2 views") and hasAnyAncestor(hasTestTag("storyViewersSheet"))).assertExists()
        compose.onNodeWithText("1 reaction").assertExists()
        compose.onNodeWithTag("storyViewerReaction_u7", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storyViewerReaction_u8", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("storyViewer_u8").performClick()
        assertEquals(listOf("u8"), opened)
    }

    @Test
    fun aCustomEmojiReactionIsDrawnAsItselfOnceItsFileIsThere() {
        val star = MediaItem("doc5", "emoji", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\u2B50", animated = true)
        val page = StoryViewersPage(listOf(StoryViewer("u7", "Alice", peerId = 7, date = now, reaction = Reaction.CustomEmoji(5))), views = 1, reactions = 1, nextOffset = null)
        var documents by mutableStateOf(emptyMap<Long, MediaItem>())
        var files by mutableStateOf(emptyMap<String, ByteArray>())
        compose.setContent {
            MioTheme { StoryViewersSheet(page, emptyMap(), loading = false, onMore = {}, onOpenPeer = {}, onDismiss = {}, customEmoji = documents, emojiFiles = files) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("storyViewerReaction_u7", useUnmergedTree = true).assertContentDescriptionEquals("Custom emoji")
        compose.onNodeWithTag("sticker_doc5", useUnmergedTree = true).assertDoesNotExist()
        documents = mapOf(5L to star)
        compose.waitForIdle()
        compose.onNodeWithTag("storyViewerReaction_u7", useUnmergedTree = true).assertContentDescriptionEquals("\u2B50")
        compose.onNodeWithTag("sticker_doc5", useUnmergedTree = true).assertDoesNotExist()
        files = mapOf("doc5" to "{}".toByteArray())
        compose.waitForIdle()
        compose.onNodeWithTag("sticker_doc5", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theSheetAsksForMoreWhileThereIsANextPage() {
        var more = 0
        val page = StoryViewersPage(listOf(StoryViewer("u7", "Alice", peerId = 7, date = now)), views = 40, reactions = 0, nextOffset = "next")
        compose.setContent { MioTheme { StoryViewersSheet(page, emptyMap(), loading = false, onMore = { more++ }, onOpenPeer = {}, onDismiss = {}) } }
        compose.waitForIdle()
        assertTrue(more > 0)
    }

    @Test
    fun anEmptyListSaysWhetherNobodyLookedOrTheListIsHidden() {
        compose.setContent {
            MioTheme { StoryViewersSheet(StoryViewersPage(emptyList(), views = 5, reactions = 0, nextOffset = null), emptyMap(), loading = false, onMore = {}, onOpenPeer = {}, onDismiss = {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("storyViewersEmpty").assertTextEquals("The list of viewers is not available")
    }

    @Test
    fun nobodyViewedYet() {
        compose.setContent {
            MioTheme { StoryViewersSheet(StoryViewersPage(emptyList(), views = 0, reactions = 0, nextOffset = null), emptyMap(), loading = false, onMore = {}, onOpenPeer = {}, onDismiss = {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("storyViewersEmpty").assertTextEquals("Nobody has viewed this story yet")
    }

    @Test
    fun theFirstPageLoads() {
        compose.setContent { MioTheme { StoryViewersSheet(null, emptyMap(), loading = true, onMore = {}, onOpenPeer = {}, onDismiss = {}) } }
        compose.waitForIdle()
        compose.onNodeWithTag("storyViewersLoading").assertExists()
    }

    @Test
    fun theOwnMenuEditsPinsAndDeletesAfterAskingWhileTheStoryWaits() {
        val pauses = mutableListOf<Boolean>()
        val calls = mutableListOf<String>()
        var story by mutableStateOf(StoryItem(1, date = now, expireDate = now + 86_400, out = true))
        compose.setContent {
            MioTheme {
                StoryOwnMenu(story, onEdit = { calls += "edit" }, onPin = { calls += "pin $it" }, onDelete = { calls += "delete" }, pause = { pauses += it })
            }
        }
        compose.onNodeWithTag("storyOwnMenu").performClick()
        compose.onNodeWithText("Keep on profile").performClick()
        story = story.copy(pinned = true)
        compose.onNodeWithTag("storyOwnMenu").performClick()
        compose.onNodeWithText("Remove from profile").performClick()
        compose.onNodeWithTag("storyOwnMenu").performClick()
        compose.onNodeWithTag("storyEdit").performClick()
        compose.onNodeWithTag("storyOwnMenu").performClick()
        compose.onNodeWithTag("storyDelete").performClick()
        compose.onNodeWithText("Delete this story?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("storyOwnMenu").performClick()
        compose.onNodeWithTag("storyDelete").performClick()
        compose.onNodeWithTag("storyDeleteConfirm").performClick()
        assertEquals(listOf("pin true", "pin false", "edit", "delete"), calls)
        assertEquals(List(5) { listOf(true, false) }.flatten(), pauses)
    }
}
