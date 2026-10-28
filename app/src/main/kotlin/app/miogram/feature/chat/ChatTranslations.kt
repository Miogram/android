package app.miogram.feature.chat

import app.miogram.data.telegram.TranslationsRepository
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MessageTranslation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ChatTranslations(
    private val dialogKey: String,
    private val repository: TranslationsRepository,
    private val work: (suspend () -> Unit) -> Job,
) {
    private data class Source(val language: String, val text: String, val article: ArticleMessage?)

    private class Cached(val source: Source, val translation: MessageTranslation)

    private val cache = HashMap<Int, Cached>()
    private val state = MutableStateFlow<Map<Int, MessageTranslation>>(emptyMap())

    val shown: StateFlow<Map<Int, MessageTranslation>> = state.asStateFlow()

    fun translate(message: MessageItem, language: String) {
        val source = Source(language, message.text, message.article)
        val known = synchronized(cache) { cache[message.id]?.takeIf { it.source == source } }
        if (known != null) {
            state.update { it + (message.id to known.translation) }
            return
        }
        work {
            val translation = if (message.article != null) {
                repository.translateRich(dialogKey, message.id, language, repository.botOf(dialogKey, message.senderId, message.viaBot))
            } else {
                repository.translate(dialogKey, message.id, language)
            }
            synchronized(cache) { cache[message.id] = Cached(source, translation) }
            state.update { it + (message.id to translation) }
        }
    }

    fun showOriginal(id: Int) {
        state.update { it - id }
    }

    fun edited(message: MessageItem) {
        val stale = synchronized(cache) {
            val cached = cache[message.id] ?: return@synchronized false
            (cached.source.text != message.text || cached.source.article != message.article).also { if (it) cache.remove(message.id) }
        }
        if (stale) state.update { it - message.id }
    }

    fun apply(messages: List<MessageItem>, shown: Map<Int, MessageTranslation>): List<MessageItem> =
        if (shown.isEmpty()) messages else messages.map { message -> shown[message.id]?.takeIf { message.ephemeral == null }?.let { message.copy(translation = it) } ?: message }
}
