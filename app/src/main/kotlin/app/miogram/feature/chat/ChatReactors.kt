package app.miogram.feature.chat

import app.miogram.data.DialogsStore
import app.miogram.data.telegram.ReactionsRepository
import cn.ellerotta.core.model.ReactorsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ChatReactors(
    private val dialogKey: String,
    private val repository: ReactionsRepository,
    private val store: DialogsStore,
    private val work: (suspend () -> Unit) -> Job,
    private val shown: (List<String?>) -> Unit,
    private val showMessage: (Int) -> Unit,
) {
    private val state = MutableStateFlow<ReactorsState?>(null)

    val ui: StateFlow<ReactorsState?> = state.asStateFlow()

    private var lastUnread = 0

    fun open(id: Int, total: Int) {
        state.value = ReactorsState(id, total)
        load(null)
    }

    fun more() {
        val now = state.value ?: return
        val next = now.next ?: return
        if (now.loading) return
        state.value = now.copy(loading = true)
        load(next)
    }

    fun close() {
        state.value = null
    }

    private fun load(offset: String?) {
        val id = state.value?.messageId ?: return
        val job = work {
            val page = repository.reactors(dialogKey, id, offset)
            shown(page.list.map { it.reactor.avatarKey })
            state.update { now ->
                now?.takeIf { it.messageId == id }?.let { it.copy(list = it.list + page.list, count = page.count, next = page.next, loading = false) } ?: now
            }
        }
        job.invokeOnCompletion { state.update { now -> now?.takeIf { it.messageId == id && it.loading }?.copy(loading = false) ?: now } }
    }

    fun nextUnread(unread: Int) {
        work {
            val ids = repository.unread(dialogKey, lastUnread)
            val id = ids.firstOrNull()
            if (id != null) {
                lastUnread = id
                showMessage(id)
            }
            val left = if (id == null) 0 else maxOf(unread - 1, ids.size - 1)
            if (left == 0) repository.readAll(dialogKey)
            store.change(dialogKey) { it.copy(reactions = left) }
        }
    }

    fun readAll() {
        work {
            repository.readAll(dialogKey)
            lastUnread = 0
            store.change(dialogKey) { it.copy(reactions = 0) }
        }
    }
}
