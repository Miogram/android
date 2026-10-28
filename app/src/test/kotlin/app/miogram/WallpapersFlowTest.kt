package app.miogram

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.miogram.FakeWallpapers.Companion.PATTERN_ID
import app.miogram.FakeWallpapers.Companion.UPLOADED_ID
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputWallPaper
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class WallpapersFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun pickAPatternForEveryChat() {
        compose.logIn()
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("chatBackground")
        compose.onNodeWithTag("back").performClick()

        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("settings")
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("openWallpapers"))
        compose.onNodeWithTag("openWallpapers").performSemanticsAction(SemanticsActions.OnClick)

        compose.waitForTag("wallpaper_wp$PATTERN_ID")
        compose.onNodeWithTag("wallpaper_builtin").assertIsSelected()
        compose.waitUntilIdle { fake.wallpapers.downloads.contains("$PATTERN_ID") }
        compose.onNodeWithTag("wallpaper_wp$PATTERN_ID").performClick()

        compose.waitForTag("wallpaperEditor")
        compose.onNodeWithTag("wallpaperIntensity").assertExists()
        compose.onNodeWithTag("wallpaperMotion").performClick()
        compose.onNodeWithTag("wallpaperApply").performClick()

        compose.waitUntilIdle { fake.wallpapers.installCalls.isNotEmpty() }
        val install = fake.wallpapers.installCalls.single()
        assertEquals(PATTERN_ID, (install.wallpaper as InputWallPaper).id)
        assertEquals(listOf(0xdbddbb, 0x6ba587, 0xd5d88d, 0x88b884, 50, 0), install.settings.let { listOf(it.backgroundColor, it.secondBackgroundColor, it.thirdBackgroundColor, it.fourthBackgroundColor, it.intensity, it.rotation) })
        assertTrue(install.settings.motion)
        compose.waitUntilIdle { compose.onAllNodesWithTag("wallpaperEditor").fetchSemanticsNodes().isEmpty() }
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("wallpaper_wp$PATTERN_ID").assertIsSelected() }.isSuccess }
        compose.onNodeWithTag("wallpaper_builtin").assertIsNotSelected()

        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("settings")
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("chatWallpaper")
        compose.onNodeWithTag("chatWallpaperMotion", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noWallpaperForEveryChat() {
        compose.logIn()
        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("settings")
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("openWallpapers"))
        compose.onNodeWithTag("openWallpapers").performSemanticsAction(SemanticsActions.OnClick)

        compose.waitForTag("wallpaper_none")
        compose.onNodeWithTag("wallpaper_none").performClick()
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("wallpaper_none").assertIsSelected() }.isSuccess }
        compose.onNodeWithTag("wallpaper_builtin").assertIsNotSelected()
        assertTrue(fake.wallpapers.installCalls.isEmpty())

        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("settings")
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("chatPlain")
        compose.onNodeWithTag("chatBackground").assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun anOwnPhotoForEveryChat() {
        compose.logIn()
        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("settings")
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("openWallpapers"))
        compose.onNodeWithTag("openWallpapers").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("wallpaper_photo")
        compose.onNodeWithTag("wallpaper_photo").performClick()

        val bitmap = Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff4f9de0.toInt()) }
        val file = File(dir, "beach.jpg").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        val shadow = shadowOf(compose.activity)
        shadow.receiveResult(shadow.nextStartedActivityForResult.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))

        compose.waitForTag("wallpaperEditor")
        compose.onNodeWithTag("wallpaperSwatches").assertDoesNotExist()
        compose.onNodeWithTag("wallpaperBlur").performClick()
        compose.onNodeWithTag("wallpaperApply").performClick()

        compose.waitUntilIdle { fake.wallpapers.installCalls.isNotEmpty() }
        val upload = fake.wallpapers.uploadCalls.single()
        assertEquals("image/jpeg", upload.mimeType)
        assertTrue(upload.settings.blur && !upload.forChat)
        val sent = BitmapFactory.decodeFile(File(dir, "wallpapers/chosen_$UPLOADED_ID").path)
        assertEquals(2560 to 1280, sent.width to sent.height)
        assertEquals(UPLOADED_ID, (fake.wallpapers.installCalls.single().wallpaper as InputWallPaper).id)
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("wallpapers").performScrollToNode(hasTestTag("wallpaper_wp$UPLOADED_ID")) }.isSuccess }
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("wallpaper_wp$UPLOADED_ID").assertIsSelected() }.isSuccess }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aChatsOwnWallpaper() {
        compose.logIn()
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("chatBackground")

        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.onNodeWithTag("menu_wallpaper").performClick()
        compose.waitForTag("wallpaper_wp$PATTERN_ID")
        compose.onNodeWithTag("wallpaper_builtin").assertIsSelected()
        compose.waitUntilIdle { fake.wallpapers.downloads.contains("$PATTERN_ID") }
        compose.onNodeWithTag("wallpaper_wp$PATTERN_ID").performClick()
        compose.waitForTag("wallpaperEditor")
        compose.onNodeWithTag("wallpaperApply").performClick()

        compose.waitUntilIdle { fake.wallpapers.setChatCalls.isNotEmpty() }
        val call = fake.wallpapers.setChatCalls.single()
        assertEquals(fake.alice.id, (call.peer as InputPeerUser).userId)
        assertEquals(PATTERN_ID, (call.wallpaper as InputWallPaper).id)
        assertTrue(fake.wallpapers.installCalls.isEmpty())
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("wallpaper_wp$PATTERN_ID").assertIsSelected() }.isSuccess }
        compose.onNodeWithTag("wallpaper_builtin").assertIsNotSelected()

        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("chatWallpaper")
        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("chatBackground")
        compose.onNodeWithTag("back").performClick()

        val offered = fake.wallpapers.offer(fake.alice.id, fake.wallpapers.wallpapers()[2])
        compose.waitForText("Alice Example")
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("applyWallpaper_${offered.id}")
        compose.onNodeWithTag("applyWallpaper_${offered.id}").performClick()
        compose.waitUntilIdle { fake.wallpapers.setChatCalls.size == 2 }
        val apply = fake.wallpapers.setChatCalls.last()
        assertEquals(offered.id, apply.id)
        assertNull(apply.wallpaper)
        assertTrue(apply.settings?.blur == true)
        compose.waitForTag("chatWallpaper")
        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("openChats")
    }
}
