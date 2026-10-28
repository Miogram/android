package cn.ellerotta.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Looper
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperChoices
import cn.ellerotta.core.model.WallpaperEdit
import cn.ellerotta.core.model.NO_WALLPAPER
import cn.ellerotta.core.model.PLAIN_WALLPAPER
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.components.chat.ChatPattern
import cn.ellerotta.core.ui.components.chat.WallpaperRenders
import cn.ellerotta.core.ui.components.chat.ChatBackground
import cn.ellerotta.core.ui.components.profile.WallpapersScreen
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
class WallpapersScreenTest {
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
    fun pickerShowsTheChoicesAndPicks() {
        val picks = mutableListOf<String?>()
        var photos = 0
        val choices = choices(chosen = "wp1")
        compose.setContent { MioTheme(useDynamicColor = false) { WallpapersScreen(choices, busy = false, onBack = {}, onPick = { picks += it }, onPickPhoto = { photos++ }) } }
        compose.onNodeWithTag("wallpaper_wp1").assertIsSelected()
        compose.onNodeWithTag("wallpaper_builtin").assertIsNotSelected()
        compose.onNodeWithTag("wallpaper_builtin").performClick()
        compose.onNodeWithTag("wallpaper_wp4").performClick()
        assertEquals(listOf(null, "wp4"), picks)
        compose.onNodeWithTag("wallpaper_photo").assertIsNotSelected().performClick()
        assertEquals(1, photos)
        compose.onNodeWithTag("wallpaper_none").assertIsNotSelected().assertContentDescriptionEquals("No wallpaper").performClick()
        assertEquals(listOf(null, "wp4", NO_WALLPAPER), picks)
    }

    @Test
    fun noWallpaperIsChosenAndChatsShowThePlainBackground() {
        var chosen by mutableStateOf(true)
        var surface = Color.Unspecified
        compose.setContent {
            MioTheme(darkTheme = true, useDynamicColor = false) {
                surface = MaterialTheme.colorScheme.surface
                if (chosen) {
                    WallpapersScreen(choices(chosen = NO_WALLPAPER), busy = false, onBack = {}, onPick = {})
                } else {
                    ChatBackground(Modifier.size(100.dp), ChatWallpaper(PLAIN_WALLPAPER))
                }
            }
        }
        compose.onNodeWithTag("wallpaper_none").assertIsSelected()
        compose.onNodeWithTag("wallpaper_builtin").assertIsNotSelected()
        chosen = false
        compose.onNodeWithTag("chatBackground").assertDoesNotExist()
        val pixels = compose.onNodeWithTag("chatPlain").captureToImage().toPixelMap()
        for ((x, y) in listOf(0 to 0, 50 to 50, pixels.width - 1 to pixels.height - 1)) assertEquals(surface, pixels[x, y])
    }

    @Test
    fun pickerForAChatGoesBackToEveryChats() {
        val picks = mutableListOf<String?>()
        val everyChat = ChatWallpaper(Wallpaper("wp4", WallpaperKind.Fill, listOf(0x7fa381)))
        compose.setContent {
            MioTheme(useDynamicColor = false) { WallpapersScreen(choices(chosen = null), busy = false, onBack = {}, onPick = { picks += it }, forChat = true, everyChat = everyChat) }
        }
        compose.onAllNodesWithText("Wallpaper for this chat").onFirst().assertExists()
        compose.onNodeWithText("Only this chat shows it. The other side is offered the same wallpaper.").assertExists()
        compose.onNodeWithTag("wallpaper_builtin").assertIsSelected().assertContentDescriptionEquals("As every chat")
        compose.onNodeWithTag("chatBackground", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("wallpaper_builtin").performClick()
        compose.onNodeWithTag("wallpaper_wp4").performClick()
        assertEquals(listOf(null, "wp4"), picks)
    }

    @Test
    fun editorChangesTheSettingsAndApplies() {
        val choices = choices(chosen = null)
        val opened = mutableListOf<String>()
        var editing by mutableStateOf<WallpaperEdit?>(null)
        var applied = 0
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                WallpapersScreen(
                    choices, busy = false, onBack = {}, onPick = {},
                    onEdit = { key ->
                        opened += key
                        editing = WallpaperEdit(choices.wallpapers.first { it.key == key }, choices.previews[key])
                    },
                    editing = editing,
                    onChange = { editing = editing?.copy(wallpaper = it) },
                    onApply = { applied++ },
                    onCloseEdit = { editing = null },
                )
            }
        }
        compose.onNodeWithTag("wallpapers").performScrollToNode(hasTestTag("wallpaper_wp2"))
        compose.onNodeWithTag("wallpaper_wp2").performClick()
        assertEquals(listOf("wp2"), opened)
        compose.onNodeWithTag("wallpaperEditor").assertExists()

