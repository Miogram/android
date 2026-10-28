package app.miogram.feature.chats

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.DialogsRepository
import app.miogram.data.telegram.StoriesRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.ui.components.chats.ChatAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val repository: DialogsRepository,
    private val store: DialogsStore,
    private val avatarStore: AvatarStore,
    private val stories: StoriesRepository,
) : WorkViewModel() {
    val dialogs: StateFlow<List<DialogItem>> = combine(store.decorated, stories.rings) { state, rings -> state.withStories(rings) }
        .map { state -> state.archived.sortedWith(compareByDescending<DialogItem> { it.pinned }.thenByDescending { it.date }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars
    val refreshing: StateFlow<Boolean> = store.state.map { it.refreshing && it.loaded }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hiddenStories: StateFlow<List<PeerStories>> = stories.hidden
        .onEach { list -> avatarStore.load(list.map { it.avatarKey }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun act(dialog: DialogItem, action: ChatAction) = act(store, repository, dialog, action)

    fun mute(dialog: DialogItem, duration: MuteDuration) = act(store, repository, dialog, ChatAction.Mute, duration.seconds)

    fun loadMore() = work { store.loadMoreArchive() }

    fun unhideStories(key: String) = work { stories.setHidden(key, false) }
}
