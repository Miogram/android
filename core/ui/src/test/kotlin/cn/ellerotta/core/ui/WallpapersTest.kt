package cn.ellerotta.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.chat.Tgv
import cn.ellerotta.core.ui.components.chat.WallpaperRenders
import cn.ellerotta.core.ui.components.chat.renderWallpaper
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.zip.GZIPOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpapersTest {
    @get:Rule
    val compose = createComposeRule()

    private val dir = Files.createTempDirectory("wallpapers").toFile()

    @Before
    fun clearRenders() = WallpaperRenders.clear()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun tgvParsesTheSvgSubset() {
        val image = Tgv.parse(gzip(SAMPLE))
        assertEquals(100f to 200f, image.width to image.height)
        assertEquals(4, image.shapes.size)
        assertNull(image.shapes[0].strokeWidth)
        assertEquals(4f, image.shapes[1].strokeWidth)
        assertEquals(0.5f, image.shapes[3].alpha)
        val bounds = RectF().also { image.shapes[2].path.computeBounds(it, true) }
        assertEquals(RectF(10f, 110f, 40f, 140f), bounds)

        val boxed = Tgv.parse("""<svg viewBox="10 10 50 100" width="100" height="200"><rect x="10" y="10" width="5" height="5"/></svg>""".toByteArray())
        assertEquals(RectF(0f, 0f, 10f, 10f), RectF().also { boxed.shapes.single().path.computeBounds(it, true) })
        val transformed = Tgv.parse("""<svg width="10" height="10"><g transform="matrix(2 0 0 2 1 1) rotate(90)"><rect width="2" height="1"/></g></svg>""".toByteArray())
        assertEquals(RectF(-1f, 1f, 1f, 5f), RectF().also { transformed.shapes.single().path.computeBounds(it, true) })
    }

    @Test
    fun tgvDrawsBlackShapesOnTransparency() {
        val image = Tgv.parse(gzip(SAMPLE))
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        image.draw(Canvas(bitmap), 1f, 0f, 0f)
        assertEquals(Color.BLACK, bitmap.getPixel(25, 25))
        assertEquals(0, Color.alpha(bitmap.getPixel(50, 5)))
        assertEquals(0, Color.alpha(bitmap.getPixel(70, 40)))
        assertEquals(255, Color.alpha(bitmap.getPixel(70, 25)))
        assertEquals(Color.BLACK, bitmap.getPixel(35, 115))
        assertNear(128, Color.alpha(bitmap.getPixel(85, 115)), 2)
    }

    @Test
    fun patternIntensityDarkensTheFillOrShowsItOnBlack() {
        val file = File(dir, "pattern.tgv").apply { writeBytes(gzip(SAMPLE)) }
        fun render(intensity: Int) = renderWallpaper(Wallpaper("wp1", WallpaperKind.Pattern, listOf(0x3366cc), intensity = intensity), file, 100, 200)
        render(100).let { assertRgb(0x000000, it.getPixel(25, 25)); assertRgb(0x3366cc, it.getPixel(50, 180)) }
        render(50).let { assertRgb(0x1a3366, it.getPixel(25, 25)); assertRgb(0x3366cc, it.getPixel(50, 180)) }
        render(0).let { assertRgb(0x3366cc, it.getPixel(25, 25)) }
        render(-100).let { assertRgb(0x3366cc, it.getPixel(25, 25)); assertRgb(0x000000, it.getPixel(50, 180)) }
        render(-50).let { assertRgb(0x1a3366, it.getPixel(25, 25)); assertRgb(0x000000, it.getPixel(50, 180)) }
        assertRgb(0x3366cc, renderWallpaper(Wallpaper("wp1", WallpaperKind.Pattern, listOf(0x3366cc)), File(dir, "missing"), 10, 10).getPixel(5, 5))
    }

    @Test
    fun fillsSolidGradientAndFreeform() {
        assertRgb(0x7fa381, renderWallpaper(Wallpaper("wp1", WallpaperKind.Fill, listOf(0x7fa381)), null, 10, 10).getPixel(3, 3))
        val down = renderWallpaper(Wallpaper("wp2", WallpaperKind.Fill, listOf(0xff0000, 0x0000ff)), null, 100, 100)
        assertRgb(0xff0000, down.getPixel(50, 0), tolerance = 8)
        assertRgb(0x0000ff, down.getPixel(50, 99), tolerance = 8)
        val turned = renderWallpaper(Wallpaper("wp3", WallpaperKind.Fill, listOf(0xff0000, 0x0000ff), rotation = 90), null, 100, 100)
        assertRgb(0xff0000, turned.getPixel(99, 50), tolerance = 8)
        assertRgb(0x0000ff, turned.getPixel(0, 50), tolerance = 8)
        val freeform = renderWallpaper(Wallpaper("wp4", WallpaperKind.Fill, listOf(0xff0000, 0x00ff00, 0x0000ff, 0xffff00)), null, 100, 200)
        assertRgb(0xff0000, freeform.getPixel(20, 20), tolerance = 40)
        assertRgb(0x0000ff, freeform.getPixel(75, 180), tolerance = 40)
    }

    @Test
    fun blurredImageSoftensEdges() {
        val file = File(dir, "image.jpg").apply { writeBytes(halfAndHalfJpeg()) }
        val sharp = renderWallpaper(Wallpaper("wp1", WallpaperKind.Image), file, 200, 200)
        val blurred = renderWallpaper(Wallpaper("wp1", WallpaperKind.Image, blur = true), file, 200, 200)
        assertTrue(Color.red(sharp.getPixel(98, 100)) > 240, "sharp edge")
        assertTrue(Color.red(blurred.getPixel(98, 100)) in 60..200, "blurred edge")
        assertTrue(Color.red(blurred.getPixel(10, 100)) > 240, "white away from the edge")
    }

    @Test
    fun chatPatternScreenshotLight() = chatScreenshot(dark = false)

    @Test
    fun chatPatternScreenshotDark() = chatScreenshot(dark = true)

    private fun chatScreenshot(dark: Boolean) {
        val pattern = File(dir, "doodles.tgv").apply { writeBytes(gzip(doodles())) }
        val wallpaper = if (dark) {
            Wallpaper("wp2", WallpaperKind.Pattern, listOf(0x1b2836, 0x5f7f9c), rotation = 45, intensity = -60, dark = true)
        } else {
            Wallpaper("wp1", WallpaperKind.Pattern, listOf(0xdbddbb, 0x6ba587, 0xd5d88d, 0x88b884), intensity = 30)
        }
        val now = (System.currentTimeMillis() / 1000).toInt()
        val messages = listOf(
            MessageItem(1, "the engine weaves algebraic patterns", out = false, date = now - 600, senderName = "Ada Lovelace"),
            MessageItem(2, "just as the loom weaves flowers and leaves", out = true, date = now - 300),
            MessageItem(3, "see note g", out = false, date = now - 60, senderName = "Ada Lovelace"),
        )
        compose.setContent {
            MioTheme(darkTheme = dark, useDynamicColor = false) {
                ChatScreen(
                    "Ada Lovelace", messages, emptyMap(), busy = false, onBack = {}, onSend = {}, onDownload = {},
                    dialog = DialogItem("u1", "Ada Lovelace", kind = PeerKind.User, presence = Presence.Recently),
                    onAttachMedia = {}, onAttachFile = {}, onVoiceStart = {},
                    wallpaper = ChatWallpaper(wallpaper, pattern.path),
                )
            }
        }
        val placeholder = 0xff000000.toInt() or wallpaper.colors.first()
        compose.waitUntil(30_000) {
            shadowOf(Looper.getMainLooper()).idle()
            val image = compose.onNodeWithTag("chatWallpaper").captureToImage().asAndroidBitmap()
            (1..20).any { i -> image.getPixel(image.width * i / 22, image.height / 3) != placeholder }
        }
        compose.waitForIdle()
        screenshot(if (dark) "chat_wallpaper_dark" else "chat_wallpaper_light")
    }

    private fun screenshot(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width > 0)
        System.getenv("MIOGRAM_SCREENSHOTS")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun halfAndHalfJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(200f, 0f, 400f, 400f, Paint().apply { color = Color.BLACK })
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    private fun assertRgb(expected: Int, actual: Int, tolerance: Int = 3) =
        assertTrue(listOf(16, 8, 0).all { abs((expected shr it and 0xff) - (actual shr it and 0xff)) <= tolerance }, "%06x is not %06x".format(actual and 0xffffff, expected))

    private fun assertNear(expected: Int, actual: Int, tolerance: Int) = assertTrue(abs(expected - actual) <= tolerance, "$actual is not $expected")
}

