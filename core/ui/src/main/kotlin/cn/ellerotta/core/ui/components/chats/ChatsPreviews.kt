package cn.ellerotta.core.ui.components.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PendingRemoval
import cn.ellerotta.core.model.SearchFilter
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.list.ChatComponent
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.contacts.ContactsPreviewData
import cn.ellerotta.core.ui.components.settings.SettingsPreviewData

@MioPreviews
@Composable
internal fun ChatsScreenPreview() {
    PreviewTheme {
        ChatsScreen(
            tabs = ChatsPreviewData.tabs,
            archived = ChatsPreviewData.archived,
            avatars = ChatsPreviewData.avatars,
            me = ChatsPreviewData.me,
            status = ChatsStatus.Ready,
            refreshing = false,
            onOpenChat = {},
            onOpenArchive = {},
            onRefresh = {},
            onAction = { _, _ -> },
            contacts = ContactsPreviewData.state,
            settings = SettingsPreviewData.state,
            stories = ChatsPreviewData.storyPeers,
            myStories = ChatsPreviewData.myStories,
            onOpenStories = {},
            onAddStory = {},
            now = ChatsPreviewData.NOW,
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsScreenNoFoldersPreview() {
    PreviewTheme {
        ChatsScreen(
            tabs = ChatsPreviewData.singleTab,
            archived = emptyList(),
            avatars = ChatsPreviewData.avatars,
            me = ChatsPreviewData.me,
            status = ChatsStatus.Updating,
            refreshing = true,
            onOpenChat = {},
            onOpenArchive = {},
            onRefresh = {},
            onAction = { _, _ -> },
            now = ChatsPreviewData.NOW,
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsScreenConnectingPreview() {
    PreviewTheme {
        ChatsScreen(
            tabs = ChatsPreviewData.emptyTabs,
            archived = emptyList(),
            avatars = emptyMap(),
            me = null,
            status = ChatsStatus.Connecting,
            refreshing = false,
            onOpenChat = {},
            onOpenArchive = {},
            onRefresh = {},
            onAction = { _, _ -> },
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsScreenWaitingPreview() {
    PreviewTheme {
        ChatsScreen(
            tabs = ChatsPreviewData.emptyTabs,
            archived = emptyList(),
            avatars = emptyMap(),
            me = null,
            status = ChatsStatus.WaitingForNetwork,
            refreshing = false,
            onOpenChat = {},
            onOpenArchive = {},
            onRefresh = {},
            onAction = { _, _ -> },
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsScreenEmptyPreview() {
    PreviewTheme {
        ChatsScreen(
            tabs = ChatsPreviewData.emptyTabs,
            archived = emptyList(),
            avatars = emptyMap(),
            me = ChatsPreviewData.me,
            status = ChatsStatus.Ready,
            refreshing = false,
            onOpenChat = {},
            onOpenArchive = {},
            onRefresh = {},
            onAction = { _, _ -> },
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsTopBarExpandedPreview() {
    var query by remember { mutableStateOf("") }
    PreviewTheme {
        ChatsTopBar(
            collapseProgress = 0f,
            status = ChatsStatus.Ready,
            query = query,
            onQueryChange = { query = it },
            onSearchClick = {},
            onArchiveClick = {},
            searchFocus = remember { FocusRequester() },
            stories = ChatsPreviewData.topBarStories(),
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsTopBarCollapsedPreview() {
    PreviewTheme {
        ChatsTopBar(
            collapseProgress = 1f,
            status = ChatsStatus.Updating,
            query = "",
            onQueryChange = {},
            onSearchClick = {},
            onArchiveClick = {},
            searchFocus = remember { FocusRequester() },
            stories = ChatsPreviewData.topBarStories(),
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsTopBarConnectingPreview() {
    PreviewTheme {
        ChatsTopBar(
            collapseProgress = 0f,
            status = ChatsStatus.Connecting,
            query = "",
            onQueryChange = {},
            onSearchClick = {},
            onArchiveClick = {},
            searchFocus = remember { FocusRequester() },
            stories = ChatsPreviewData.topBarStories(self = ChatsPreviewData.myStoriesEmpty),
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsPagerTopBarContactsPreview() {
    val tabs = ChatsPreviewData.tabs
    PreviewTheme {
        ChatsPagerTopBar(
            pagerState = rememberPagerState(initialPage = tabs.size) { tabs.size + 2 },
            contactsPageIndex = tabs.size,
            collapseProgress = 1f,
            status = ChatsStatus.Ready,
            query = "",
            onQueryChange = {},
            onSearchClick = {},
            onArchiveClick = {},
            searchFocus = remember { FocusRequester() },
            contactsQuery = "",
            onContactsQueryChange = {},
            onAddContact = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsPagerTopBarSettingsPreview() {
    val tabs = ChatsPreviewData.tabs
    PreviewTheme {
        ChatsPagerTopBar(
            pagerState = rememberPagerState(initialPage = tabs.size + 1) { tabs.size + 2 },
            contactsPageIndex = tabs.size,
            collapseProgress = 1f,
            status = ChatsStatus.Ready,
            query = "",
            onQueryChange = {},
            onSearchClick = {},
            onArchiveClick = {},
            searchFocus = remember { FocusRequester() },
            contactsQuery = "",
            onContactsQueryChange = {},
            onAddContact = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ArchivedChatsScreenPreview() {
    PreviewTheme {
        ArchivedChatsScreen(
            dialogs = ChatsPreviewData.archived,
            avatars = ChatsPreviewData.avatars,
            refreshing = false,
            onBack = {},
            onOpenChat = {},
            onAction = { _, _ -> },
            onOpenStories = {},
            hiddenStories = ChatsPreviewData.hiddenStories,
            now = ChatsPreviewData.NOW,
        )
    }
}

@MioPreviews
@Composable
internal fun ArchivedChatsScreenEmptyPreview() {
    PreviewTheme {
        ArchivedChatsScreen(
            dialogs = emptyList(),
            avatars = emptyMap(),
            refreshing = false,
            onBack = {},
            onOpenChat = {},
            onAction = { _, _ -> },
            now = ChatsPreviewData.NOW,
        )
    }
}

@MioPreviews
@Composable
internal fun PickChatScreenPreview() {
    PreviewTheme {
        PickChatScreen(
            dialogs = ChatsPreviewData.dialogs,
            avatars = ChatsPreviewData.avatars,
            onPick = {},
            onBack = {},
            now = ChatsPreviewData.NOW,
        )
    }
}

@MioPreviews
@Composable
internal fun ChatsFloatingToolbarPreview() {
    var window by remember { mutableStateOf(ChatsWindow.Chats) }
    PreviewTheme {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            ChatsFloatingToolbar(currentWindow = window, onWindowSelected = { window = it }, onOpenArchive = {})
        }
    }
}

@MioPreviews
@Composable
internal fun ChatsFloatingToolbarSettingsPreview() {
    PreviewTheme {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            ChatsFloatingToolbar(currentWindow = ChatsWindow.Settings, onWindowSelected = {}, onOpenArchive = {})
        }
    }
}

@MioPreviews
@Composable
internal fun FolderPillRowPreview() {
    val tabs = ChatsPreviewData.tabs
    PreviewTheme {
        FolderPillRow(tabs = tabs, pagerState = rememberPagerState(initialPage = 1) { tabs.size }, onTabClick = {})
    }
}

@MioPreviews
@Composable
internal fun DialogComponentPreview() {
    PreviewTheme {
        Column {
            ChatsPreviewData.dialogs.forEach { dialog ->
                DialogComponent(dialog, ChatsPreviewData.avatars, ChatsPreviewData.NOW, onClick = {}, onLongClick = {}, onOpenStories = {})
            }
        }
    }
}

@MioPreviews
@Composable
internal fun DialogComponentSelectedPreview() {
    PreviewTheme {
        DialogComponent(ChatsPreviewData.alice, ChatsPreviewData.avatars, ChatsPreviewData.NOW, onClick = {}, onLongClick = {}, isSelected = true)
    }
}

@MioPreviews
@Composable
internal fun ChatComponentPreview() {
    PreviewTheme {
        Column {
            ChatComponent(
                name = ChatsPreviewData.group.title,
                message = AnnotatedString("Pushed the new icons, have a look at the second page and tell me what you think"),
                time = "12:40",
                unreadCount = 5,
                unreadMentionCount = 1,
                avatar = { Avatar(ChatsPreviewData.group.title, null, seed = ChatsPreviewData.group.peerId) },
                onClick = {},
                onLongClick = {},
                isPinned = true,
                isMuted = true,
            )
            ChatComponent(
                name = ChatsPreviewData.alice.title,
                message = AnnotatedString(""),
                time = "12:41",
                unreadCount = 0,
                unreadMentionCount = 0,
                avatar = { Avatar(ChatsPreviewData.alice.title, ChatsPreviewData.avatars["photo_alice"]) },
                onClick = {},
                onLongClick = {},
                typing = "",
            )
        }
    }
}

@MioPreviews
@Composable
internal fun UnreadBadgePreview() {
    PreviewTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            UnreadBadge(count = 3, muted = false)
            UnreadBadge(count = 1240, muted = true)
            UnreadBadge(count = 1, muted = false, text = "@")
            OnlineDot(visible = true, ring = MaterialTheme.colorScheme.surface)
        }
    }
}

@MioPreviews
@Composable
internal fun TypingTextPreview() {
    PreviewTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TypingText("")
            TypingText("Mark")
        }
    }
}

@MioPreviews
@Composable
internal fun EmptyChatsPreview() {
    PreviewTheme {
        Box(Modifier.padding(bottom = 24.dp)) { EmptyChats(R.string.chats_empty, onFindPeople = {}) }
    }
}

@MioPreviews
@Composable
internal fun ChatPlaceholdersPreview() {
    PreviewTheme {
        ChatPlaceholders()
    }
}

@MioPreviews
@Composable
internal fun ChatActionsSheetPreview() {
    PreviewTheme {
        ChatActionsSheetPreviewContent(ChatsPreviewData.pinned)
    }
}

@MioPreviews
@Composable
internal fun ChatActionsSheetOwnerPreview() {
    PreviewTheme {
        ChatActionsSheetPreviewContent(ChatsPreviewData.group.copy(owner = true))
    }
}

@Composable
private fun ChatActionsSheetPreviewContent(dialog: DialogItem) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.fillMaxWidth(), shape = BottomSheetDefaults.ExpandedShape, color = BottomSheetDefaults.ContainerColor) {
            Column(Modifier.padding(top = 16.dp)) {
                ChatActionsContent(dialog, ChatsPreviewData.avatars, onAction = {}, onDismiss = {}, onRemove = {}, onMute = null)
            }
        }
    }
}

@MioPreviews
@Composable
internal fun ChatRemovalDialogPreview() {
    PreviewTheme {
        ChatRemovalDialog(ChatsPreviewData.alice, ChatRemoval.Delete, onConfirm = {}, onDismiss = {})
    }
}

@MioPreviews
@Composable
internal fun ChatRemovalDialogGroupPreview() {
    PreviewTheme {
        ChatRemovalDialog(ChatsPreviewData.group.copy(owner = true), ChatRemoval.DeleteForEveryone, onConfirm = {}, onDismiss = {})
    }
}

@MioPreviews
@Composable
internal fun RemovalSnackbarPreview() {
    PreviewTheme {
        Column {
            RemovalSnackbar(PendingRemoval(ChatsPreviewData.alice, ChatRemoval.Delete), onUndo = {})
            RemovalSnackbar(PendingRemoval(ChatsPreviewData.group, ChatRemoval.Delete), onUndo = {})
            RemovalSnackbar(PendingRemoval(ChatsPreviewData.alice, ChatRemoval.Clear), onUndo = {})
        }
    }
}

@MioPreviews
@Composable
internal fun SearchIdlePreview() {
    PreviewTheme {
        SearchPreviewContent(query = "", filter = SearchFilter.All, search = GlobalSearchState(recent = ChatsPreviewData.recent, topPeers = ChatsPreviewData.topPeers))
    }
}

@MioPreviews
@Composable
internal fun SearchHitsPreview() {
    PreviewTheme {
        SearchPreviewContent(query = "design", filter = SearchFilter.All, search = GlobalSearchState(hits = ChatsPreviewData.hits))
    }
}

@MioPreviews
@Composable
internal fun SearchFilterPreview() {
    PreviewTheme {
        SearchPreviewContent(query = "", filter = SearchFilter.Links, search = GlobalSearchState(hits = ChatsPreviewData.linkHits, hitsLoading = true))
    }
}

@Composable
private fun SearchPreviewContent(query: String, filter: SearchFilter, search: GlobalSearchState) {
    SearchResults(
        query = query,
        filter = filter,
        search = search,
        searchActions = GlobalSearchActions(),
        onSearchAgain = {},
        tabs = ChatsPreviewData.tabs,
        archived = ChatsPreviewData.archived,
        avatars = ChatsPreviewData.avatars,
        now = ChatsPreviewData.NOW,
        globalResults = emptyList(),
        listState = rememberLazyListState(),
        contentPadding = PaddingValues(),
        nestedScrollConnection = remember { object : NestedScrollConnection {} },
        selectedKey = null,
        onOpenChat = {},
        onMenu = {},
        onOpenStories = null,
    )
}
