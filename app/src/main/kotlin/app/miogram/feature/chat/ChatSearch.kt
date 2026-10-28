package app.miogram.feature.chat

import app.miogram.data.telegram.MessagesRepository
import cn.ellerotta.core.model.ChatSearchState
import cn.ellerotta.core.model.UiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class ChatSearch(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val repository: MessagesRepository,
    private val fail: (UiError) -> Unit,
    private val topicId: Int? = null,
) {
    private val query = MutableStateFlow("")
    private val state = MutableStateFlow(ChatSearchState())
    private var more: Job? = null

    val ui: StateFlow<ChatSearchState> = state.asStateFlow()

    init {
        scope.launch(Dispatchers.Default) {
            query.debounce(PAUSE).collectLatest { text ->
                more?.cancel()
                if (text.isBlank()) {
                    state.value = ChatSearchState(query = text)
                    return@collectLatest
                }
                state.value = ChatSearchState(query = text, loading = true)
                try {
                    val page = repository.search(dialogKey, text, limit = PAGE, topicId = topicId)
                    state.value = ChatSearchState(text, page.messages, page.total, more = page.messages.size >= PAGE && page.messages.size < page.total)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.value = ChatSearchState(query = text)
                    fail(UiError.Message(e.message ?: "cannot search"))
                }
            }
        }
    }

    fun search(text: String) {
        query.value = text
    }

    fun loadMore() {
        val current = state.value
        val oldest = current.results.lastOrNull()?.id ?: return
        if (!current.more || current.loading) return
        state.value = current.copy(loading = true)
        more = scope.launch(Dispatchers.Default) {
            try {
                val page = repository.search(dialogKey, current.query, offsetId = oldest, limit = PAGE, topicId = topicId)
                state.update { now ->
                    if (now.query != current.query) return@update now
                    val results = (now.results + page.messages).distinctBy { it.id }
                    now.copy(results = results, total = maxOf(page.total, results.size), more = page.messages.size >= PAGE && results.size < page.total, loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { if (it.query == current.query) it.copy(loading = false) else it }
                fail(UiError.Message(e.message ?: "cannot search"))
            }
        }
    }

    fun close() {
        more?.cancel()
        query.value = ""
        state.value = ChatSearchState()
    }

    private companion object {
        const val PAGE = 30
        const val PAUSE = 400L
    }
}
