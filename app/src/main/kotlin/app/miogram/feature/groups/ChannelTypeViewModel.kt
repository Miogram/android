package app.miogram.feature.groups

import androidx.lifecycle.viewModelScope
import app.miogram.data.telegram.GroupsRepository
import app.miogram.data.telegram.InvitesRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.UsernameAvailability
import cn.ellerotta.core.model.UsernameStatus
import cn.ellerotta.core.model.usernameProblem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = ChannelTypeViewModel.Factory::class)
class ChannelTypeViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val groups: GroupsRepository,
    private val invites: InvitesRepository,
) : WorkViewModel() {
    private val publicState = MutableStateFlow(false)
    private val usernameState = MutableStateFlow("")
    private val statusState = MutableStateFlow<UsernameStatus>(UsernameStatus.Idle)
    private val linkState = MutableStateFlow(invites.storedLinks(dialogKey)?.primary?.link)
    private val doneFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val public: StateFlow<Boolean> = publicState.asStateFlow()
    val username: StateFlow<String> = usernameState.asStateFlow()
    val status: StateFlow<UsernameStatus> = statusState.asStateFlow()

    val link: StateFlow<String?> = linkState.asStateFlow()

    val done: SharedFlow<Unit> = doneFlow.asSharedFlow()

    init {
        refresh(linkState.value != null) { linkState.value = invites.links(dialogKey).primary?.link }
        viewModelScope.launch(Dispatchers.Default) {
            usernameState.drop(1).debounce(PAUSE).collectLatest { name ->
                if (name.isEmpty() || usernameProblem(name) != null) return@collectLatest
                try {
                    val availability = groups.checkUsername(dialogKey, name)
                    if (usernameState.value == name) statusState.value = UsernameStatus.Checked(name, availability)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (usernameState.value == name) statusState.value = UsernameStatus.Idle
                    fail(UiError.Message(e.message ?: "cannot check the username"))
                }
            }
        }
    }

    fun setPublic(value: Boolean) {
        publicState.value = value
    }

    fun setUsername(text: String) {
        if (text == usernameState.value) return
        usernameState.value = text
        statusState.value = when {
            text.isEmpty() -> UsernameStatus.Idle
            else -> usernameProblem(text)?.let { UsernameStatus.Problem(it) } ?: UsernameStatus.Checking
        }
    }

    fun save() {
        val name = usernameState.value
        val checked = statusState.value
        val ok = checked is UsernameStatus.Checked && checked.username == name && checked.availability == UsernameAvailability.Available
        work {
            if (publicState.value) {
                if (!ok) return@work
                groups.setUsername(dialogKey, name)
            }
            doneFlow.tryEmit(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): ChannelTypeViewModel
    }

    private companion object {
        const val PAUSE = 400L
    }
}
