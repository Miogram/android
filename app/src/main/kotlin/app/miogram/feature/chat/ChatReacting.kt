package app.miogram.feature.chat

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.ReactionsRepository
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.ReactionCount
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.exceedsReactionLimit
import cn.ellerotta.core.model.ownReactionsAfter
import cn.ellerotta.core.model.pickerReactions
import cn.ellerotta.core.model.reactionsUpdated
import cn.ellerotta.core.model.withOwnReactions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ChatReacting(
    scope: CoroutineScope,
    private val dialogKey: String,
    private val repository: ReactionsRepository,
    private val messages: MutableStateFlow<List<MessageItem>>,
    private val work: (suspend () -> Unit) -> Unit,
    private val requestEmoji: (Collection<Long>) -> Unit,
    private val fail: (UiError) -> Unit,
    private val loadFile: (MediaItem) -> Unit,
) {
    private val state = MutableStateFlow(ChatReactionState())
    private val loaded = HashSet<String>()

    val ui: StateFlow<ChatReactionState> = state.asStateFlow()

    init {
        scope.launch {
            try {
                val premium = optional(false) { repository.premium() }
                val chat = repository.chatReactions(dialogKey)
                val catalog = repository.catalog()
                val top = optional(emptyList()) { repository.top() }
                val recent = optional(emptyList()) { repository.recent() }.filter { chat.allows(it, premium) }
                val allowed = pickerReactions(top, catalog, chat, premium)
                val default = repository.defaultReaction()?.takeIf { it in allowed } ?: allowed.firstOrNull()
                val maxOwn = optional(1) { repository.maxOwn(premium) }
                val limit = chat.limit ?: optional<Int?>(null) { repository.uniqueMax() }
                requestEmoji((allowed + recent).mapNotNull { (it as? Reaction.CustomEmoji)?.documentId })
                val custom = premium && chat.all && chat.custom
                state.value = ChatReactionState(allowed, default, catalog.associateBy { it.reaction }, recent, custom, premium, maxOwn, limit)
                allowed.take(ROW_PRELOAD).forEach { reaction ->
                    val art = state.value.art[reaction] ?: return@forEach
                    load(listOfNotNull(art.still, art.appear, art.center, art.around))
                }
                prepare(messages.value)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun <T> optional(fallback: T, call: suspend () -> T): T = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }

    fun prepare(shown: List<MessageItem>) {
        val art = state.value.art
        if (art.isEmpty()) return
        load(shown.flatMap { it.reactions }.mapNotNull { art[it.reaction]?.still })
    }

    fun loadEffect(reaction: Reaction, big: Boolean) {
        val art = state.value.art[reaction] ?: return
        load(if (big) listOfNotNull(art.activate, art.effect) else listOfNotNull(art.center, art.around))
    }

    @Synchronized
    private fun load(files: List<MediaItem>) {
        files.filter { loaded.add(it.key) }.forEach(loadFile)
    }

    fun react(id: Int, reaction: Reaction, picked: Boolean, big: Boolean = false) {
        if (reaction == Reaction.Paid) return
        val before = messages.value.firstOrNull { it.id == id && !it.pending }?.reactions ?: return
        val own = ownReactionsAfter(before, reaction, state.value.maxOwn)
        val after = withOwnReactions(before, own)
        val limit = state.value.limit
        if (limit != null && exceedsReactionLimit(before, after, limit)) {
            fail(UiError.ReactionLimit(limit))
            return
        }
        if (reaction in own) loadEffect(reaction, big)
        if (reaction is Reaction.CustomEmoji) requestEmoji(listOf(reaction.documentId))
        set(id, after)
        work {
            try {
                repository.react(dialogKey, id, own, addToRecent = picked, big = big && reaction in own)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                set(id, before)
                throw e
            }
        }
    }

    fun apply(event: ChatEvent.Reactions) {
        if (event.key != dialogKey) return
        messages.update { list -> list.map { if (it.id == event.id) it.copy(reactions = reactionsUpdated(it.reactions, event.reactions, event.min), reactorsListed = event.listed) else it } }
        prepare(messages.value.filter { it.id == event.id })
        requestEmoji(event.reactions.mapNotNull { (it.reaction as? Reaction.CustomEmoji)?.documentId })
    }

    private fun set(id: Int, reactions: List<ReactionCount>) {
        messages.update { list -> list.map { if (it.id == id) it.copy(reactions = reactions) else it } }
    }

    private companion object {
        const val ROW_PRELOAD = 8
    }
}
