package cn.ellerotta.core.ui.components.music

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import cn.ellerotta.core.ui.components.common.DecodedPhotos
import cn.ellerotta.core.ui.theme.Lch
import cn.ellerotta.core.ui.theme.artworkScheme
import cn.ellerotta.core.ui.theme.artworkSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberArtworkScheme(cover: ByteArray?): ColorScheme {
    val base = MaterialTheme.colorScheme
    val dark = base.surface.luminance() < 0.5f
    val seed = rememberCoverSeed(cover)
    val target = remember(seed, dark, base) { seed?.let { artworkScheme(it, dark) } ?: base }
    return animated(target)
}

@Composable
internal fun rememberCoverSeed(cover: ByteArray?): Lch? {
    val read by produceState(cover?.let(CoverSeeds::cached), cover) {
        value = cover?.let { CoverSeeds.cached(it) ?: withContext(Dispatchers.Default) { CoverSeeds.read(it) } }
    }
    return if (cover == null) null else CoverSeeds.cached(cover) ?: read
}

internal object CoverSeeds {
    private val seeds = LruCache<ByteArray, Lch>(SEEDS)

    fun cached(bytes: ByteArray): Lch? = seeds.get(bytes)

    fun read(bytes: ByteArray): Lch? = seeds.get(bytes) ?: DecodedPhotos.get(bytes)?.let(::seedOf)?.also { seeds.put(bytes, it) }
}

private fun seedOf(image: ImageBitmap): Lch? {
    val bitmap = image.asAndroidBitmap()
    val small = Bitmap.createScaledBitmap(bitmap, SEED_SIZE, SEED_SIZE, true)
    val pixels = IntArray(SEED_SIZE * SEED_SIZE)
    small.getPixels(pixels, 0, SEED_SIZE, 0, 0, SEED_SIZE, SEED_SIZE)
    if (small !== bitmap) small.recycle()
    return artworkSeed(pixels)
}

@Composable
private fun animated(target: ColorScheme): ColorScheme {
    val primary = target.primary.animate()
    val onPrimary = target.onPrimary.animate()
    val primaryContainer = target.primaryContainer.animate()
    val onPrimaryContainer = target.onPrimaryContainer.animate()
    val secondary = target.secondary.animate()
    val onSecondary = target.onSecondary.animate()
    val secondaryContainer = target.secondaryContainer.animate()
    val onSecondaryContainer = target.onSecondaryContainer.animate()
    val tertiaryContainer = target.tertiaryContainer.animate()
    val onTertiaryContainer = target.onTertiaryContainer.animate()
    val surface = target.surface.animate()
    val onSurface = target.onSurface.animate()
    val onSurfaceVariant = target.onSurfaceVariant.animate()
    val surfaceContainerLow = target.surfaceContainerLow.animate()
    val surfaceContainer = target.surfaceContainer.animate()
    val surfaceContainerHigh = target.surfaceContainerHigh.animate()
    val surfaceContainerHighest = target.surfaceContainerHighest.animate()
    val outline = target.outline.animate()
    val outlineVariant = target.outlineVariant.animate()
    return remember(
        target, primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, onSecondary, secondaryContainer, onSecondaryContainer,
        tertiaryContainer, onTertiaryContainer, surface, onSurface, onSurfaceVariant, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest, outline, outlineVariant,
    ) {
        target.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            surface = surface,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant,
        )
    }
}

@Composable
private fun Color.animate(): Color = animateColorAsState(this, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "artworkColor").value

private const val SEED_SIZE = 48
private const val SEEDS = 32
