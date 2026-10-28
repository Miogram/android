package cn.ellerotta.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chats.DialogComponent
import cn.ellerotta.core.ui.components.profile.ProfileHeader
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryRingTest {
    @get:Rule
    val compose = createComposeRule()

    private val unread = DialogItem("u1", "Ada", peerId = 1, story = StoryRing.Unread)
    private val friends = DialogItem("u2", "Grace", peerId = 2, story = StoryRing.CloseFriends)
    private val read = DialogItem("u3", "Linus", peerId = 3, story = StoryRing.Read)
    private val none = DialogItem("u4", "Alan", peerId = 4)

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun rowsStateTheRingAndItsPhotoOpensTheStories() {
        val opened = mutableListOf<String>()
        val stories = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                Column {
                    listOf(unread, friends, read, none).forEach { dialog ->
                        DialogComponent(dialog, emptyMap(), 0, onClick = { opened += dialog.key }, onLongClick = {}, onOpenStories = { stories += it })
                    }
                }
            }
        }
        compose.onNodeWithTag("dialogAvatar_u1").assert(state("Unread stories"))
        compose.onNodeWithTag("dialogAvatar_u2").assert(state("Unread stories for close friends"))
        compose.onNodeWithTag("dialogAvatar_u3").assert(state("Viewed stories"))
        compose.onNodeWithTag("dialogAvatar_u4", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .assertHasNoClickAction()
        compose.onNodeWithTag("dialogAvatar_u1").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("dialogAvatar_u4", useUnmergedTree = true).assertHeightIsEqualTo(48.dp)

        compose.onNodeWithTag("dialogAvatar_u1").performClick()
        compose.onNodeWithTag("dialogAvatar_u2").performClick()
        assertEquals(listOf("u1", "u2"), stories)
        assertEquals(emptyList<String>(), opened)
        compose.onNodeWithTag("dialog_u4").performClick()
        assertEquals(listOf("u4"), opened)
    }

    @Test
    fun withoutACallbackTheRingIsOnlyStated() {
        val opened = mutableListOf<String>()
        compose.setContent {
            MioTheme {
                DialogComponent(unread, emptyMap(), 0, onClick = { opened += unread.key }, onLongClick = {})
            }
        }
        compose.onNodeWithTag("dialogAvatar_u1", useUnmergedTree = true).assert(state("Unread stories")).assertHasNoClickAction()
        compose.onNodeWithTag("dialog_u1").performClick()
        assertEquals(listOf("u1"), opened)
    }

    @Test
    fun theChatHeaderAvatarOpensStoriesWhenRinged() {
        var profiles = 0
        val stories = mutableListOf<String>()
        var dialog by mutableStateOf(unread)
        compose.setContent {
            MioTheme {
                ChatScreen(
                    "Ada", listOf(MessageItem(1, "hi", out = false, date = 0)), emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    onOpenProfile = { profiles++ }, dialog = dialog, onOpenStories = { stories += it },
                )
            }
        }
        compose.onNodeWithTag("avatar").assert(state("Unread stories")).performClick()
        assertEquals(listOf("u1"), stories)
        assertEquals(0, profiles)

        dialog = none
        compose.waitForIdle()
        compose.onNodeWithTag("avatar").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)).performClick()
        assertEquals(1, profiles)
        assertEquals(listOf("u1"), stories)
    }

    @Test
    fun theProfileHeaderOpensStoriesWhenRingedAndThePhotoOtherwise() {
        var photos = 0
        var stories = 0
        var ring by mutableStateOf(StoryRing.CloseFriends)
        compose.setContent {
            MioTheme {
                ProfileHeader(
                    name = "Grace", subtitle = "", avatar = null, seed = 2, colorIndex = 0, verified = false, premium = false, collapse = 0f,
                    onAvatarClick = { photos++ }, storyRing = ring, onOpenStories = { stories++ },
                )
            }
        }
        compose.onNodeWithTag("profileAvatar").assert(state("Unread stories for close friends")).performClick()
        assertEquals(1, stories)
        assertEquals(0, photos)

        ring = StoryRing.None
        compose.waitForIdle()
        compose.onNodeWithTag("profileAvatar").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)).performClick()
        assertEquals(1, stories)
        assertEquals(1, photos)
    }
}
