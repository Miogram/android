package cn.ellerotta.core.ui.components.chat

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import cn.ellerotta.core.ui.theme.GRAPHIC_CONTRAST
import cn.ellerotta.core.ui.theme.TEXT_CONTRAST
import cn.ellerotta.core.ui.theme.contrastRatio
import cn.ellerotta.core.ui.theme.fadedOver
import cn.ellerotta.core.ui.theme.legibleOn

@Immutable
internal data class BubbleColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color,
    val secondary: Color,
    val track: Color,
    val success: Color,
    val danger: Color,
)

internal fun bubbleColors(scheme: ColorScheme, out: Boolean): BubbleColors {
    val container = if (out) scheme.primaryContainer else scheme.surfaceContainerHigh
    val content = if (out) scheme.onPrimaryContainer else scheme.onSurface
    val primary = !out && contrastRatio(scheme.primary, container) >= TEXT_CONTRAST
    return BubbleColors(
        container = container,
        content = content,
        accent = if (primary) scheme.primary else content,
        onAccent = if (primary) scheme.onPrimary else container,
        secondary = content.fadedOver(container, SECONDARY_ALPHA, TEXT_CONTRAST),
        track = content.fadedOver(container, TRACK_ALPHA, GRAPHIC_CONTRAST),
        success = legibleOn(container, listOf(DarkGreen, LightGreen)),
        danger = legibleOn(container, listOf(DarkRed, LightRed)),
    )
}

internal val LocalBubbleColors = staticCompositionLocalOf<BubbleColors?> { null }

@Composable
@ReadOnlyComposable
internal fun bubbleColors(): BubbleColors = LocalBubbleColors.current ?: bubbleColors(MaterialTheme.colorScheme, out = false)

internal fun senderColor(id: Long, container: Color): Color {
    val index = (id % SENDER_HUES).toInt().let { if (it < 0) it + SENDER_HUES else it }
    return legibleOn(container, listOf(senderOnLight[index], senderOnDark[index]))
}

private const val SENDER_HUES = 7

internal val senderOnLight = listOf(
    Color(0xFFB8193E), Color(0xFF944B00), Color(0xFF4E49D6), Color(0xFF016E2E), Color(0xFF036970), Color(0xFF0160A8), Color(0xFF8D36AB),
)

internal val senderOnDark = listOf(
    Color(0xFFFE9A9F), Color(0xFFFE9F53), Color(0xFFBCAAFE), Color(0xFF65CB7B), Color(0xFF5AC5CE), Color(0xFF8CB7FF), Color(0xFFE798FF),
)

private const val SECONDARY_ALPHA = 0.7f
private const val TRACK_ALPHA = 0.4f

private val DarkGreen = Color(0xFF1E7F36)
private val LightGreen = Color(0xFF6DD58C)
private val DarkRed = Color(0xFFC62828)
private val LightRed = Color(0xFFFF8A80)
