package app.miogram.data

import app.miogram.data.telegram.AppConfigRepository
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.di.ApplicationScope
import cn.ellerotta.core.model.AppConfig
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.MessageItem
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BotDraft(
    val key: String,
    val topicId: Int?,
    val randomId: Long,
    val botId: Long,
    val message: MessageItem,
    val canStop: Boolean,
    val keepOnStop: Boolean,
    val replacedBy: Int? = null,
)

data class BotDraftsState(val drafts: List<BotDraft> = emptyList(), val replaced: Map<String, Map<Int, Int>> = emptyMap())

@Singleton
class BotDraftsStore @Inject constructor(
    private val updates: UpdatesRepository,
    private val appConfig: AppConfigRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val stateFlow = MutableStateFlow(BotDraftsState())
    private val expiries = ConcurrentHashMap<Long, Job>()
    private val stopped = LinkedHashSet<Long>()
    private var lastLocalId = 0
    private var events: Job? = null

    val state: StateFlow<BotDraftsState> = stateFlow.asStateFlow()

    @Synchronized
    fun start() {
        if (events?.isActive == true) return
        events = scope.launch { updates.events().collect(::onEvent) }
    }

    private fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.BotDraft -> streamed(event)
            is ChatEvent.NewMessage -> if (!event.message.out) arrived(event.key, event.message)
            else -> Unit
        }
    }

    private fun streamed(event: ChatEvent.BotDraft) {
        val gone = synchronized(lock) {
            if (event.randomId in stopped) return
            val state = stateFlow.value
            val localId = state.drafts.firstOrNull { it.randomId == event.randomId }?.message?.id ?: --lastLocalId
            val draft = BotDraft(event.key, event.topicId, event.randomId, event.botId, event.message.copy(id = localId), event.canStop, event.keepOnStop)
            val (others, kept) = state.drafts.filter { it.randomId != event.randomId }.partition { it.replacedBy == null && it.key == event.key && sameTopic(it.topicId, event.topicId) }
            stateFlow.value = state.copy(drafts = kept + draft)
            others
        }
        gone.forEach { expiries.remove(it.randomId)?.cancel() }
        expire(event.randomId, null)
    }

    private fun arrived(key: String, message: MessageItem) {
        val draft = synchronized(lock) {
            val state = stateFlow.value
            val draft = state.drafts.firstOrNull {
                it.key == key && it.replacedBy == null && sameTopic(it.topicId, message.topicId) && (key.startsWith("u") || it.botId == message.senderId)
            } ?: return
            val chat = (state.replaced[key].orEmpty() + (message.id to draft.message.id)).entries.toList().takeLast(MAX_REPLACED).associate { it.key to it.value }
            stateFlow.value = state.copy(
                drafts = state.drafts.map { if (it === draft) it.copy(replacedBy = message.id, canStop = false) else it },
                replaced = state.replaced + (key to chat),
            )
            draft
        }
        expire(draft.randomId, REPLACED_KEEP)
    }

    fun stop(randomId: Long): BotDraft? {
        val draft = synchronized(lock) {
            stopped += randomId
            if (stopped.size > MAX_STOPPED) stopped.remove(stopped.first())
            val state = stateFlow.value
            val draft = state.drafts.firstOrNull { it.randomId == randomId } ?: return null
            stateFlow.value = state.copy(drafts = if (draft.keepOnStop) state.drafts.map { if (it === draft) it.copy(canStop = false) else it } else state.drafts - draft)
            draft
        }
        if (!draft.keepOnStop) expiries.remove(randomId)?.cancel()
        return draft
    }

    private fun expire(randomId: Long, after: Duration?) {
        val job = scope.launch {
            delay(after ?: ttl())
            synchronized(lock) { stateFlow.value = stateFlow.value.let { state -> state.copy(drafts = state.drafts.filter { it.randomId != randomId }) } }
        }
        expiries.put(randomId, job)?.cancel()
        job.invokeOnCompletion { expiries.remove(randomId, job) }
    }

    private suspend fun ttl(): Duration = try {
        appConfig.config().typingDraftTtl.seconds
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppConfig.DEFAULT_TYPING_DRAFT_TTL.seconds
    }

    @Synchronized
    fun clear() {
        events?.cancel()
        events = null
        expiries.values.forEach { it.cancel() }
        expiries.clear()
        synchronized(lock) {
            stopped.clear()
            stateFlow.value = BotDraftsState()
        }
    }

    private companion object {
        val REPLACED_KEEP = 5.seconds
        const val MAX_STOPPED = 100
        const val MAX_REPLACED = 50

        fun sameTopic(a: Int?, b: Int?) = (a ?: ForumTopic.GENERAL_TOPIC) == (b ?: ForumTopic.GENERAL_TOPIC)
    }
}
