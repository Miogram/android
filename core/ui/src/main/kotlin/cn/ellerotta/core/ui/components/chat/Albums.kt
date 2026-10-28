package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem

internal data class Corners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp) {
    fun shape(): RoundedCornerShape = RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomEnd = bottomEnd, bottomStart = bottomStart)

    fun inner(inset: Dp, above: Boolean = false, below: Boolean = false): Corners = Corners(
        topStart = if (above) MERGED_CORNER else (topStart - inset).coerceAtLeast(0.dp),
        topEnd = if (above) MERGED_CORNER else (topEnd - inset).coerceAtLeast(0.dp),
        bottomEnd = if (below) MERGED_CORNER else (bottomEnd - inset).coerceAtLeast(0.dp),
        bottomStart = if (below) MERGED_CORNER else (bottomStart - inset).coerceAtLeast(0.dp),
    )
}

internal fun albumMosaic(items: List<MessageItem>, width: Dp): Mosaic {
    items.singleOrNull()?.media?.let { media ->
        val side = min(width, BUBBLE_WIDTH).value
        val tile = MosaicTile(0f, 0f, side, side / bubbleRatio(media))
        return Mosaic(listOf(tile), tile.width, tile.height)
    }
    val ratios = items.map { item ->
        val media = item.media
        if (media != null && media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 1f
    }
    return mosaic(ratios, width.value, width.value * MOSAIC_HEIGHT_RATIO)
}

@Composable
internal fun AlbumGrid(
    items: List<MessageItem>,
    grid: Mosaic,
    corners: Corners,
    downloads: Map<String, DownloadState>,
    onDownload: (MediaItem) -> Unit,
    onOpen: (MessageItem) -> Unit,
    modifier: Modifier = Modifier,
    pressSource: MutableInteractionSource? = null,
    onLongPress: (() -> Unit)? = null,
) {
    Box(modifier.size(grid.width.dp, grid.height.dp).testTag("album")) {
        items.forEachIndexed { index, item ->
            val media = item.media ?: return@forEachIndexed
            val tile = grid.tiles.getOrNull(index) ?: return@forEachIndexed
            val edges = grid.touches(tile)
            val shape = RoundedCornerShape(
                topStart = if (edges.top && edges.left) corners.topStart else 0.dp,
                topEnd = if (edges.top && edges.right) corners.topEnd else 0.dp,
                bottomEnd = if (edges.bottom && edges.right) corners.bottomEnd else 0.dp,
                bottomStart = if (edges.bottom && edges.left) corners.bottomStart else 0.dp,
            )
            key(item.id) {
                MediaTile(
                    media, downloads, onDownload, onOpen = { onOpen(item) },
                    side = maxOf(tile.width, tile.height).dp,
                    modifier = Modifier.offset(tile.x.dp, tile.y.dp).size(tile.width.dp, tile.height.dp).clip(shape),
                    pressSource = pressSource,
                    onLongPress = onLongPress,
                )
            }
        }
    }
}

private const val MOSAIC_HEIGHT_RATIO = 380f / 300f

internal val MEDIA_INSET = 4.dp

internal val MERGED_CORNER = 7.dp
