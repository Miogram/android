package app.miogram.feature.profile

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.SafetyRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BlockPickerViewModel @Inject constructor(
    store: DialogsStore,
    avatarStore: AvatarStore,
    private val safety: SafetyRepository,
) : WorkViewModel() {
    val dialogs: StateFlow<List<DialogItem>> = store.decorated
        .map { state ->
            state.dialogs.filter { (it.kind == PeerKind.User || it.kind == PeerKind.Bot) && !it.isSaved }
                .sortedWith(compareByDescending<DialogItem> { it.pinned }.thenByDescending { it.date })
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    private val doneFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val done: SharedFlow<Unit> = doneFlow.asSharedFlow()

    fun block(key: String) = work {
        safety.block(key)
        doneFlow.tryEmit(Unit)
    }
}
