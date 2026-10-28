package app.miogram.feature.profile

import android.net.Uri
import androidx.lifecycle.viewModelScope
import app.miogram.data.Attachments
import app.miogram.data.telegram.WallpapersRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.NO_WALLPAPER
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperChoices
import cn.ellerotta.core.model.WallpaperEdit
import cn.ellerotta.core.model.WallpaperKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = WallpapersViewModel.Factory::class)
class WallpapersViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String?,
    private val repository: WallpapersRepository,
    private val attachments: Attachments,
) : WorkViewModel() {
    private val state = MutableStateFlow(WallpaperChoices(chosen = current()?.wallpaper?.key))
    val choices: StateFlow<WallpaperChoices> = state.asStateFlow()
    private val downloads = Semaphore(PARALLEL_PREVIEWS)
    private val requestedPreviews = HashSet<String>()
    private val editState = MutableStateFlow<WallpaperEdit?>(null)

    val editing: StateFlow<WallpaperEdit?> = editState.asStateFlow()

    val everyChat: StateFlow<ChatWallpaper?> = repository.chosen

    init {
        viewModelScope.launch {
            if (dialogKey == null) {
                repository.chosen.collect { chosen -> state.update { it.copy(chosen = chosen?.wallpaper?.key) } }
            } else {
                repository.chats.collect { chats -> state.update { it.copy(chosen = chats[dialogKey]?.wallpaper?.key) } }
            }
        }
        viewModelScope.launch {
            repository.stored()?.let(::show)
            refresh(state.value.wallpapers.isNotEmpty()) { show(repository.wallpapers()) }
        }
    }

    private fun current(): ChatWallpaper? = if (dialogKey == null) repository.chosen.value else repository.chats.value[dialogKey]

    private fun show(wallpapers: List<Wallpaper>) {
        state.update { it.copy(wallpapers = wallpapers) }
        wallpapers.filter { it.kind != WallpaperKind.Fill && it.key !in state.value.previews && requestedPreviews.add(it.key) }.forEach { wallpaper ->
            viewModelScope.launch {
                val path = downloads.withPermit { repository.preview(wallpaper.key) } ?: return@launch
                state.update { it.copy(previews = it.previews + (wallpaper.key to path)) }
            }
        }
    }

    fun pick(key: String?) = work {
        when {
            dialogKey == null -> repository.choose(key)
            key == null -> repository.resetChat(dialogKey)
            key == NO_WALLPAPER -> repository.setChatPlain(dialogKey)
            else -> repository.setChat(dialogKey, key, state.value.wallpapers.firstOrNull { it.key == key } ?: error("unknown wallpaper $key"))
        }
    }

    fun edit(key: String) {
        val applied = current()?.wallpaper?.takeIf { it.key == key }
        val wallpaper = applied ?: state.value.wallpapers.firstOrNull { it.key == key } ?: return
        editState.value = WallpaperEdit(wallpaper, state.value.previews[key])
        if (wallpaper.kind != WallpaperKind.Image) return
        viewModelScope.launch {
            val file = repository.file(key) ?: return@launch
            editState.update { edit -> edit?.takeIf { it.wallpaper.key == key }?.copy(file = file) ?: edit }
        }
    }

    fun change(wallpaper: Wallpaper) = editState.update { edit -> edit?.copy(wallpaper = wallpaper) }

    fun editPhoto(uri: Uri) = work {
        val jpeg = withContext(Dispatchers.IO) { attachments.photoBytes(uri) } ?: error("the photo cannot be read")
        editState.value = WallpaperEdit(Wallpaper(OWN_PHOTO, WallpaperKind.Image), repository.importPhoto(jpeg))
    }

    fun apply() {
        val edit = editState.value ?: return
        work {
            val photo = edit.file?.takeIf { edit.wallpaper.key == OWN_PHOTO }
            when {
                dialogKey != null && photo != null -> repository.setChatPhoto(dialogKey, photo, edit.wallpaper)
                dialogKey != null -> repository.setChat(dialogKey, edit.wallpaper.key, edit.wallpaper)
                photo != null -> repository.choosePhoto(photo, edit.wallpaper)
                else -> repository.choose(edit.wallpaper.key, edit.wallpaper)
            }
            if (photo != null) current()?.let { applied -> state.update { it.copy(previews = it.previews + (applied.wallpaper.key to (applied.file ?: photo))) } }
            editState.value = null
            if (photo != null && dialogKey == null) show(repository.wallpapers())
        }
    }

    fun closeEdit() {
        editState.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String?): WallpapersViewModel
    }

    private companion object {
        const val PARALLEL_PREVIEWS = 3

        const val OWN_PHOTO = "photo"
    }
}
