package app.miogram.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import app.miogram.data.telegram.VideoStream
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.voiceTime
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(stream: () -> VideoStream, active: Boolean, looping: Boolean, modifier: Modifier = Modifier) {
    val player = rememberStreamPlayer(stream, looping)
    LaunchedEffect(active) { player.playWhenReady = active }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(TICK_MS)
        }
    }
    Box(modifier.testTag("video"), contentAlignment = Alignment.Center) {
        ContentFrame(player, Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = ContentScale.Fit)
        if (looping) return@Box
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            val seekLabel = stringResource(R.string.viewer_seek)
            val slider = remember { SliderState() }
            slider.value = seeking ?: if (duration > 0) position.toFloat() / duration else 0f
            Slider(
                state = slider,
                onValueChange = { seeking = it },
                onValueChangeFinished = {
                    seeking?.let { player.seekTo((it * duration).toLong()) }
                    seeking = null
                },
                modifier = Modifier.semantics { contentDescription = seekLabel }.testTag("videoSeek"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(52.dp).testTag("videoToggle"),
                ) {
                    val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow
                    Icon(painterResource(icon), stringResource(if (playing) R.string.chat_voice_pause else R.string.chat_voice_play))
                }
                Text(
                    stringResource(R.string.chat_voice_progress, voiceTime(position), voiceTime(duration)),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun StoryVideo(stream: () -> VideoStream, playing: Boolean, looping: Boolean, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val player = rememberStreamPlayer(stream, looping)
    LaunchedEffect(playing) { player.playWhenReady = playing }
    ContentFrame(player, modifier.testTag("storyVideo"), surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = contentScale, shutter = {})
}

@OptIn(UnstableApi::class)
@Composable
private fun rememberStreamPlayer(stream: () -> VideoStream, looping: Boolean): ExoPlayer {
    val context = LocalContext.current
    val player = remember {
        val source = stream()
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(ProgressiveMediaSource.Factory(source.factory).createMediaSource(MediaItem.fromUri(source.uri)))
            if (looping) {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
            }
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private const val TICK_MS = 250L
