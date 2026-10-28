package app.miogram.feature.chat

import androidx.lifecycle.viewModelScope
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.ScheduledRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.trimmed
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ScheduledViewModel.Factory::class)
class ScheduledViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val repository: ScheduledRepository,
    updates: UpdatesRepository,
) : WorkViewModel() {
    private val messagesState = MutableStateFlow(repository.stored(dialogKey).orEmpty())
    private val loadedState = MutableStateFlow(repository.stored(dialogKey) != null)

    val messages: StateFlow<List<MessageItem>> = messagesState.asStateFlow()

    val loaded: StateFlow<Boolean> = loadedState.asStateFlow()

    init {
        refresh(loadedState.value) { load() }
        viewModelScope.launch { messagesState.collect { if (loadedState.value) repository.remember(dialogKey, it) } }
        viewModelScope.launch {
            updates.events().collect { event ->
                val here = when (event) {
                    is ChatEvent.ScheduledNew -> event.key == dialogKey
                    is ChatEvent.ScheduledDeleted -> event.key == dialogKey
                    else -> false
                }
                if (here) runCatching { load() }
            }
        }
    }

    private suspend fun load() {
        messagesState.value = repository.list(dialogKey)
        loadedState.value = true
    }

    fun sendNow(message: MessageItem) {
        messagesState.update { list -> list.filter { it.id != message.id } }
        work { settled { repository.sendNow(dialogKey, listOf(message.id)) } }
    }

    fun reschedule(message: MessageItem, date: Int) {
        messagesState.update { list -> list.map { if (it.id == message.id) it.copy(date = date) else it }.sortedWith(compareBy({ it.date }, { it.id })) }
        work { settled { repository.reschedule(dialogKey, message.id, date) } }
    }

    fun edit(message: MessageItem, text: FormattedText) {
        val edited = text.trimmed()
        messagesState.update { list -> list.map { if (it.id == message.id) it.copy(text = edited.text, entities = edited.entities, edited = true) else it } }
        work { settled { repository.edit(dialogKey, message.id, edited.text, edited.entities) } }
    }

    fun delete(message: MessageItem) {
        messagesState.update { list -> list.filter { it.id != message.id } }
        work { settled { repository.delete(dialogKey, listOf(message.id)) } }
    }

    private suspend fun settled(action: suspend () -> Unit) {
        try {
            action()
        } finally {
            runCatching { load() }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): ScheduledViewModel
    }
}
