package app.miogram.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import app.miogram.data.telegram.SELF_KEY
import app.miogram.feature.auth.AuthViewModel
import app.miogram.feature.chats.ArchiveViewModel
import app.miogram.feature.chats.ChatsViewModel
import app.miogram.feature.chats.RemovalsViewModel
import app.miogram.feature.chats.ContactsViewModel
import app.miogram.feature.links.ShareTargetViewModel
import app.miogram.feature.profile.SettingsViewModel
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.AttachPeerType
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.ui.components.chats.ArchivedChatsScreen
import cn.ellerotta.core.ui.components.chats.ChatsScreen
import cn.ellerotta.core.ui.components.chats.FolderActions
import cn.ellerotta.core.ui.components.chats.GlobalSearchActions
import cn.ellerotta.core.ui.components.chats.PickChatScreen
import cn.ellerotta.core.ui.components.contacts.ContactsActions
import cn.ellerotta.core.ui.components.contacts.ContactsPageState
import cn.ellerotta.core.ui.components.settings.SettingsActions
import cn.ellerotta.core.ui.components.settings.SettingsPageState

internal fun EntryProviderScope<Route>.chatsEntries(activity: ComponentActivity, navigator: Navigator, version: String, back: () -> Unit, onOpenApp: (AttachMenuBot) -> Unit) {
    entry<Route.Chats> {
        val model = hiltViewModel<ChatsViewModel>()
        val removals = hiltViewModel<RemovalsViewModel>(activity)
        val tabs by model.tabs.collectAsStateWithLifecycle()
        val archived by model.archived.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val me by model.me.collectAsStateWithLifecycle()
        val status by model.status.collectAsStateWithLifecycle()
        val refreshing by model.refreshing.collectAsStateWithLifecycle()
        val globalResults by model.globalResults.collectAsStateWithLifecycle()
        val globalSearch by model.globalSearch.collectAsStateWithLifecycle()
        val storyPeers by model.storyPeers.collectAsStateWithLifecycle()
        val myStories by model.myStories.collectAsStateWithLifecycle()
        val canPost by model.canPost.collectAsStateWithLifecycle()
        val musicModel = musicModel()
        val playerShown by remember(musicModel) { musicModel.music.map { it != null }.distinctUntilChanged() }.collectAsStateWithLifecycle(musicModel.music.value != null)
        val contactsModel = hiltViewModel<ContactsViewModel>()
        val contacts by contactsModel.contacts.collectAsStateWithLifecycle()
        val contactsLoading by contactsModel.loading.collectAsStateWithLifecycle()
        val contactsFound by contactsModel.globalResults.collectAsStateWithLifecycle()
        val notFound by contactsModel.notFound.collectAsStateWithLifecycle()
        val added by contactsModel.added.collectAsStateWithLifecycle()
        val settingsModel = hiltViewModel<SettingsViewModel>()
        val auth = hiltViewModel<AuthViewModel>(activity)
        val sessions by settingsModel.sessions.collectAsStateWithLifecycle()
        val appearance by settingsModel.appearance.collectAsStateWithLifecycle()
        val autoSaveDownloads by settingsModel.autoSaveDownloads.collectAsStateWithLifecycle()
        val apps by settingsModel.apps.collectAsStateWithLifecycle()
        val server = settingsModel.server?.let {
            stringResource(if (it.testMode) cn.ellerotta.core.ui.R.string.server_line_test else cn.ellerotta.core.ui.R.string.server_line_production, it.thisDc)
        }
        val library = stringResource(cn.ellerotta.core.ui.R.string.profile_library_line, settingsModel.libraryVersion, settingsModel.layer)
        val savedTitle = stringResource(cn.ellerotta.core.ui.R.string.saved_messages)
        fun open(dialog: DialogItem) = navigator.go(chatRoute(dialog, if (dialog.isSaved) savedTitle else dialog.title))
        val searchActions = remember(model, savedTitle) {
            GlobalSearchActions(
                onFilter = model::setSearchFilter,
                onLoadMore = model::loadMoreHits,
                onOpenHit = { hit ->
                    val dialog = hit.dialog
                    val title = if (dialog.isSaved) savedTitle else dialog.title
                    navigator.go(if (dialog.forum) Route.Topics(dialog.key, title) else Route.Chat(dialog.key, title, messageId = hit.messageId))
                },
                onOpened = model::searchOpened,
                onRemoveRecent = model::forgetRecentSearch,
                onClearRecent = model::clearRecentSearches,
            )
        }
        val contactsActions = remember(contactsModel) {
            ContactsActions(
                onOpen = { navigator.go(Route.Chat(it.key, it.name)) },
                onSearch = contactsModel::search,
                onAdd = contactsModel::add,
                onDelete = contactsModel::delete,
                onShown = contactsModel::refresh,
                onEditNumber = contactsModel::clearNotFound,
                onNewGroup = { navigator.go(Route.NewGroupMembers) },
                onNewChannel = { navigator.go(Route.NewChannel) },
            )
        }
        val settingsActions = remember(settingsModel) {
            SettingsActions(
                onOpenProfile = { navigator.go(Route.PeerProfile(SELF_KEY, model.me.value?.name.orEmpty())) },
                onEditProfile = { navigator.go(Route.EditProfile) },
                onOpenNotifications = { navigator.go(Route.Notifications) },
                onOpenDataStorage = { navigator.go(Route.DataStorage) },
                onOpenPrivacy = { navigator.go(Route.Privacy) },
                onOpenSessions = { navigator.go(Route.Sessions) },
                onOpenFolders = { navigator.go(Route.Folders) },
                onAppearance = settingsModel::setAppearance,
                onOpenWallpapers = { navigator.go(Route.Wallpapers()) },
                onAutoSaveDownloads = settingsModel::setAutoSaveDownloads,
                onOpenLanguage = { activity.openLanguageSettings() },
                onOpenLog = { navigator.go(Route.Log) },
                onLogOut = { settingsModel.logOut(auth::loggedOut) },
                onShown = settingsModel::refresh,
                onOpenApp = onOpenApp,
                onRemoveApp = settingsModel::removeApp,
            )
        }
        val folderActions = remember(model) {
            FolderActions(
                onEdit = { navigator.go(Route.FolderEdit(it.id)) },
                onReorder = { navigator.go(Route.Folders) },
                onDelete = { model.deleteFolder(it.id) },
            )
        }
        Work(model) {
            Work(contactsModel) {
                Work(settingsModel) {
                    ChatsScreen(
                        tabs = tabs,
                        archived = archived,
                        avatars = avatars,
                        me = me,
                        status = status,
                        refreshing = refreshing,
                        onOpenChat = ::open,
                        onOpenArchive = { navigator.go(Route.Archive) },
                        onRefresh = model::refresh,
                        onAction = model::act,
                        onLoadMore = model::loadMore,
                        globalResults = globalResults,
                        onSearch = model::search,
                        player = { MusicBar(keepLast = true) },
                        playerShown = playerShown,
                        contacts = ContactsPageState(contacts, contactsLoading, contactsFound, notFound, added),
                        contactsActions = contactsActions,
                        settings = SettingsPageState(sessions, appearance, autoSaveDownloads, version, library, apps),
                        settingsActions = settingsActions,
                        folderActions = folderActions,
                        stories = storyPeers,
                        myStories = myStories,
                        onOpenStories = { navigator.go(Route.Stories(it)) },
                        onAddStory = if (canPost) ({ navigator.go(Route.StoryComposer()) }) else null,
                        onHideStories = model::hideStories,
                        onRemove = removals::remove,
                        onMute = model::mute,
                        search = globalSearch,
                        searchActions = searchActions,
                    )
                }
            }
        }
    }
    entry<Route.Archive> {
        val model = hiltViewModel<ArchiveViewModel>()
        val removals = hiltViewModel<RemovalsViewModel>(activity)
        val dialogs by model.dialogs.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val refreshing by model.refreshing.collectAsStateWithLifecycle()
        val hiddenStories by model.hiddenStories.collectAsStateWithLifecycle()
        Work(model) {
            ArchivedChatsScreen(
                dialogs,
                avatars,
                refreshing,
                back,
                onOpenChat = { navigator.go(chatRoute(it, it.title)) },
                onAction = model::act,
                hiddenStories = hiddenStories,
                onOpenStories = { navigator.go(Route.Stories(it)) },
                onUnhideStories = model::unhideStories,
                onLoadMore = model::loadMore,
                onRemove = removals::remove,
                onMute = model::mute,
            )
        }
    }
    entry<Route.ShareTarget> { route ->
        val model = hiltViewModel<ShareTargetViewModel>()
        val dialogs by model.dialogs.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val savedTitle = stringResource(cn.ellerotta.core.ui.R.string.saved_messages)
        Work(model) {
            PickChatScreen(dialogs, avatars, onBack = back, onPick = { dialog ->
                navigator.back()
                navigator.go(Route.Chat(dialog.key, if (dialog.isSaved) savedTitle else dialog.title, draft = route.text))
            })
        }
    }
    entry<Route.AttachTarget> { route ->
        val model = hiltViewModel<ShareTargetViewModel>()
        val dialogs by model.dialogs.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val bot = route.attach.bot
        val kinds = route.kinds
        val offered = remember(dialogs, bot, kinds) {
            dialogs.filter { dialog ->
                val kind = when (dialog.kind) {
                    PeerKind.User -> AttachPeerType.Users
                    PeerKind.Bot -> AttachPeerType.Bots
                    PeerKind.Group -> AttachPeerType.Groups
                    PeerKind.Channel -> AttachPeerType.Channels
                    PeerKind.Saved -> null
                }
                kind in kinds && bot.allowedIn(dialog.key, dialog.kind)
            }
        }
        Work(model) {
            PickChatScreen(offered, avatars, onBack = back, onPick = { dialog ->
                navigator.back()
                navigator.go(Route.Chat(dialog.key, dialog.title, attach = route.attach))
            })
        }
    }
}

internal fun chatRoute(dialog: DialogItem, title: String): Route = if (dialog.forum) Route.Topics(dialog.key, title) else Route.Chat(dialog.key, title)

private fun ComponentActivity.openLanguageSettings() {
    val uri = Uri.fromParts("package", packageName, null)
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, uri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
    }
    startActivity(intent)
}

internal fun ComponentActivity.openNotificationSettings() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}

internal fun ComponentActivity.openChannelSettings(channel: String) {
    startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channel))
}
