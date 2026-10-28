package app.miogram.feature.groups

import app.miogram.data.telegram.InvitesRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.InviteLinks
import cn.ellerotta.core.model.NewInviteLink
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel(assistedFactory = InviteLinksViewModel.Factory::class)
class InviteLinksViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val invites: InvitesRepository,
) : WorkViewModel() {
    private val linksState = MutableStateFlow(invites.storedLinks(dialogKey))

    val links: StateFlow<InviteLinks?> = linksState.asStateFlow()

    private val pendingState = MutableStateFlow(invites.storedPending(dialogKey) ?: 0)

    val pending: StateFlow<Int> = pendingState.asStateFlow()

    init {
        refresh(linksState.value != null) { load() }
    }

    fun refreshPending() = refresh(true) {
        pendingState.value = invites.requests(dialogKey, limit = 1).total
    }

    private suspend fun load() {
        linksState.value = invites.links(dialogKey)
    }

    fun create(link: NewInviteLink) = work {
        invites.create(dialogKey, link)
        load()
    }

    fun revoke(link: String) = work {
        invites.revoke(dialogKey, link)
        load()
    }

    fun delete(link: String) = work {
        invites.delete(dialogKey, link)
        load()
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): InviteLinksViewModel
    }
}
