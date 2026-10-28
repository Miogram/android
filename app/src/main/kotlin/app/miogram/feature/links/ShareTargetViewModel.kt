package app.miogram.feature.links

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.DialogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ShareTargetViewModel @Inject constructor(store: DialogsStore, avatarStore: AvatarStore) : WorkViewModel() {
    val dialogs: StateFlow<List<DialogItem>> = store.decorated
        .map { state -> state.dialogs.sortedWith(compareByDescending<DialogItem> { it.pinned }.thenByDescending { it.date }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars
}
