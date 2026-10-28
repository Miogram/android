package cn.ellerotta.core.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.components.chat.ChatBackground
import cn.ellerotta.core.ui.components.chat.PARALLAX_SHIFT
import cn.ellerotta.core.ui.components.chat.parallaxOffset
import cn.ellerotta.core.ui.components.chat.rememberTilt
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.SensorEventBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParallaxTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theOffsetFollowsTheTiltUpToItsShift() {
        val size = Size(1000f, 2000f)
        parallaxOffset(Offset.Zero, size).let { still -> assertTrue(still.x == 0f && still.y == 0f) }
        val half = parallaxOffset(Offset(0.5f, 0.5f), size)
        assertEquals(-0.5f * 1000 * PARALLAX_SHIFT, half.x, 0.01f)
        assertEquals(0.5f * 2000 * PARALLAX_SHIFT, half.y, 0.01f)
        assertEquals(parallaxOffset(Offset(-1f, 1f), size), parallaxOffset(Offset(-3f, 7f), size))
    }

    @Test
    fun theTiltComesFromTheGravitySensor() {
        val sensors = ApplicationProvider.getApplicationContext<Context>().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravity = ShadowSensor.newInstance(Sensor.TYPE_GRAVITY)
        shadowOf(sensors).addSensor(gravity)
        val enabled = mutableStateOf(true)
        var tilt = Offset.Zero
        compose.setContent { tilt = rememberTilt(enabled.value).value }
        compose.waitForIdle()
        assertTrue(shadowOf(sensors).listeners.isNotEmpty())

        fun read(x: Float, y: Float) {
            compose.runOnIdle { shadowOf(sensors).sendSensorEventToListeners(SensorEventBuilder.newBuilder().setSensor(gravity).setValues(floatArrayOf(x, y, 9.8f)).build()) }
            compose.waitForIdle()
        }
        read(0f, 0f)
        assertEquals(Offset.Zero, tilt)
        repeat(60) { read(8f, -8f) }
        assertEquals(1f, tilt.x, 0.01f)
        assertEquals(-1f, tilt.y, 0.01f)

        enabled.value = false
        compose.waitForIdle()
        assertTrue(shadowOf(sensors).listeners.isEmpty())
        assertEquals(Offset.Zero, tilt)
    }

    @Test
    fun onlyAMotionWallpaperMoves() {
        val wallpaper = mutableStateOf(ChatWallpaper(Wallpaper("wp1", WallpaperKind.Fill, listOf(0x7fa381))))
        compose.setContent { MioTheme(useDynamicColor = false) { Box(Modifier.fillMaxSize()) { ChatBackground(Modifier.fillMaxSize(), wallpaper.value) } } }
        compose.onNodeWithTag("chatWallpaper").assertExists()
        compose.onNodeWithTag("chatWallpaperMotion", useUnmergedTree = true).assertDoesNotExist()
        wallpaper.value = ChatWallpaper(Wallpaper("wp1", WallpaperKind.Fill, listOf(0x7fa381), motion = true))
        compose.onNodeWithTag("chatWallpaperMotion", useUnmergedTree = true).assertExists()
    }
}
