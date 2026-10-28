package cn.ellerotta.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cn.ellerotta.core.ui.R

@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int) = Font(
    resId = R.font.googlesansflex_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(100f),
        FontVariation.grade(0),
        FontVariation.slant(0f),
        FontVariation.Setting("ROND", 100f),
        FontVariation.Setting("opsz", 16f),
    ),
)

val fontFamily = FontFamily(flex(400), flex(500), flex(600), flex(700), flex(800))

private fun style(size: Int, line: Int, tracking: Float, weight: FontWeight) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp, fontWeight = weight)

private val regular = FontWeight.Normal
private val medium = FontWeight.Medium
private val bold = FontWeight.Bold

val Typography = Typography(
    fontFamily = fontFamily,
    displayLarge = style(57, 64, -0.2f, regular),
    displayMedium = style(45, 52, 0f, regular),
    displaySmall = style(36, 44, 0f, regular),
    headlineLarge = style(32, 40, 0f, regular),
    headlineMedium = style(28, 36, 0f, regular),
    headlineSmall = style(24, 32, 0f, regular),
    titleLarge = style(22, 28, 0f, regular),
    titleMedium = style(16, 24, 0.2f, medium),
    titleSmall = style(14, 20, 0.1f, medium),
    bodyLarge = style(16, 24, 0.5f, regular),
    bodyMedium = style(14, 20, 0.2f, regular),
    bodySmall = style(12, 16, 0.4f, regular),
    labelLarge = style(14, 20, 0.1f, medium),
    labelMedium = style(12, 16, 0.5f, medium),
    labelSmall = style(11, 16, 0.5f, medium),
    displayLargeEmphasized = style(57, 64, 0f, medium),
    displayMediumEmphasized = style(45, 52, 0f, medium),
    displaySmallEmphasized = style(36, 44, 0f, medium),
    headlineLargeEmphasized = style(32, 40, 0f, medium),
    headlineMediumEmphasized = style(28, 36, 0f, medium),
    headlineSmallEmphasized = style(24, 32, 0f, medium),
    titleLargeEmphasized = style(22, 28, 0f, medium),
    titleMediumEmphasized = style(16, 24, 0.15f, bold),
    titleSmallEmphasized = style(14, 20, 0.1f, bold),
    bodyLargeEmphasized = style(16, 24, 0.15f, medium),
    bodyMediumEmphasized = style(14, 20, 0.25f, medium),
    bodySmallEmphasized = style(12, 16, 0.4f, medium),
    labelLargeEmphasized = style(14, 20, 0.1f, bold),
    labelMediumEmphasized = style(12, 16, 0.5f, bold),
    labelSmallEmphasized = style(11, 16, 0.5f, bold),
)
