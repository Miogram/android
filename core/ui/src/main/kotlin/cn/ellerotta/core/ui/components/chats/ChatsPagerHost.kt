package cn.ellerotta.core.ui.components.chats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.IntOffset
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.appbar.TopBarStories
import cn.ellerotta.core.ui.components.contacts.ContactsActions
import cn.ellerotta.core.ui.components.contacts.ContactsContent
import cn.ellerotta.core.ui.components.contacts.ContactsPageState
import cn.ellerotta.core.ui.components.contacts.ContactsTopBar
import cn.ellerotta.core.ui.components.settings.SettingsActions
import cn.ellerotta.core.ui.components.settings.SettingsContent
import cn.ellerotta.core.ui.components.settings.SettingsPageState
import cn.ellerotta.core.ui.components.settings.SettingsTopBar
import kotlin.math.roundToInt

private const val MIN_TOP_BAR_ALPHA = 0.8f
private const val TOP_BAR_ALPHA_DELTA = 0.2f
private const val CONTACTS_SEARCH_REVEAL_START = 0.2f
private const val CONTACTS_SEARCH_REVEAL_RANGE = 0.8f
internal const val CONTACTS_PAGE_KEY = "contacts"
internal const val SETTINGS_PAGE_KEY = "settings"

internal fun pagerPageKey(page: Int, folderTabs: List<FolderTab>, contactsPageIndex: Int = folderTabs.size.coerceAtLeast(1)): String = when {
    page == contactsPageIndex -> CONTACTS_PAGE_KEY
    page == contactsPageIndex + 1 -> SETTINGS_PAGE_KEY
    else -> folderTabs.getOrNull(page)?.folder?.id?.let { "folder:$it" } ?: "chats:$page"
}

@Composable
internal fun ChatsPagerTopBar(
    pagerState: PagerState,
    contactsPageIndex: Int,
    collapseProgress: Float,
    status: ChatsStatus,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onArchiveClick: () -> Unit,
    searchFocus: FocusRequester,
    contactsQuery: String,
    onContactsQueryChange: (String) -> Unit,
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier,
    stories: TopBarStories? = null,
    onSearchFocusChange: ((Boolean) -> Unit)? = null,
) {
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val chatsToContactsProgress = pagerState.chatsToContactsProgress(contactsPageIndex)
    val contactsToSettingsProgress = (position - contactsPageIndex).coerceIn(0f, 1f)
    val chatsTopBarCollapseProgress = maxOf(collapseProgress, FastOutSlowInEasing.transform(chatsToContactsProgress))
    val contactsSearchRevealProgress = contactsSearchReveal(chatsToContactsProgress)

    BoxWithConstraints(modifier = modifier.fillMaxWidth().clipToBounds()) {
        val widthPx = constraints.maxWidth
        if (chatsToContactsProgress < 1f) {
            ChatsTopBar(
                modifier = Modifier.chatsTopBarTransform(chatsToContactsProgress, widthPx),
                collapseProgress = chatsTopBarCollapseProgress,
                status = status,
                query = query,
                onQueryChange = onQueryChange,
                onSearchClick = onSearchClick,
                onArchiveClick = onArchiveClick,
                searchFocus = searchFocus,
                stories = stories,
                onSearchFocusChange = onSearchFocusChange,
            )
        }
        if (chatsToContactsProgress > 0f && contactsToSettingsProgress < 1f) {
            ContactsTopBar(
                modifier = Modifier.contactsTopBarTransform(chatsToContactsProgress, contactsToSettingsProgress, widthPx),
                query = contactsQuery,
                onQueryChange = onContactsQueryChange,
                onAdd = onAddContact,
                searchRevealProgress = contactsSearchRevealProgress,
            )
        }
        if (contactsToSettingsProgress > 0f) {
            SettingsTopBar(modifier = Modifier.settingsTopBarTransform(contactsToSettingsProgress, widthPx))
        }
    }
}

internal fun PagerState.chatsToContactsProgress(contactsPageIndex: Int): Float =
    (currentPage + currentPageOffsetFraction - (contactsPageIndex - 1)).coerceIn(0f, 1f)

internal fun contactsSearchReveal(chatsToContactsProgress: Float): Float = FastOutSlowInEasing.transform(
    ((chatsToContactsProgress - CONTACTS_SEARCH_REVEAL_START) / CONTACTS_SEARCH_REVEAL_RANGE).coerceIn(0f, 1f),
)

internal fun Modifier.foldersTransform(pagerState: PagerState, contactsPageIndex: Int): Modifier =
    clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val progress = pagerState.chatsToContactsProgress(contactsPageIndex)
            val visibleHeight = (placeable.height * (1f - contactsSearchReveal(progress))).roundToInt()
            layout(placeable.width, visibleHeight) { placeable.placeRelative((-progress * placeable.width).roundToInt(), 0) }
        }
        .graphicsLayer { alpha = 1f - contactsSearchReveal(pagerState.chatsToContactsProgress(contactsPageIndex)) }

