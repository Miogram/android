package cn.ellerotta.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

data class Lch(val l: Double, val c: Double, val h: Double)

fun artworkSeed(pixels: IntArray): Lch? {
    val count = IntArray(HUE_BINS)
    val chroma = DoubleArray(HUE_BINS)
    val sumA = DoubleArray(HUE_BINS)
    val sumB = DoubleArray(HUE_BINS)
    var opaque = 0
    var lightness = 0.0
    for (pixel in pixels) {
        if (pixel ushr 24 < 0xF0) continue
        val lab = labOf(pixel)
        opaque++
        lightness += lab[0]
        val c = hypot(lab[1], lab[2])
        if (c < NEUTRAL_CHROMA) continue
        val bin = ((hueOf(lab[1], lab[2]) / 360.0 * HUE_BINS).toInt()).coerceIn(0, HUE_BINS - 1)
        count[bin]++
        chroma[bin] += c
        sumA[bin] += lab[1]
        sumB[bin] += lab[2]
    }
    if (opaque == 0) return null
    val colourful = count.sum()
    if (colourful < opaque * MIN_COLOURFUL) return Lch(lightness / opaque, 0.0, 0.0)
    var best = -1
    var bestScore = Double.NEGATIVE_INFINITY
    for (bin in 0 until HUE_BINS) {
        if (count[bin] == 0) continue
        val window = intArrayOf((bin + HUE_BINS - 1) % HUE_BINS, bin, (bin + 1) % HUE_BINS)
        val share = window.sumOf { count[it] }.toDouble() / opaque
        val meanChroma = window.sumOf { chroma[it] } / window.sumOf { count[it] }
        val score = share * SHARE_WEIGHT + (meanChroma.coerceAtMost(CHROMA_CAP) / CHROMA_CAP) * CHROMA_WEIGHT
        if (score > bestScore) {
            bestScore = score
            best = bin
        }
    }
    val window = intArrayOf((best + HUE_BINS - 1) % HUE_BINS, best, (best + 1) % HUE_BINS)
    val n = window.sumOf { count[it] }
    return Lch(lightness / opaque, window.sumOf { chroma[it] } / n, hueOf(window.sumOf { sumA[it] }, window.sumOf { sumB[it] }))
}

fun artworkScheme(seed: Lch, dark: Boolean): ColorScheme {
    val grey = seed.c < NEUTRAL_CHROMA
    val primaryChroma = if (grey) 0.0 else seed.c.coerceIn(MIN_PRIMARY_CHROMA, MAX_PRIMARY_CHROMA)
    val primary = Palette(seed.h, primaryChroma)
    val secondary = Palette(seed.h, primaryChroma / 3)
    val tertiary = Palette((seed.h + 60) % 360, primaryChroma / 2)
    val neutral = Palette(seed.h, (primaryChroma / 10).coerceAtMost(6.0))
    val variant = Palette(seed.h, (primaryChroma / 6).coerceAtMost(10.0))
    val error = Palette(ERROR_HUE, ERROR_CHROMA)
    return if (dark) {
        darkColorScheme(
            primary = primary(80), onPrimary = primary(20), primaryContainer = primary(30), onPrimaryContainer = primary(90), inversePrimary = primary(40),
            secondary = secondary(80), onSecondary = secondary(20), secondaryContainer = secondary(30), onSecondaryContainer = secondary(90),
            tertiary = tertiary(80), onTertiary = tertiary(20), tertiaryContainer = tertiary(30), onTertiaryContainer = tertiary(90),
            background = neutral(6), onBackground = neutral(90), surface = neutral(6), onSurface = neutral(90),
            surfaceVariant = variant(30), onSurfaceVariant = variant(80), surfaceTint = primary(80),
            inverseSurface = neutral(90), inverseOnSurface = neutral(20),
            error = error(80), onError = error(20), errorContainer = error(30), onErrorContainer = error(90),
            outline = variant(60), outlineVariant = variant(30), scrim = Color.Black,
            surfaceBright = neutral(24), surfaceDim = neutral(6), surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10),
            surfaceContainer = neutral(12), surfaceContainerHigh = neutral(17), surfaceContainerHighest = neutral(22),
        )
    } else {
        lightColorScheme(
            primary = primary(40), onPrimary = primary(100), primaryContainer = primary(90), onPrimaryContainer = primary(10), inversePrimary = primary(80),
            secondary = secondary(40), onSecondary = secondary(100), secondaryContainer = secondary(90), onSecondaryContainer = secondary(10),
            tertiary = tertiary(40), onTertiary = tertiary(100), tertiaryContainer = tertiary(90), onTertiaryContainer = tertiary(10),
            background = neutral(98), onBackground = neutral(10), surface = neutral(98), onSurface = neutral(10),
            surfaceVariant = variant(90), onSurfaceVariant = variant(30), surfaceTint = primary(40),
            inverseSurface = neutral(20), inverseOnSurface = neutral(95),
            error = error(40), onError = error(100), errorContainer = error(90), onErrorContainer = error(10),
            outline = variant(50), outlineVariant = variant(80), scrim = Color.Black,
            surfaceBright = neutral(98), surfaceDim = neutral(87), surfaceContainerLowest = neutral(100), surfaceContainerLow = neutral(96),
            surfaceContainer = neutral(94), surfaceContainerHigh = neutral(92), surfaceContainerHighest = neutral(90),
        )
    }
}

