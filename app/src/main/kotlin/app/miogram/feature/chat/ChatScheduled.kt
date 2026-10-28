package app.miogram.feature.chat

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.ScheduledRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatScheduled(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val repository: ScheduledRepository,
    private val enabled: Boolean,
) {
    private val countState = MutableStateFlow(0)
    private val sentEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    private var loading: Job? = null

    val count: StateFlow<Int> = countState.asStateFlow()

    val dates: SharedFlow<Int> = sentEvents.asSharedFlow()

    fun changed() {
        if (!enabled) return
        loading?.cancel()
        loading = scope.launch {
            try {
                countState.value = repository.list(dialogKey).size
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                countState.value = 0
            }
        }
    }

    fun sent(date: Int) {
        sentEvents.tryEmit(date)
        changed()
    }

    fun apply(event: ChatEvent) {
        when (event) {
            is ChatEvent.ScheduledNew -> if (event.key == dialogKey) changed()
            is ChatEvent.ScheduledDeleted -> if (event.key == dialogKey) changed()
            else -> Unit
        }
    }
}
