package app.miogram.feature.chats

import androidx.lifecycle.viewModelScope
import app.miogram.connection.ConnectionStatus
import app.miogram.data.AvatarStore
import app.miogram.data.BotDraftsStore
import app.miogram.data.EphemeralStore
import app.miogram.data.RecentSearches
import app.miogram.data.DialogsStore
import app.miogram.data.telegram.DialogsRepository
import app.miogram.data.telegram.ProfileRepository
import app.miogram.data.telegram.StoriesRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.push.PushNotifier
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.MessageHit
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.SearchCursor
import cn.ellerotta.core.model.SearchFilter
import cn.ellerotta.core.ui.components.chats.ChatAction
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.chats.GlobalSearchState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val repository: DialogsRepository,
    private val profiles: ProfileRepository,
    private val store: DialogsStore,
    private val avatarStore: AvatarStore,
    private val botDrafts: BotDraftsStore,
    private val ephemeral: EphemeralStore,
    private val notifier: PushNotifier,
    private val stories: StoriesRepository,
    private val recentSearches: RecentSearches,
    connection: ConnectionStatus,
) : WorkViewModel() {
    private val meState = MutableStateFlow<ProfileInfo?>(null)
    private val queryState = MutableStateFlow("")
    private val filterState = MutableStateFlow(SearchFilter.All)
    private val hitsState = MutableStateFlow(Hits())
    private val topPeersState = MutableStateFlow<List<DialogItem>>(emptyList())

    val globalResults: StateFlow<List<DialogItem>> = queryState
        .debounce(SEARCH_PAUSE)
        .mapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.length < 2) emptyList() else runCatching { repository.search(trimmed) }.getOrDefault(emptyList())
        }
        .onEach { found -> avatarStore.load(found.map { it.avatarKey }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun search(query: String) {
        queryState.value = query
    }

    fun setSearchFilter(filter: SearchFilter) {
        filterState.value = filter
    }

    val globalSearch: StateFlow<GlobalSearchState> = combine(hitsState, recentSearches.queries, topPeersState) { hits, recent, top ->
        GlobalSearchState(hits.hits, hits.loading, recent, top)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSearchState())

    fun loadMoreHits() {
        val current = hitsState.value
        val cursor = current.next ?: return
        if (current.loading) return
        hitsState.value = current.copy(loading = true)
        viewModelScope.launch {
            val page = try {
                repository.searchMessages(current.query, current.filter, cursor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hitsState.update { if (it.sameSearch(current)) it.copy(loading = false) else it }
                return@launch
            }
            avatarStore.load(page.hits.map { it.dialog.avatarKey })
            hitsState.update { if (it.sameSearch(current)) it.copy(hits = (it.hits + page.hits).distinctBy { hit -> hit.key }, next = page.next, loading = false) else it }
        }
    }

    fun searchOpened(query: String) = recentSearches.add(query)

    fun forgetRecentSearch(query: String) = recentSearches.remove(query)

    fun clearRecentSearches() = recentSearches.clear()

    private suspend fun searchHits(query: String, filter: SearchFilter) {
        if (filter == SearchFilter.All && query.length < 2) {
            hitsState.value = Hits()
            return
        }
        val fresh = Hits(query = query, filter = filter, loading = true)
        hitsState.value = fresh
        val page = try {
            repository.searchMessages(query, filter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            hitsState.value = fresh.copy(loading = false)
            return
        }
        avatarStore.load(page.hits.map { it.dialog.avatarKey })
        hitsState.value = fresh.copy(hits = page.hits, next = page.next, loading = false)
    }

    private fun loadTopPeers() = viewModelScope.launch {
        try {
            val people = repository.topPeers()
            topPeersState.value = people
            avatarStore.load(people.map { it.avatarKey })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    private val decorated = combine(store.decorated, stories.rings) { state, rings -> state.withStories(rings) }

    val tabs: StateFlow<List<FolderTab>> = decorated
        .map { state -> state.folders.map { folder -> FolderTab(folder, state.chatsOf(folder), state.hasMore(folder)) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val archived: StateFlow<List<DialogItem>> = decorated.map { it.archived }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val status: StateFlow<ChatsStatus> = connection.status.stateIn(viewModelScope, SharingStarted.Eagerly, ChatsStatus.Connecting)

    val refreshing: StateFlow<Boolean> = store.state.map { it.refreshing && it.loaded }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val me: StateFlow<ProfileInfo?> = meState.asStateFlow()
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val storyPeers: StateFlow<List<PeerStories>> = stories.stories
        .map { list -> list.filterNot { it.self } }
        .onEach { list -> avatarStore.load(list.map { it.avatarKey }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myStories: StateFlow<PeerStories?> = stories.self

    val canPost: StateFlow<Boolean> = stories.canPost

    init {
        viewModelScope.launch {
            profiles.self.filterNotNull().collect {
                meState.value = it
                avatarStore.load(listOf(it.avatarKey))
            }
        }
        store.start()
        botDrafts.start()
        ephemeral.start()
        work {
            profiles.storedSelf()?.let { stored ->
                if (meState.value == null) meState.value = stored
                avatarStore.load(listOf(stored.avatarKey))
            }
            val profile = profiles.me()
            meState.value = profile
            avatarStore.load(listOf(profile.avatarKey))
        }
        loadStories()
        loadTopPeers()
        viewModelScope.launch {
            combine(queryState.debounce(SEARCH_PAUSE).map { it.trim() }, filterState) { query, filter -> query to filter }
                .distinctUntilChanged()
                .collectLatest { (query, filter) -> searchHits(query, filter) }
        }
    }

    fun refresh() = work {
        store.refresh()
        loadStories()
        loadTopPeers()
    }

    private fun loadStories() = work {
        stories.stored()
        stories.load()
        stories.load(hidden = true)
        stories.refreshCanPost()
    }

    fun hideStories(key: String) = work { stories.setHidden(key, true) }

    fun loadMore(folderId: Int) = work { store.loadMore(folderId) }

    fun deleteFolder(id: Int) = work { store.deleteFolder(id) }

    fun mute(dialog: DialogItem, duration: MuteDuration) = act(store, repository, dialog, ChatAction.Mute, duration.seconds)

    fun act(dialog: DialogItem, action: ChatAction) {
        act(store, repository, dialog, action)
        if (action == ChatAction.MarkRead) viewModelScope.launch { notifier.clearChat(dialog.key) }
    }
}

internal fun WorkViewModel.act(store: DialogsStore, repository: DialogsRepository, dialog: DialogItem, action: ChatAction, muteFor: Int? = null) = workWith {
    store.change(dialog.key) {
        when (action) {
            ChatAction.Pin -> it.copy(pinned = true)
            ChatAction.Unpin -> it.copy(pinned = false)
            ChatAction.Mute -> it.copy(muted = true, followsDefault = false)
            ChatAction.Unmute -> it.copy(muted = false, followsDefault = false)
            ChatAction.MarkRead -> it.copy(unread = 0, unreadMark = false, mentions = 0)
            ChatAction.MarkUnread -> it.copy(unreadMark = true)
            ChatAction.Archive -> it.copy(archived = true, pinned = false)
            ChatAction.Unarchive -> it.copy(archived = false, pinned = false)
        }
    }
    try {
        when (action) {
            ChatAction.Pin, ChatAction.Unpin -> repository.setPinned(dialog.key, action == ChatAction.Pin)
            ChatAction.Mute, ChatAction.Unmute -> repository.setMuted(dialog.key, action == ChatAction.Mute, muteFor)
            ChatAction.MarkRead -> repository.markRead(dialog.key)
            ChatAction.MarkUnread -> repository.markUnread(dialog.key)
            ChatAction.Archive, ChatAction.Unarchive -> repository.setArchived(dialog.key, action == ChatAction.Archive)
        }
    } finally {
        store.reload(dialog.key)
    }
}

private const val SEARCH_PAUSE = 400L

private data class Hits(
    val hits: List<MessageHit> = emptyList(),
    val next: SearchCursor? = null,
    val loading: Boolean = false,
    val query: String = "",
    val filter: SearchFilter = SearchFilter.All,
) {
    fun sameSearch(other: Hits) = query == other.query && filter == other.filter
}
