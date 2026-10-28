package app.miogram.feature.folders

import androidx.lifecycle.viewModelScope
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.DialogsRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.FolderLimits
import cn.ellerotta.core.model.SuggestedFolder
import cn.ellerotta.core.ui.components.folders.FoldersState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val store: DialogsStore,
    private val repository: DialogsRepository,
) : WorkViewModel() {
    private val suggestedState = MutableStateFlow(repository.storedSuggestedFolders().orEmpty())
    private val loadingState = MutableStateFlow(repository.storedSuggestedFolders() == null)
    private val limitsState = MutableStateFlow(FolderLimits())

    val state: StateFlow<FoldersState> = combine(store.state.map { it.folders }, suggestedState, loadingState, limitsState) { folders, suggested, loading, limits ->
        val named = folders.mapTo(HashSet()) { it.title }
        FoldersState(folders, suggested.filter { it.folder.title !in named }, loading, limits)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FoldersState(store.state.value.folders, suggestedState.value, suggestedLoading = loadingState.value))

    init {
        viewModelScope.launch {
            try {
                limitsState.value = repository.folderLimits()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        viewModelScope.launch {
            try {
                suggestedState.value = repository.suggestedFolders()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            } finally {
                loadingState.value = false
            }
        }
    }

    fun reorder(ids: List<Int>) = work { store.reorderFolders(ids) }

    fun add(suggestion: SuggestedFolder) = work {
        store.saveFolder(suggestion.folder.copy(id = ChatFolder.NEW))
        suggestedState.update { list -> list - suggestion }
    }
}
