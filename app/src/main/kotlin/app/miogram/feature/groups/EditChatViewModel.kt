package app.miogram.feature.groups

import android.net.Uri
import androidx.lifecycle.viewModelScope
import app.miogram.data.Attachments
import app.miogram.data.AvatarStore
import app.miogram.data.telegram.GroupsRepository
import app.miogram.data.telegram.ProfileRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.chatTitleOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditChatState(
    val loaded: Boolean = false,
    val channel: Boolean = false,
    val name: String = "",
    val about: String = "",
    val photo: ByteArray? = null,
    val changed: Boolean = false,
)

@HiltViewModel(assistedFactory = EditChatViewModel.Factory::class)
class EditChatViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val groups: GroupsRepository,
    private val profiles: ProfileRepository,
    private val attachments: Attachments,
    private val avatarStore: AvatarStore,
) : WorkViewModel() {
    private val state = MutableStateFlow(EditChatState())
    private val savedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var name = ""
    private var about = ""
    private var avatarKey: String? = null

    private var picked: ByteArray? = null
    private var removed = false

    val edit: StateFlow<EditChatState> = state.asStateFlow()

    val saved: SharedFlow<Unit> = savedFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            profiles.stored(dialogKey)?.let(::adopt)
            refresh(state.value.loaded) { adopt(profiles.profile(dialogKey)) }
        }
        viewModelScope.launch {
            avatarStore.avatars.collect { photos ->
                val own = avatarKey?.let { photos[it] }
                if (picked == null && !removed) state.update { it.copy(photo = own) }
            }
        }
    }

    private fun adopt(profile: ProfileDetails) {
        name = profile.name
        about = profile.about.orEmpty()
        avatarKey = profile.avatarKey
        avatarStore.load(listOf(profile.avatarKey))
        state.update { now ->
            if (now.loaded && now.changed) {
                now.copy(changed = changed(now.name, now.about))
            } else {
                EditChatState(true, profile.kind == PeerKind.Channel, name, about, avatarKey?.let { avatarStore.avatars.value[it] })
            }
        }
    }

    fun setName(text: String) = state.update { it.copy(name = text, changed = changed(text, it.about)) }

    fun setAbout(text: String) = state.update { it.copy(about = text, changed = changed(it.name, text)) }

    fun pickPhoto(uri: Uri) {
        work {
            val photo = attachments.squareJpeg(uri)
            picked = photo
            removed = false
            state.update { it.copy(photo = photo, changed = true) }
        }
    }

    fun removePhoto() {
        picked = null
        removed = true
        state.update { it.copy(photo = null, changed = true) }
    }

    private fun changed(newName: String, newAbout: String) =
        chatTitleOf(newName) != name || newAbout.trim() != about || picked != null || removed

    fun save() {
        val now = state.value
        work {
            if (chatTitleOf(now.name) != name) {
                groups.setTitle(dialogKey, now.name)
                name = chatTitleOf(now.name)
            }
            if (now.about.trim() != about) {
                groups.setAbout(dialogKey, now.about)
                about = now.about.trim()
            }
            val photo = picked
            if (photo != null || removed) {
                groups.setPhoto(dialogKey, photo)
                picked = null
                removed = false
            }
            state.update { it.copy(changed = false) }
            savedFlow.tryEmit(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): EditChatViewModel
    }
}
