package cn.ellerotta.core.ui.components.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

fun qrModules(text: String): Array<BooleanArray> {
    val matrix = Encoder.encode(text, ErrorCorrectionLevel.M).matrix
    return Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y).toInt() == 1 } }
}

internal fun inFinder(x: Int, y: Int, size: Int): Boolean =
    (x < 7 && y < 7) || (x >= size - 7 && y < 7) || (x < 7 && y >= size - 7)

@Composable
fun QrCode(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val modules = remember(text) { qrModules(text) }
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        val count = modules.size
        val cell = size.width / count
        val dot = cell * 0.86f
        val inset = (cell - dot) / 2
        for (y in 0 until count) {
            for (x in 0 until count) {
                if (!modules[y][x] || inFinder(x, y, count)) continue
                drawRoundRect(color, Offset(x * cell + inset, y * cell + inset), Size(dot, dot), CornerRadius(dot / 2))
            }
        }
        for ((fx, fy) in listOf(0 to 0, count - 7 to 0, 0 to count - 7)) {
            val stroke = cell
            drawRoundRect(
                color = color,
                topLeft = Offset(fx * cell + stroke / 2, fy * cell + stroke / 2),
                size = Size(7 * cell - stroke, 7 * cell - stroke),
                cornerRadius = CornerRadius(cell * 2.2f),
                style = Stroke(width = stroke),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset((fx + 2) * cell, (fy + 2) * cell),
                size = Size(3 * cell, 3 * cell),
                cornerRadius = CornerRadius(cell * 1.1f),
            )
        }
    }
}