        compose.onNodeWithTag("wallpaperIntensity").assertExists()
        compose.onNodeWithTag("wallpaperBlur").assertDoesNotExist()
        compose.onNodeWithTag("wallpaperColor_0").assertIsSelected()
        compose.onNodeWithTag("wallpaperColor_1").performClick()
        compose.onNodeWithTag("wallpaperSwatches").performScrollToNode(hasTestTag("swatch_4f9de0"))
        compose.onNodeWithTag("swatch_4f9de0").performClick()
        compose.onNodeWithTag("wallpaperRotate").performClick()
        compose.onNodeWithTag("wallpaperMotion").performClick()
        val pattern = assertNotNull(editing).wallpaper
        assertEquals(listOf(0x1b2836, 0x4f9de0), pattern.colors)
        assertEquals(90, pattern.rotation)
        assertTrue(pattern.motion)
        compose.onNodeWithTag("wallpaperApply").performClick()
        assertEquals(1, applied)

        compose.onNodeWithTag("wallpaperEditClose").performClick()
        compose.onNodeWithTag("wallpaperEditor").assertDoesNotExist()
        compose.onNodeWithTag("wallpapers").performScrollToNode(hasTestTag("wallpaper_wp3"))
        compose.onNodeWithTag("wallpaper_wp3").performClick()
        compose.onNodeWithTag("wallpaperBlur").performClick()
        compose.onNodeWithTag("wallpaperIntensity").assertDoesNotExist()
        compose.onNodeWithTag("wallpaperSwatches").assertDoesNotExist()
        assertEquals(false, editing?.wallpaper?.blur)
        compose.onNodeWithTag("wallpaperEditClose").performClick()
        compose.onNodeWithTag("wallpapers").performScrollToNode(hasTestTag("wallpaper_wp4"))
        compose.onNodeWithTag("wallpaper_wp4").performClick()
        compose.onNodeWithTag("wallpaperRotate").assertDoesNotExist()
        compose.onNodeWithTag("swatch_fec496").performClick()
        assertEquals(listOf(0xfec496), editing?.wallpaper?.colors)
    }

    @Test
    fun pickerScreenshot() {
        val start = WallpaperRenders.renderCount()
        val choices = choices(chosen = "wp1")
        compose.setContent { MioTheme(darkTheme = false, useDynamicColor = false) { WallpapersScreen(choices, busy = false, onBack = {}, onPick = {}) } }
        compose.waitUntil(30_000) {
            shadowOf(Looper.getMainLooper()).idle()
            WallpaperRenders.renderCount() >= start + choices.wallpapers.size && ChatPattern.cached != null
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width > 0)
        System.getenv("MIOGRAM_SCREENSHOTS")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "wallpapers_light.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun choices(chosen: String?): WallpaperChoices {
        val pattern = File(dir, "doodles.tgv").apply { writeBytes(gzip(doodles())) }
        val image = File(dir, "photo.jpg").apply { writeBytes(photoJpeg()) }
        return WallpaperChoices(
            wallpapers = listOf(
                Wallpaper("wp1", WallpaperKind.Pattern, listOf(0xdbddbb, 0x6ba587, 0xd5d88d, 0x88b884), intensity = 50),
                Wallpaper("wp2", WallpaperKind.Pattern, listOf(0x1b2836, 0x5f7f9c), rotation = 45, intensity = -60, dark = true),
                Wallpaper("wp3", WallpaperKind.Image, blur = true),
                Wallpaper("wp4", WallpaperKind.Fill, listOf(0x7fa381)),
                Wallpaper("wp5", WallpaperKind.Fill, listOf(0xfec496, 0xdd6cb9), rotation = 90),
                Wallpaper("wp6", WallpaperKind.Fill, listOf(0x4f5bd5, 0x962fbf, 0xdd6cb9)),
                Wallpaper("wp7", WallpaperKind.Pattern, listOf(0xe8c06e, 0xf29ebf), intensity = 40),
                Wallpaper("wp8", WallpaperKind.Image),
            ),
            previews = mapOf("wp1" to pattern.path, "wp2" to pattern.path, "wp3" to image.path, "wp8" to image.path),
            chosen = chosen,
        )
    }

    private fun photoJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 640f, 0xfff7b267.toInt(), 0xff6a4c93.toInt(), Shader.TileMode.CLAMP) })
            drawCircle(180f, 330f, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfffff1c1.toInt() })
            drawOval(RectF(-120f, 380f, 300f, 760f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff2d3a5c.toInt() })
            drawOval(RectF(120f, 420f, 520f, 800f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff1c2541.toInt() })
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
