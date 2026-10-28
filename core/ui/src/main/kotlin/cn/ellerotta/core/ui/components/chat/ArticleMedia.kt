package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleCaption
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ArticleVisualBlock(block: ArticleBlock, media: MediaItem?, spoiler: Boolean, caption: ArticleCaption, scope: ArticleScope) {
    Column(Modifier.fillMaxWidth()) {
        if (media != null) ArticleVisual(block, media, spoiler, scope, Modifier.fillMaxWidth().fitted(ratioOf(media), mediaCap()))
        ArticleCaptionView(caption, scope)
    }
}

@Composable
internal fun ArticleCollage(block: ArticleBlock.Collage, scope: ArticleScope) {
    val items = remember(block) { block.items.mapNotNull(::visualOf) }
    val columns = when (items.size) {
        1 -> 1
        2, 4 -> 2
        else -> 3
    }
    Column(Modifier.fillMaxWidth().testTag("collage"), verticalArrangement = Arrangement.spacedBy(COLLAGE_GAP)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(COLLAGE_GAP)) {
                row.forEach { item -> ArticleVisual(item.block, item.media, item.spoiler, scope, Modifier.weight(1f).aspectRatio(1f), corner = 4.dp) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        ArticleCaptionView(block.caption, scope)
    }
}

@Composable
internal fun ArticleSlideshow(block: ArticleBlock.Slideshow, scope: ArticleScope) {
    val items = remember(block) { block.items.mapNotNull(::visualOf) }
    Column(Modifier.fillMaxWidth()) {
        if (items.isNotEmpty()) {
            val pager = rememberPagerState { items.size }
            Box(Modifier.fillMaxWidth().fitted(ratioOf(items.first().media), mediaCap())) {
                HorizontalPager(pager, Modifier.fillMaxSize().testTag("slideshow"), pageSpacing = COLLAGE_GAP) { page ->
                    val item = items[page]
                    ArticleVisual(item.block, item.media, item.spoiler, scope, Modifier.fillMaxSize())
                }
                MediaLabel(stringResource(R.string.viewer_position, pager.currentPage + 1, items.size), Modifier.align(Alignment.TopEnd), tag = "slideshowPosition")
            }
        }
        ArticleCaptionView(block.caption, scope)
    }
}

@Composable
internal fun ArticleAudio(block: ArticleBlock.Audio, scope: ArticleScope) {
    Column(Modifier.testTag("articleAudio")) {
        block.media?.let { media ->
            VoiceBlock(media, scope.host.playback?.takeIf { it.key == media.key }, enabled = scope.host.enabled, onToggle = scope.host.onToggleVoice, onSpeed = scope.host.onVoiceSpeed)
            Text(media.fileName, style = MaterialTheme.typography.bodySmall, color = bubbleColors().secondary)
        }
        ArticleCaptionView(block.caption, scope)
    }
}

@Composable
internal fun ArticleDocument(block: ArticleBlock.Document, scope: ArticleScope) {
    Column(Modifier.testTag("articleDocument")) {
        block.media?.let { media -> MediaBlock(media, scope.host.downloads, scope.host.onDownload) }
        ArticleCaptionView(block.caption, scope)
    }
}

@Composable
internal fun ArticleMap(block: ArticleBlock.Map, scope: ArticleScope) {
    val uri = LocalUriHandler.current
    val color = LocalContentColor.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.08f))
                .clickable { runCatching { uri.openUri("geo:${block.latitude},${block.longitude}?z=${block.zoom}") } }
                .padding(10.dp)
                .testTag("map"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = bubbleColors().accent, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_location_on), null, tint = bubbleColors().onAccent)
                }
            }
            Column {
                Text(stringResource(R.string.chat_article_map), style = MaterialTheme.typography.titleSmall)
                Text(String.format(Locale.ROOT, "%.5f, %.5f", block.latitude, block.longitude), style = MaterialTheme.typography.bodySmall, color = bubbleColors().secondary)
            }
        }
        ArticleCaptionView(block.caption, scope)
    }
}

@Composable
internal fun ArticleCaptionView(caption: ArticleCaption, scope: ArticleScope) {
    if (caption.isEmpty) return
    Column(Modifier.padding(top = 4.dp).testTag("caption")) {
        ArticleParagraph(caption.text, scope, MaterialTheme.typography.bodyMedium)
        ArticleParagraph(caption.credit, scope, MaterialTheme.typography.labelMedium.copy(color = bubbleColors().secondary))
    }
}

@Composable
private fun ArticleVisual(block: ArticleBlock, media: MediaItem, spoiler: Boolean, scope: ArticleScope, modifier: Modifier, corner: Dp = 12.dp) {
    val host = scope.host
    val side = with(LocalDensity.current) { MEDIA_SIDE.roundToPx() }
    val preview = remember(media, side) { media.preview(side) }
    val state = preview?.let { host.downloads[it.key] }
    LaunchedEffect(preview?.key) { if (preview != null && host.downloads[preview.key] == null) host.onDownload(preview) }
    var hidden by remember(media.key, spoiler) { mutableStateOf(spoiler) }
    val index = scope.mediaIndex(block)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = host.enabled) { if (hidden) hidden = false else index?.let(host.onOpenMedia) }
            .testTag("articleMedia_$index"),
        contentAlignment = Alignment.Center,
    ) {
        StillImage(media, state, modifier = Modifier.fillMaxSize().then(if (hidden) Modifier.blur(SPOILER_BLUR) else Modifier))
        when {
            hidden -> Surface(shape = CircleShape, color = Color.Black.copy(alpha = MEDIA_SCRIM)) {
                Icon(
                    painterResource(R.drawable.ic_visibility),
                    stringResource(R.string.chat_article_spoiler),
                    Modifier.padding(12.dp).size(28.dp).testTag("mediaSpoiler"),
                    tint = Color.White,
                )
            }
            media.kind == MediaKind.Video -> {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = MEDIA_SCRIM)) {
                    Icon(painterResource(R.drawable.ic_play_arrow), stringResource(R.string.chat_play_video), Modifier.padding(12.dp).size(32.dp), tint = Color.White)
                }
                if (media.duration > 0) MediaLabel(voiceTime(media.duration * 1000L), Modifier.align(Alignment.TopStart), tag = "videoDuration")
            }
        }
    }
}

private class Visual(val block: ArticleBlock, val media: MediaItem, val spoiler: Boolean)

private fun visualOf(block: ArticleBlock): Visual? = when (block) {
    is ArticleBlock.Photo -> block.media?.let { Visual(block, it, block.spoiler) }
    is ArticleBlock.Video -> block.media?.let { Visual(block, it, block.spoiler) }
    else -> null
}

private fun ratioOf(media: MediaItem): Float =
    if (media.width > 0 && media.height > 0) (media.width.toFloat() / media.height).coerceAtLeast(MIN_RATIO) else 1f

@Composable
private fun mediaCap(): Dp {
    val configuration = LocalConfiguration.current
    return (maxOf(configuration.screenWidthDp, configuration.screenHeightDp) * MEDIA_HEIGHT_SHARE).dp
}

private fun Modifier.fitted(ratio: Float, cap: Dp): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else MEDIA_SIDE.roundToPx()
    val height = minOf((width / ratio).roundToInt(), cap.roundToPx()).coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

private const val MEDIA_HEIGHT_SHARE = 0.55f
private const val MIN_RATIO = 0.2f
private val MEDIA_SIDE = 320.dp
private val COLLAGE_GAP = 2.dp

internal val SPOILER_BLUR = 24.dp
