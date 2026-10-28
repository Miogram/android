package cn.ellerotta.core.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.StoryAudience
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.ui.components.stories.StoryComposer
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
class StoryComposerTest {
    @get:Rule
    val compose = createComposeRule()

    private var picks = 0
    private var closes = 0
    private val sent = mutableListOf<Pair<String, StoryAudience?>>()

    private fun show(preview: ByteArray? = null, isVideo: Boolean = false, sending: Boolean = false, progress: Float? = null, editing: StoryItem? = null) {
        compose.setContent {
            MioTheme {
                StoryComposer(
                    preview, isVideo, sending, progress,
                    onPick = { picks++ },
                    onSend = { caption, audience -> sent += caption to audience },
                    onClose = { closes++ },
                    editing = editing,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun withNothingPickedItAsksForMediaAndCannotSend() {
        show()
        compose.onNodeWithTag("storyPreview").assertDoesNotExist()
        compose.onNodeWithTag("storyPick").performClick()
        assertEquals(1, picks)
        compose.onNodeWithTag("storySend").assertIsNotEnabled()
        compose.onNodeWithTag("storyComposerClose").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun aPickedVideoIsSentWithTheCaptionAndTheAudience() {
        show(isVideo = true)
        compose.onNodeWithTag("storyPreview").assertExists()
        compose.onNodeWithTag("storyPickAgain").performClick()
        assertEquals(1, picks)
        compose.onNodeWithTag("storyAudience_Everybody").assertIsSelected()
        compose.onNodeWithTag("storyCaption").performTextInput(" sunset ")
        compose.onNodeWithTag("storyAudience_CloseFriends").performClick()
        compose.onNodeWithTag("storyAudience_CloseFriends").assertIsSelected()
        compose.onNodeWithTag("storySend").assertIsEnabled().performClick()
        assertEquals(listOf<Pair<String, StoryAudience?>>("sunset" to StoryAudience.CloseFriends), sent)
    }

    @Test
    fun sendingShowsTheUploadAndBlocksASecondSend() {
        show(isVideo = true, sending = true, progress = 0.4f)
        compose.onNodeWithTag("storySend").assertIsNotEnabled()
        compose.onNodeWithTag("storySendProgress", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Posting… 40%", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storyPickAgain").assertIsNotEnabled()
    }

    @Test
    fun editingStartsFromTheStoryAndSavesWithoutAPick() {
        val photo = MediaItem("story1", "story1.jpg", 1_000, 2, isPhoto = true, kind = MediaKind.Photo, width = 720, height = 1280)
        show(editing = StoryItem(1, date = 1, expireDate = 2, media = photo, caption = "old", out = true, audience = StoryAudience.Contacts))
        compose.onNodeWithText("Edit story").assertExists()
        compose.onNodeWithTag("storyCurrentMedia", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storyCaption").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("old")))
        compose.onNodeWithTag("storyAudience_Contacts").assertIsSelected()
        compose.onNodeWithText("Save", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("storySend").assertIsEnabled()
        compose.onNodeWithTag("storyCaption").performTextReplacement("new")
        compose.onNodeWithTag("storySend").performClick()
        assertEquals(listOf<Pair<String, StoryAudience?>>("new" to StoryAudience.Contacts), sent)
    }

    @Test
    fun aStoryForChosenPeopleHasNoAudienceUntilOneIsChosen() {
        show(editing = StoryItem(1, date = 1, expireDate = 2, caption = "old", out = true))
        compose.onNodeWithTag("storyAudience_Everybody").assertIsNotSelected()
        compose.onNodeWithTag("storySend").performClick()
        assertEquals(listOf<Pair<String, StoryAudience?>>("old" to null), sent)
    }
}
