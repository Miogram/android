package app.miogram.feature.profile

import app.miogram.data.telegram.ProfileRepository
import app.miogram.music.MusicPlayer
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.model.ProfileDetails
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class ProfileMusic(private val profiles: ProfileRepository, private val player: MusicPlayer) {
    val music: StateFlow<MusicState?> = player.music
    private val openedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val opened: SharedFlow<Unit> = openedFlow.asSharedFlow()

    suspend fun play(details: ProfileDetails) {
        val shown = details.music ?: return
        val now = player.music.value?.track
        if (now == null || !now.saved || now.dialogKey != details.key) {
            val songs = profiles.music(details.key).ifEmpty { listOf(shown) }
            val tracks = songs.map { MusicTrack(details.key, 0, it, saved = true) }
            player.playList(tracks, tracks.indexOfFirst { it.media.key == shown.key }.coerceAtLeast(0))
        }
        openedFlow.tryEmit(Unit)
    }
}
