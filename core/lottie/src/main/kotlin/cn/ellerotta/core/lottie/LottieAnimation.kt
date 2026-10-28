package cn.ellerotta.core.lottie

import java.nio.ByteBuffer
import java.nio.ByteOrder

class LottieAnimation private constructor(private var handle: Long) : AutoCloseable {
    val width: Int = LottieNative.width(handle)
    val height: Int = LottieNative.height(handle)
    val frameRate: Float = LottieNative.frameRate(handle).takeIf { it > 0 } ?: 60f

    val frameCount: Int = LottieNative.frameCount(handle)

    val duration: Float get() = frameCount / frameRate

    private var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)

    val pixels: ByteBuffer get() = buffer

    fun render(frame: Float, width: Int, height: Int): Boolean {
        if (handle == 0L || width <= 0 || height <= 0) return false
        val bytes = width.toLong() * height * 4
        if (bytes > Int.MAX_VALUE) return false
        if (buffer.capacity().toLong() != bytes) buffer = ByteBuffer.allocateDirect(bytes.toInt()).order(ByteOrder.nativeOrder())
        buffer.clear()
        return LottieNative.render(handle, frame, width, height, buffer)
    }

    override fun close() {
        if (handle != 0L) LottieNative.destroy(handle)
        handle = 0
    }

    companion object {
        fun load(data: ByteArray, layerColors: Map<String, Int> = emptyMap(), fitz: Int = 0): LottieAnimation? {
            val handle = LottieNative.create(data, fitz, layerColors.keys.toTypedArray(), layerColors.values.toIntArray())
            return if (handle == 0L) null else LottieAnimation(handle)
        }
    }
}
