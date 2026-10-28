package cn.ellerotta.core.lottie

import java.nio.ByteBuffer

internal object LottieNative {
    init {
        val path = System.getProperty("ktlottie.library")
        if (path != null) System.load(path) else System.loadLibrary("ktlottie")
    }

    @JvmStatic
    external fun create(data: ByteArray, fitz: Int, layers: Array<String>, colors: IntArray): Long

    @JvmStatic
    external fun width(handle: Long): Int

    @JvmStatic
    external fun height(handle: Long): Int

    @JvmStatic
    external fun frameRate(handle: Long): Float

    @JvmStatic
    external fun frameCount(handle: Long): Int

    @JvmStatic
    external fun render(handle: Long, frame: Float, width: Int, height: Int, buffer: ByteBuffer): Boolean

    @JvmStatic
    external fun destroy(handle: Long)
}