private fun Modifier.chatsTopBarTransform(progress: Float, widthPx: Int): Modifier =
    offset { IntOffset(x = (-progress * widthPx).roundToInt(), y = 0) }
        .graphicsLayer { alpha = 1f - (progress * TOP_BAR_ALPHA_DELTA) }

private fun Modifier.contactsTopBarTransform(chatsToContactsProgress: Float, contactsToSettingsProgress: Float, widthPx: Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val visibleHeight = (placeable.height * (1f - contactsToSettingsProgress)).roundToInt()
        layout(placeable.width, visibleHeight) { placeable.placeRelative(0, 0) }
    }.offset {
        IntOffset(x = ((1f - chatsToContactsProgress - contactsToSettingsProgress) * widthPx).roundToInt(), y = 0)
    }.graphicsLayer {
        alpha = MIN_TOP_BAR_ALPHA + (chatsToContactsProgress * TOP_BAR_ALPHA_DELTA) * (1f - contactsToSettingsProgress)
    }

private fun Modifier.settingsTopBarTransform(progress: Float, widthPx: Int): Modifier =
    offset { IntOffset(x = ((1f - progress) * widthPx).roundToInt(), y = 0) }
        .graphicsLayer { alpha = MIN_TOP_BAR_ALPHA + (progress * TOP_BAR_ALPHA_DELTA) }

@Composable
internal fun ChatsPagerContent(
    folderTabs: List<FolderTab>,
    avatars: Map<String, ByteArray>,
    now: Long,
    pagerState: PagerState,
    contactsPageIndex: Int,
    listStateForPage: (String) -> LazyListState,
    contactsListState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    contentPadding: PaddingValues,
    selectedKey: String?,
    placeholders: Boolean,
    contacts: ContactsPageState,
    contactsQuery: String,
    contactsActions: ContactsActions,
    me: ProfileInfo?,
    settingsListState: LazyListState,
    settings: SettingsPageState,
    settingsActions: SettingsActions,
    onOpenChat: (DialogItem) -> Unit,
    onMenu: (DialogItem) -> Unit,
    onLoadMore: (Int) -> Unit,
    onFindPeople: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenStories: ((String) -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> pagerPageKey(page, folderTabs, contactsPageIndex) },
        ) { page ->
            if (page == contactsPageIndex + 1) {
                SettingsContent(settings, me, avatars, settingsActions, settingsListState, contentPadding)
                return@HorizontalPager
            }
            if (page == contactsPageIndex) {
                ContactsContent(
                    contacts = contacts.contacts,
                    loading = contacts.loading,
                    query = contactsQuery,
                    globalResults = contacts.globalResults,
                    avatars = avatars,
                    now = now,
                    listState = contactsListState,
                    contentPadding = contentPadding,
                    onOpen = contactsActions.onOpen,
                    onDelete = contactsActions.onDelete,
                    onNewGroup = contactsActions.onNewGroup,
                    onNewChannel = contactsActions.onNewChannel,
                )
                return@HorizontalPager
            }
            if (placeholders) {
                Box(Modifier.padding(top = contentPadding.calculateTopPadding())) { ChatPlaceholders() }
                return@HorizontalPager
            }
            val tab = folderTabs.getOrNull(page) ?: return@HorizontalPager
            ChatsPage(
                tab = tab,
                avatars = avatars,
                now = now,
                listState = listStateForPage(pagerPageKey(page, folderTabs, contactsPageIndex)),
                nestedScrollConnection = nestedScrollConnection,
                contentPadding = contentPadding,
                selectedKey = selectedKey,
                onOpenChat = onOpenChat,
                onMenu = onMenu,
                onLoadMore = onLoadMore,
                onFindPeople = onFindPeople,
                onOpenStories = onOpenStories,
            )
        }
    }
}

@Composable
private fun ChatsPage(
    tab: FolderTab,
    avatars: Map<String, ByteArray>,
    now: Long,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    contentPadding: PaddingValues,
    selectedKey: String?,
    onOpenChat: (DialogItem) -> Unit,
    onMenu: (DialogItem) -> Unit,
    onLoadMore: (Int) -> Unit,
    onFindPeople: () -> Unit,
    onOpenStories: ((String) -> Unit)?,
) {
    ChatListContent(
        dialogs = tab.dialogs,
        avatars = avatars,
        now = now,
        listState = listState,
        contentPadding = contentPadding,
        nestedScrollConnection = nestedScrollConnection,
        selectedKey = selectedKey,
        onOpenChat = onOpenChat,
        onMenu = onMenu,
        empty = { EmptyChats(R.string.chats_empty, onFindPeople) },
        loading = tab.loading,
        onEnd = if (tab.loading) ({ onLoadMore(tab.folder.id) }) else null,
        onOpenStories = onOpenStories,
    )
}
