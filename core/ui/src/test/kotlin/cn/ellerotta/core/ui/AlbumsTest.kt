package cn.ellerotta.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.components.chat.ChatEntry
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.entries
import cn.ellerotta.core.ui.components.chat.mosaic
import cn.ellerotta.core.ui.theme.MioTheme
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlbumsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    private fun photo(id: Int, width: Int = 1280, height: Int = 960) =
        MediaItem("photo$id", "photo$id.jpg", 1000, 2, isPhoto = true, kind = MediaKind.Photo, width = width, height = height)

    @Test
    fun fivePhotosGoInTwoRowsFillingTheWidth() {
        val grid = mosaic(listOf(0.92f, 0.92f, 0.8f, 0.8f, 0.8f), 310f, 392f)
        val (top, bottom) = grid.tiles.partition { it.y == 0f }
        assertEquals(2, top.size)
        assertEquals(3, bottom.size)
        assertTrue(abs(top[1].x - top[0].right - 1f) < 0.01f)
        assertTrue(top.all { abs(it.height - top[0].height) < 0.01f } && bottom.all { abs(it.y - (top[0].height + 1f)) < 0.01f })
        assertTrue(abs(top.last().right - 310f) < 0.01f && abs(bottom.last().right - 310f) < 0.01f)
        assertEquals(310f, grid.width)
        val first = grid.touches(grid.tiles[0])
        assertTrue(first.left && first.top && !first.right && !first.bottom)
        val last = grid.touches(grid.tiles[4])
        assertTrue(last.right && last.bottom && !last.left && !last.top)
    }

    @Test
    fun twoSquaresSitSideBySideAndTwoWideOnesStack() {
        val squares = mosaic(listOf(1f, 1f), 310f, 392f)
        assertEquals(listOf(0f, 155.5f), squares.tiles.map { it.x })
        assertEquals(listOf(154.5f, 154.5f), squares.tiles.map { it.width })
        val wide = mosaic(listOf(1.9f, 1.9f), 310f, 392f)
        assertEquals(listOf(0f, 0f), wide.tiles.map { it.x })
        assertTrue(wide.tiles[1].y > wide.tiles[0].bottom)
    }

    @Test
    fun anAlbumIsOneRowKeptAsItsItemsLoad() {
        val text = MessageItem(1, "look", out = false, date = now - 60, senderId = 7)
        val a = MessageItem(2, "", out = false, date = now, senderId = 7, media = photo(2), groupedId = 99)
        val b = MessageItem(3, "two cats", out = false, date = now, senderId = 7, media = photo(3), groupedId = 99)
        val c = MessageItem(4, "", out = false, date = now, senderId = 7, media = photo(4), groupedId = 99)
        val rows = entries(listOf(text, a, b, c), ZoneOffset.UTC).filterIsInstance<ChatEntry.Bubble>()
        assertEquals(2, rows.size)
        val album = rows.first()
        assertEquals("g99", album.key)
        assertEquals(listOf(2, 3, 4), album.album.map { it.id })
        assertEquals(3, album.message.id)
        assertTrue(album.shows(4) && !album.shows(1))
        assertEquals("g99", entries(listOf(b, c), ZoneOffset.UTC).filterIsInstance<ChatEntry.Bubble>().single().key)
    }

    @Test
    fun theAlbumIsOneBubbleAndATileOpensItsMessage() {
        val opened = mutableListOf<Int>()
        val messages = listOf(
            MessageItem(2, "two cats", out = false, date = now, media = photo(2), groupedId = 99),
            MessageItem(3, "", out = false, date = now, media = photo(3, 960, 1280), groupedId = 99),
        )
        val loading = DownloadState.Progress(10, 100)
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = messages, downloads = mapOf("photo2" to loading, "photo3" to loading), busy = false,
                    onBack = {}, onSend = {}, onDownload = {}, onOpenMedia = { opened += it.id },
                )
            }
        }
        compose.onNodeWithTag("album", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("two cats").assertIsDisplayed()
        compose.onNodeWithTag("message_2").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("message_3")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("photo_photo3").performClick()
        assertEquals(listOf(3), opened)
    }

    @Test
    fun photosAndStickersCarryTheirTimeInAPill() {
        val sticker = MediaItem("doc9", "s.tgs", 10, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = "🐱", animated = true)
        val messages = listOf(
            MessageItem(1, "", out = false, date = now - 30, media = photo(1)),
            MessageItem(2, "a caption", out = false, date = now - 20, media = photo(2)),
            MessageItem(3, "", out = true, date = now - 10, media = sticker),
        )
        val loading = DownloadState.Progress(10, 100)
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = messages, downloads = mapOf("photo1" to loading, "photo2" to loading), busy = false, onBack = {}, onSend = {}, onDownload = {})
            }
        }
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("photo_photo1"))
        compose.onNodeWithTag("metaPill_1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("messages").performScrollToNode(hasText("a caption"))
        assertTrue(compose.onAllNodes(hasTestTag("metaPill_2"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_3"))
        compose.onNodeWithTag("metaPill_3", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("messages").performScrollToNode(hasText("Today"))
    }
}
