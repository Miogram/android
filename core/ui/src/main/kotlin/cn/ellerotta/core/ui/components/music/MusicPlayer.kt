package cn.ellerotta.core.ui.components.music

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.VoiceSpeedButton
import cn.ellerotta.core.ui.components.common.rememberDecodedPhoto
import cn.ellerotta.core.ui.theme.MioShapes
import cn.ellerotta.core.ui.theme.crossfade

data class MusicActions(
    val onToggle: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onPlayAt: (Int) -> Unit,
    val onClose: () -> Unit,
    val onSpeed: () -> Unit = {},
    val onOrder: (MusicOrder) -> Unit = {},
    val onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onPlayNext: (Int) -> Unit = {},
    val onRemove: (Int) -> Unit = {},
    val onSleep: (Int?) -> Unit = {},
    val onSleepAtEnd: () -> Unit = {},
    val onGoToMessage: () -> Unit = {},
    val onSaveToProfile: () -> Unit = {},
)

@Composable
fun MusicCover(bytes: ByteArray?, modifier: Modifier = Modifier, voice: Boolean = false, shape: Shape = MaterialTheme.shapes.medium) {
    val bitmap = rememberDecodedPhoto(bytes)
    Box(modifier.clip(if (voice) CircleShape else shape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(painterResource(if (voice) R.drawable.ic_mic else R.drawable.ic_music_note), null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
internal fun MusicTrack.heading(): String = if (isVoice) senderName ?: stringResource(R.string.music_voice) else title

@Composable
internal fun MusicTrack.subheading(): String =
    if (isVoice) stringResource(R.string.music_voice) else performer ?: stringResource(R.string.music_unknown_performer)

@Composable
fun MiniPlayer(state: MusicState, onOpen: () -> Unit, actions: MusicActions, modifier: Modifier = Modifier) {
    val track = state.track
    val anchor = LocalMusicCoverAnchor.current
    DisposableEffect(anchor) { onDispose { anchor?.bounds = null } }
    val swipe = remember { SkipSwipe() }
    val scheme = MaterialTheme.colorScheme
    val openLabel = stringResource(R.string.music_open)
    Surface(color = scheme.surfaceContainer, shape = MioShapes.previewBar, modifier = modifier.fillMaxWidth().testTag("miniPlayer")) {
        Row(
            Modifier
                .skipSwipe(swipe, actions)
                .clickable(onClickLabel = openLabel, onClick = onOpen)
                .graphicsLayer {
                    translationX = swipe.offset
                    alpha = 1f - swipe.share * MINI_SWIPE_FADE
                }
                .padding(start = MINI_INSET, end = 4.dp, top = MINI_INSET - (MINI_RING - MINI_COVER) / 2, bottom = MINI_INSET - (MINI_RING - MINI_COVER) / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicCover(state.cover, Modifier.size(MINI_COVER).coverAnchor(anchor), voice = track.isVoice)
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                Text(track.heading(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("miniTitle"))
                Text(
                    track.subheading(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("miniPerformer"),
                )
            }
            if (track.hasSpeed) VoiceSpeedButton(state.speed, actions.onSpeed, Modifier.testTag("miniSpeed"))
            Box(Modifier.size(MINI_RING), contentAlignment = Alignment.Center) {
                val length = state.lengthMs
                val position = state.positionMs
                CircularWavyProgressIndicator(
                    progress = { if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f },
                    color = scheme.primary,
                    trackColor = scheme.secondaryContainer,
                    amplitude = if (state.playing) RING_WAVING else RING_FLAT,
                    modifier = Modifier.size(MINI_RING).testTag("miniProgress"),
                )
                IconButton(onClick = actions.onToggle, modifier = Modifier.size(MINI_TOGGLE).testTag("miniToggle"), shapes = IconButtonDefaults.shapes()) {
                    PlayIcon(state.playing)
                }
            }
            IconButton(onClick = actions.onClose, modifier = Modifier.testTag("miniClose"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.music_close))
            }
        }
    }
}

@Composable
internal fun PlayIcon(playing: Boolean, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(playing, transitionSpec = { motion.crossfade() }, label = "playIcon") { shown ->
        Icon(
            painterResource(if (shown) R.drawable.ic_pause else R.drawable.ic_play_arrow),
            stringResource(if (shown) R.string.music_pause else R.string.music_play),
            modifier,
        )
    }
}

private val RING_WAVING = WavyProgressIndicatorDefaults.indicatorAmplitude
private val RING_FLAT: (Float) -> Float = { 0f }
private val MINI_INSET = 8.dp
private val MINI_COVER = 40.dp
private val MINI_RING = 48.dp
private val MINI_TOGGLE = 36.dp
private const val MINI_SWIPE_FADE = 0.6f
