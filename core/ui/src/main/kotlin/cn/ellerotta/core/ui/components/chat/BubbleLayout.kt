package cn.ellerotta.core.ui.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kotlin.math.roundToInt

internal const val PRESS_SCALE = 0.95f

internal val MIN_BUBBLE_WIDTH = 72.dp

private const val BUBBLE_SHARE = 0.79f
private val MAX_BUBBLE_WIDTH = 560.dp
private val MAX_BUBBLE_WIDTH_AVATAR = 512.dp

fun bubbleMaxWidth(available: Dp, avatar: Boolean): Dp =
    max(min(available * BUBBLE_SHARE, if (avatar) MAX_BUBBLE_WIDTH_AVATAR else MAX_BUBBLE_WIDTH), MIN_BUBBLE_WIDTH)

internal fun Modifier.bubbleWidth(sides: Dp, avatar: Boolean): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) return@layout measurable.measure(constraints).let { layout(it.width, it.height) { it.place(0, 0) } }
    val available = constraints.maxWidth.toDp() + sides
    val cap = bubbleMaxWidth(available, avatar).roundToPx().coerceAtMost(constraints.maxWidth)
    val least = MIN_BUBBLE_WIDTH.roundToPx().coerceAtMost(cap)
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth.coerceIn(least, cap), maxWidth = cap))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

internal class LastLine {
    var right = Float.POSITIVE_INFINITY
}

@Composable
internal fun TextWithTime(
    lastLine: LastLine,
    gap: Dp,
    headers: @Composable () -> Unit,
    text: @Composable () -> Unit,
    time: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(headers, text, time),
        modifier = modifier,
    ) { (headerMeasurables, textMeasurables, timeMeasurables), constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val headerPlaceables = headerMeasurables.map { it.measure(loose) }
        val textPlaceables = textMeasurables.map { it.measure(loose) }
        val timePlaceables = timeMeasurables.map { it.measure(loose) }
        val headersHeight = headerPlaceables.sumOf { it.height }
        val textWidth = textPlaceables.maxOfOrNull { it.width } ?: 0
        val textHeight = textPlaceables.sumOf { it.height }
        val timeWidth = timePlaceables.maxOfOrNull { it.width } ?: 0
        val timeHeight = timePlaceables.maxOfOrNull { it.height } ?: 0
        val widest = maxOf(headerPlaceables.maxOfOrNull { it.width } ?: 0, textWidth)
        val end = lastLine.right.takeIf { it.isFinite() }?.roundToInt()
        val inlineWidth = end?.let { it + gap.roundToPx() + timeWidth }
        val inline = inlineWidth != null && inlineWidth <= constraints.maxWidth
        val width = (if (inline) maxOf(widest, inlineWidth!!) else maxOf(widest, timeWidth)).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = headersHeight + if (inline) maxOf(textHeight, timeHeight) else textHeight + timeHeight
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            headerPlaceables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
            textPlaceables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
            val timeY = if (inline) headersHeight + maxOf(textHeight, timeHeight) - timeHeight else y
            timePlaceables.forEach { it.placeRelative(width - it.width, timeY) }
        }
    }
}
