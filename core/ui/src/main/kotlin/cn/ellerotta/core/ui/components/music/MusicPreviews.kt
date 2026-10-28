package cn.ellerotta.core.ui.components.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.music.MusicPreviewData as Data

@MioPreviews
@Composable
internal fun MiniPlayerSongPreview() {
    PreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniPlayer(Data.playing, onOpen = {}, actions = Data.actions)
            MiniPlayer(Data.pausedNoCover, onOpen = {}, actions = Data.actions)
        }
    }
}

@MioPreviews
@Composable
internal fun MiniPlayerVoicePreview() {
    PreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniPlayer(Data.voice, onOpen = {}, actions = Data.actions)
            MiniPlayer(Data.voicePausedNoPhoto, onOpen = {}, actions = Data.actions)
        }
    }
}

@Composable
private fun PlayerPreview(state: MusicState) {
    PreviewTheme {
        MaterialTheme(colorScheme = rememberArtworkScheme(state.cover)) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().height(780.dp)) {
                MusicPlayerContent(state, Data.actions, onCollapse = {})
            }
        }
    }
}

@MioPreviews
@Composable
internal fun MusicPlayerSongPreview() = PlayerPreview(Data.playing)

@MioPreviews
@Composable
internal fun MusicPlayerPausedPreview() = PlayerPreview(Data.pausedNoCover)

@MioPreviews
@Composable
internal fun MusicPlayerBlueCoverPreview() = PlayerPreview(Data.bluePaused)

@MioPreviews
@Composable
internal fun MusicPlayerVoicePreview() = PlayerPreview(Data.voice)

@MioPreviews
@Composable
internal fun MusicPlayerLongTalkPreview() = PlayerPreview(Data.longTalk)

@MioPreviews
@Composable
internal fun MusicQueuePreview() {
    PreviewTheme {
        MaterialTheme(colorScheme = rememberArtworkScheme(Data.playing.cover)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                MusicQueueContent(Data.playing, Data.actions)
            }
        }
    }
}
