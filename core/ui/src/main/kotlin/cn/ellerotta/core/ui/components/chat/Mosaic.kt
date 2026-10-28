package cn.ellerotta.core.ui.components.chat

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

internal data class MosaicTile(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
}

internal data class Mosaic(val tiles: List<MosaicTile>, val width: Float, val height: Float) {
    fun touches(tile: MosaicTile): Edges = Edges(
        left = tile.x < EDGE_SLACK,
        top = tile.y < EDGE_SLACK,
        right = tile.right > width - EDGE_SLACK,
        bottom = tile.bottom > height - EDGE_SLACK,
    )
}

internal data class Edges(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean)

internal fun mosaic(ratios: List<Float>, maxWidth: Float, maxHeight: Float, gap: Float = MOSAIC_GAP): Mosaic {
    val items = ratios.map { if (it.isFinite() && it > 0f) it else 1f }
    val shapes = items.joinToString("") { shapeOf(it) }
    val mean = (1f + items.sum()) / items.size
    val veryWide = items.any { it > 2f }
    val tiles = when {
        items.size < 2 -> listOf(MosaicTile(0f, 0f, maxWidth, maxWidth / (items.firstOrNull() ?: 1f)))
        veryWide || items.size > 4 -> rows(items, mean, maxWidth, gap)
        items.size == 2 -> pair(items, shapes, mean, maxWidth, maxHeight, gap)
        items.size == 3 -> three(items, shapes, maxWidth, maxHeight, gap)
        else -> four(items, shapes, maxWidth, maxHeight, gap)
    }.map { it.copy(width = max(it.width, 1f), height = max(it.height, 1f)) }
    return Mosaic(tiles, round(tiles.maxOf { it.right }), round(tiles.maxOf { it.bottom }))
}

private fun shapeOf(ratio: Float) = when {
    ratio > WIDE -> "w"
    ratio < NARROW -> "n"
    else -> "q"
}

private fun pair(r: List<Float>, shapes: String, mean: Float, w: Float, h: Float, gap: Float): List<MosaicTile> {
    val (a, b) = r
    return when {
        shapes == "ww" && mean > 1.4f * (w / h) && b - a < 0.2f -> {
            val height = floor(minOf(w / a, w / b, (h - gap) / 2))
            listOf(MosaicTile(0f, 0f, w, height), MosaicTile(0f, height + gap, w, height))
        }
        shapes == "ww" || shapes == "qq" -> {
            val half = (w - gap) / 2
            val height = floor(minOf(half / a, half / b, h))
            listOf(MosaicTile(0f, 0f, half, height), MosaicTile(half + gap, 0f, half, height))
        }
        else -> {
            val second = floor(min(0.5f * (w - gap), round((w - gap) / a / (1 / a + 1 / b))))
            val first = w - second - gap
            val height = floor(min(h, round(min(first / a, second / b))))
            listOf(MosaicTile(0f, 0f, first, height), MosaicTile(first + gap, 0f, second, height))
        }
    }
}

private fun three(r: List<Float>, shapes: String, w: Float, h: Float, gap: Float): List<MosaicTile> {
    if (shapes.startsWith("n")) {
        val lower = min((h - gap) * 0.5f, round(r[1] * (w - gap) / (r[2] + r[1])))
        val upper = h - lower - gap
        val right = max(MIN_WIDTH, min((w - gap) * 0.5f, round(min(lower * r[2], upper * r[1]))))
        val left = round(min(h * r[0], w - gap - right))
        return listOf(
            MosaicTile(0f, 0f, left, h),
            MosaicTile(left + gap, 0f, right, upper),
            MosaicTile(left + gap, upper + gap, right, lower),
        )
    }
    val top = floor(min(w / r[0], (h - gap) * 0.66f))
    val half = (w - gap) / 2
    val bottom = min(h - top - gap, round(min(half / r[1], half / r[2])))
    return listOf(
        MosaicTile(0f, 0f, w, top),
        MosaicTile(0f, top + gap, half, bottom),
        MosaicTile(half + gap, top + gap, half, bottom),
    )
}

