package cn.ellerotta.core.ui.components.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.SearchFilter
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.appbar.TopBarStories
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.contacts.AddContactDialog
import cn.ellerotta.core.ui.components.contacts.ContactsActions
import cn.ellerotta.core.ui.components.contacts.ContactsPageState
import cn.ellerotta.core.ui.components.settings.SettingsActions
import cn.ellerotta.core.ui.components.settings.SettingsPageState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ChatsStatus { WaitingForNetwork, Connecting, Updating, Ready }

private const val MAX_PULL = 350f
private const val REVEAL_THRESHOLD = 130f
private const val FLING_VELOCITY_THRESHOLD = 600f

@Composable
fun ChatsScreen(
    tabs: List<FolderTab>,
    archived: List<DialogItem>,
    avatars: Map<String, ByteArray>,
    me: ProfileInfo?,
    status: ChatsStatus,
    refreshing: Boolean,
    onOpenChat: (DialogItem) -> Unit,
    onOpenArchive: () -> Unit,
    onRefresh: () -> Unit,
    onAction: (DialogItem, ChatAction) -> Unit,
    onLoadMore: (Int) -> Unit = {},
    globalResults: List<DialogItem> = emptyList(),
    onSearch: (String) -> Unit = {},
    player: (@Composable () -> Unit)? = null,
    playerShown: Boolean = true,
    contacts: ContactsPageState = ContactsPageState(),
    contactsActions: ContactsActions = ContactsActions(),
    settings: SettingsPageState = SettingsPageState(),
    settingsActions: SettingsActions = SettingsActions(),
    stories: List<PeerStories> = emptyList(),
    myStories: PeerStories? = null,
    onOpenStories: ((String) -> Unit)? = null,
    onAddStory: (() -> Unit)? = null,
    onHideStories: (String) -> Unit = {},
    onRemove: ((DialogItem, ChatRemoval, Boolean) -> Unit)? = null,
    onMute: ((DialogItem, MuteDuration) -> Unit)? = null,
    search: GlobalSearchState = GlobalSearchState(),
    searchActions: GlobalSearchActions = GlobalSearchActions(),
    now: Long = rememberNow(),
    folderActions: FolderActions = FolderActions(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf(SearchFilter.All) }
    var searchFocused by remember { mutableStateOf(false) }
    var contactsQuery by rememberSaveable { mutableStateOf("") }
    var addingContact by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<DialogItem?>(null) }
    var deletingFolder by remember { mutableStateOf<ChatFolder?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    var focusSearch by remember { mutableStateOf(false) }

    val effectsSpec = motionScheme.defaultEffectsSpec<Float>()
    val sizeSpatialSpec = motionScheme.defaultSpatialSpec<IntSize>()
    val floatingToolbarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp

    val hasFolders = tabs.size > 1
    val pullOffset = remember { Animatable(0f) }
    var isTopBarExpanded by remember { mutableStateOf(false) }

    val searchListState = rememberLazyListState()
    val contactsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val pagerListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val listStateForPage = remember(pagerListStates) { { pageKey: String -> pagerListStates.getOrPut(pageKey) { LazyListState() } } }
    val contactsPageIndex = tabs.size.coerceAtLeast(1)
    val settingsPageIndex = contactsPageIndex + 1
    val pagerState = rememberPagerState(pageCount = { tabs.size.coerceAtLeast(1) + 2 })
    val onContacts = pagerState.currentPage == contactsPageIndex
    val onSettings = pagerState.currentPage == settingsPageIndex
    val currentWindow = when {
        onContacts -> ChatsWindow.Contacts
        onSettings -> ChatsWindow.Settings
        else -> ChatsWindow.Chats
    }
    val searching = currentWindow == ChatsWindow.Chats && (query.isNotBlank() || searchFilter != SearchFilter.All || searchFocused)
    val foldersInReach by remember(pagerState, contactsPageIndex) { derivedStateOf { pagerState.chatsToContactsProgress(contactsPageIndex) < 1f } }
    var lastChatsPage by rememberSaveable { mutableIntStateOf(0) }
    var lastNonSettingsPage by rememberSaveable { mutableIntStateOf(0) }
    var settledKey by rememberSaveable { mutableStateOf("") }
    val contactsActionsState = rememberUpdatedState(contactsActions)
    val settingsActionsState = rememberUpdatedState(settingsActions)

    LaunchedEffect(pagerState, contactsPageIndex) {
        val follow = when (settledKey) {
            CONTACTS_PAGE_KEY -> contactsPageIndex
            SETTINGS_PAGE_KEY -> settingsPageIndex
            else -> null
        }
        if (follow != null && pagerState.currentPage != follow) pagerState.scrollToPage(follow)
        snapshotFlow { pagerState.settledPage }.collect { page ->
            settledKey = pagerPageKey(page, tabs, contactsPageIndex)
            when (page) {
                contactsPageIndex -> {
                    lastNonSettingsPage = page
                    contactsActionsState.value.onShown()
                }
                settingsPageIndex -> settingsActionsState.value.onShown()
                else -> {
                    lastChatsPage = page
                    lastNonSettingsPage = page
                }
            }
        }
    }

    val currentListState by remember(pagerState, tabs, pagerListStates, contactsPageIndex) {
        derivedStateOf {
            when {
                pagerState.currentPage == contactsPageIndex -> contactsListState
                pagerState.currentPage == settingsPageIndex -> settingsListState
                searching -> searchListState
                else -> pagerListStates[pagerPageKey(pagerState.currentPage, tabs, contactsPageIndex)] ?: searchListState
            }
        }
    }
    val currentListStateState = rememberUpdatedState(currentListState)
    val searchingState = rememberUpdatedState(searching)
    val onRefreshState = rememberUpdatedState(onRefresh)
    val openSpec = rememberUpdatedState(MaterialTheme.motionScheme.slowSpatialSpec<Float>())
    val closeSpec = rememberUpdatedState(MaterialTheme.motionScheme.fastSpatialSpec<Float>())
    val settleSpec = rememberUpdatedState(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())

    suspend fun expand() {
        isTopBarExpanded = true
        pullOffset.animateTo(MAX_PULL, openSpec.value)
    }

    suspend fun collapse() {
        isTopBarExpanded = false
        pullOffset.animateTo(0f, closeSpec.value)
    }

    LaunchedEffect(currentListState) {
        snapshotFlow { currentListState.firstVisibleItemIndex > 0 || currentListState.firstVisibleItemScrollOffset > 0 }
            .collect { isNotAtTop -> if (isNotAtTop && isTopBarExpanded && !searchingState.value) collapse() }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullOffset.value > 0f && available.y < 0f && source == NestedScrollSource.UserInput && !searchingState.value) {
                    val current = pullOffset.value
                    val newOffset = (current + available.y).coerceAtLeast(0f)
                    val consumed = current - newOffset
                    scope.launch { pullOffset.snapTo(newOffset) }
                    if (newOffset <= 0f) isTopBarExpanded = false
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val ls = currentListStateState.value
                val isAtTop = ls.firstVisibleItemIndex == 0 && ls.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && isAtTop && source == NestedScrollSource.UserInput) {
                    val progress = (pullOffset.value / MAX_PULL).coerceIn(0f, 1f)
                    val damping = (1f - progress * 0.65f).coerceAtLeast(0.1f)
                    val newOffset = (pullOffset.value + available.y * damping).coerceAtMost(MAX_PULL)
                    scope.launch { pullOffset.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset.value > 0f && !(isTopBarExpanded && pullOffset.value >= MAX_PULL)) {
                    settle(available.y > FLING_VELOCITY_THRESHOLD)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullOffset.value > 0f && !isTopBarExpanded) settle(false)
                return super.onPostFling(consumed, available)
            }

            private suspend fun settle(flung: Boolean) {
                val wasExpanded = isTopBarExpanded
                val shouldExpand = pullOffset.value > REVEAL_THRESHOLD || flung
                pullOffset.animateTo(if (shouldExpand) MAX_PULL else 0f, settleSpec.value)
                isTopBarExpanded = shouldExpand
                if (shouldExpand && !wasExpanded) onRefreshState.value()
            }
        }
    }

    fun closeSearchFilter() {
        if (searchFilter == SearchFilter.All) return
        searchFilter = SearchFilter.All
        searchActions.onFilter(SearchFilter.All)
    }

    LaunchedEffect(currentWindow) { if (currentWindow != ChatsWindow.Chats) searchFocused = false }

    val collapseProgress = if (searching) 0f else (1f - (pullOffset.value / MAX_PULL)).coerceIn(0f, 1f)

    LaunchedEffect(focusSearch, collapseProgress < 0.5f) {
        if (focusSearch && collapseProgress < 0.5f) {
            delay(50)
            runCatching { searchFocus.requestFocus() }
            focusSearch = false
        }
    }

    BackHandler(enabled = !onContacts && !onSettings && (searching || isTopBarExpanded)) {
        query = ""
        onSearch("")
        closeSearchFilter()
        searchFocused = false
        focusManager.clearFocus()
        scope.launch { collapse() }
    }

    BackHandler(enabled = onContacts) {
        if (contactsQuery.isNotEmpty()) {
            contactsQuery = ""
            contactsActions.onSearch("")
        } else {
            scope.launch { pagerState.animateScrollToPage(lastChatsPage.coerceAtMost(contactsPageIndex - 1)) }
        }
    }

    BackHandler(enabled = onSettings) {
        scope.launch { pagerState.animateScrollToPage(lastNonSettingsPage.coerceAtMost(contactsPageIndex)) }
    }

    fun showPage(page: Int) {
        val wasSearching = searching
        query = ""
        if (wasSearching) onSearch("")
        closeSearchFilter()
        searchFocused = false
        scope.launch {
            if (wasSearching || isTopBarExpanded) {
                collapse()
                withFrameNanos {}
            }
            pagerState.animateScrollToPage(page)
        }
    }

    fun startSearch() {
        focusSearch = true
        scope.launch { expand() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ChatsFloatingToolbar(
                currentWindow = currentWindow,
                onWindowSelected = { window ->
                    when (window) {
                        ChatsWindow.Chats -> scope.launch { pagerState.animateScrollToPage(lastChatsPage.coerceAtMost(contactsPageIndex - 1)) }
                        ChatsWindow.Contacts -> showPage(contactsPageIndex)
                        ChatsWindow.Settings -> showPage(settingsPageIndex)
                    }
                },
                onOpenArchive = onOpenArchive,
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ChatsPagerTopBar(
                    pagerState = pagerState,
                    contactsPageIndex = contactsPageIndex,
                    collapseProgress = collapseProgress,
                    status = status,
                    query = query,
                    onQueryChange = {
                        query = it
                        onSearch(it)
                    },
                    onSearchClick = ::startSearch,
                    onArchiveClick = onOpenArchive,
                    searchFocus = searchFocus,
                    onSearchFocusChange = { searchFocused = it },
                    contactsQuery = contactsQuery,
                    onContactsQueryChange = {
                        contactsQuery = it
                        contactsActions.onSearch(it)
                    },
                    onAddContact = { addingContact = true },
                    stories = TopBarStories(
                        peers = stories,
                        self = myStories,
                        avatars = avatars,
                        onExpand = { scope.launch { expand() } },
                        onOpenStories = onOpenStories ?: {},
                        onAddStory = onAddStory,
                        onHideStories = onHideStories,
                    ),
                )

                AnimatedVisibility(
                    visible = currentWindow == ChatsWindow.Chats && refreshing && tabs.any { it.dialogs.isNotEmpty() },
                    enter = expandVertically(animationSpec = sizeSpatialSpec) + fadeIn(animationSpec = effectsSpec),
                    exit = shrinkVertically(animationSpec = sizeSpatialSpec) + fadeOut(animationSpec = effectsSpec),
                ) {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("refreshing"))
                }

                AnimatedVisibility(
                    visible = currentWindow == ChatsWindow.Contacts && contacts.loading && contacts.contacts.isEmpty(),
                    enter = expandVertically(animationSpec = sizeSpatialSpec) + fadeIn(animationSpec = effectsSpec),
                    exit = shrinkVertically(animationSpec = sizeSpatialSpec) + fadeOut(animationSpec = effectsSpec),
                ) {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("contactsLoading"))
                }

                if (foldersInReach) {
                    AnimatedVisibility(
                        visible = hasFolders && !searching,
                        enter = expandVertically(animationSpec = sizeSpatialSpec) + fadeIn(animationSpec = effectsSpec),
                        exit = shrinkVertically(animationSpec = sizeSpatialSpec) + fadeOut(animationSpec = effectsSpec),
                    ) {
                        FolderPillRow(
                            tabs = tabs,
                            pagerState = pagerState,
                            onTabClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            modifier = Modifier.foldersTransform(pagerState, contactsPageIndex),
                            actions = FolderActions(folderActions.onEdit, folderActions.onReorder) { deletingFolder = it },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = player != null && playerShown,
                    enter = expandVertically(animationSpec = sizeSpatialSpec) + fadeIn(animationSpec = effectsSpec),
                    exit = shrinkVertically(animationSpec = sizeSpatialSpec) + fadeOut(animationSpec = effectsSpec),
                ) {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) { player?.invoke() }
                }
            }
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + floatingToolbarBottomPadding,
        )
        when {
            searching -> SearchResults(
                query = query,
                filter = searchFilter,
                search = search,
                searchActions = searchActions.copy(
                    onFilter = { kind ->
                        searchFilter = kind
                        searchActions.onFilter(kind)
                    },
                ),
                onSearchAgain = { text ->
                    query = text
                    onSearch(text)
                },
                tabs = tabs,
                archived = archived,
                avatars = avatars,
                now = now,
                globalResults = globalResults,
                listState = searchListState,
                contentPadding = contentPadding,
                nestedScrollConnection = nestedScrollConnection,
                selectedKey = menuFor?.key,
                onOpenChat = onOpenChat,
                onMenu = { menuFor = it },
                onOpenStories = onOpenStories,
            )
            else -> ChatsPagerContent(
                folderTabs = tabs,
                avatars = avatars,
                now = now,
                pagerState = pagerState,
                contactsPageIndex = contactsPageIndex,
                listStateForPage = listStateForPage,
                contactsListState = contactsListState,
                nestedScrollConnection = nestedScrollConnection,
                contentPadding = contentPadding,
                selectedKey = menuFor?.key,
                placeholders = (status == ChatsStatus.Connecting || status == ChatsStatus.WaitingForNetwork) && tabs.all { it.dialogs.isEmpty() },
                contacts = contacts,
                contactsQuery = contactsQuery,
                contactsActions = contactsActions,
                me = me,
                settingsListState = settingsListState,
                settings = settings,
                settingsActions = settingsActions,
                onOpenChat = onOpenChat,
                onMenu = { menuFor = it },
                onLoadMore = onLoadMore,
                onFindPeople = ::startSearch,
                onOpenStories = onOpenStories,
            )
        }
    }

    if (addingContact) {
        var addedSeen by remember { mutableIntStateOf(contacts.added) }
        LaunchedEffect(contacts.added) {
            if (contacts.added != addedSeen) {
                addedSeen = contacts.added
                addingContact = false
            }
        }
        AddContactDialog(
            notFound = contacts.notFound,
            onAdd = contactsActions.onAdd,
            onEdit = contactsActions.onEditNumber,
            onDismiss = {
                addingContact = false
                contactsActions.onEditNumber()
            },
        )
    }

    menuFor?.let { dialog ->
        ChatActionsSheet(
            dialog,
            avatars,
            onAction = { action ->
                menuFor = null
                onAction(dialog, action)
            },
            onDismiss = { menuFor = null },
            onRemove = onRemove?.let { remove -> { removal, revoke -> remove(dialog, removal, revoke) } },
            onMute = onMute?.let { mute -> { duration -> mute(dialog, duration) } },
        )
    }

    deletingFolder?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.folders_delete_title),
            text = stringResource(R.string.folders_delete_text, folder.title),
            confirm = stringResource(R.string.folders_delete_confirm),
            tag = "confirmFolderDelete",
            onConfirm = {
                deletingFolder = null
                folderActions.onDelete(folder)
            },
            onDismiss = { deletingFolder = null },
        )
    }
}

