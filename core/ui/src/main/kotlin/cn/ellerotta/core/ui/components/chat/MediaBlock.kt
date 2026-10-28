package cn.ellerotta.core.ui.components.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.ui.R

@Composable
internal fun MediaBlock(media: MediaItem, downloads: Map<String, DownloadState>, onDownload: (MediaItem) -> Unit, enabled: Boolean = true) {
    val state = downloads[media.key]
    val colors = bubbleColors()
    Column(Modifier.padding(bottom = 6.dp)) {
        val name = stringResource(if (media.isPhoto) R.string.chat_photo else R.string.chat_file, media.fileName)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                Surface(color = colors.accent, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(if (media.isPhoto) R.drawable.ic_image else R.drawable.ic_description), null, tint = colors.onAccent)
                    }
                }
            }
            Text(media.size?.let { stringResource(R.string.chat_media_size, name, it / 1024, media.dcId) } ?: name, style = MaterialTheme.typography.bodySmall)
        }
        when (state) {
            null -> if (enabled) FilledTonalButton(
                onClick = { onDownload(media) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.padding(top = 6.dp).testTag("download_${media.key}"),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp))
                Text(stringResource(R.string.chat_download), Modifier.padding(start = 6.dp))
            }
            is DownloadState.Progress -> {
                val total = state.total
                if (total != null && total > 0) {
                    LinearWavyProgressIndicator(progress = { state.downloaded.toFloat() / total }, color = colors.accent, trackColor = colors.track, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                } else {
                    LinearWavyProgressIndicator(color = colors.accent, trackColor = colors.track, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
                Text(stringResource(R.string.chat_kb, state.downloaded / 1024), style = MaterialTheme.typography.labelSmall)
            }
            is DownloadState.Done -> {
                if (media.isPhoto) {
                    val bitmap = remember(state.file) { BitmapFactory.decodeFile(state.file.path)?.asImageBitmap() }
                    if (bitmap != null) Image(bitmap, media.fileName, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(MaterialTheme.shapes.medium))
                }
                Text(stringResource(R.string.chat_saved_file, state.file.name, state.file.length()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("downloaded_${media.key}"))
            }
        }
    }
}
