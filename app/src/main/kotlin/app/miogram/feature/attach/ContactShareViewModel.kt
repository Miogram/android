package app.miogram.feature.attach

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.telegram.AttachRepository
import app.miogram.data.telegram.ContactsRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.card
import cn.ellerotta.core.model.sortedByPresence
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ContactShareViewModel.Factory::class)
class ContactShareViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    @Assisted("replyTo") private val replyTo: Int?,
    @Assisted("topicId") private val topicId: Int?,
    private val contacts: ContactsRepository,
    private val avatarStore: AvatarStore,
    private val attach: AttachRepository,
) : WorkViewModel() {
    private val listState = MutableStateFlow<List<ContactItem>>(emptyList())
    private val queryState = MutableStateFlow("")
    private val loadedState = MutableStateFlow(false)
    private val sentEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val query: StateFlow<String> = queryState.asStateFlow()
    val loaded: StateFlow<Boolean> = loadedState.asStateFlow()
    val shown: StateFlow<List<ContactItem>> = combine(listState, queryState) { list, query -> list.filter { it.matches(query) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val sent: SharedFlow<Unit> = sentEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            show(contacts.stored())
            refresh(listState.value.isNotEmpty()) {
                try {
                    show(contacts.contacts())
                } finally {
                    loadedState.value = true
                }
            }
        }
    }

    fun search(query: String) {
        queryState.value = query
    }

    fun send(person: ContactItem) {
        val card = person.card() ?: return
        work {
            attach.sendContact(dialogKey, card, replyTo, topicId)
            sentEvents.emit(Unit)
        }
    }

    private fun show(list: List<ContactItem>) {
        val withNumbers = list.filter { it.card() != null }
        listState.value = withNumbers.sortedByPresence()
        avatarStore.load(withNumbers.map { it.avatarKey })
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String, @Assisted("replyTo") replyTo: Int?, @Assisted("topicId") topicId: Int?): ContactShareViewModel
    }
}
