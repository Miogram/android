package app.miogram.feature.groups

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.telegram.ChannelsRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ChatInfo
import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.UiError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = MembersViewModel.Factory::class)
class MembersViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val channels: ChannelsRepository,
    private val avatarStore: AvatarStore,
) : WorkViewModel() {
    private val state = MutableStateFlow(channels.storedMembers(dialogKey)?.let { stateOf("", it.members, it.total) } ?: MembersState())
    private val infoState = MutableStateFlow(channels.storedInfo(dialogKey))
    private val query = MutableStateFlow("")

    val members: StateFlow<MembersState> = state.asStateFlow()

    val info: StateFlow<ChatInfo?> = infoState.asStateFlow()
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    init {
        refresh(infoState.value != null) { infoState.value = channels.info(dialogKey) }
        if (state.value.loaded) avatarStore.load(state.value.members.map { it.avatarKey }) else more()
        viewModelScope.launch(Dispatchers.Default) {
            query.drop(1).debounce(PAUSE).collectLatest { text ->
                try {
                    val page = channels.members(dialogKey, text, limit = PAGE)
                    avatarStore.load(page.members.map { it.avatarKey })
                    state.update { if (it.query == text) stateOf(text, page.members, page.total) else it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.update { if (it.query == text) it.copy(loading = false, loaded = true) else it }
                    fail(UiError.Message(e.message ?: "cannot search members"))
                }
            }
        }
    }

    fun search(text: String) {
        if (text == query.value) return
        query.value = text
        state.value = MembersState(query = text, loading = true)
    }

    fun reload() {
        val current = state.value
        if (!current.loaded) return
        refresh(true) {
            val page = channels.members(dialogKey, current.query, limit = PAGE)
            avatarStore.load(page.members.map { it.avatarKey })
            state.update { if (it.query == current.query && !it.loading) stateOf(it.query, page.members, page.total) else it }
        }
    }

    fun more() {
        val current = state.value
        if (current.loading || (current.loaded && !current.more)) return
        state.update { it.copy(loading = true) }
        work {
            try {
                val page = channels.members(dialogKey, current.query, offset = current.members.size, limit = PAGE)
                avatarStore.load(page.members.map { it.avatarKey })
                state.update {
                    if (it.query != current.query) return@update it
                    stateOf(it.query, (it.members + page.members).distinctBy { member -> member.userKey }, page.total, fresh = page.members)
                }
            } finally {
                state.update { if (it.query == current.query) it.copy(loading = false) else it }
            }
        }
    }

    fun remove(member: ChatMember, ban: Boolean) {
        state.update { it.copy(members = it.members - member, total = (it.total - 1).coerceAtLeast(0)) }
        work { channels.removeMember(dialogKey, member.userKey, ban) }
    }

    private fun stateOf(query: String, members: List<ChatMember>, total: Int, fresh: List<ChatMember> = members) =
        MembersState(query, members, total, more = fresh.isNotEmpty() && members.size < total, loaded = true)

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): MembersViewModel
    }

    private companion object {
        const val PAGE = 50
        const val PAUSE = 400L
    }
}

data class MembersState(
    val query: String = "",
    val members: List<ChatMember> = emptyList(),
    val total: Int = 0,
    val more: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
)
