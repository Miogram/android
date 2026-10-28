package cn.ellerotta.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.components.chat.GlassPill
import cn.ellerotta.core.ui.components.chat.backdropSource
import cn.ellerotta.core.ui.components.chat.rememberChatBackdrop
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassTest {
    @get:Rule
    val compose = createComposeRule()

    private fun contrastUnderPill(blurPhoto: Boolean): Double {
        compose.setContent {
            Box(Modifier.size(120.dp, 40.dp)) {
                val photo = rememberChatBackdrop()
                Canvas(Modifier.fillMaxSize().backdropSource(photo)) {
                    drawRect(Color.Black)
                    var x = 0f
                    while (x < size.width) {
                        drawRect(Color.White, Offset(x, 0f), Size(1f, size.height))
                        x += 2f
                    }
                }
                GlassPill(RectangleShape, Modifier.fillMaxSize().testTag("pill"), backdrop = if (blurPhoto) photo else null, fill = Color.Transparent) {}
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag("pill").captureToImage().asAndroidBitmap()
        val y = image.height / 2
        val from = image.width / 4
        val to = image.width * 3 / 4
        return (from until to).map { x -> abs(android.graphics.Color.red(image.getPixel(x, y)) - android.graphics.Color.red(image.getPixel(x + 1, y))) }.average()
    }

    @Test
    fun aPillBlursThePhotoItLiesOn() {
        val blurred = contrastUnderPill(blurPhoto = true)
        assertTrue(blurred < 40, "stripes under the pill differ by $blurred")
    }

    @Test
    fun withoutABackdropThePhotoShowsThrough() {
        val sharp = contrastUnderPill(blurPhoto = false)
        assertTrue(sharp > 120, "stripes differ by $sharp")
    }
}
