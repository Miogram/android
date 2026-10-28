package cn.ellerotta.core.ui.components.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.LoopingVideo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun bubbleRatio(media: MediaItem): Float = if (media.width > 0 && media.height > 0) (media.width.toFloat() / media.height).coerceIn(0.5f, 2f) else 1f

@Composable
internal fun MediaTile(
    media: MediaItem,
    downloads: Map<String, DownloadState>,
    onDownload: (MediaItem) -> Unit,
    onOpen: () -> Unit,
    side: Dp,
    modifier: Modifier,
    pressSource: MutableInteractionSource? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val px = with(LocalDensity.current) { side.roundToPx() }
    val preview = remember(media, px) { media.preview(px) }
    val state = preview?.let { downloads[it.key] }
    LaunchedEffect(preview?.key) { if (preview != null && downloads[preview.key] == null) onDownload(preview) }
    var hidden by rememberSaveable(media.key, media.spoiler) { mutableStateOf(media.spoiler) }
    val gif = (downloads[media.key] as? DownloadState.Done)?.file?.takeIf { media.kind == MediaKind.Gif && !hidden }
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(interactionSource = pressSource, indication = LocalIndication.current, onLongClick = onLongPress) {
                when {
                    hidden -> hidden = false
                    media.kind == MediaKind.Photo && state == null && preview != null -> onDownload(preview)
                    else -> onOpen()
                }
            }
            .testTag("photo_${media.key}"),
        contentAlignment = Alignment.Center,
    ) {
        StillImage(media, state, modifier = Modifier.fillMaxSize().then(if (hidden) Modifier.blur(SPOILER_BLUR) else Modifier))
        if (gif != null) LoopingVideo(gif, Modifier.fillMaxSize().testTag("gif_${media.key}"))
        when {
            hidden -> Surface(shape = CircleShape, color = Color.Black.copy(alpha = MEDIA_SCRIM)) {
                Icon(
                    painterResource(R.drawable.ic_visibility),
                    stringResource(R.string.chat_article_spoiler),
                    Modifier.padding(12.dp).size(28.dp).testTag("spoiler_${media.key}"),
                    tint = Color.White,
                )
            }
            media.kind == MediaKind.Video -> {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = MEDIA_SCRIM)) {
                    Icon(painterResource(R.drawable.ic_play_arrow), stringResource(R.string.chat_play_video), Modifier.padding(12.dp).size(32.dp), tint = Color.White)
                }
                MediaLabel(voiceTime(media.duration * 1000L), Modifier.align(Alignment.TopStart), tag = "videoDuration")
            }
            media.kind == MediaKind.Gif -> MediaLabel(stringResource(R.string.chat_gif), Modifier.align(Alignment.TopStart))
            else -> LoadState(state, preview)
        }
    }
}

@Composable
internal fun StillImage(media: MediaItem, state: DownloadState?, contentScale: ContentScale = ContentScale.Crop, modifier: Modifier = Modifier.fillMaxSize()) {
    val file = (state as? DownloadState.Done)?.file
    val full by produceState<ImageBitmap?>(null, file) { value = file?.let { withContext(Dispatchers.IO) { decodeSampled(it, PHOTO_SIDE) } } }
    val thumb = remember(media.key) { media.thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Crossfade(full, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "photo") { image ->
        when {
            image != null -> Image(image, null, contentScale = contentScale, modifier = modifier.testTag("photoFull"))
            thumb != null -> Image(thumb, null, contentScale = contentScale, modifier = modifier.blur(14.dp).testTag("photoThumb"))
            else -> Box(modifier)
        }
    }
}

@Composable
private fun LoadState(state: DownloadState?, preview: MediaItem?) {
    when (state) {
        is DownloadState.Progress -> {
            val total = state.total
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = MEDIA_SCRIM)) {
                if (total != null && total > 0) {
                    CircularWavyProgressIndicator(progress = { state.downloaded.toFloat() / total }, modifier = Modifier.padding(6.dp).size(40.dp), color = Color.White)
                } else {
                    CircularWavyProgressIndicator(Modifier.padding(6.dp).size(40.dp), color = Color.White)
                }
            }
        }
        null -> if (preview != null) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = MEDIA_SCRIM)) {
                Icon(painterResource(R.drawable.ic_download), stringResource(R.string.chat_download), Modifier.padding(12.dp).size(28.dp), tint = Color.White)
            }
        }
        is DownloadState.Done -> Unit
    }
}

@Composable
internal fun MediaLabel(text: String, modifier: Modifier, tag: String = "mediaLabel") {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = PILL_ALPHA), contentColor = Color.White, modifier = modifier.padding(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).testTag(tag))
    }
}

internal fun decodeSampled(file: File, side: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

internal val BUBBLE_WIDTH = 260.dp
internal const val PHOTO_SIDE = 2048
