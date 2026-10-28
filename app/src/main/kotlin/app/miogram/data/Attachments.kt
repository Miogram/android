package app.miogram.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import cn.ellerotta.ktproto.client.UploadSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

class Attachment(val name: String, val mimeType: String, val source: UploadSource, private val onClose: () -> Unit) : Closeable {
    override fun close() = onClose()
}

data class PickedFile(val name: String, val mimeType: String, val size: Long)

data class MediaFileInfo(val width: Int = 0, val height: Int = 0, val duration: Double = 0.0, val title: String? = null, val performer: String? = null)

@Singleton
class Attachments @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun openUpload(uri: Uri, asFile: Boolean = false): Attachment = withContext(Dispatchers.IO) {
        val file = open(uri)
        if (asFile) return@withContext file
        val jpeg = try {
            uploadPhoto(context.contentResolver, uri)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return@withContext file
        file.close()
        Attachment(file.name.substringBeforeLast('.') + ".jpg", "image/jpeg", UploadSource.of(jpeg)) {}
    }

    fun photoBytes(uri: Uri): ByteArray? = try {
        uploadPhoto(context.contentResolver, uri) ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    fun captureFile(): File = File(context.cacheDir, "avatar_capture/capture.jpg")

    fun captureUri(): Uri {
        val file = captureFile()
        file.parentFile?.mkdirs()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun describe(uri: Uri): PickedFile {
        val resolver = context.contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.let { name = it }
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        return PickedFile(name, resolver.getType(uri) ?: "application/octet-stream", size)
    }

    fun open(uri: Uri): Attachment {
        val resolver = context.contentResolver
        val (name, type, known) = describe(uri)
        var size = known
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw IOException("cannot open $uri")
        if (size < 0) size = descriptor.statSize
        val channel = FileInputStream(descriptor.fileDescriptor).channel
        val total = size
        val source = object : UploadSource {
            override val size: Long = total

            override suspend fun read(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
                val buffer = ByteBuffer.allocate(length)
                var position = offset
                while (buffer.hasRemaining()) {
                    val read = channel.read(buffer, position)
                    if (read < 0) throw IOException("$name ended before its size")
                    position += read
                }
                buffer.array()
            }
        }
        return Attachment(name, type, source) {
            channel.close()
            descriptor.close()
        }
    }

    fun mediaInfo(uri: Uri): MediaFileInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            fun number(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
            val width = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val turned = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
            MediaFileInfo(
                width = if (turned) height else width,
                height = if (turned) width else height,
                duration = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000.0,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                performer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            )
        } catch (_: Exception) {
            MediaFileInfo()
        } finally {
            retriever.release()
        }
    }

    fun mimeType(uri: Uri): String = context.contentResolver.getType(uri) ?: "application/octet-stream"

    fun preview(uri: Uri, video: Boolean, side: Int = PREVIEW_SIZE): ByteArray? {
        val bitmap = try {
            if (video) frame(uri) else photo(uri, side)
        } catch (_: Exception) {
            null
        } ?: return null
        val scale = side.toFloat() / maxOf(bitmap.width, bitmap.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        return ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, PREVIEW_QUALITY, it) }.toByteArray()
    }

    fun squarePhoto(uri: Uri, side: Int = SQUARE_SIZE): ByteArray? {
        val bitmap = try {
            photo(uri, side)
        } catch (_: Exception) {
            null
        } ?: return null
        val edge = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - edge) / 2, (bitmap.height - edge) / 2, edge, edge)
        val scaled = if (edge > side) Bitmap.createScaledBitmap(square, side, side, true) else square
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, SQUARE_QUALITY, it) }.toByteArray()
    }

    private fun frame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0)
        } finally {
            retriever.release()
        }
    }

    private fun photo(uri: Uri, side: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return oriented(bitmap, orientation)
    }

    private companion object {
        const val PREVIEW_SIZE = 1280
        const val PREVIEW_QUALITY = 85
        const val SQUARE_SIZE = 640
        const val SQUARE_QUALITY = 90
    }
}

internal fun oriented(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { setRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
