package cn.ellerotta.core.ui.components.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.cover
import cn.ellerotta.core.ui.R

@Composable
internal fun SongOrderSheet(songs: List<MediaItem>, covers: Map<String, ByteArray>, onMove: (from: Int, to: Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        modifier = Modifier.testTag("songOrderSheet"),
    ) {
        SongOrderContent(songs, covers, onMove)
    }
}

@Composable
internal fun SongOrderContent(songs: List<MediaItem>, covers: Map<String, ByteArray>, onMove: (from: Int, to: Int) -> Unit) {
    val motion = MaterialTheme.motionScheme
    Column(Modifier.navigationBarsPadding()) {
        Text(
            stringResource(R.string.profile_songs_order),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).semantics { heading() },
        )
        LazyColumn(Modifier.heightIn(max = ORDER_LIST_MAX).testTag("songOrder")) {
            itemsIndexed(songs, key = { _, song -> song.key }) { index, song ->
                val title = song.title?.takeIf { it.isNotBlank() } ?: song.fileName.substringBeforeLast('.').ifEmpty { stringResource(R.string.music_unknown_title) }
                val performer = song.performer?.takeIf { it.isNotBlank() } ?: stringResource(R.string.music_unknown_performer)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ORDER_ROW)
                        .padding(start = 16.dp, end = 4.dp)
                        .animateItem(fadeInSpec = motion.fastEffectsSpec(), placementSpec = motion.fastSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec())
                        .testTag("songOrder_${song.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val bytes = song.cover()?.let { covers[it.key] } ?: song.thumb
                    val image = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                    Box(Modifier.size(ORDER_COVER).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        if (image != null) {
                            Image(image, null, Modifier.size(ORDER_COVER), contentScale = ContentScale.Crop)
                        } else {
                            Icon(painterResource(R.drawable.ic_music_note), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(performer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0, modifier = Modifier.testTag("songUp_${song.key}"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.chat_sets_move_up, title))
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < songs.lastIndex, modifier = Modifier.testTag("songDown_${song.key}"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.chat_sets_move_down, title))
                    }
                }
            }
        }
    }
}

private val ORDER_LIST_MAX = 480.dp
private val ORDER_ROW = 64.dp
private val ORDER_COVER = 44.dp
