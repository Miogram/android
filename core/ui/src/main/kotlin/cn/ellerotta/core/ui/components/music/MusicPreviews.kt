package cn.ellerotta.core.ui.components.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.ui.components.chats.ChatsPreviewData
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.music.MusicPreviewData as Data

@Composable
private fun WithCovers(vararg states: MusicState, content: @Composable () -> Unit) {
    remember { states.forEach { state -> state.cover?.let(CoverSeeds::read) } }
    content()
}

@MioPreviews
@Composable
internal fun MiniPlayerSongPreview() = WithCovers(Data.playing, Data.pausedNoCover) {
    PreviewTheme {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniPlayer(Data.playing, onOpen = {}, actions = Data.actions)
            MiniPlayer(Data.pausedNoCover, onOpen = {}, actions = Data.actions)
        }
    }
}

@MioPreviews
@Composable
internal fun MiniPlayerVoicePreview() = WithCovers(Data.voice, Data.longTalk) {
    PreviewTheme {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniPlayer(Data.voice, onOpen = {}, actions = Data.actions)
            MiniPlayer(Data.voicePausedNoPhoto, onOpen = {}, actions = Data.actions)
            MiniPlayer(Data.longTalk, onOpen = {}, actions = Data.actions)
        }
    }
}

@MioPreviews
@Composable
internal fun MiniPlayerOverTheChatsPreview() = WithCovers(Data.playing) {
    PreviewTheme {
        ChatsScreen(
            tabs = ChatsPreviewData.tabs,
            archived = emptyList(),
            avatars = ChatsPreviewData.avatars,
            me = ChatsPreviewData.me,
            status = ChatsStatus.Ready,
            refreshing = false,
            onOpenChat = {},
            onOpenArchive = {},
            onRefresh = {},
            onAction = { _, _ -> },
            player = { MiniPlayer(Data.playing, onOpen = {}, actions = Data.actions) },
            now = ChatsPreviewData.NOW,
        )
    }
}

@Composable
private fun PlayerPreview(state: MusicState, lyrics: Boolean = false) = WithCovers(state) {
    PreviewTheme {
        MaterialTheme(colorScheme = rememberArtworkScheme(state.cover)) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().height(780.dp)) {
                MusicPlayerContent(state, Data.actions, onCollapse = {}, showLyrics = lyrics)
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
internal fun MusicPlayerLyricsPreview() = PlayerPreview(Data.longTalk, lyrics = true)

@MioPreviews
@Composable
internal fun MusicQueuePreview() = WithCovers(Data.playing) {
    PreviewTheme {
        MaterialTheme(colorScheme = rememberArtworkScheme(Data.playing.cover)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                MusicQueueContent(Data.playing, Data.actions)
            }
        }
    }
}
