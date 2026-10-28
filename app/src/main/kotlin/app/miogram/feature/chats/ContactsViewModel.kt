package app.miogram.feature.chats

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.ContactsRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.sortedByPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactsRepository,
    updates: UpdatesRepository,
    private val avatarStore: AvatarStore,
) : WorkViewModel() {
    private val listState = MutableStateFlow<List<ContactItem>>(emptyList())
    private val presenceState = MutableStateFlow<Map<String, Presence>>(emptyMap())
    private val loadingState = MutableStateFlow(false)
    private val queryState = MutableStateFlow("")
    private val notFoundState = MutableStateFlow(false)
    private val addedState = MutableStateFlow(0)

    private var answered = false

    val contacts: StateFlow<List<ContactItem>> = combine(listState, presenceState) { list, presence ->
        list.map { contact -> presence[contact.key]?.let { contact.copy(presence = it) } ?: contact }.sortedByPresence()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loading: StateFlow<Boolean> = loadingState.asStateFlow()

    val globalResults: StateFlow<List<ContactItem>> = queryState
        .debounce(SEARCH_PAUSE)
        .mapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.length < 2) emptyList() else runCatching { repository.search(trimmed) }.getOrDefault(emptyList())
        }
        .onEach { found -> avatarStore.load(found.map { it.avatarKey }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notFound: StateFlow<Boolean> = notFoundState.asStateFlow()

    val added: StateFlow<Int> = addedState.asStateFlow()

    init {
        viewModelScope.launch {
            show(repository.stored())
            refresh()
        }
        viewModelScope.launch {
            updates.events().filterIsInstance<ChatEvent.Status>().collect { event ->
                if (listState.value.any { it.key == event.key }) presenceState.update { it + (event.key to event.presence) }
            }
        }
    }

    fun search(query: String) {
        queryState.value = query
    }

    fun refresh() {
        refresh(answered || listState.value.isNotEmpty()) { load() }
    }

    fun add(phone: String, firstName: String, lastName: String) = work {
        notFoundState.value = false
        if (repository.import(phone, firstName, lastName) == null) {
            notFoundState.value = true
        } else {
            show(repository.stored())
            addedState.update { it + 1 }
        }
    }

    fun clearNotFound() {
        notFoundState.value = false
    }

    fun delete(contact: ContactItem) = work {
        listState.update { list -> list.filter { it.key != contact.key } }
        try {
            repository.delete(listOf(contact.key))
        } finally {
            show(repository.stored())
        }
    }

    private suspend fun load() {
        loadingState.value = !answered && listState.value.isEmpty()
        try {
            show(repository.contacts())
            answered = true
        } finally {
            loadingState.value = false
        }
    }

    private fun show(list: List<ContactItem>) {
        listState.value = list
        presenceState.value = emptyMap()
        avatarStore.load(list.map { it.avatarKey })
    }
}

private const val SEARCH_PAUSE = 400L
