package cn.ellerotta.core.ui

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.reflect.ComposableMethod
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import cn.ellerotta.core.ui.components.common.LocalPreviewScheme
import java.io.File
import kotlin.test.assertEquals
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() = capture("light")

    @Test
    @Config(sdk = [35], qualifiers = "w412dp-h892dp-night-xhdpi")
    fun dark() = capture("dark")

    @Test
    fun largeFont() = capture("large", fontScale = 2f)

    private fun capture(mode: String, fontScale: Float = 1f) {
        val dir = System.getenv("MIOGRAM_SCREENSHOTS")
        assumeNotNull(dir)
        val only = System.getenv("MIOGRAM_SCREENSHOT_FILTER")?.let(::Regex)
        val schemes = System.getenv("MIOGRAM_SCREENSHOT_SCHEMES")?.split(',')?.map { name -> "-$name" to schemeNamed(name) } ?: listOf("" to null)
        File(dir).mkdirs()
        var current by mutableStateOf<Pair<String, ComposableMethod>?>(null)
        var scheme by mutableStateOf<((Boolean) -> ColorScheme)?>(null)
        var shown: String? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalPreviewScheme provides scheme) {
                current?.let { (name, method) ->
                    key(name, scheme) {
                        method.invoke(currentComposer, null)
                        SideEffect { shown = name }
                    }
                }
            }
        }
        val previews = mioPreviews().filter { only == null || only.containsMatchIn(it.first) }
        for ((suffix, colours) in schemes) for (preview in previews) {
            try {
                current = preview
                scheme = colours
                Snapshot.sendApplyNotifications()
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                assertEquals(preview.first, shown)
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                File(dir, "${preview.first}_$mode$suffix.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: Throwable) {
                if (e.javaClass.simpleName != "AppNotIdleException") throw AssertionError("${preview.first} failed", e)
                System.err.println("${preview.first} skipped: never idle")
            }
        }
    }

    private fun schemeNamed(name: String): ((Boolean) -> ColorScheme)? = when (name) {
        "brand" -> null
        "monochrome" -> { dark: Boolean -> monochromeScheme(dark) }
        else -> wallpaperSeeds.toMap().getValue(name).let { seed -> { dark: Boolean -> wallpaperScheme(seed, dark) } }
    }
}
