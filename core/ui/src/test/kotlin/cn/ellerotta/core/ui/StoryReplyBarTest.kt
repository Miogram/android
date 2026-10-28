package cn.ellerotta.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.ui.components.stories.StoryHeart
import cn.ellerotta.core.ui.components.stories.StoryQuickReactions
import cn.ellerotta.core.ui.components.stories.StoryReplyBar
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
class StoryReplyBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val replies = mutableListOf<String>()
    private val reacted = mutableListOf<Reaction?>()
    private val paused = mutableListOf<Boolean>()

    private fun show(story: StoryItem = StoryItem(1, date = 1_700_000_000, expireDate = 1_700_086_400)) {
        compose.setContent {
            MioTheme { StoryReplyBar(story, "Alice", onReply = { replies += it }, onReact = { reacted += it }, pause = { paused += it }) }
        }
        compose.waitForIdle()
    }

    @Test
    fun typingAndSendingRepliesAndClearsTheField() {
        show()
        compose.onNodeWithTag("storyReplySend").assertDoesNotExist()
        compose.onNodeWithTag("storyReplyField").performTextInput("  nice view ")
        compose.onNodeWithTag("storyReplySend").performClick()
        assertEquals(listOf("nice view"), replies)
        compose.onNodeWithTag("storyReplyField").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("storyReplySend").assertDoesNotExist()
    }

    @Test
    fun focusPausesTheStoryAndShowsQuickReactions() {
        show()
        assertEquals(emptyList<Boolean>(), paused)
        compose.onNodeWithTag("storyQuickReactions").assertDoesNotExist()
        compose.onNodeWithTag("storyReplyField").performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), paused)
        compose.onNodeWithTag("storyQuickReactions").assertExists()
    }

    @Test
    fun aQuickReactionIsSentAndResumes() {
        show()
        compose.onNodeWithTag("storyReplyField").performClick()
        compose.onNodeWithTag("storyQuickReaction_1").performClick()
        compose.waitForIdle()
        assertEquals(listOf<Reaction?>(StoryQuickReactions[1]), reacted)
        assertEquals(listOf(true, false), paused)
        compose.onNodeWithTag("storyQuickReactions").assertDoesNotExist()
    }

    @Test
    fun theHeartTogglesTheOwnReaction() {
        var story by mutableStateOf(StoryItem(1, date = 1_700_000_000, expireDate = 1_700_086_400))
        compose.setContent {
            MioTheme {
                StoryReplyBar(story, "Alice", onReply = {}, onReact = { reacted += it; story = story.copy(reaction = it) }, pause = {})
            }
        }
        compose.onNodeWithTag("storyHeart").assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("storyHeart").assertIsOn().performClick()
        compose.waitForIdle()
        assertEquals(listOf(StoryHeart, null), reacted)
        compose.onNodeWithTag("storyHeart").assertIsOff()
    }

    @Test
    fun customEmojiReactionsAreDrawnAsThemselves() {
        val star = MediaItem("doc5", "emoji", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\u2B50", animated = true)
        val heart = MediaItem("doc6", "emoji", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "\u2764", animated = true)
        val story = StoryItem(1, date = 1_700_000_000, expireDate = 1_700_086_400, reaction = Reaction.CustomEmoji(6))
        val files = mapOf("doc5" to "{}".toByteArray(), "doc6" to "{}".toByteArray())
        compose.setContent {
            MioTheme {
                StoryReplyBar(
                    story, "Alice", onReply = {}, onReact = { reacted += it }, pause = {},
                    reactions = listOf(Reaction.CustomEmoji(5)) + StoryQuickReactions,
                    customEmoji = mapOf(5L to star, 6L to heart), emojiFiles = files,
                )
            }
        }
        compose.onNode(hasTestTag("sticker_doc6") and hasAnyAncestor(hasTestTag("storyHeart")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storyReplyField").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("storyQuickReaction_0").assertContentDescriptionEquals("React with \u2B50")
        compose.onNode(hasTestTag("sticker_doc5") and hasAnyAncestor(hasTestTag("storyQuickReaction_0")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storyQuickReaction_0").performClick()
        assertEquals(listOf<Reaction?>(Reaction.CustomEmoji(5)), reacted)
    }
}
