package app.miogram.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import cn.ellerotta.core.model.CropRect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

const val PHOTO_SIDE = 2560

private const val PHOTO_QUALITY = 87
private val STILL_TYPES = setOf("image/jpeg", "image/png", "image/webp")

internal fun uploadPhoto(resolver: ContentResolver, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0 || bounds.outMimeType !in STILL_TYPES) return null
    val orientation = runCatching {
        resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val long = max(width, height)
    val upright = orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED
    if (bounds.outMimeType == "image/jpeg" && long <= PHOTO_SIDE && upright) return null
    var sample = 1
    while (long / (sample * 2) >= PHOTO_SIDE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    var bitmap = decoded
    if (long > PHOTO_SIDE) {
        val ratio = PHOTO_SIDE.toDouble() / long
        val scaled = Bitmap.createScaledBitmap(bitmap, (width * ratio).roundToInt().coerceIn(1, PHOTO_SIDE), (height * ratio).roundToInt().coerceIn(1, PHOTO_SIDE), true)
        if (scaled !== bitmap) bitmap.recycle()
        bitmap = scaled
    }
    val turned = oriented(bitmap, orientation)
    if (turned !== bitmap) bitmap.recycle()
    bitmap = turned
    if (bitmap.hasAlpha()) {
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        bitmap.recycle()
        bitmap = flat
    }
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }.toByteArray().also { bitmap.recycle() }
}

internal fun cropSquare(photo: ByteArray, rect: CropRect): ByteArray? {
    val bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.size) ?: return null
    val side = rect.side.coerceAtMost(minOf(bitmap.width, bitmap.height))
    val cropped = Bitmap.createBitmap(bitmap, rect.left.coerceIn(0, bitmap.width - side), rect.top.coerceIn(0, bitmap.height - side), side, side)
    if (cropped !== bitmap) bitmap.recycle()
    return ByteArrayOutputStream().also { cropped.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }.toByteArray().also { cropped.recycle() }
}
