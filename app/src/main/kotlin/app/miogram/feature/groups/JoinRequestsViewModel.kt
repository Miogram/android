package app.miogram.feature.groups

import app.miogram.data.AvatarStore
import app.miogram.data.telegram.InvitesRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.JoinRequest
import cn.ellerotta.core.model.JoinRequestsPage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel(assistedFactory = JoinRequestsViewModel.Factory::class)
class JoinRequestsViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val invites: InvitesRepository,
    private val avatarStore: AvatarStore,
) : WorkViewModel() {
    private val state = MutableStateFlow(invites.storedRequests(dialogKey)?.let { stateOf(it) } ?: JoinRequestsState())

    val requests: StateFlow<JoinRequestsState> = state.asStateFlow()
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    init {
        if (state.value.loaded) {
            avatarStore.load(state.value.requests.map { it.avatarKey })
            refresh(true) {
                val page = invites.requests(dialogKey)
                avatarStore.load(page.requests.map { it.avatarKey })
                state.update { if (it.loading) it else stateOf(page) }
            }
        } else {
            more()
        }
    }

    private fun stateOf(page: JoinRequestsPage) =
        JoinRequestsState(page.requests, page.total, more = page.requests.isNotEmpty() && page.requests.size < page.total, loaded = true)

    fun more() {
        val current = state.value
        if (current.loading || (current.loaded && !current.more)) return
        state.update { it.copy(loading = true) }
        work {
            try {
                val page = invites.requests(dialogKey, after = current.requests.lastOrNull())
                avatarStore.load(page.requests.map { it.avatarKey })
                state.update {
                    val all = (it.requests + page.requests).distinctBy { request -> request.userKey }
                    JoinRequestsState(all, page.total, more = page.requests.isNotEmpty() && all.size < page.total, loaded = true)
                }
            } finally {
                state.update { it.copy(loading = false) }
            }
        }
    }

    fun decide(request: JoinRequest, approve: Boolean) {
        state.update { it.copy(requests = it.requests - request, total = (it.total - 1).coerceAtLeast(0)) }
        work { invites.decide(dialogKey, request.userKey, approve) }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): JoinRequestsViewModel
    }
}

data class JoinRequestsState(
    val requests: List<JoinRequest> = emptyList(),
    val total: Int = 0,
    val more: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
)
