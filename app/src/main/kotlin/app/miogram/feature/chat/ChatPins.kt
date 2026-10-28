package app.miogram.feature.chat

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.PinsRepository
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PinnedMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

internal class ChatPins(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val topicId: Int?,
    private val repository: PinsRepository,
    private val work: (suspend () -> Unit) -> Job,
    private val shown: (List<MessageItem>) -> Unit,
) {
    private val state = MutableStateFlow(PinnedMessages())
    private var loading: Job? = null

    val ui: StateFlow<PinnedMessages> = state.asStateFlow()

    fun load() {
        loading?.cancel()
        loading = scope.launch {
            try {
                val hiddenUpTo = repository.hiddenUpTo(dialogKey, topicId)
                val list = repository.pinned(dialogKey, topicId)
                state.update { it.reloaded(list).let { pins -> pins.copy(hiddenUpTo = maxOf(pins.hiddenUpTo, hiddenUpTo)) } }
                shown(list)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun tapped(): Int? {
        val id = state.value.shown?.id
        state.update { it.next() }
        return id
    }

    fun show(id: Int) {
        state.update { it.showing(id) }
    }

    fun hide() {
        val upTo = state.updateAndGet { it.hide() }.hiddenUpTo
        scope.launch { repository.hide(dialogKey, topicId, upTo) }
    }

    fun pin(id: Int, silent: Boolean, forBoth: Boolean) {
        work { repository.pin(dialogKey, id, silent, forBoth) }
    }

    fun unpin(id: Int) {
        state.update { it.without(listOf(id)) }
        work {
            try {
                repository.unpin(dialogKey, id)
            } catch (e: Exception) {
                load()
                throw e
            }
        }
    }

    fun unpinAll() {
        state.value = PinnedMessages()
        work {
            try {
                repository.unpinAll(dialogKey, topicId)
            } catch (e: Exception) {
                load()
                throw e
            }
        }
    }

    fun apply(event: ChatEvent.MessagesPinned) {
        if (event.key != dialogKey) return
        if (event.pinned) load() else state.update { it.without(event.ids) }
    }

    fun edited(message: MessageItem) {
        state.update { pins -> if (pins.messages.none { it.id == message.id }) pins else pins.copy(messages = pins.messages.map { if (it.id == message.id) message else it }) }
    }

    fun deleted(ids: List<Int>) {
        state.update { it.without(ids) }
    }
}
