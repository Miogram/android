package cn.ellerotta.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

const val TEXT_CONTRAST = 4.5f

const val GRAPHIC_CONTRAST = 3f

fun Color.fadedOver(background: Color, alpha: Float, min: Float): Color {
    var a = alpha
    while (a < 1f && contrastRatio(copy(alpha = a).compositeOver(background), background) < min) a += 0.05f
    return copy(alpha = a.coerceAtMost(1f))
}

fun legibleOn(background: Color, colors: List<Color>): Color = colors.maxBy { contrastRatio(it, background) }
