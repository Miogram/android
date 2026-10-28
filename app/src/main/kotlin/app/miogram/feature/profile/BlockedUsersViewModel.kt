package app.miogram.feature.profile

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.SafetyRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.BlockedList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val safety: SafetyRepository,
    private val avatarStore: AvatarStore,
    updates: UpdatesRepository,
) : WorkViewModel() {
    private val state = MutableStateFlow(safety.storedBlocked() ?: BlockedList())
    private var paging = false

    val blocked: StateFlow<BlockedList> = state.asStateFlow()
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    init {
        if (state.value.loaded) avatarStore.load(state.value.users.map { it.avatarKey })
        refresh(state.value.loaded) { reload() }
        viewModelScope.launch {
            merge(safety.changes, updates.events().filter { it is ChatEvent.Blocked })
                .collect { runCatching { reload() } }
        }
    }

    private suspend fun reload() {
        val page = safety.blocked(0)
        avatarStore.load(page.users.map { it.avatarKey })
        state.value = page
    }

    fun more() {
        val current = state.value
        if (paging || !current.loaded || !current.more) return
        paging = true
        work {
            try {
                val page = safety.blocked(current.read)
                avatarStore.load(page.users.map { it.avatarKey })
                state.update { it.plus(page) }
            } finally {
                paging = false
            }
        }
    }

    fun unblock(key: String) {
        state.update { it.without(key) }
        work {
            try {
                safety.unblock(key)
            } catch (e: Exception) {
                runCatching { reload() }
                throw e
            }
        }
    }
}
