package cn.ellerotta.core.ui

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatStickerState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MediaSize
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.StickerPanel
import cn.ellerotta.core.model.StickerSetDetail
import cn.ellerotta.core.model.StickerSetInfo
import cn.ellerotta.core.model.stickerOutline
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatStickerActions
import cn.ellerotta.core.ui.components.chat.StickerView
import cn.ellerotta.core.ui.components.chat.previewOf
import cn.ellerotta.core.ui.components.chat.stickerOutlinePath
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StickerPlaceholdersTest {
    @get:Rule
    val compose = createComposeRule()

    private val square = "M100,100h312v312h-312z"

    private fun sticker(id: Long, emoji: String, outline: String? = square, animated: Boolean = true) =
        MediaItem("doc$id", "sticker", 100, 2, isPhoto = false, kind = MediaKind.Sticker, emoji = emoji, animated = animated, outline = outline)

    private val actions = ChatStickerActions(
        onOpenPanel = {},
        onOpenTab = {},
        onLoad = {},
        onSend = {},
        onDraft = {},
        onShowSet = {},
        onInstall = { _, _ -> },
        onDismissSet = {},
    )

    @Test
    fun anOutlineIsAComposePathInItsBox() {
        val path = assertNotNull(stickerOutlinePath(square))
        assertEquals(Rect(100f, 100f, 412f, 412f), path.getBounds())
        val bytes = "100,100h312v312h-312".map { (192 + LOOKUP.indexOf(it)).toByte() }.toByteArray()
        assertEquals(Rect(100f, 100f, 412f, 412f), assertNotNull(stickerOutlinePath(stickerOutline(bytes))).getBounds())
    }

    @Test
    fun outlinesShowInBubblesThePanelSuggestionsAndTheSetSheet() {
        val inChat = sticker(901, "🐱")
        val recent = sticker(902, "🐶")
        val suggested = sticker(903, "🦊")
        val plain = sticker(904, "🐭", outline = null)
        var stickers by mutableStateOf(emptyMap<String, ByteArray>())
        var state by mutableStateOf(ChatStickerState())
        val messages = listOf(MessageItem(1, "", out = false, date = 1_700_000_000, media = inChat))
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    stickers = stickers, stickerState = state, stickerActions = actions,
                )
            }
        }
        compose.onNodeWithTag("stickerOutline_doc901", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("messageInput").performTextInput("🦊")
        state = state.copy(suggestions = listOf(suggested))
        compose.waitForIdle()
        compose.onNodeWithTag("stickerOutline_doc903", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("messageInput").performTextClearance()
        state = ChatStickerState(panel = StickerPanel(recent = listOf(recent, plain)))
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("panelTab_Stickers").performClick()
        compose.waitForIdle()
        compose.waitForIdle()
        compose.onNodeWithTag("stickerOutline_doc902", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("🐭", useUnmergedTree = true).assertExists()
        state = state.copy(openSet = StickerSetDetail(StickerSetInfo("set905", "Pets", "pets", 1, installed = false), listOf(sticker(906, "🐹", animated = false))))
        compose.waitForIdle()
        compose.onNodeWithTag("stickerOutline_doc906", useUnmergedTree = true).assertExists()
        stickers = mapOf("doc906" to webp())
        compose.waitForIdle()
        compose.onNodeWithTag("stickerOutline_doc906", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("sticker_doc906", useUnmergedTree = true).assertExists()
    }

    @Test
    fun stillPreviewsShowUntilAnimatedStickersHaveAFrame() {
        val sizes = listOf(MediaSize("m", 128, 128, 1_000))
        val lottie = sticker(911, "🐯").copy(sizes = sizes)
        val stripped = sticker(912, "🦁").copy(thumb = webp())
        val webm = javaClass.getResourceAsStream("/webm/alpha.webm")!!.use { it.readBytes() }
        val video = sticker(913, "🐸", animated = false).copy(mimeType = "video/webm", sizes = sizes)
        var stickers by mutableStateOf(emptyMap<String, ByteArray>())
        compose.setContent {
            MioTheme {
                Column {
                    listOf(lottie, stripped, video).forEach { StickerView(it, stickers[it.key], size = 64.dp, preview = stickers.previewOf(it)) }
                }
            }
        }
        compose.onNodeWithTag("stickerOutline_doc911", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("stickerPreview_doc912", useUnmergedTree = true).assertExists()
        stickers = mapOf("doc911@m" to webp(), "doc913@m" to webp())
        compose.waitForIdle()
        compose.onNodeWithTag("stickerOutline_doc911", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("stickerPreview_doc911", useUnmergedTree = true).assertExists()
        stickers = stickers + mapOf("doc911" to tgs(), "doc913" to webm)
        repeat(20) {
            Thread.sleep(25)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("stickerPreview_doc911", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("stickerPreview_doc913", useUnmergedTree = true).assertExists()
    }

    @Test
    fun bubblesAndThePanelShowLoadedPreviews() {
        val tiger = sticker(921, "🐯").copy(sizes = listOf(MediaSize("m", 128, 128, 1_000)))
        val messages = listOf(MessageItem(1, "", out = false, date = 1_700_000_000, media = tiger))
        val state = ChatStickerState(panel = StickerPanel(recent = listOf(tiger.copy(key = "doc922"))))
        val stickers = mapOf("doc921@m" to webp(), "doc922@m" to webp())
        compose.setContent {
            MioTheme {
                ChatScreen(
                    title = "Saved", messages = messages, downloads = emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    stickers = stickers, stickerState = state, stickerActions = actions,
                )
            }
        }
        compose.onNodeWithTag("stickerPreview_doc921", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("panelTab_Stickers").performClick()
        compose.waitForIdle()
        compose.waitForIdle()
        compose.onNodeWithTag("stickerPreview_doc922", useUnmergedTree = true).assertExists()
    }

    private fun tgs(): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write("""{"v":"5.7.4","fr":60,"ip":0,"op":60,"w":512,"h":512,"layers":[]}""".encodeToByteArray()) }
    }.toByteArray()

    private fun webp(): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it)
    }.toByteArray()

    private companion object {
        const val LOOKUP = "AACAAAAHAAALMAAAQASTAVAAAZaacaaaahaaalmaaaqastava.az0123456789-,"
    }
}
