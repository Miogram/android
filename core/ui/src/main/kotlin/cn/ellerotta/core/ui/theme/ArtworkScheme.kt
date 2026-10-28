package cn.ellerotta.core.ui.theme

import androidx.compose.material3.ColorScheme
import kotlin.math.hypot

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

fun artworkScheme(seed: Lch, dark: Boolean): ColorScheme = seedScheme(seed, dark, SchemeVariant.Content)

private const val HUE_BINS = 24
private const val MIN_COLOURFUL = 0.05
private const val SHARE_WEIGHT = 70.0
private const val CHROMA_WEIGHT = 30.0
private const val CHROMA_CAP = 60.0