private fun four(r: List<Float>, shapes: String, w: Float, h: Float, gap: Float): List<MosaicTile> {
    if (shapes.startsWith("w")) {
        val top = round(min(w / r[0], (h - gap) * 0.66f))
        val fit = round((w - 2 * gap) / (r[1] + r[2] + r[3]))
        val first = max(MIN_WIDTH, min((w - 2 * gap) * 0.4f, fit * r[1]))
        val third = max(max(MIN_WIDTH, (w - 2 * gap) * 0.33f), fit * r[3])
        val second = w - first - third - 2 * gap
        val row = max(MIN_HEIGHT, min(h - top - gap, fit))
        val y = top + gap
        return listOf(
            MosaicTile(0f, 0f, w, top),
            MosaicTile(0f, y, first, row),
            MosaicTile(first + gap, y, second, row),
            MosaicTile(first + second + 2 * gap, y, third, row),
        )
    }
    val left = round(min(h * r[0], (w - gap) * 0.6f))
    val fit = round((h - 2 * gap) / (1 / r[1] + 1 / r[2] + 1 / r[3]))
    val first = floor(fit / r[1])
    val second = floor(fit / r[2])
    val third = h - first - second - 2 * gap
    val column = max(MIN_WIDTH, min(w - left - gap, fit))
    val x = left + gap
    return listOf(
        MosaicTile(0f, 0f, left, h),
        MosaicTile(x, 0f, column, first),
        MosaicTile(x, first + gap, column, second),
        MosaicTile(x, first + second + 2 * gap, column, third),
    )
}

private fun rows(r: List<Float>, mean: Float, w: Float, gap: Float): List<MosaicTile> {
    val cropped = r.map { (if (mean > 1.1f) max(1f, it) else min(1f, it)).coerceIn(MIN_CROP, MAX_CROP) }
    fun heightOf(from: Int, to: Int) = (w - (to - from - 1) * gap) / cropped.subList(from, to).sum()
    val n = cropped.size
    val splits = mutableListOf<List<Int>>()
    for (a in 1 until n) if (a <= 3 && n - a <= 3) splits += listOf(a, n - a)
    for (a in 1 until n - 1) for (b in 1 until n - a) {
        val c = n - a - b
        if (a <= 3 && b <= (if (mean < 0.85f) 4 else 3) && c <= 3) splits += listOf(a, b, c)
    }
    for (a in 1 until n - 2) for (b in 1 until n - a) for (c in 1 until n - a - b) {
        val d = n - a - b - c
        if (a <= 3 && b <= 3 && c <= 3 && d <= 3) splits += listOf(a, b, c, d)
    }
    val target = floor(w / 3f * 4f)
    var best: List<Int>? = null
    var bestHeights = emptyList<Float>()
    var bestScore = Float.MAX_VALUE
    for (split in splits) {
        var start = 0
        val heights = split.map { count -> heightOf(start, start + count).also { start += count } }
        val total = gap * (heights.size - 1) + heights.sumOf { floor(it).toDouble() }.toFloat()
        var score = abs(total - target)
        if (split.zipWithNext().any { (upper, lower) -> upper > lower }) score *= 1.5f
        if (gap * (heights.size - 1) + floor(heights.first()) < MIN_WIDTH) score *= 1.5f
        if (score < bestScore) {
            best = split
            bestHeights = heights
            bestScore = score
        }
    }
    val tiles = mutableListOf<MosaicTile>()
    var index = 0
    var y = 0f
    best.orEmpty().forEachIndexed { line, count ->
        val height = bestHeights[line]
        var x = 0f
        repeat(count) { k ->
            val width = if (k == count - 1) w - x else cropped[index] * height
            tiles += MosaicTile(x, y, width, height)
            x += width + gap
            index++
        }
        y += height + gap
    }
    return tiles
}

internal const val MOSAIC_GAP = 1f

private const val WIDE = 1.2f
private const val NARROW = 0.8f
private const val MIN_WIDTH = 68f
private const val MIN_HEIGHT = 81f
private const val MIN_CROP = 0.66667f
private const val MAX_CROP = 1.7f
private const val EDGE_SLACK = 1f
