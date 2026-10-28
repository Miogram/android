package cn.ellerotta.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import cn.ellerotta.core.model.LinkPreview
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MediaSize
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.MediaViewer
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
class MediaViewerTest {
    @get:Rule
    val compose = createComposeRule()

    private val sizes = listOf(MediaSize("m", 320, 240, 20_000), MediaSize("x", 800, 600, 80_000), MediaSize("w", 1280, 960, 200_000))
    private val photo = MediaItem("photo1", "photo_1_w.jpg", 200_000, 2, isPhoto = true, kind = MediaKind.Photo, width = 1280, height = 960, sizes = sizes)
    private val video = MediaItem("doc2", "clip.mp4", 5_000_000, 2, isPhoto = false, kind = MediaKind.Video, width = 1280, height = 720, duration = 84, sizes = listOf(MediaSize("m", 320, 180, 9_000)))

    @Test
    fun bubblesAskForTheSizeThatFitsAndOpenTheViewer() {
        val asked = mutableListOf<String>()
        val opened = mutableListOf<Int>()
        val messages = listOf(
            MessageItem(1, "", out = false, date = 1_700_000_000, media = photo),
            MessageItem(2, "", out = false, date = 1_700_000_001, media = video),
            MessageItem(3, "see the docs", out = true, date = 1_700_000_002, link = LinkPreview("https://core.telegram.org/api/files", "Telegram APIs", "Files", "How to download")),
        )
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {},
                    onDownload = { asked += it.key }, onOpenMedia = { opened += it.id },
                )
            }
        }
        compose.waitForIdle()
        assertEquals(setOf("photo1@x", "doc2@m"), asked.toSet())
        compose.onNodeWithTag("videoDuration", useUnmergedTree = true).assertTextEquals("1:24")
        compose.onNodeWithText("Telegram APIs").assertIsDisplayed()
        compose.onNodeWithText("How to download").assertIsDisplayed()
        compose.onNodeWithTag("photo_doc2").performClick()
        assertEquals(listOf(2), opened)
    }

    @Test
    fun viewerOpensAtTheTappedMediaPagesAndZooms() {
        val pages = mutableListOf<Int>()
        val saved = mutableListOf<String>()
        val items = listOf(
            MessageItem(1, "", out = false, date = 1, media = photo),
            MessageItem(2, "", out = false, date = 2, media = video),
            MessageItem(3, "", out = false, date = 3, media = photo.copy(key = "photo3")),
        )
        compose.setContent {
            MioTheme {
                MediaViewer(
                    items = items, initial = 0, downloads = emptyMap(), onBack = {}, onPage = { pages += it },
                    onSave = { saved += it.key }, onShare = {},
                    video = { media, active, modifier -> Box(modifier.testTag("player_${media.key}_$active")) },
                )
            }
        }
        compose.onNodeWithTag("viewerPosition").assertTextEquals("1 of 3")
        val zoom = { value: String -> SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value) }
        compose.onNodeWithTag("zoomable_photo1").performTouchInput { doubleClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("zoomable_photo1").assert(zoom("Zoom 250%"))
        compose.onNodeWithTag("zoomable_photo1").performTouchInput { doubleClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("zoomable_photo1").assert(zoom("Zoom 100%"))
        compose.onNodeWithTag("viewer").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("viewerPosition").assertTextEquals("2 of 3")
        compose.onNodeWithTag("player_doc2_true").assertIsDisplayed()
        compose.onNodeWithTag("viewerSave").performClick()
        assertEquals(listOf("doc2"), saved)
        assertEquals(listOf(0, 1), pages.distinct())
    }
}
