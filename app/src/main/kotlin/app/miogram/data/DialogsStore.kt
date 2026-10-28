package app.miogram.data

import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.DialogsRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.data.telegram.floodWaitOf
import app.miogram.di.ApplicationScope
import cn.ellerotta.core.model.ActivityKind
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.Mutes
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.Presence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class DialogsState(
    val folders: List<ChatFolder> = listOf(ChatFolder.all("")),
    val dialogs: List<DialogItem> = emptyList(),
    val archived: List<DialogItem> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val complete: Boolean = false,
    val archiveComplete: Boolean = false,
    val mainCursor: DialogItem? = null,
    val archiveCursor: DialogItem? = null,
    val pulled: List<DialogItem> = emptyList(),
    val missing: Set<String> = emptySet(),
) {
    fun unreadChatsBesides(key: String): Int = (dialogs + archived + pulled).count { it.key != key && !it.muted && it.hasUnread }

    fun chatsOf(folder: ChatFolder): List<DialogItem> = folder.pick(if (folder.isAll) dialogs else dialogs + archived + pulled)

    fun wanted(folders: List<ChatFolder>): List<String> {
        val named = folders.flatMap { it.explicit }.distinct()
        if (named.isEmpty()) return named
        val known = (dialogs + archived + pulled).mapTo(HashSet()) { it.key }
        return named.filter { it !in known && it !in missing }
    }

    fun hasMore(folder: ChatFolder): Boolean = when {
        !loaded -> true
        wanted(listOf(folder)).isNotEmpty() -> true
        folder.isAll -> !complete
        !folder.hasRules -> false
        else -> !complete || !folder.excludeArchived && !archiveComplete
    }
}

private data class Typer(val userId: Long, val name: String, val kind: ActivityKind)

