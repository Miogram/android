package cn.ellerotta.core.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import cn.ellerotta.core.model.ChatStickerState
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.GifPanel
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.ChatStickerActions
import cn.ellerotta.core.ui.components.chat.MAX_PLAYING_PANEL_GIFS
import cn.ellerotta.core.ui.components.chat.panelGifSlots
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp")
class PanelGifsTest {
    @get:Rule
    val compose = createComposeRule()

    private val playing = SemanticsMatcher("a gif playing") { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("gifVideo_") == true }

    @Test
    fun onlySoManyGifsPlayAtOnce() {
        val file = File.createTempFile("gif", ".mp4")
        val gifs = (1..30).map { MediaItem("doc$it", "gif.mp4", 100, 2, isPhoto = false, kind = MediaKind.Gif, width = 240, height = 180) }
        val state = ChatStickerState(gifs = GifPanel(saved = gifs), keyboardHeight = 1000)
        val downloads = gifs.associate { it.key to DownloadState.Done(file) }
        val actions = ChatStickerActions(onOpenPanel = {}, onOpenTab = {}, onLoad = {}, onSend = {}, onDraft = {}, onShowSet = {}, onInstall = { _, _ -> }, onDismissSet = {})
        compose.setContent {
            MioTheme {
                ChatScreen(title = "Saved", messages = emptyList(), downloads = downloads, busy = false, onBack = {}, onSend = {}, onDownload = {}, stickerState = state, stickerActions = actions)
            }
        }
        compose.onNodeWithTag("stickersButton").performClick()
        compose.onNodeWithTag("panelTab_Gifs").performClick()
        compose.waitForIdle()
        val shown = compose.onNodeWithTag("gifGrid").fetchSemanticsNode().children.size
        assertTrue(shown > MAX_PLAYING_PANEL_GIFS, "only $shown gifs on screen")
        assertEquals(MAX_PLAYING_PANEL_GIFS, compose.onAllNodes(playing).fetchSemanticsNodes().size)
        assertEquals(MAX_PLAYING_PANEL_GIFS, panelGifSlots.playing)
        compose.onNodeWithTag("gifGrid").performScrollToIndex(gifs.lastIndex)
        compose.waitForIdle()
        val now = compose.onAllNodes(playing).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(MAX_PLAYING_PANEL_GIFS, now.size)
        assertTrue(now.all { it.removePrefix("gifVideo_doc").toInt() > 6 }, now.toString())
        file.delete()
    }
}
