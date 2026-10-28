package app.miogram.feature.chat

import app.miogram.data.telegram.BotsRepository
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.InlineState
import cn.ellerotta.core.model.inlineQueryOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class ChatInline(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val repository: BotsRepository,
    private val person: (String) -> Boolean = { false },
) {
    private val state = MutableStateFlow(InlineState())
    private val text = MutableStateFlow("")

    private val resolved = HashMap<String, InlineBot?>()
    private var recent: List<InlineBot>? = null
    private var paging: Job? = null

    val ui: StateFlow<InlineState> = state.asStateFlow()

    init {
        scope.launch { text.debounce(QUERY_PAUSE).distinctUntilChanged().collectLatest(::follow) }
    }

    fun text(value: String) {
        text.value = value
    }

    private suspend fun follow(value: String) {
        paging?.cancel()
        val query = inlineQueryOf(value)
        if (query == null || person(query.username)) {
            state.value = InlineState()
            return
        }
        val name = query.username.lowercase()
        val bot = if (resolved.containsKey(name)) {
            resolved[name]
        } else {
            val answer = quietly { Resolved(repository.inlineBot(query.username)) }
            if (answer != null) resolved[name] = answer.bot
            answer?.bot
        }
        if (bot == null) {
            state.value = InlineState()
            return
        }
        state.update { InlineState(bot = bot, query = query.query, results = it.results.takeIf { _ -> it.bot == bot }, loading = true) }
        val page = quietly { repository.inlineResults(bot, dialogKey, query.query) }
        state.value = InlineState(bot = bot, query = query.query, results = page, more = page?.nextOffset != null)
    }

    fun loadMore() {
        val current = state.value
        val bot = current.bot ?: return
        val offset = current.results?.nextOffset ?: return
        if (current.loading) return
        state.value = current.copy(loading = true)
        paging = scope.launch {
            val page = quietly { repository.inlineResults(bot, dialogKey, current.query, offset) }
            state.update { now ->
                if (now.bot != bot || now.query != current.query) return@update now
                val results = now.results ?: return@update now.copy(loading = false)
                if (page == null) return@update now.copy(loading = false)
                now.copy(results = results.copy(results = results.results + page.results, nextOffset = page.nextOffset), more = page.nextOffset != null, loading = false)
            }
        }
    }

    fun clear() {
        paging?.cancel()
        text.value = ""
        state.value = InlineState()
    }

    suspend fun recentBots(): List<InlineBot> = recent ?: quietly { repository.recentInlineBots() }?.also { recent = it }.orEmpty()

    private class Resolved(val bot: InlineBot?)

    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val QUERY_PAUSE = 400L
    }
}
