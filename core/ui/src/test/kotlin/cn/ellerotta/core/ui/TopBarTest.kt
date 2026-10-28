package cn.ellerotta.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.components.common.absorbTouches
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import cn.ellerotta.core.model.AppPrivacy
import cn.ellerotta.core.model.GlobalPrivacy
import cn.ellerotta.core.model.PrivacyBase
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.PrivacySetting
import cn.ellerotta.core.model.PrivacyState
import cn.ellerotta.core.model.SessionInfo
import cn.ellerotta.core.ui.components.common.titleProgress
import cn.ellerotta.core.ui.components.common.scrimProgress
import cn.ellerotta.core.ui.components.profile.PrivacyScreen
import cn.ellerotta.core.ui.components.profile.SessionsScreen
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.File
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val privacy = PrivacyState(
        settings = PrivacyKey.entries.map { PrivacySetting(it, PrivacyBase.Contacts) },
        global = GlobalPrivacy(),
        accountTtlDays = 365,
    )

    private val sessions = listOf(
        SessionInfo(1, "Pixel 9", "Android", "16", "miogram", "0.1.0", "Amsterdam, Netherlands", "1.2.3.4", 0, current = true, official = false),
    ) + (2L..12L).map { SessionInfo(it, "iPhone $it", "iOS", "26", "Telegram iOS", "12.0", "Berlin, Germany", "5.6.7.8", 1_790_000_000, current = false, official = true) }

    @Test
    fun aBarAbsorbsTheTouchesOnItsEmptyPartsAndKeepsItsOwn() {
        var under = 0
        var inside = 0
        compose.setContent {
            MioTheme(useDynamicColor = false) {
                Box(Modifier.size(200.dp)) {
                    Box(Modifier.fillMaxSize().clickable { under++ }.testTag("under"))
                    Box(Modifier.size(width = 200.dp, height = 80.dp).absorbTouches().testTag("bar")) {
                        Box(Modifier.size(40.dp).clickable { inside++ }.testTag("button"))
                    }
                }
            }
        }
        compose.onNodeWithTag("bar").performTouchInput { click(Offset(width - 10f, height - 10f)) }
        assertEquals(0 to 0, under to inside)
        compose.onNodeWithTag("button").performClick()
        assertEquals(0 to 1, under to inside)
        compose.onNodeWithTag("under").performTouchInput { click(Offset(width / 2f, height - 10f)) }
        assertEquals(1 to 1, under to inside)
    }

    @Test
    fun progressFollowsTheScroll() {
        assertEquals(0f, scrimProgress(0, 0, 100f))
        assertEquals(0.5f, scrimProgress(0, 50, 100f))
        assertEquals(1f, scrimProgress(1, 0, 100f))
        assertEquals(0f, titleProgress(40, 100))
        assertEquals(0.5f, titleProgress(70, 100), 0.001f)
        assertEquals(1f, titleProgress(95, 100))
    }

    @Test
    fun backPillAndLargeTitle() {
        var backs = 0
        compose.setContent { MioTheme(useDynamicColor = false) { PrivacyScreen(privacy, AppPrivacy(), busy = false, onBack = { backs++ }, onApp = {}, onPrivacy = { _, _ -> }, onGlobal = {}, onAccountTtl = {}) } }
        compose.onNodeWithTag("largeTitle").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("barTitle").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun screenshots() {
        var screen by mutableStateOf("privacy")
        compose.setContent {
            MioTheme(darkTheme = false, useDynamicColor = false) {
                when (screen) {
                    "privacy" -> PrivacyScreen(privacy, AppPrivacy(), busy = false, onBack = {}, onApp = {}, onPrivacy = { _, _ -> }, onGlobal = {}, onAccountTtl = {})
                    else -> SessionsScreen(sessions, busy = false, onBack = {}, onTerminate = {}, onTerminateOthers = {})
                }
            }
        }
        capture("topbar_privacy", "privacy")
        screen = "sessions"
        capture("topbar_sessions", "sessions")
    }

    private fun capture(name: String, tag: String) {
        compose.waitForIdle()
        save("${name}_top", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithTag(tag).performScrollToIndex(2)
        compose.waitForIdle()
        save("${name}_scrolled", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun save(name: String, image: Bitmap) {
        System.getenv("MIOGRAM_SCREENSHOTS")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
