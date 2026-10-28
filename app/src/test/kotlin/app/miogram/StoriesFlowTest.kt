package app.miogram

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputReplyToStory
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "w412dp-h892dp")
class StoriesFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun ring(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    private fun ringed(key: String, state: String) = hasTestTag("dialogAvatar_$key") and ring(state)

    @Test
    fun ringsShowInTheListAndTheChatHeader() {
        compose.logIn()
        compose.waitUntilIdle { compose.onAllNodes(ringed("u501", "Unread stories for close friends"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val saved = "dialogAvatar_u${fake.selfId}"
        compose.waitForTag(saved, unmerged = true)
        assertTrue(compose.onAllNodes(hasTestTag(saved) and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText("Alice Example").performClick()
        compose.waitForTag("chatHeader")
        compose.waitUntilIdle {
            compose.onAllNodes(hasAnyAncestor(hasTestTag("chatHeader")) and ring("Unread stories for close friends"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theBarShowsStoriesAndHidingMovesThemToTheArchive() {
        compose.logIn()
        compose.waitForTag("storyAvatar_u501", unmerged = true)
        compose.onNodeWithTag("storiesCollapsed").performClick()
        compose.waitForTag("story_u501")
        compose.onNodeWithTag("story_u501").performTouchInput { longClick() }
        compose.onNodeWithTag("storyMenuItem").performClick()
        compose.waitUntilIdle { FakeStories.ALICE to true in fake.stories.toggles }

        compose.onNodeWithTag("openChats").performTouchInput { longClick() }
        compose.waitForTag("hiddenStories")
        compose.waitForTag("story_u503")
        compose.waitForTag("story_u501")
        compose.onNodeWithTag("story_u501").performTouchInput { longClick() }
        compose.onNodeWithTag("storyMenuItem").performClick()
        compose.waitUntilIdle { FakeStories.ALICE to false in fake.stories.toggles }
    }

    @Test
    fun aRingedPhotoOpensTheViewerWhichReadsAndCountsTheStory() {
        compose.logIn()
        compose.waitUntilIdle { compose.onAllNodes(ringed("u501", "Unread stories for close friends"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag("dialogAvatar_u501"), useUnmergedTree = true).performClick()
        compose.waitUntilIdle { FakeStories.ALICE to 2 in fake.stories.reads }
        compose.waitUntilIdle { fake.stories.views.any { it.first == FakeStories.ALICE && 2 in it.second } }
        compose.waitUntilIdle { FakeStories.ALICE_PHOTO in fake.stories.downloads }
        compose.waitUntilIdle { compose.onAllNodes(ringed("u501", "Viewed stories"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun othersStoriesTakeARepliesAndAHeart() {
        compose.logIn()
        compose.waitUntilIdle { compose.onAllNodes(ringed("u501", "Unread stories for close friends"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        fake.stories.holdFiles = CompletableDeferred()
        compose.onNode(hasTestTag("dialogAvatar_u501"), useUnmergedTree = true).performClick()
        compose.waitForTag("story_u501_2")
        compose.onNodeWithTag("storyHeart").performClick()
        compose.waitUntilIdle { fake.stories.reactions.any { it.storyId == 2 } }
        compose.onNodeWithTag("storyReplyField").performClick()
        compose.onNodeWithTag("storyReplyField").performTextInput("Nice view")
        compose.onNodeWithTag("storyReplySend").performClick()
        compose.waitUntilIdle { fake.stories.replies.any { it.message == "Nice view" && (it.replyTo as InputReplyToStory).storyId == 2 } }
        fake.stories.holdFiles?.complete(Unit)
    }

    @Test
    fun ownStoriesListTheirViewersPageByPage() {
        compose.logIn()
        compose.waitForTag("storyAvatar_u501", unmerged = true)
        fake.stories.holdFiles = CompletableDeferred()
        compose.onNodeWithTag("storiesCollapsed").performClick()
        compose.waitForTag("addStory")
        compose.onNodeWithTag("addStory").performClick()
        compose.waitForTag("story_u${fake.selfId}_1")
        compose.onNodeWithTag("storyViews").performClick()
        compose.waitForTag("storyViewer_u501")
        compose.waitForTag("storyViewer_u502")
        assertTrue(fake.stories.viewerCalls.size >= 2)
        fake.stories.holdFiles?.complete(Unit)
        compose.waitUntilIdle {
            compose.onAllNodes(hasTestTag("sticker_doc${FakeTelegram.EMOJI_STAR}") and hasAnyAncestor(hasTestTag("storyViewerReaction_u502")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntilIdle { FakeStories.ALICE_PHOTO in fake.stories.downloads }
        compose.onNodeWithTag("story_u${fake.selfId}_1").assertExists()
    }

    @Test
    fun ownStoriesAreKeptOnTheProfileEditedAndDeleted() {
        compose.logIn()
        compose.waitForTag("storyAvatar_u501", unmerged = true)
        fake.stories.holdFiles = CompletableDeferred()
        compose.onNodeWithTag("storiesCollapsed").performClick()
        compose.waitForTag("addStory")
        compose.onNodeWithTag("addStory").performClick()
        val own = "story_u${fake.selfId}_1"
        compose.waitForTag(own)
        compose.onNodeWithTag("storyOwnMenu").performClick()
        compose.onNodeWithTag("storyPin").performClick()
        compose.waitUntilIdle { fake.stories.pins.any { it.pinned && it.id == listOf(1) } }

        compose.onNodeWithTag("storyOwnMenu").performClick()
        compose.onNodeWithTag("storyEdit").performClick()
        val field = hasTestTag("storyCaption") and hasAnyAncestor(hasTestTag("storyComposer"))
        compose.waitUntilIdle { compose.onAllNodes(field and editableText("own story")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(field).performTextReplacement("edited")
        compose.onNodeWithTag("storySend").performClick()
        compose.waitUntilIdle { fake.stories.edits.any { it.caption == "edited" && it.privacyRules == null && it.media == null } }

        compose.waitForTag(own)
        compose.onNodeWithTag("storyOwnMenu").performClick()
        compose.onNodeWithTag("storyDelete").performClick()
        compose.onNodeWithTag("storyDeleteConfirm").performClick()
        compose.waitUntilIdle { fake.stories.deletes == listOf(listOf(1)) }
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag(own)).fetchSemanticsNodes().isEmpty() }
        fake.stories.holdFiles?.complete(Unit)
    }

    @Test
    fun thePlusOpensTheComposerWhilePostingIsAllowed() {
        compose.logIn()
        compose.waitForTag("storyAvatar_u501", unmerged = true)
        compose.onNodeWithTag("storiesCollapsed").performClick()
        compose.waitForTag("addStoryPlus")
        compose.onNodeWithTag("addStoryPlus").performClick()
        compose.waitForTag("storyComposer")
        compose.onNodeWithTag("storyPick").assertExists()
    }
}
