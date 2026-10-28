package cn.ellerotta.core.ui.components.chat

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem as PlayerItem

internal val ROUND_SIZE = 240.dp
internal val ROUND_PLAYING_SIZE = 320.dp

@Stable
internal class RoundVideoState {
    var playing by mutableStateOf(false)

    fun toggle() {
        playing = !playing
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun RoundVideo(media: MediaItem, downloads: Map<String, DownloadState>, onDownload: (MediaItem) -> Unit, state: RoundVideoState, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { ROUND_PLAYING_SIZE.roundToPx() }
    val preview = remember(media, px) { media.preview(px) }
    val previewState = preview?.let { downloads[it.key] }
    LaunchedEffect(preview?.key) { if (preview != null && downloads[preview.key] == null) onDownload(preview) }
    val download = downloads[media.key]
    val file = (download as? DownloadState.Done)?.file
    LaunchedEffect(state.playing, download == null) { if (state.playing && download == null) onDownload(media) }
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    LaunchedEffect(state.playing, file) {
        if (!state.playing) {
            player?.pause()
            return@LaunchedEffect
        }
        val source = file ?: return@LaunchedEffect
        val current = player ?: ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setMediaSource(ProgressiveMediaSource.Factory(FileDataSource.Factory()).createMediaSource(PlayerItem.fromUri(Uri.fromFile(source))))
            prepare()
        }.also { player = it }
        current.play()
        while (true) {
            val total = current.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            progress = total?.let { (current.currentPosition.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
            val ended = current.playbackState == Player.STATE_ENDED
            if (ended || !current.playWhenReady) {
                if (ended) {
                    current.pause()
                    current.seekTo(0)
                    progress = 0f
                }
                state.playing = false
                break
            }
            delay(PROGRESS_TICK_MS)
        }
    }
    val grow by animateFloatAsState(if (state.playing) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "roundSize")
    val shown by animateFloatAsState(progress, tween(PROGRESS_SMOOTH_MS, easing = LinearEasing), label = "roundProgress")
    val size = ROUND_SIZE + (ROUND_PLAYING_SIZE - ROUND_SIZE) * grow
    val label = stringResource(R.string.media_round)
    val playState = stringResource(if (state.playing) R.string.chat_voice_pause else R.string.chat_voice_play)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black)
            .semantics {
                contentDescription = label
                stateDescription = playState
            }
            .testTag("round_${media.key}"),
        contentAlignment = Alignment.Center,
    ) {
        StillImage(media, previewState)
        val playing = player
        if (playing != null && file != null) PlayerSurface(playing, Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW)
        val stroke = with(LocalDensity.current) { Stroke(RING_WIDTH.toPx(), cap = StrokeCap.Round) }
        val ring = Modifier.fillMaxSize().padding(RING_INSET).testTag("roundRing_${media.key}")
        val track = Color.Black.copy(alpha = MEDIA_SCRIM)
        when {
            download is DownloadState.Progress && state.playing -> {
                val total = download.total
                if (total != null && total > 0) {
                    CircularWavyProgressIndicator(progress = { download.downloaded.toFloat() / total }, modifier = ring, color = Color.White, trackColor = track, stroke = stroke, trackStroke = stroke)
                } else {
                    CircularWavyProgressIndicator(ring, color = Color.White, trackColor = track, stroke = stroke, trackStroke = stroke)
                }
            }
            state.playing || progress > 0f -> CircularWavyProgressIndicator(progress = { shown }, modifier = ring, color = Color.White, trackColor = track, stroke = stroke, trackStroke = stroke)
            else -> Surface(shape = CircleShape, color = track) {
                Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.padding(12.dp).size(32.dp), tint = Color.White)
            }
        }
    }
}

private val RING_WIDTH = 3.dp
private val RING_INSET = 4.dp
private const val PROGRESS_TICK_MS = 100L
private const val PROGRESS_SMOOTH_MS = 300
