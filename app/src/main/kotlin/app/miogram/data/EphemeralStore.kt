package app.miogram.data

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.di.ApplicationScope
import cn.ellerotta.core.model.MessageItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class EphemeralStore @Inject constructor(
    private val updates: UpdatesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val stateFlow = MutableStateFlow<Map<String, List<MessageItem>>>(emptyMap())
    private var events: Job? = null

    val state: StateFlow<Map<String, List<MessageItem>>> = stateFlow.asStateFlow()

    @Synchronized
    fun start() {
        if (events?.isActive == true) return
        events = scope.launch {
            updates.events().collect { event ->
                when (event) {
                    is ChatEvent.Ephemeral -> put(event.key, event.message)
                    is ChatEvent.EphemeralDeleted -> remove(event.key, event.ids)
                    else -> Unit
                }
            }
        }
    }

    private fun put(key: String, message: MessageItem) = stateFlow.update { state ->
        val chat = (state[key].orEmpty().filter { it.id != message.id } + message).sortedWith(compareBy({ it.date }, { it.id })).takeLast(MAX_PER_CHAT)
        state + (key to chat)
    }

    fun remove(key: String, ids: List<Int>) = stateFlow.update { state ->
        val chat = state[key] ?: return@update state
        state + (key to chat.filter { it.ephemeral?.id !in ids })
    }

    @Synchronized
    fun clear() {
        events?.cancel()
        events = null
        stateFlow.value = emptyMap()
    }

    private companion object {
        const val MAX_PER_CHAT = 100
    }
}
