package cn.ellerotta.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import cn.ellerotta.core.ui.theme.Lch
import cn.ellerotta.core.ui.theme.SchemeVariant
import cn.ellerotta.core.ui.theme.colorOf
import cn.ellerotta.core.ui.theme.lchOf
import cn.ellerotta.core.ui.theme.seedScheme

val TonalSpot = SchemeVariant(
    primary = { 48.0 },
    secondary = { 16.0 },
    tertiary = { 24.0 },
    tertiaryTurn = 60.0,
    neutral = { 6.0 },
    neutralVariant = { 8.0 },
)

fun wallpaperScheme(seed: Color, dark: Boolean): ColorScheme = seedScheme(lchOf(seed.toArgb()), dark, TonalSpot)

fun monochromeScheme(dark: Boolean): ColorScheme {
    fun grey(tone: Int) = Color(colorOf(Lch(tone.toDouble(), 0.0, 0.0)))
    return seedScheme(Lch(50.0, 0.0, 0.0), dark, TonalSpot).copy(
        primary = grey(if (dark) 100 else 0),
        onPrimary = grey(if (dark) 10 else 90),
        primaryContainer = grey(if (dark) 85 else 25),
        onPrimaryContainer = grey(if (dark) 0 else 100),
        secondaryContainer = grey(if (dark) 30 else 85),
        tertiary = grey(if (dark) 90 else 25),
        onTertiary = grey(if (dark) 10 else 90),
        tertiaryContainer = grey(if (dark) 60 else 49),
        onTertiaryContainer = grey(if (dark) 0 else 100),
    )
}

val wallpaperSeeds = listOf(
    "blue" to Color(0xFF3F6FD8),
    "green" to Color(0xFF3E8E41),
    "pink" to Color(0xFFE05A9A),
    "yellow" to Color(0xFFFFEB3B),
    "cyan" to Color(0xFF00BCD4),
    "red" to Color(0xFFF44336),
    "magenta" to Color(0xFFE91E63),
    "indigo" to Color(0xFF3F51B5),
)
