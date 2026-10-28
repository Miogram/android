package cn.ellerotta.core.ui.components.common

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.atomic.AtomicInteger

object DecodedPhotos {
    private const val MAX_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<ByteArray, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: ByteArray, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private val decodes = AtomicInteger()

    val decodeCount: Int get() = decodes.get()

    fun get(bytes: ByteArray): ImageBitmap? {
        cache.get(bytes)?.let { return it }
        decodes.incrementAndGet()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: return null
        cache.put(bytes, bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()
}
