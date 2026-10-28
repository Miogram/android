package cn.ellerotta.core.model

import kotlin.math.max
import kotlin.math.min

data class CropRect(val left: Int, val top: Int, val side: Int)

data class SquareCrop(
    val width: Int,
    val height: Int,
    val zoom: Float = 1f,
    val centerX: Float = width / 2f,
    val centerY: Float = height / 2f,
) {
    val side: Float get() = min(width, height) / zoom

    fun zoomed(value: Float): SquareCrop = copy(zoom = value.coerceIn(1f, MAX_ZOOM)).fitted()

    fun panned(dx: Float, dy: Float, viewSize: Float): SquareCrop {
        if (viewSize <= 0f) return this
        val scale = side / viewSize
        return copy(centerX = centerX - dx * scale, centerY = centerY - dy * scale).fitted()
    }

    private fun fitted(): SquareCrop {
        val half = side / 2f
        return copy(
            centerX = centerX.coerceIn(half, max(half, width - half)),
            centerY = centerY.coerceIn(half, max(half, height - half)),
        )
    }

    fun rect(): CropRect {
        val s = side.toInt().coerceIn(1, min(width, height))
        val left = (centerX - side / 2f).toInt().coerceIn(0, width - s)
        val top = (centerY - side / 2f).toInt().coerceIn(0, height - s)
        return CropRect(left, top, s)
    }

    companion object {
        const val MAX_ZOOM = 8f
    }
}