internal fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()

private const val SAMPLE = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="100" height="200" viewBox="0 0 100 200">
  <style>.hollow { fill: none; stroke: #000; stroke-width: 4 }</style>
  <defs><clipPath id="c"><rect width="100" height="200"/></clipPath></defs>
  <g id="bounds"><rect width="100" height="200" fill="#000"/></g>
  <rect x="10" y="10" width="30" height="30"/>
  <circle class="hollow" cx="70" cy="40" r="15"/>
  <g transform="translate(0 100)">
    <path d="M10 10 L40 10 L40 40 Z"/>
    <polygon points="60,10 90,10 90,40" opacity="0.5"/>
  </g>
</svg>"""

internal fun doodles(): String {
    val shapes = StringBuilder()
    val cell = 60
    for (row in 0 until 13) {
        for (column in 0 until 7) {
            val x = column * cell + (if (row % 2 == 1) cell / 2 else 0) + 10
            val y = row * cell + 10
            val angle = (row * 37 + column * 53) % 90 - 45
            val shape = when ((row * 3 + column) % 5) {
                0 -> {
                    val points = (0 until 10).joinToString(" ") { i ->
                        val r = if (i % 2 == 0) 14.0 else 6.0
                        val a = PI / 5 * i - PI / 2
                        "%.1f,%.1f".format(Locale.ROOT, r * cos(a), r * sin(a))
                    }
                    """<polygon points="$points"/>"""
                }
                1 -> """<path d="M0 4 C0 -4 -12 -4 -12 4 C-12 10 -4 14 0 18 C4 14 12 10 12 4 C12 -4 0 -4 0 4 Z" transform="translate(0 -8)"/>"""
                2 -> """<circle r="11" fill="none" stroke="#000" stroke-width="3"/>"""
                3 -> """<rect x="-9" y="-9" width="18" height="18" rx="4"/>"""
                else -> """<path d="M-16 0 Q-8 -8 0 0 T16 0" fill="none" stroke="#000" stroke-width="3"/>"""
            }
            shapes.append("""<g transform="translate($x $y) rotate($angle)">$shape</g>""")
        }
    }
    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 760">$shapes</svg>"""
}