@Composable
internal fun SearchResults(
    query: String,
    filter: SearchFilter,
    search: GlobalSearchState,
    searchActions: GlobalSearchActions,
    onSearchAgain: (String) -> Unit,
    tabs: List<FolderTab>,
    archived: List<DialogItem>,
    avatars: Map<String, ByteArray>,
    now: Long,
    globalResults: List<DialogItem>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    selectedKey: String?,
    onOpenChat: (DialogItem) -> Unit,
    onMenu: (DialogItem) -> Unit,
    onOpenStories: ((String) -> Unit)?,
) {
    val text = query.trim()
    val open: (DialogItem) -> Unit = { dialog ->
        searchActions.onOpened(text)
        onOpenChat(dialog)
    }
    val chats = filter == SearchFilter.All
    val all = (tabs.firstOrNull { it.folder.isAll }?.dialogs.orEmpty() + archived).distinctBy { it.key }
    val found = if (chats && text.isNotEmpty()) all.filter { it.title.contains(text, ignoreCase = true) } else emptyList()
    val foundKeys = found.map { it.key }.toSet()
    val global = if (chats && text.isNotEmpty()) globalResults.filter { it.key !in foundKeys } else emptyList()
    val idle = chats && text.isEmpty()
    ChatListContent(
        dialogs = found,
        avatars = avatars,
        now = now,
        listState = listState,
        contentPadding = contentPadding,
        nestedScrollConnection = nestedScrollConnection,
        selectedKey = selectedKey,
        onOpenChat = open,
        onMenu = onMenu,
        onOpenStories = onOpenStories,
        loading = search.hitsLoading,
        onEnd = if (idle) null else searchActions.onLoadMore,
        empty = { if (!idle && global.isEmpty() && search.hits.isEmpty()) EmptyChats(R.string.chats_nothing_found) },
        header = {
            searchFilterRow(filter, searchActions.onFilter)
            if (idle) {
                topPeersRow(search.topPeers, avatars) { peer -> onOpenChat(peer) }
                recentSearches(search.recent, onSearchAgain, searchActions.onRemoveRecent, searchActions.onClearRecent)
            }
        },
        footer = {
            if (global.isNotEmpty()) {
                item(key = "globalTitle") {
                    Text(
                        stringResource(R.string.chats_global_search),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).semantics { heading() }.testTag("globalTitle"),
                    )
                }
                items(global, key = { "global_${it.key}" }) { dialog ->
                    DialogComponent(dialog, avatars, now, onClick = { open(dialog) }, onLongClick = {}, modifier = Modifier.animateItem().testTag("global_${dialog.key}"))
                }
            }
            messageHits(search.hits, avatars, now) { hit ->
                searchActions.onOpened(text)
                searchActions.onOpenHit(hit)
            }
        },
    )
}

@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}
