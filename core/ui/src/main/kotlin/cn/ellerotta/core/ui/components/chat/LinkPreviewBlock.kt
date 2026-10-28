package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.LinkPreview
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.theme.MioShapes

@Composable
internal fun LinkPreviewBlock(
    link: LinkPreview,
    accent: Color,
    downloads: Map<String, DownloadState>,
    onDownload: (cn.ellerotta.core.model.MediaItem) -> Unit,
    onOpen: () -> Unit = {},
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp).widthIn(max = 280.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onOpen).testTag("link_${link.url}"),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().clip(MioShapes.accentBar).background(accent))
            Column(Modifier.weight(1f, fill = false).padding(horizontal = 10.dp, vertical = 8.dp)) {
                link.siteName?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                link.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                link.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            }
            link.photo?.let { photo ->
                val side = with(LocalDensity.current) { THUMB.roundToPx() }
                val preview = remember(photo, side) { photo.preview(side) }
                LaunchedEffect(preview?.key) { if (preview != null && downloads[preview.key] == null) onDownload(preview) }
                Box(Modifier.padding(8.dp).size(THUMB).clip(MaterialTheme.shapes.small).align(Alignment.Top).testTag("linkPhoto")) {
                    StillImage(photo, preview?.let { downloads[it.key] })
                }
            }
        }
    }
}

private val THUMB = 64.dp
