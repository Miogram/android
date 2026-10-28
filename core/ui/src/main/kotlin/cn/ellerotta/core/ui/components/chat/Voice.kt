package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.ui.R
import java.util.Locale

fun voiceTime(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

internal fun speedText(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else String.format(Locale.ROOT, "%.1f", speed)

@Composable
internal fun VoiceSpeedButton(speed: Float, onSpeed: () -> Unit, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val text = speedText(speed)
    val label = stringResource(R.string.music_speed_change, text)
    TextButton(
        onClick = onSpeed,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Text(stringResource(R.string.music_speed, text), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun VoiceBlock(media: MediaItem, playback: VoicePlayback?, enabled: Boolean, onToggle: (MediaItem) -> Unit, onSpeed: () -> Unit = {}, unread: Boolean = false) {
    val colors = bubbleColors()
    val playing = playback?.playing == true
    val loading = playback?.loading == true
    val total = playback?.durationMs?.takeIf { it > 0 } ?: (media.duration * 1000L)
    val progress = if (playback != null && total > 0) (playback.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    Row(
        Modifier.padding(bottom = 4.dp).testTag("voice_${media.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilledIconToggleButton(
            checked = playing,
            onCheckedChange = { onToggle(media) },
            enabled = enabled && !loading,
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                checkedContainerColor = colors.accent,
                checkedContentColor = colors.onAccent,
            ),
            modifier = Modifier.size(VOICE_BUTTON).testTag("voiceToggle_${media.key}"),
        ) {
            if (loading) {
                LoadingIndicator(Modifier.size(32.dp), color = LocalContentColor.current)
            } else {
                Icon(
                    painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    stringResource(if (playing) R.string.chat_voice_pause else R.string.chat_voice_play),
                )
            }
        }
        Column(Modifier.weight(1f, fill = false)) {
            VoiceWaveform(media.waveform, progress, colors.accent, colors.track, Modifier.width(150.dp).height(28.dp).testTag("voiceWave"))
            val time = if (playback != null && !loading) stringResource(R.string.chat_voice_progress, voiceTime(playback.positionMs), voiceTime(total)) else voiceTime(total)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = colors.secondary, modifier = Modifier.testTag("voiceTime"))
                if (unread && playback == null) {
                    val label = stringResource(R.string.chat_voice_unlistened)
                    Box(Modifier.size(UNREAD_DOT).background(colors.accent, CircleShape).semantics { contentDescription = label }.testTag("voiceUnread_${media.key}"))
                }
            }
        }
        if (playback != null && !loading) VoiceSpeedButton(playback.speed, onSpeed, Modifier.testTag("voiceSpeed_${media.key}"), colors.accent)
    }
}

private val VOICE_BUTTON = 48.dp

private val UNREAD_DOT = 6.dp

@Composable
fun VoiceWaveform(samples: List<Int>, progress: Float, played: Color, rest: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bar = 2.5.dp.toPx()
        val gap = 1.5.dp.toPx()
        val lowest = 2.dp.toPx()
        val count = ((size.width + gap) / (bar + gap)).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val value = if (samples.isEmpty()) {
                0
            } else {
                val from = i * samples.size / count
                samples.subList(from, maxOf(from + 1, (i + 1) * samples.size / count)).max()
            }
            val height = lowest + (size.height - lowest) * value.coerceIn(0, 31) / 31f
            drawRoundRect(
                color = if ((i + 0.5f) / count <= progress) played else rest,
                topLeft = Offset(i * (bar + gap), (size.height - height) / 2),
                size = Size(bar, height),
                cornerRadius = CornerRadius(bar / 2),
            )
        }
    }
}
