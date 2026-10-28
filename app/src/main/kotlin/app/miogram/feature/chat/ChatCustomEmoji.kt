package app.miogram.feature.chat

import app.miogram.data.telegram.StickersRepository
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.customEmojiIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ChatCustomEmoji(
    private val scope: CoroutineScope,
    private val repository: StickersRepository,
    private val loadFile: (MediaItem) -> Unit,
) {
    private val documentsState = MutableStateFlow<Map<Long, MediaItem>>(emptyMap())
    private val requested = HashSet<Long>()
    private val pending = LinkedHashSet<Long>()
    private var batch: Job? = null

    val documents: StateFlow<Map<Long, MediaItem>> = documentsState.asStateFlow()

    fun request(messages: List<MessageItem>) = requestIds(customEmojiIds(messages))

    @Synchronized
    fun requestIds(ids: Collection<Long>) {
        val fresh = ids.filter { requested.add(it) }
        if (fresh.isEmpty()) return
        pending += fresh
        if (batch != null) return
        batch = scope.launch {
            while (true) {
                delay(BATCH_PAUSE)
                val ids = takeOrFinish()
                if (ids.isEmpty()) break
                fetch(ids)
            }
        }
    }

    @Synchronized
    fun add(media: MediaItem) {
        if (!requested.add(media.documentId)) return
        documentsState.update { it + (media.documentId to media) }
        loadFile(media)
    }

    private suspend fun fetch(ids: List<Long>) {
        try {
            val found = repository.customEmoji(ids)
            documentsState.update { it + found }
            found.values.forEach(loadFile)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            forget(ids)
        }
    }

    @Synchronized
    private fun takeOrFinish(): List<Long> {
        val ids = pending.toList()
        pending.clear()
        if (ids.isEmpty()) batch = null
        return ids
    }

    @Synchronized
    private fun forget(ids: List<Long>) {
        requested -= ids.toSet()
    }

    private companion object {
        const val BATCH_PAUSE = 50L
    }
}
