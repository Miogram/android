package app.miogram.feature.chat

import app.miogram.data.telegram.ChannelsRepository
import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.mentionSuggestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class ChatMentions(
    private val scope: CoroutineScope,
    private val dialogKey: String,
    private val kind: () -> PeerKind?,
    private val channels: ChannelsRepository,
    private val recentBots: suspend () -> List<InlineBot>,
    private val speakers: () -> List<String>,
) {
    private val typed = MutableStateFlow<Typed?>(null)
    private val state = MutableStateFlow<List<ChatMember>>(emptyList())

    private var members: List<ChatMember>? = null
    private var loading: Deferred<List<ChatMember>?>? = null
    private var total = 0
    private val searched = HashMap<String, List<ChatMember>>()

    val ui: StateFlow<List<ChatMember>> = state.asStateFlow()

    init {
        scope.launch { typed.collectLatest(::follow) }
    }

    fun typed(query: String?, atStart: Boolean) {
        typed.value = query?.let { Typed(it, atStart) }
    }

    fun person(username: String): Boolean = members.orEmpty().any { !it.bot && it.username.equals(username, ignoreCase = true) }

    private suspend fun follow(now: Typed?) {
        if (now == null) {
            state.value = emptyList()
            return
        }
        val loaded = members ?: loaded()
        val bots = if (now.atStart) recentBots().map(::memberOf) else emptyList()
        val talked = speakers()
        val known = loaded.mapTo(HashSet()) { it.userKey }
        val heard = talked.filter { it !in known }.mapNotNull { channels.knownUser(it) }
        state.value = mentionSuggestions(loaded + heard + bots, now.query, talked)
        if (now.query.isEmpty() || !group() || total <= loaded.size) return
        val found = searched[now.query] ?: run {
            delay(SEARCH_PAUSE)
            quietly { channels.members(dialogKey, now.query, 0, SEARCH_LIMIT).members }?.also { searched[now.query] = it }
        } ?: return
        state.value = mentionSuggestions(loaded + heard + found + bots, now.query, talked)
    }

    private suspend fun loaded(): List<ChatMember> {
        val load = loading ?: scope.async { load() }.also { loading = it }
        val result = load.await()
        if (result == null) {
            if (loading === load) loading = null
        } else {
            members = result
        }
        return result.orEmpty()
    }

    private suspend fun load(): List<ChatMember>? = when {
        group() -> quietly { channels.members(dialogKey, limit = MEMBERS_LIMIT) }?.let { page ->
            total = page.total
            page.members
        }
        dialogKey.startsWith("u") && kind() != PeerKind.Saved -> channels.knownUser(dialogKey, bot = kind() == PeerKind.Bot)?.let { listOfNotNull(it.takeIf { other -> !other.self }) }
        else -> emptyList()
    }

    private fun group(): Boolean = kind().let { it == PeerKind.Group || it == null && dialogKey.startsWith("c") }

    private fun memberOf(bot: InlineBot) = ChatMember(bot.key, bot.name, bot.avatarKey, presence = Presence.Bot, bot = true, username = bot.username, firstName = bot.name)

    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private data class Typed(val query: String, val atStart: Boolean)

    private companion object {
        const val MEMBERS_LIMIT = 200
        const val SEARCH_LIMIT = 50
        const val SEARCH_PAUSE = 300L
    }
}
