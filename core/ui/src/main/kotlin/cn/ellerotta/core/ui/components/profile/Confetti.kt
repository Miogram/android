package cn.ellerotta.core.ui.components.profile

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Piece(val angle: Float, val speed: Float, val spin: Float, val color: Color, val width: Float, val height: Float, val sway: Float)

@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    val palette = with(MaterialTheme.colorScheme) { listOf(primary, secondary, tertiary, error, primaryContainer, tertiaryContainer, Color(0xFFFFC94D)) }
    var pieces by remember { mutableStateOf<List<Piece>>(emptyList()) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val random = Random(trigger)
        pieces = List(90) {
            Piece(
                angle = (-PI / 2 + (random.nextFloat() - 0.5f) * PI * 0.9).toFloat(),
                speed = 900f + random.nextFloat() * 900f,
                spin = (random.nextFloat() - 0.5f) * 1080f,
                color = palette[random.nextInt(palette.size)],
                width = 10f + random.nextFloat() * 10f,
                height = 6f + random.nextFloat() * 8f,
                sway = random.nextFloat() * 6f,
            )
        }
        val start = withFrameNanos { it }
        do {
            elapsed = withFrameNanos { it } - start
        } while (elapsed < DURATION)
        pieces = emptyList()
    }
    Canvas(modifier) {
        val t = elapsed / 1e9f
        val alpha = (1f - elapsed / DURATION.toFloat()).coerceIn(0f, 1f)
        pieces.forEach { piece ->
            val x = size.width / 2 + cos(piece.angle) * piece.speed * t * 0.6f + sin(t * piece.sway) * 24f
            val y = size.height * 0.3f + sin(piece.angle) * piece.speed * t * 0.6f + 1400f * t * t
            rotate(piece.spin * t, Offset(x, y)) {
                drawRect(piece.color.copy(alpha = alpha), topLeft = Offset(x - piece.width / 2, y - piece.height / 2), size = Size(piece.width, piece.height))
            }
        }
    }
}

private const val DURATION = 2_400_000_000L