@Singleton
class DialogsStore @Inject constructor(
    private val repository: DialogsRepository,
    private val updates: UpdatesRepository,
    private val avatarStore: AvatarStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val stateFlow = MutableStateFlow(DialogsState())
    private val typingFlow = MutableStateFlow<Map<String, List<Typer>>>(emptyMap())
    private val onlineFlow = MutableStateFlow<Map<String, Presence>>(emptyMap())
    private val hiddenFlow = MutableStateFlow<Set<String>>(emptySet())
    private val typingJobs = ConcurrentHashMap<Pair<String, Long>, Job>()
    private val loadLock = Mutex()
    private var events: Job? = null

    private val rowsLock = Any()
    private var loads = 0
    private var sequence = 0L
    private val replay = ArrayList<Pair<Long, ChatEvent>>()

    private val reading = ConcurrentHashMap<String, Int>()

    private val stale = ConcurrentHashMap.newKeySet<String>()
    private val fetches = Channel<Unit>(Channel.CONFLATED)
    private val refreshes = Channel<Unit>(Channel.CONFLATED)

    private val filling = ConcurrentHashMap.newKeySet<Int>()
    private val deferred = ConcurrentHashMap.newKeySet<Int>()

    @Volatile private var nextRefresh: TimeMark? = null

    @Volatile private var floodUntil: TimeMark? = null

    val state: StateFlow<DialogsState> = stateFlow.asStateFlow()

    val decorated: StateFlow<DialogsState> = combine(stateFlow, typingFlow, onlineFlow, hiddenFlow) { state, typing, presence, hidden ->
        fun DialogItem.live(): DialogItem {
            val typers = typing[key].orEmpty()
            return copy(
                typing = typers.firstOrNull()?.name,
                activity = typers.map { it.kind }.distinct().singleOrNull() ?: ActivityKind.Typing,
                typingMore = typers.drop(1).map { it.name },
                online = presence[key]?.let { it == Presence.Online } ?: online,
                presence = presence[key] ?: this.presence,
            )
        }
        fun List<DialogItem>.shown() = if (hidden.isEmpty()) this else filter { it.key !in hidden }
        state.copy(dialogs = state.dialogs.shown().map { it.live() }, archived = state.archived.shown().map { it.live() }, pulled = state.pulled.shown().map { it.live() })
    }.stateIn(scope, SharingStarted.Eagerly, DialogsState())

    @Synchronized
    fun start() {
        if (events?.isActive == true) return
        events = scope.launch {
            launch {
                showStored()
                runCatching { refresh() }
            }
            launch {
                stateFlow.filter { it.loaded }.map { it to System.currentTimeMillis() }.conflate().collect { (state, at) ->
                    runCatching { repository.keep(state.dialogs + state.pulled.filter { !it.archived }, state.archived + state.pulled.filter { it.archived }, at) }
                    delay(KEEP_PAUSE)
                }
            }
            launch {
                for (signal in refreshes) {
                    delay(REFRESH_PAUSE)
                    refreshes.tryReceive()
                    try {
                        refresh()
                    } catch (e: Exception) {
                        if (floodWaitOf(e) != null) refreshes.trySend(Unit)
                    }
                }
            }
            launch {
                for (signal in fetches) {
                    delay(FETCH_PAUSE)
                    fetchStale()
                }
            }
            updates.events().collect(::onEvent)
        }
    }

    private suspend fun showStored() {
        val stored = runCatching { repository.stored() }.getOrNull() ?: return
        synchronized(rowsLock) {
            stateFlow.update { state ->
                if (state.loaded || state.dialogs.isNotEmpty() || state.archived.isNotEmpty()) state
                else state.copy(folders = stored.folders ?: state.folders).withLists(stored.dialogs, stored.archived)
            }
        }
        avatarStore.load((stored.dialogs + stored.archived).map { it.avatarKey })
    }

    private suspend fun showFolders(folders: List<ChatFolder>) {
        stateFlow.update { it.copy(folders = folders) }
        if (stateFlow.value.loaded) pull(stateFlow.value.wanted(folders))
    }

    suspend fun saveFolder(folder: ChatFolder) = showFolders(repository.saveFolder(folder))

    suspend fun deleteFolder(id: Int) = showFolders(repository.deleteFolder(id))

    suspend fun reorderFolders(ids: List<Int>) {
        val before = stateFlow.value.folders
        val byId = before.associateBy { it.id }
        stateFlow.update { it.copy(folders = ids.mapNotNull(byId::get)) }
        try {
            showFolders(repository.reorderFolders(ids))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            showFolders(before)
            throw e
        }
    }

    fun reading(key: String, open: Boolean) {
        if (open) reading.merge(key, 1, Int::plus) else reading.computeIfPresent(key) { _, count -> (count - 1).takeIf { it > 0 } }
    }

    private fun onEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.DialogsChanged -> refreshes.trySend(Unit)
            ChatEvent.FoldersChanged -> scope.launch { runCatching { showFolders(repository.folders()) } }
            is ChatEvent.Typing -> {
                val who = event.who
                if (who == null) {
                    if (event.userId == 0L) clearTyping(event.key) else {
                        typingJobs.remove(event.key to event.userId)?.cancel()
                        stopTyping(event.key, event.userId)
                    }
                } else {
                    val typer = Typer(event.userId, who, event.kind)
                    typingJobs.remove(event.key to event.userId)?.cancel()
                    typingFlow.update { all -> all + (event.key to (all[event.key].orEmpty().let { list -> if (list.any { it.userId == typer.userId }) list.map { if (it.userId == typer.userId) typer else it } else list + typer })) }
                    typingJobs[event.key to event.userId] = scope.launch {
                        delay(TYPING_TIMEOUT)
                        stopTyping(event.key, event.userId)
                    }
                }
            }
            is ChatEvent.Status -> onlineFlow.update { it + (event.key to event.presence) }
            is ChatEvent.NewMessage -> {
                clearTyping(event.key)
                rows(event)
            }
            is ChatEvent.Edited, is ChatEvent.Deleted, is ChatEvent.ReadOutbox, is ChatEvent.ReadInbox, is ChatEvent.Pinned,
            is ChatEvent.PinnedOrder, is ChatEvent.UnreadMark, is ChatEvent.Muted, is ChatEvent.DefaultMute, is ChatEvent.Archived, is ChatEvent.Draft,
            -> rows(event)
            is ChatEvent.DialogStale -> fetch(event.key)
            is ChatEvent.Reactions, is ChatEvent.BotDraft, is ChatEvent.Ephemeral, is ChatEvent.EphemeralDeleted, is ChatEvent.ThreadRead,
            is ChatEvent.PollChanged, is ChatEvent.MessagesPinned, ChatEvent.Reload,
            is ChatEvent.Story, is ChatEvent.StoryDeleted, is ChatEvent.StoriesRead, is ChatEvent.StoryReaction, is ChatEvent.ChatWallpaperChanged,
            is ChatEvent.Blocked, is ChatEvent.PeerBarChanged,
            is ChatEvent.ScheduledNew, is ChatEvent.ScheduledDeleted,
            -> Unit
        }
    }

    private fun stopTyping(key: String, userId: Long) = typingFlow.update { all ->
        val rest = all[key].orEmpty().filter { it.userId != userId }
        if (rest.isEmpty()) all - key else all + (key to rest)
    }

    private fun clearTyping(key: String) {
        typingJobs.keys.filter { it.first == key }.forEach { typingJobs.remove(it)?.cancel() }
        typingFlow.update { it - key }
    }

    private fun rows(event: ChatEvent) = synchronized(rowsLock) {
        if (loads > 0) replay += ++sequence to event
        applyRow(event)
    }

    private fun applyRow(event: ChatEvent) {
        when (event) {
            is ChatEvent.NewMessage -> if (!row(event.key) { arrived(it, event) }) fetch(event.key)
            is ChatEvent.Edited -> row(event.key) { if (it.topMessage == event.message.id) it.copy(preview = event.preview) else it }
            is ChatEvent.Deleted -> rowsOf(stateFlow.value)
                .filter { it.topMessage in event.ids && if (event.key == null) !it.key.startsWith("ch") else it.key == event.key }
                .forEach { fetch(it.key) }
            is ChatEvent.ReadOutbox -> row(event.key) { it.copy(read = it.read || it.outgoing && it.topMessage <= event.maxId) }
            is ChatEvent.ReadInbox -> row(event.key) { it.copy(unread = event.unread, mentions = if (event.unread == 0) 0 else it.mentions) }
            is ChatEvent.Pinned -> if (!row(event.key) { it.copy(pinned = event.pinned) } && event.pinned) fetch(event.key)
            is ChatEvent.PinnedOrder -> {
                val keys = event.keys
                if (keys == null) {
                    fetchPinned(event.archived)
                } else {
                    stateFlow.update { it.pinnedIn(event.archived, keys) }
                    val known = rowsOf(stateFlow.value).map { it.key }.toSet()
                    keys.filter { it !in known }.forEach(::fetch)
                }
            }
            is ChatEvent.UnreadMark -> row(event.key) { it.copy(unreadMark = event.marked) }
            is ChatEvent.Muted -> row(event.key) { it.copy(muted = repository.muted(event.muteUntil, it.kind), followsDefault = event.muteUntil == null) }
            is ChatEvent.DefaultMute -> stateFlow.update { state ->
                val muted = Mutes.muted(null, event.muteUntil, System.currentTimeMillis() / 1000)
                fun DialogItem.follow() = if (followsDefault && NotifyScope.of(kind) == event.scope) copy(muted = muted) else this
                state.copy(dialogs = state.dialogs.map { it.follow() }, archived = state.archived.map { it.follow() }, pulled = state.pulled.map { it.follow() })
            }
            is ChatEvent.Archived -> if (!row(event.key) { if (it.archived == event.archived) it else it.copy(archived = event.archived, pinned = false) }) fetch(event.key)
            is ChatEvent.Draft -> row(event.key) { it.copy(draft = event.draft, date = if (event.draft == null) it.date else maxOf(it.date, event.date)) }
            else -> Unit
        }
    }

    private fun arrived(row: DialogItem, event: ChatEvent.NewMessage): DialogItem {
        val message = event.message
        if (message.id <= row.topMessage) return row
        val counted = !message.out && !reading.containsKey(event.key)
        return row.copy(
            preview = event.preview,
            topMessage = message.id,
            date = maxOf(row.date, message.date),
            outgoing = message.out,
            read = message.read,
            unread = if (counted) row.unread + 1 else row.unread,
            mentions = if (counted && event.mention) row.mentions + 1 else row.mentions,
        )
    }

    private fun row(key: String, transform: (DialogItem) -> DialogItem): Boolean {
        var found = false
        stateFlow.update { state ->
            val old = state.dialogs.firstOrNull { it.key == key } ?: state.archived.firstOrNull { it.key == key } ?: state.pulled.firstOrNull { it.key == key }
            found = old != null
            val new = old?.let(transform)
            if (old == null || new == old) state else state.with(new!!)
        }
        return found
    }

    private fun fetch(key: String) {
        if (!stateFlow.value.loaded) return
        if (stale.add(key)) fetches.trySend(Unit)
    }

    private suspend fun fetchStale() {
        val keys = stale.toSet()
        stale.removeAll(keys)
        if (keys.isEmpty()) return
        try {
            val rows = loading(keys, { repository.peerDialogs(keys) }) { state, rows -> rows.fold(state) { next, row -> next.with(row) } }
            avatarStore.load(rows.map { it.avatarKey })
        } catch (e: Exception) {
            if (floodWaitOf(e) != null) {
                stale += keys
                fetches.trySend(Unit)
            }
        }
    }

    private fun fetchPinned(archived: Boolean) = scope.launch {
        runCatching {
            loading(null, { repository.pinnedDialogs(archived) }) { state, rows ->
                rows.fold(state) { next, row -> next.with(row) }.pinnedIn(archived, rows.map { it.key })
            }
        }
    }

    suspend fun reload(key: String) {
        val rows = loading(setOf(key), { repository.peerDialogs(listOf(key)) }) { state, rows -> rows.fold(state) { next, row -> next.with(row) } }
        avatarStore.load(rows.map { it.avatarKey })
    }

    fun forget(key: String) = stateFlow.update { state ->
        state.copy(dialogs = state.dialogs.filter { it.key != key }, archived = state.archived.filter { it.key != key }, pulled = state.pulled.filter { it.key != key })
    }

    fun hide(key: String) = hiddenFlow.update { it + key }

    fun unhide(key: String) = hiddenFlow.update { it - key }

    private suspend fun <T> loading(keys: Set<String>?, load: suspend () -> T, merge: (DialogsState, T) -> DialogsState): T {
        val start = synchronized(rowsLock) {
            loads++
            sequence
        }
        try {
            waitFor(floodUntil)
            val result = try {
                load()
            } catch (e: Exception) {
                floodWaitOf(e)?.let { floodUntil = TimeSource.Monotonic.markNow() + it.seconds }
                throw e
            }
            synchronized(rowsLock) {
                stateFlow.update { merge(it, result) }
                replay.filter { (at, event) -> at > start && (keys == null || keyOf(event)?.let { it in keys } ?: true) }.forEach { applyRow(it.second) }
            }
            return result
        } finally {
            synchronized(rowsLock) { if (--loads == 0) replay.clear() }
        }
    }

    suspend fun refresh() = loadLock.withLock {
        waitFor(nextRefresh)
        waitFor(floodUntil)
        nextRefresh = TimeSource.Monotonic.markNow() + REFRESH_INTERVAL
        stateFlow.update { it.copy(refreshing = true) }
        try {
            val folders = runCatching { repository.folders() }.getOrDefault(stateFlow.value.folders)
            val (main, archived) = loading(null, { repository.dialogs() to repository.dialogs(archived = true) }) { state, (main, archived) ->
                state.copy(
                    folders = folders,
                    loaded = true,
                    complete = main.complete,
                    archiveComplete = archived.complete,
                    mainCursor = main.dialogs.lastOrNull(),
                    archiveCursor = archived.dialogs.lastOrNull(),
                    missing = emptySet(),
                ).withLists(main.dialogs, archived.dialogs)
            }
            avatarStore.load((main.dialogs + archived.dialogs).map { it.avatarKey })
            onlineFlow.value = emptyMap()
        } finally {
            stateFlow.update { it.copy(refreshing = false) }
        }
        try {
            val state = stateFlow.value
            val listed = (state.dialogs + state.archived).mapTo(HashSet()) { it.key }
            pull(state.folders.flatMap { it.explicit }.distinct().filter { it !in listed })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
        deferred.toList().forEach { id ->
            deferred.remove(id)
            scope.launch { runCatching { loadMore(id) } }
        }
    }

    private suspend fun pull(keys: Collection<String>) {
        keys.distinct().chunked(PULL_BATCH).forEach { batch ->
            val asked = batch.toSet()
            val rows = loading(asked, { repository.peerDialogs(batch) }) { state, rows -> state.pulling(asked, rows) }
            avatarStore.load(rows.map { it.avatarKey })
        }
    }

    suspend fun loadMore(folderId: Int) {
        if (!stateFlow.value.loaded) {
            deferred += folderId
            return
        }
        if (!filling.add(folderId)) return
        try {
            val start = stateFlow.value
            val named = start.folders.firstOrNull { it.id == folderId } ?: return
            val first = start.chatsOf(named).size
            pull(start.wanted(listOf(named)))
            while (true) {
                val state = stateFlow.value
                val folder = state.folders.firstOrNull { it.id == folderId } ?: return
                if (!folder.isAll && !folder.hasRules) return
                if (state.chatsOf(folder).size - first >= FOLDER_PAGE) return
                if (!page(folder)) return
            }
        } finally {
            filling.remove(folderId)
        }
    }

    private suspend fun page(folder: ChatFolder): Boolean = loadLock.withLock {
        val current = stateFlow.value
        val main = current.loaded && !current.complete
        val archive = current.loaded && !folder.isAll && !folder.excludeArchived && !current.archiveComplete
        if (!main && !archive) return@withLock false
        val fromArchive = archive && (!main || (current.archiveCursor?.date ?: 0) > (current.mainCursor?.date ?: 0))
        fetchPage(fromArchive)
    }

    suspend fun loadMoreArchive() {
        loadLock.withLock {
            val current = stateFlow.value
            if (current.loaded && !current.archiveComplete) fetchPage(fromArchive = true)
        }
    }

    private suspend fun fetchPage(fromArchive: Boolean): Boolean {
        val cursor = if (fromArchive) stateFlow.value.archiveCursor else stateFlow.value.mainCursor
        val page = loading(emptySet(), { repository.dialogs(archived = fromArchive, after = cursor) }) { state, page ->
            val known = (state.dialogs + state.archived).mapTo(HashSet()) { it.key }
            val fresh = page.dialogs.filter { it.archived == fromArchive && it.key !in known }
            val freshKeys = fresh.mapTo(HashSet()) { it.key }
            val done = page.complete || page.dialogs.isEmpty()
            val next = page.dialogs.lastOrNull() ?: cursor
            val held = state.pulled.filter { it.key !in freshKeys }
            if (fromArchive) {
                state.copy(archived = state.archived + fresh, archiveComplete = done, archiveCursor = next, pulled = held)
            } else {
                state.copy(dialogs = state.dialogs + fresh, complete = done, mainCursor = next, pulled = held)
            }
        }
        avatarStore.load(page.dialogs.map { it.avatarKey })
        return page.dialogs.lastOrNull()?.let { it.key != cursor?.key } ?: false
    }

    fun change(key: String, transform: (DialogItem) -> DialogItem) {
        row(key, transform)
    }

    fun clear() {
        events?.cancel()
        events = null
        stateFlow.value = DialogsState()
        typingFlow.value = emptyMap()
        onlineFlow.value = emptyMap()
        hiddenFlow.value = emptySet()
        stale.clear()
        filling.clear()
        deferred.clear()
        refreshes.tryReceive()
        fetches.tryReceive()
    }

    private companion object {
        const val TYPING_TIMEOUT = 6_000L
        const val REFRESH_PAUSE = 400L
        const val KEEP_PAUSE = 1_000L
        const val FETCH_PAUSE = 300L
        const val PULL_BATCH = 100
        const val FOLDER_PAGE = 20
        val REFRESH_INTERVAL = 10.seconds

        suspend fun waitFor(mark: TimeMark?) {
            val left = -(mark?.elapsedNow() ?: return)
            if (left > 0.milliseconds) delay(left)
        }

        fun rowsOf(state: DialogsState) = state.dialogs + state.archived + state.pulled

        fun keyOf(event: ChatEvent): String? = when (event) {
            is ChatEvent.NewMessage -> event.key
            is ChatEvent.Edited -> event.key
            is ChatEvent.ReadOutbox -> event.key
            is ChatEvent.ReadInbox -> event.key
            is ChatEvent.Pinned -> event.key
            is ChatEvent.UnreadMark -> event.key
            is ChatEvent.Muted -> event.key
            is ChatEvent.Archived -> event.key
            is ChatEvent.Draft -> event.key
            else -> null
        }

        fun DialogsState.withLists(main: List<DialogItem>, archived: List<DialogItem>): DialogsState {
            val (inArchive, inMain) = (archived + main).distinctBy { it.key }.partition { it.archived }
            val listed = (inArchive + inMain).mapTo(HashSet()) { it.key }
            return copy(dialogs = inMain, archived = inArchive, pulled = pulled.filter { it.key !in listed })
        }

        fun DialogsState.with(row: DialogItem): DialogsState {
            val next = if (row.archived) {
                copy(dialogs = dialogs.filter { it.key != row.key }, archived = archived.placed(row, complete = true))
            } else {
                copy(dialogs = dialogs.placed(row, complete), archived = archived.filter { it.key != row.key })
            }
            val landed = next.dialogs.any { it.key == row.key } || next.archived.any { it.key == row.key }
            val held = pulled.any { it.key == row.key }
            return when {
                landed -> if (held) next.copy(pulled = pulled.filter { it.key != row.key }) else next
                held -> next.copy(pulled = pulled.map { if (it.key == row.key) row else it })
                else -> next
            }
        }

        fun DialogsState.pulling(asked: Collection<String>, rows: List<DialogItem>): DialogsState {
            val next = rows.fold(this) { state, row ->
                val cursor = if (row.archived) state.archiveCursor else state.mainCursor
                val more = if (row.archived) !state.archiveComplete else !state.complete
                val listed = state.dialogs.any { it.key == row.key } || state.archived.any { it.key == row.key }
                if (!listed && more && cursor != null && row.date < cursor.date) {
                    state.copy(pulled = state.pulled.filter { it.key != row.key } + row)
                } else {
                    state.with(row)
                }
            }
            val got = rows.mapTo(HashSet()) { it.key }
            val gone = asked.filter { it !in got }.toSet()
            return next.copy(pulled = next.pulled.filter { it.key !in gone }, missing = next.missing + gone)
        }

        fun List<DialogItem>.placed(row: DialogItem, complete: Boolean): List<DialogItem> {
            val index = indexOfFirst { it.key == row.key }
            val old = getOrNull(index)
            if (old != null && old.pinned == row.pinned && (row.pinned || old.date == row.date)) return toMutableList().also { it[index] = row }
            val rest = if (old == null) this else filterIndexed { i, _ -> i != index }
            val at = if (row.pinned) 0 else rest.indexOfFirst { !it.pinned && it.date <= row.date }
            return when {
                at >= 0 -> rest.toMutableList().apply { add(at, row) }
                complete -> rest + row
                else -> rest
            }
        }

        fun DialogsState.pinnedIn(archived: Boolean, order: List<String>): DialogsState {
            val keys = order.distinct()
            val list = if (archived) this.archived else dialogs
            val byKey = list.associateBy { it.key }
            val pinned = keys.mapNotNull { byKey[it]?.copy(pinned = true) }
            val others = list.filter { it.key !in keys }.map { if (it.pinned) it.copy(pinned = false) else it }.sortedByDescending { it.date }
            return if (archived) copy(archived = pinned + others) else copy(dialogs = pinned + others)
        }
    }
}
