package cn.ellerotta.core.ui.components.common

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

class AvatarPalette(val top: Color, val bottom: Color, val content: Color) {
    val brush: Brush get() = Brush.verticalGradient(listOf(top, bottom))
}

internal val avatarPalettes = listOf(
    AvatarPalette(Color(0xFFFF885E), Color(0xFFFF516A), Color(0xFF3D1319)),
    AvatarPalette(Color(0xFFFFCD6A), Color(0xFFFFA85C), Color(0xFF5E3E22)),
    AvatarPalette(Color(0xFF82B1FF), Color(0xFF7A74FF), Color(0xFF100E33)),
    AvatarPalette(Color(0xFFA0DE7E), Color(0xFF54CB68), Color(0xFF1D4724)),
    AvatarPalette(Color(0xFF00FCFD), Color(0xFF4ACCCD), Color(0xFF1B4B4C)),
    AvatarPalette(Color(0xFF72D5FD), Color(0xFF2A9EF1), Color(0xFF0B2B41)),
    AvatarPalette(Color(0xFFE0A2F3), Color(0xFFD669ED), Color(0xFF3A1C40)),
)

internal val savedMessagesPalette = avatarPalettes[5]

fun avatarPalette(id: Long): AvatarPalette = avatarPalettes[(id % avatarPalettes.size).absoluteValue.toInt()]

fun avatarBrush(id: Long): Brush = avatarPalette(id).brush

fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return words.take(2).mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }.joinToString("").uppercase()
        .ifEmpty { name.trim().take(1).uppercase() }
}