private class Palette(private val hue: Double, private val chroma: Double) {
    operator fun invoke(tone: Int): Color = Color(colorOf(Lch(tone.toDouble(), chroma, hue)))
}

internal fun colorOf(lch: Lch): Int {
    if (lch.l <= 0) return 0xFF000000.toInt()
    if (lch.l >= 100) return 0xFFFFFFFF.toInt()
    val rad = Math.toRadians(lch.h)
    var low = 0.0
    var high = lch.c
    var fit = linearOf(lch.l, 0.0, 0.0)!!
    linearOf(lch.l, lch.c * cos(rad), lch.c * sin(rad))?.let { return argbOf(it) }
    repeat(GAMUT_STEPS) {
        val mid = (low + high) / 2
        val rgb = linearOf(lch.l, mid * cos(rad), mid * sin(rad))
        if (rgb != null) {
            fit = rgb
            low = mid
        } else {
            high = mid
        }
    }
    return argbOf(fit)
}

private fun linearOf(l: Double, a: Double, b: Double): DoubleArray? {
    val fy = (l + 16) / 116
    val x = WHITE_X * finv(fy + a / 500)
    val y = finv(fy)
    val z = WHITE_Z * finv(fy - b / 200)
    val rgb = doubleArrayOf(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
    )
    return rgb.takeIf { channels -> channels.all { it in -GAMUT_SLACK..1 + GAMUT_SLACK } }
}

private fun argbOf(linear: DoubleArray): Int {
    fun channel(c: Double): Int {
        val v = c.coerceIn(0.0, 1.0)
        val srgb = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
        return (srgb * 255 + 0.5).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(linear[0]) shl 16) or (channel(linear[1]) shl 8) or channel(linear[2])
}

internal fun labOf(argb: Int): DoubleArray {
    fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    val r = linear(argb shr 16 and 0xFF)
    val g = linear(argb shr 8 and 0xFF)
    val b = linear(argb and 0xFF)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z
    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

private fun hueOf(a: Double, b: Double): Double = (Math.toDegrees(atan2(b, a)) + 360) % 360

private fun f(t: Double): Double = if (t > EPSILON) cbrt(t) else t / (3 * DELTA * DELTA) + 4.0 / 29

private fun finv(t: Double): Double = if (t > DELTA) t * t * t else 3 * DELTA * DELTA * (t - 4.0 / 29)

private const val DELTA = 6.0 / 29
private const val EPSILON = DELTA * DELTA * DELTA
private const val WHITE_X = 0.95047
private const val WHITE_Z = 1.08883
private const val GAMUT_STEPS = 16
private const val GAMUT_SLACK = 1e-4
private const val HUE_BINS = 24
private const val NEUTRAL_CHROMA = 8.0
private const val MIN_COLOURFUL = 0.05
private const val SHARE_WEIGHT = 70.0
private const val CHROMA_WEIGHT = 30.0
private const val CHROMA_CAP = 60.0
private const val MIN_PRIMARY_CHROMA = 36.0
private const val MAX_PRIMARY_CHROMA = 72.0
private const val ERROR_HUE = 30.0
private const val ERROR_CHROMA = 70.0
