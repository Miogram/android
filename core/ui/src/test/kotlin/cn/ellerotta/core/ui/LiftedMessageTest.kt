package cn.ellerotta.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.ui.components.chat.ChatMessageList
import cn.ellerotta.core.ui.components.chat.entries
import cn.ellerotta.core.ui.theme.MioTheme
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiftedMessageTest {
    @get:Rule
    val compose = createComposeRule()

    private val background = Color(0xFFFF00FF)
    private val now = (System.currentTimeMillis() / 1000).toInt()

    private fun drawn(tag: String): Boolean {
        val image = compose.onNodeWithTag(tag).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return pixels.any { it != background.toArgb() }
    }

    private fun check(list: List<MessageItem>, tag: String = "message_${list.first().id}") {
        var liftedId by mutableStateOf<Int?>(null)
        lateinit var layer: GraphicsLayer
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                layer = rememberGraphicsLayer()
                val zone = ZoneId.systemDefault()
                Box(Modifier.fillMaxSize().background(background)) {
                    ChatMessageList(
                        entries(list, zone), zone, rememberLazyListState(), DialogItem("u1", "Ada", kind = PeerKind.User), list.associateBy { it.id },
                        emptyMap(), emptyMap(), false, null,
                        onDownload = {}, onToggleVoice = {}, onSelect = { _, _ -> }, onOpenMedia = {}, onOpenSticker = {},
                        lifted = layer, liftedId = liftedId,
                    )
                }
            }
        }
        compose.waitForIdle()
        assertTrue(drawn(tag), "$tag is drawn in the list before the menu")
        liftedId = list.first().id
        compose.waitForIdle()
        assertFalse(drawn(tag), "$tag is not drawn in the list while the menu holds it")
        assertTrue(layer.size.width > 0 && layer.size.height > 0, "$tag is recorded for the menu")
        compose.onNodeWithTag(tag).assertExists()
        liftedId = null
        compose.waitForIdle()
        assertTrue(drawn(tag), "$tag is back once the menu lets go")
    }

    @Test
    fun aTextBubble() = check(listOf(MessageItem(1, "the engine weaves algebraic patterns", out = true, date = now)))

    @Test
    fun aTextBubbleWithSender() = check(listOf(MessageItem(1, "see note g", out = false, date = now, senderName = "Ada Lovelace")))

    @Test
    fun aPhoto() = check(listOf(photoMessage(1)))

    @Test
    fun aVideo() = check(listOf(videoMessage(1)))

    @Test
    fun aGif() = check(listOf(gifMessage(1)))

    @Test
    fun aSticker() = check(listOf(stickerMessage(1)))

    @Test
    fun aRoundVideo() = check(listOf(roundMessage(1)))

    @Test
    fun anAlbum() = check(listOf(photoMessage(1, groupedId = 5), photoMessage(2, groupedId = 5)))
}
