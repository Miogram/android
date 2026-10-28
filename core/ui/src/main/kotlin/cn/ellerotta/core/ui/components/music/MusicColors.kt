package cn.ellerotta.core.ui.components.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cn.ellerotta.core.ui.theme.Lch
import cn.ellerotta.core.ui.theme.artworkScheme
import cn.ellerotta.core.ui.theme.artworkSeed

@Composable
internal fun rememberArtworkScheme(cover: ByteArray?): ColorScheme {
    val base = MaterialTheme.colorScheme
    val dark = base.surface.luminance() < 0.5f
    val seed = remember(cover) { cover?.let(::coverSeed) }
    val target = remember(seed, dark, base) { seed?.let { artworkScheme(it, dark) } ?: base }
    return animated(target)
}

internal fun coverSeed(bytes: ByteArray): Lch? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= SEED_SIZE && bounds.outHeight / (sample * 2) >= SEED_SIZE) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val small = Bitmap.createScaledBitmap(decoded, SEED_SIZE, SEED_SIZE, true)
    val pixels = IntArray(SEED_SIZE * SEED_SIZE)
    small.getPixels(pixels, 0, SEED_SIZE, 0, 0, SEED_SIZE, SEED_SIZE)
    if (small !== decoded) small.recycle()
    decoded.recycle()
    return artworkSeed(pixels)
}

@Composable
private fun animated(target: ColorScheme): ColorScheme {
    return target.copy(
        primary = target.primary.animate(),
        onPrimary = target.onPrimary.animate(),
        primaryContainer = target.primaryContainer.animate(),
        onPrimaryContainer = target.onPrimaryContainer.animate(),
        secondary = target.secondary.animate(),
        onSecondary = target.onSecondary.animate(),
        secondaryContainer = target.secondaryContainer.animate(),
        onSecondaryContainer = target.onSecondaryContainer.animate(),
        tertiaryContainer = target.tertiaryContainer.animate(),
        onTertiaryContainer = target.onTertiaryContainer.animate(),
        surface = target.surface.animate(),
        onSurface = target.onSurface.animate(),
        onSurfaceVariant = target.onSurfaceVariant.animate(),
        surfaceContainerLow = target.surfaceContainerLow.animate(),
        surfaceContainer = target.surfaceContainer.animate(),
        surfaceContainerHigh = target.surfaceContainerHigh.animate(),
        surfaceContainerHighest = target.surfaceContainerHighest.animate(),
        outline = target.outline.animate(),
        outlineVariant = target.outlineVariant.animate(),
    )
}

@Composable
private fun Color.animate(): Color = animateColorAsState(this, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "artworkColor").value

private const val SEED_SIZE = 48
