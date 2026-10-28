package cn.ellerotta.core.ui

import android.graphics.Bitmap
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import cn.ellerotta.core.model.GiftAttribute
import cn.ellerotta.core.model.GiftBackdrop
import cn.ellerotta.core.model.GiftPage
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.UniqueGift
import cn.ellerotta.core.ui.components.profile.GiftCallbacks
import cn.ellerotta.core.ui.components.profile.PatternDrawn
import cn.ellerotta.core.ui.components.profile.GiftsPage
import cn.ellerotta.core.model.ProfileGifts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftTileTest {
    @get:Rule
    val compose = createComposeRule()

    private fun webp(draw: Canvas.(Paint) -> Unit): ByteArray {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).draw(Paint(Paint.ANTI_ALIAS_FLAG))
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }.toByteArray()
    }

    private fun sticker(key: String) = MediaItem(key, "$key.webp", 0, 2, isPhoto = false, kind = MediaKind.Sticker, mimeType = "image/webp")

    private val model = webp { paint ->
        paint.color = 0xFF2B2B2B.toInt(); drawCircle(64f, 70f, 46f, paint)
        paint.color = 0xFFF2F2F2.toInt(); drawCircle(78f, 56f, 20f, paint)
    }
    private val symbol = webp { paint -> paint.color = 0xFF000000.toInt(); drawRect(44f, 44f, 84f, 84f, paint) }
    private val stickers = mapOf("model" to model, "symbol" to symbol, "plain" to model)

    private val pepe = SavedGift(
        "giftm71", 62, sticker("model"), stars = 0, date = 1_790_000_000, fromName = "Bob",
        unique = UniqueGift(
            "Plush Pepe", "plushpepe-123", 123,
            model = GiftAttribute("Gold", sticker("model"), null),
            backdrop = GiftBackdrop("Midnight", 0x5a8fd8, 0x22407a, 0x0e2350, 0xffffff, null),
            symbol = GiftAttribute("Square", sticker("symbol"), null),
        ),
    )
    private val bear = SavedGift("giftm70", 61, sticker("plain"), stars = 50, date = 1_790_000_000, limitedTotal = 1000)
    private val hidden = SavedGift("giftm72", 61, sticker("plain"), stars = 50, date = 1_790_000_000, saved = false)

    @Test
    fun tilesShowTheRibbonAndNoStars() {
        var opened: String? = null
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                GiftsPage(ProfileGifts(GiftPage(3, listOf(pepe, bear, hidden), null)), stickers, GiftCallbacks({}, {}), rememberLazyListState(), PaddingValues(), onShowCatalogue = {}) { opened = it.key }
            }
        }
        compose.onNodeWithTag("giftRibbon", useUnmergedTree = true).assertTextEquals("#123")
        compose.onNodeWithTag("gift_giftm71").assert(hasText("#123"))
        assertEquals(0, compose.onAllNodes(hasText("stars", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasTestTag("giftRibbon"), useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag("gift_giftm70").performClick()
        assertEquals("giftm70", opened)
    }

    @Test
    fun screenshots() {
        var dark by mutableStateOf(false)
        compose.setContent {
            MioTheme(darkTheme = dark, useDynamicColor = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    GiftsPage(ProfileGifts(GiftPage(3, listOf(pepe, bear, hidden), null)), stickers, GiftCallbacks({}, {}), rememberLazyListState(), PaddingValues(), onShowCatalogue = {}) {}
                }
            }
        }
        for (theme in listOf("light", "dark")) {
            dark = theme == "dark"
            compose.waitForIdle()
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.onAllNodes(SemanticsMatcher.expectValue(PatternDrawn, true)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            System.getenv("MIOGRAM_SCREENSHOTS")?.let { dir ->
                File(dir).mkdirs()
                File(dir, "gifts_$theme.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
