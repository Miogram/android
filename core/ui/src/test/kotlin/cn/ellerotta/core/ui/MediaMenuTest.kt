package cn.ellerotta.core.ui

import cn.ellerotta.core.model.DownloadState
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class MediaMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Int>()

    private fun show(vararg messages: MessageItem) {
        val file = File.createTempFile("photo", ".jpg").apply { deleteOnExit() }
        val downloads = messages.mapNotNull { it.media }.filter { it.isPhoto }.associate { it.key to DownloadState.Done(file) }
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                ChatScreen(
                    "Ada Lovelace", messages.toList(), downloads, busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("u1", "Ada Lovelace", kind = PeerKind.User, presence = Presence.Recently),
                    onOpenMedia = { opened += it.id },
                )
            }
        }
    }

    private fun assertMenuOpensOnLongPress(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("messageMenu").assertExists()
        compose.onNodeWithTag("actionReply").assertIsDisplayed()
        compose.onNodeWithTag("messageMenu").performTouchInput { click(center.copy(y = 20f)) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("messageMenu")).fetchSemanticsNodes().isEmpty(), "$tag: the menu closes on a tap outside")
    }

    private fun assertTapOpensViewer(tag: String, id: Int) {
        opened.clear()
        compose.onNodeWithTag(tag).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(id), opened, "$tag: a tap opens the viewer")
        assertTrue(compose.onAllNodes(hasTestTag("messageMenu")).fetchSemanticsNodes().isEmpty(), "$tag: a tap does not open the menu")
    }

    @Test
    fun aLongPressOnAPhotoOpensTheMenuAndATapTheViewer() {
        show(photoMessage(1))
        assertTapOpensViewer("photo_photo1", 1)
        assertMenuOpensOnLongPress("photo_photo1")
        assertMenuOpensOnLongPress("message_1")
        assertTrue(opened.size == 1, "a long press does not open the viewer")
    }

    @Test
    fun aLongPressOnAVideoOpensTheMenuAndATapTheViewer() {
        show(videoMessage(1))
        assertTapOpensViewer("photo_video1", 1)
        assertMenuOpensOnLongPress("photo_video1")
        assertMenuOpensOnLongPress("message_1")
        assertTrue(opened.size == 1, "a long press does not open the viewer")
    }

    @Test
    fun aLongPressOnAGifOpensTheMenu() {
        show(gifMessage(1))
        assertMenuOpensOnLongPress("photo_gif1")
        assertMenuOpensOnLongPress("message_1")
    }

    @Test
    fun aLongPressOnAnAlbumTileOpensTheMenuAndATapTheTilesViewer() {
        show(photoMessage(1, groupedId = 5), photoMessage(2, groupedId = 5))
        assertTapOpensViewer("photo_photo2", 2)
        assertMenuOpensOnLongPress("photo_photo1")
        assertMenuOpensOnLongPress("photo_photo2")
        assertTrue(opened.size == 1, "a long press does not open the viewer")
    }

    @Test
    fun aLongPressOnAStickerOpensTheMenu() {
        show(stickerMessage(2))
        assertMenuOpensOnLongPress("message_2")
    }

    @Test
    fun aLongPressOnARoundVideoOpensTheMenu() {
        show(roundMessage(1))
        assertMenuOpensOnLongPress("round_round1")
        assertMenuOpensOnLongPress("message_1")
    }
}
