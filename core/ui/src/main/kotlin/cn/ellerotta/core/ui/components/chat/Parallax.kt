package cn.ellerotta.core.ui.components.chat

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext

internal const val PARALLAX_SHIFT = 0.04f

internal const val PARALLAX_SCALE = 1f + 2 * PARALLAX_SHIFT

private const val FULL_TILT = 4f

private const val TILT_SMOOTHING = 0.15f

internal fun parallaxOffset(tilt: Offset, size: Size): Offset =
    Offset(-tilt.x.coerceIn(-1f, 1f) * size.width * PARALLAX_SHIFT, tilt.y.coerceIn(-1f, 1f) * size.height * PARALLAX_SHIFT)

@Composable
internal fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled, context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sensors == null || sensor == null) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose {}
        }
        var rest: Offset? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val reading = Offset(event.values[0], event.values[1])
                val start = rest ?: reading.also { rest = it }
                val target = Offset(((reading.x - start.x) / FULL_TILT).coerceIn(-1f, 1f), ((reading.y - start.y) / FULL_TILT).coerceIn(-1f, 1f))
                tilt.value += (target - tilt.value) * TILT_SMOOTHING
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }
    return tilt
}
