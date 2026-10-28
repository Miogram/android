package app.miogram.feature.media

import app.miogram.data.DialogsStore
import app.miogram.data.telegram.ProfileRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.music.MusicPlayer
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val player: MusicPlayer,
    private val dialogs: DialogsStore,
    private val profile: ProfileRepository,
) : WorkViewModel() {
    private val sheetState = MutableStateFlow(false)
    private val savedState = MutableStateFlow<Long?>(null)
    private val messages = MutableSharedFlow<MusicMessage>(extraBufferCapacity = 1)

    val music: StateFlow<MusicState?> = player.music

    val sheetOpen: StateFlow<Boolean> = sheetState.asStateFlow()

    fun play(dialogKey: String, message: MessageItem) {
        val media = message.media?.takeIf { it.kind == MediaKind.Music } ?: return
        player.play(MusicTrack(dialogKey, message.id, media))
    }

    fun toggle() = player.toggle()

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun toggleShuffle() = player.toggleShuffle()

    fun cycleRepeat() = player.cycleRepeat()

    fun setOrder(order: MusicOrder) = player.setOrder(order)

    fun move(from: Int, to: Int) = player.move(from, to)

    fun playNext(index: Int) = player.playNext(index)

    fun remove(index: Int) = player.remove(index)

    fun playAt(index: Int) = player.playAt(index)

    fun cycleSpeed() = player.cycleSpeed()

    fun sleep(minutes: Int?) = player.sleep(minutes)

    fun sleepAtEnd() = player.sleepAtEnd()

    val savedToProfile: StateFlow<Long?> = savedState.asStateFlow()

    val openMessage: SharedFlow<MusicMessage> = messages.asSharedFlow()

    fun goToMessage() {
        val track = player.music.value?.track?.takeUnless { it.saved } ?: return
        val dialog = dialogs.state.value.let { it.dialogs + it.archived }.firstOrNull { it.key == track.dialogKey }
        sheetState.value = false
        messages.tryEmit(MusicMessage(track.dialogKey, dialog?.title ?: track.senderName.orEmpty(), track.messageId, dialog?.isSaved == true))
    }

    fun saveToProfile() {
        val track = player.music.value?.track?.takeUnless { it.isVoice } ?: return
        work {
            profile.saveMusic(track.media.key)
            savedState.value = System.currentTimeMillis()
        }
    }

    fun dismissSaved() {
        savedState.value = null
    }

    fun close() {
        sheetState.value = false
        player.close()
    }

    fun openSheet() {
        if (player.music.value != null) sheetState.value = true
    }

    fun closeSheet() {
        sheetState.value = false
    }
}

data class MusicMessage(val dialogKey: String, val title: String, val messageId: Int, val saved: Boolean)
