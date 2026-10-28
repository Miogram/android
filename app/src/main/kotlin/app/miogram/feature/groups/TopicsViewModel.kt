package app.miogram.feature.groups

import androidx.lifecycle.viewModelScope
import app.miogram.data.telegram.ChannelsRepository
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.ForumRepository
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.StickersRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.ChatInfo
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.ForumTopicsPage
import cn.ellerotta.core.model.MediaItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = TopicsViewModel.Factory::class)
class TopicsViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    private val forum: ForumRepository,
    private val channels: ChannelsRepository,
    private val stickers: StickersRepository,
    private val media: MediaRepository,
    private val updates: UpdatesRepository,
) : WorkViewModel() {
    private val state = MutableStateFlow(forum.stored(dialogKey)?.let(::stateOf) ?: TopicsState())
    private val infoState = MutableStateFlow(channels.storedInfo(dialogKey))
    private val emojiState = MutableStateFlow<Map<Long, MediaItem>>(emptyMap())
    private val filesState = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    private val requestedEmoji = HashSet<Long>()
    private val createdFlow = MutableSharedFlow<ForumTopic>(extraBufferCapacity = 1)

    val topics: StateFlow<TopicsState> = state.asStateFlow()

    val info: StateFlow<ChatInfo?> = infoState.asStateFlow()

    val emoji: StateFlow<Map<Long, MediaItem>> = emojiState.asStateFlow()
    val files: StateFlow<Map<String, ByteArray>> = filesState.asStateFlow()

    val created: SharedFlow<ForumTopic> = createdFlow.asSharedFlow()

    init {
        refresh(infoState.value != null) { infoState.value = channels.info(dialogKey) }
        icons(state.value.topics)
        updates.follow(dialogKey, open = true)
        viewModelScope.launch {
            updates.events()
                .filter { event -> event is ChatEvent.NewMessage && event.key == dialogKey || event is ChatEvent.Deleted && event.key == dialogKey || event == ChatEvent.Reload }
                .debounce(REFRESH_PAUSE)
                .collect { runCatching { first() } }
        }
    }

    fun refresh() {
        refresh(state.value.loaded) { first() }
    }

    private suspend fun first() {
        val page = forum.topics(dialogKey, limit = PAGE)
        state.value = stateOf(page)
        icons(page.topics)
    }

    private fun stateOf(page: ForumTopicsPage) =
        TopicsState(page.topics, page.total, more = page.topics.size >= PAGE && page.topics.size < page.total, loaded = true)

    fun more() {
        val current = state.value
        val last = current.topics.lastOrNull() ?: return
        if (current.loading || !current.more) return
        state.update { it.copy(loading = true) }
        work {
            try {
                val page = forum.topics(dialogKey, after = last, limit = PAGE)
                state.update {
                    val all = (it.topics + page.topics).distinctBy { topic -> topic.id }
                    TopicsState(all, page.total, more = page.topics.isNotEmpty() && all.size < page.total, loaded = true)
                }
                icons(page.topics)
            } finally {
                state.update { it.copy(loading = false) }
            }
        }
    }

    fun create(title: String, color: Int) {
        if (title.isBlank()) return
        work {
            val topic = forum.create(dialogKey, title, color)
            state.update { it.copy(topics = listOf(topic) + it.topics, total = it.total + 1) }
            createdFlow.tryEmit(topic)
        }
    }

    private fun icons(topics: List<ForumTopic>) {
        val ids = synchronized(requestedEmoji) { topics.mapNotNull { it.iconEmojiId }.filter { requestedEmoji.add(it) } }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val found = stickers.customEmoji(ids)
                emojiState.update { it + found }
                found.values.forEach { item ->
                    media.sticker(item.key)?.let { bytes -> filesState.update { it + (item.key to bytes) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                synchronized(requestedEmoji) { requestedEmoji -= ids.toSet() }
            }
        }
    }

    override fun onCleared() {
        updates.follow(dialogKey, open = false)
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String): TopicsViewModel
    }

    private companion object {
        const val PAGE = 30
        const val REFRESH_PAUSE = 500L
    }
}

data class TopicsState(
    val topics: List<ForumTopic> = emptyList(),
    val total: Int = 0,
    val more: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
)
