package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.music.MusicCover

@Composable
internal fun MusicBlock(media: MediaItem, cover: ByteArray?, music: MusicState?, enabled: Boolean, onPlay: () -> Unit) {
    val colors = bubbleColors()
    val covered = cover != null || media.thumb != null
    val track = MusicTrack("", 0, media)
    val playing = music?.playing == true
    Row(
        Modifier.padding(bottom = 4.dp).testTag("music_${media.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            MusicCover(cover ?: media.thumb, Modifier.size(PLAY_SIZE))
            val container = if (covered) Color.Black.copy(alpha = MEDIA_SCRIM) else colors.accent
            val content = if (covered) Color.White else colors.onAccent
            FilledIconToggleButton(
                checked = playing,
                onCheckedChange = { onPlay() },
                enabled = enabled,
                shapes = IconButtonDefaults.toggleableShapes(),
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = container,
                    contentColor = content,
                    checkedContainerColor = container,
                    checkedContentColor = content,
                ),
                modifier = Modifier.size(PLAY_SIZE).testTag("musicToggle_${media.key}"),
            ) {
                if (playing && music?.buffering == true) {
                    LoadingIndicator(Modifier.size(32.dp), color = LocalContentColor.current)
                } else {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        stringResource(if (playing) R.string.music_pause else R.string.music_play),
                    )
                }
            }
        }
        Column(Modifier.widthIn(max = 200.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("musicTitle"))
            Text(
                track.performer ?: stringResource(R.string.music_unknown_performer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val length = music?.lengthMs ?: (media.duration * 1000L)
            val time = if (music != null) stringResource(R.string.chat_voice_progress, voiceTime(music.positionMs), voiceTime(length)) else voiceTime(length)
            Text(time, style = MaterialTheme.typography.labelSmall, color = colors.secondary, modifier = Modifier.testTag("musicTime"))
        }
    }
}

private val PLAY_SIZE = 56.dp
