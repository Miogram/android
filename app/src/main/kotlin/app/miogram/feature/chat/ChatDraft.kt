package app.miogram.feature.chat

import cn.ellerotta.core.model.Draft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ChatDraft(
    private val scope: CoroutineScope,
    private val leaveScope: CoroutineScope,
    initial: Draft,
    private val pause: Long = PAUSE,
    private val save: suspend (Draft) -> Unit,
) {
    private var current = initial.normalized()
    private var saved = Draft.EMPTY
    private var pending: Job? = null
    private val restoreState = MutableStateFlow<Draft?>(null)

    val restore: StateFlow<Draft?> = restoreState.asStateFlow()

    fun restored() {
        restoreState.value = null
    }

    fun remote(draft: Draft?) {
        val incoming = draft?.normalized() ?: Draft.EMPTY
        val clean = current == saved
        saved = incoming
        if (!clean || current == incoming) return
        pending?.cancel()
        current = incoming
        restoreState.value = incoming
    }

    fun changed(draft: Draft) {
        val next = draft.normalized()
        if (next == current || (next == Draft.EMPTY && current.article != null)) return
        current = next
        pending?.cancel()
        pending = scope.launch {
            delay(pause)
            flush()
        }
    }

    fun sent() = kept(Draft.EMPTY)

    fun kept(draft: Draft) {
        pending?.cancel()
        current = draft.normalized()
        saved = current
    }

    fun leave() {
        pending?.cancel()
        if (current == saved || current.article != null) return
        val draft = current
        saved = draft
        leaveScope.launch { runCatching { save(draft) } }
    }

    private suspend fun flush() {
        if (current == saved || current.article != null) return
        val draft = current
        val before = saved
        saved = draft
        try {
            save(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (saved == draft) saved = before
        }
    }

    private fun Draft.normalized(): Draft = if (isEmpty) Draft.EMPTY else this

    companion object {
        const val PAUSE = 3_000L
    }
}
