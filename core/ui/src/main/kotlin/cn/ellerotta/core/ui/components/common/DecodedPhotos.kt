package cn.ellerotta.core.ui.components.common

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        bitmap.prepareToDraw()
        cache.put(bytes, bitmap)
        return bitmap
    }

    fun cached(bytes: ByteArray): ImageBitmap? = cache.get(bytes)

    fun clear() = cache.evictAll()
}

@Composable
fun rememberDecodedPhoto(bytes: ByteArray?): ImageBitmap? {
    val decoded by produceState(bytes?.let(DecodedPhotos::cached), bytes) {
        value = bytes?.let { DecodedPhotos.cached(it) ?: withContext(Dispatchers.Default) { DecodedPhotos.get(it) } }
    }
    return if (bytes == null) null else DecodedPhotos.cached(bytes) ?: decoded
}
