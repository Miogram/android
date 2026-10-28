package app.miogram.feature.folders

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.DialogsRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderLimits
import cn.ellerotta.core.model.real
import cn.ellerotta.core.ui.components.folders.FolderEditState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = FolderEditViewModel.Factory::class)
class FolderEditViewModel @AssistedInject constructor(
    @Assisted private val folderId: Int,
    private val store: DialogsStore,
    private val repository: DialogsRepository,
    private val avatarStore: AvatarStore,
) : WorkViewModel() {
    private val draftState = MutableStateFlow(if (folderId == ChatFolder.NEW) ChatFolder(ChatFolder.NEW, "") else null)
    private val originalState = MutableStateFlow<ChatFolder?>(if (folderId == ChatFolder.NEW) ChatFolder(ChatFolder.NEW, "") else null)
    private val limitsState = MutableStateFlow(FolderLimits())
    private val doneFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val done: SharedFlow<Unit> = doneFlow.asSharedFlow()

    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val state: StateFlow<FolderEditState> = combine(draftState, originalState, store.state, limitsState) { draft, original, dialogs, limits ->
        FolderEditState(
            folder = draft,
            isNew = folderId == ChatFolder.NEW,
            chats = (dialogs.dialogs + dialogs.archived + dialogs.pulled).distinctBy { it.key },
            limits = limits,
            folderCount = dialogs.folders.real.size,
            changed = draft != original,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FolderEditState(draftState.value, isNew = folderId == ChatFolder.NEW))

    init {
        if (folderId != ChatFolder.NEW) {
            viewModelScope.launch {
                val folder = store.state.first { state -> state.folders.any { it.id == folderId } }.folders.first { it.id == folderId }
                draftState.value = folder
                originalState.value = folder
            }
        }
        viewModelScope.launch {
            try {
                limitsState.value = repository.folderLimits()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        viewModelScope.launch {
            state.collect { avatarStore.load(it.chats.map(DialogItem::avatarKey)) }
        }
    }

    fun change(folder: ChatFolder) {
        draftState.value = folder
    }

    fun loadMoreChats() = work { store.loadMore(ChatFolder.ALL) }

    fun save() {
        val folder = draftState.value ?: return
        work {
            store.saveFolder(folder.copy(title = folder.title.trim()))
            doneFlow.emit(Unit)
        }
    }

    fun delete() {
        if (folderId == ChatFolder.NEW) return
        work {
            store.deleteFolder(folderId)
            doneFlow.emit(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(folderId: Int): FolderEditViewModel
    }
}
