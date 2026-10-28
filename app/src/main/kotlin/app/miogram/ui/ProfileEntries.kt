package app.miogram.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cn.ellerotta.core.model.NotifyScope
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import app.miogram.feature.chats.RemovalsViewModel
import app.miogram.feature.profile.BlockPickerViewModel
import app.miogram.feature.profile.BlockedUsersViewModel
import app.miogram.feature.profile.AvatarCropViewModel
import app.miogram.feature.profile.PeerProfileViewModel
import app.miogram.feature.profile.ProfileEditViewModel
import app.miogram.feature.profile.DataStorageViewModel
import app.miogram.feature.profile.NotificationSettingsViewModel
import app.miogram.feature.profile.PrivacyViewModel
import app.miogram.feature.profile.SessionsViewModel
import app.miogram.feature.profile.TwoStepViewModel
import app.miogram.feature.profile.WallpapersViewModel
import app.miogram.nav.Forwarding
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.ui.components.profile.AvatarCropScreen
import cn.ellerotta.core.ui.components.profile.EditProfileActions
import cn.ellerotta.core.ui.components.profile.EditProfileScreen
import cn.ellerotta.core.ui.components.profile.GiftCallbacks
import cn.ellerotta.core.ui.components.profile.PeerProfileScreen
import cn.ellerotta.core.ui.components.profile.PrivacyScreen
import cn.ellerotta.core.ui.components.settings.DataStorageActions
import cn.ellerotta.core.ui.components.settings.DataStorageScreen
import cn.ellerotta.core.ui.components.settings.NotificationActions
import cn.ellerotta.core.ui.components.settings.NotificationSettingsScreen
import cn.ellerotta.core.ui.components.settings.NotifyExceptionsScreen
import cn.ellerotta.core.ui.components.profile.SessionsScreen
import cn.ellerotta.core.ui.components.profile.SharedMediaActions
import cn.ellerotta.core.model.openableUrl
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.profile.TwoStepActions
import cn.ellerotta.core.ui.components.profile.TwoStepScreen
import cn.ellerotta.core.ui.components.profile.WallpapersScreen
import cn.ellerotta.core.ui.components.chats.PickChatScreen
import cn.ellerotta.core.ui.components.safety.BlockedUsersScreen
import cn.ellerotta.core.ui.components.safety.ReportActions
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.PeerKind

internal fun EntryProviderScope<Route>.profileEntries(navigator: Navigator, back: () -> Unit, onOpenMainApp: (botKey: String) -> Unit, onOpenLink: (String) -> Unit) {
    entry<Route.PeerProfile> { route ->
        val model = hiltViewModel<PeerProfileViewModel, PeerProfileViewModel.Factory>(key = "profile_" + route.dialogKey) { it.create(route.dialogKey) }
        val profile by model.profile.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val gifts by model.gifts.collectAsStateWithLifecycle()
        val stickers by model.stickers.collectAsStateWithLifecycle()
        val customEmoji by model.customEmoji.collectAsStateWithLifecycle()
        val giftCallbacks = remember(model) {
            if (model.isSelf) {
                GiftCallbacks(model::loadSticker, model::moreGifts, onToggleSaved = model::setGiftSaved, onOpenCatalogue = model::openGiftCatalogue, onCollection = model::chooseGiftCollection)
            } else {
                GiftCallbacks(model::loadSticker, model::moreGifts, onCollection = model::chooseGiftCollection)
            }
        }
        val music by model.music.collectAsStateWithLifecycle()
        val storyRing by model.storyRing.collectAsStateWithLifecycle()
        val songOrder by model.songOrder.collectAsStateWithLifecycle()
        val report by model.report.collectAsStateWithLifecycle()
        val reportActions = remember(model) { ReportActions(model::reportChoose, model::reportComment, model::closeReport) }
        val player = musicModel()
        val shared by model.shared.collectAsStateWithLifecycle()
        val sharedDownloads by model.sharedDownloads.collectAsStateWithLifecycle()
        val sharedCovers by model.sharedCovers.collectAsStateWithLifecycle()
        val forwardTargets by model.forwardTargets.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val savedTitle = stringResource(R.string.saved_messages)
        val noApp = stringResource(R.string.shared_no_app)
        val sharedActions = remember(model, navigator) {
            fun key() = model.profile.value?.key ?: route.dialogKey
            SharedMediaActions(
                onTab = model::chooseSharedTab,
                onMore = model::moreShared,
                onDownload = model::downloadShared,
                onOpenMedia = { message, gif -> navigator.go(Route.Media(key(), message.id, gifs = gif)) },
                onPlay = model::playShared,
                onOpenFile = { message ->
                    message.media?.let { media ->
                        model.openShared(media) { file, mime ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(view)
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onOpenLink = { url -> onOpenLink(openableUrl(url)) },
                onOpenChat = { chat -> navigator.go(Route.Chat(chat.key, chat.title)) },
                onGoTo = { message ->
                    val below = navigator.backStack.getOrNull(navigator.backStack.lastIndex - 1)
                    if (below is Route.Chat && below.dialogKey == route.dialogKey && below.threadId == null && below.topicId == null) navigator.back()
                    navigator.go(Route.Chat(key(), model.profile.value?.name ?: route.title, messageId = message.id))
                },
                onForward = { message, to, dropAuthor, dropCaptions ->
                    val forward = Forwarding(key(), listOf(message.id), dropAuthor, dropCaptions, listOf(message), model.profile.value?.name ?: route.title)
                    navigator.go(Route.Chat(to.key, if (to.isSaved) savedTitle else to.title, forward = forward))
                },
                onDelete = { message, revoke -> model.deleteShared(message.id, revoke) },
            )
        }
        val removals = hiltViewModel<RemovalsViewModel>(LocalActivity.current as ComponentActivity)
        LaunchedEffect(model) { model.refresh() }
        LaunchedEffect(model) { model.left.collect { navigator.backTo(Route.Chats) } }
        LaunchedEffect(model) { model.musicOpened.collect { player.openSheet() } }
        Work(model) { busy ->
            PeerProfileScreen(
                profile = profile,
                title = route.title,
                avatars = avatars,
                busy = busy,
                onBack = back,
                onMessage = {
                    val below = navigator.backStack.getOrNull(navigator.backStack.lastIndex - 1)
                    val shown = below is Route.Chat && below.dialogKey == route.dialogKey || below is Route.Topics && below.dialogKey == route.dialogKey
                    if (shown) navigator.back() else navigator.go(Route.Chat(route.dialogKey, route.title))
                },
                onMute = model::mute,
                onUnmute = model::unmute,
                gifts = gifts,
                stickers = stickers,
                giftCallbacks = giftCallbacks,
                onLeave = if (profile?.member == true) model::leave else null,
                onEdit = if (profile?.canEdit == true) ({ navigator.go(Route.EditChat(route.dialogKey, profile?.name ?: route.title)) }) else null,
                onOpenInviteLinks = if (profile?.canInvite == true) ({ navigator.go(Route.InviteLinks(route.dialogKey, route.title)) }) else null,
                onOpenMembers = if (profile?.canViewMembers == true) ({ navigator.go(Route.Members(route.dialogKey, route.title, profile?.kind == PeerKind.Channel)) }) else null,
                customEmoji = customEmoji,
                onOpenPhoto = { navigator.go(Route.Media(profile?.key ?: route.dialogKey, 0, profilePhotos = true)) },
                onSearch = {
                    val below = navigator.backStack.getOrNull(navigator.backStack.lastIndex - 1)
                    if (below is Route.Chat && below.dialogKey == route.dialogKey && below.threadId == null && below.topicId == null) navigator.back()
                    navigator.go(Route.Chat(route.dialogKey, route.title, search = true))
                },
                onPlayMusic = model::playMusic,
                musicPlaying = music?.let { it.playing && it.track.saved && it.track.dialogKey == profile?.key } == true,
                onOpenChannel = { channel -> navigator.go(Route.Chat(channel.key, channel.title, messageId = channel.post?.id)) },
                placeholder = model.placeholder,
                onAddContact = model::addContact,
                onDeleteContact = model::deleteContact,
                storyRing = storyRing,
                onOpenStories = { model.storiesKey()?.let { navigator.go(Route.Stories(it)) } },
                onOpenApp = { onOpenMainApp(profile?.key ?: route.dialogKey) },
                self = model.isSelf,
                songOrder = songOrder,
                onOrderSongs = model::orderSongs,
                onMoveSong = model::moveSong,
                onCloseSongOrder = model::closeSongOrder,
                onRemove = { removal, revoke ->
                    model.placeholder?.let { removals.remove(it, removal, revoke) }
                    navigator.backTo(Route.Chats)
                },
                onBlock = model::block,
                onUnblock = model::unblock,
                onReport = model::reportPeer,
                report = report,
                reportActions = reportActions,
                onEditProfile = { navigator.go(Route.EditProfile) },
                shared = shared,
                sharedActions = sharedActions,
                sharedDownloads = sharedDownloads,
                sharedCovers = sharedCovers,
                playerMusic = music,
                forwardTargets = forwardTargets,
            )
        }
    }
    entry<Route.EditProfile> {
        val model = hiltViewModel<ProfileEditViewModel>()
        val edit by model.edit.collectAsStateWithLifecycle()
        val channels by model.channels.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.saved.collect { back() } }
        val sources = rememberAvatarSources(model::captureUri, onCameraDenied = model::cameraDenied) { uri, captured -> navigator.go(Route.AvatarCrop(uri.toString(), captured)) }
        val actions = remember(model, sources) {
            EditProfileActions(
                onFirstName = model::setFirstName,
                onLastName = model::setLastName,
                onAbout = model::setAbout,
                onUsername = model::setUsername,
                onBirthday = model::setBirthday,
                onChannel = model::setChannel,
                onLoadChannels = model::loadChannels,
                onToggleUsername = model::toggleUsername,
                onMoveUsername = model::moveUsername,
                onGallery = sources.gallery,
                onCamera = sources.camera,
                onOpenPhotos = { model.ownKey()?.let { navigator.go(Route.Media(it, 0, profilePhotos = true)) } },
                onSave = model::save,
            )
        }
        Work(model) { busy -> EditProfileScreen(edit, avatars, channels, busy, back, actions) }
    }
    entry<Route.AvatarCrop> { route ->
        val model = hiltViewModel<AvatarCropViewModel, AvatarCropViewModel.Factory>(key = "crop_" + route.uri) { it.create(route.uri, route.temporary) }
        val photo by model.photo.collectAsStateWithLifecycle()
        val unreadable by model.unreadable.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.done.collect { back() } }
        Work(model) { busy -> AvatarCropScreen(photo, unreadable, busy, back, model::apply) }
    }
    entry<Route.Sessions> {
        val model = hiltViewModel<SessionsViewModel>()
        val sessions by model.sessions.collectAsStateWithLifecycle()
        Work(model) { busy -> SessionsScreen(sessions, busy, back, model::terminate, model::terminateOthers) }
    }
    entry<Route.Privacy> {
        val model = hiltViewModel<PrivacyViewModel>()
        val state by model.state.collectAsStateWithLifecycle()
        val app by model.app.collectAsStateWithLifecycle()
        Work(model) { busy -> PrivacyScreen(state, app, busy, back, model::setApp, model::setPrivacy, model::setGlobal, model::setAccountTtl, onOpenBlocked = { navigator.go(Route.Blocked) }, onOpenTwoStep = { navigator.go(Route.TwoStep) }) }
    }
    entry<Route.TwoStep> {
        val model = hiltViewModel<TwoStepViewModel>()
        val state by model.state.collectAsStateWithLifecycle()
        val actions = remember(model) {
            TwoStepActions(
                onBack = { if (!model.back()) back() },
                onStart = model::start,
                onCurrent = model::submitCurrent,
                onNewPassword = model::submitNewPassword,
                onHint = model::submitHint,
                onEmail = model::submitEmail,
                onCode = model::submitCode,
                onResend = model::resendCode,
                onEnterPendingCode = model::enterPendingCode,
                onCancelEmail = model::cancelEmail,
                onForgot = model::forgot,
                onRecover = model::submitRecovery,
            )
        }
        Work(model) { busy -> TwoStepScreen(state, busy, actions) }
    }
    entry<Route.Notifications> {
        val model = hiltViewModel<NotificationSettingsViewModel>()
        val state by model.state.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val proximity by model.proximity.collectAsStateWithLifecycle()
        val activity = LocalActivity.current as ComponentActivity
        var exceptionsOf by rememberSaveable { mutableStateOf<NotifyScope?>(null) }
        BackHandler(enabled = exceptionsOf != null) { exceptionsOf = null }
        val now by produceState(System.currentTimeMillis() / 1000) {
            while (true) {
                delay(30_000)
                value = System.currentTimeMillis() / 1000
            }
        }
        val actions = remember(model) {
            NotificationActions(
                onBack = { if (exceptionsOf != null) exceptionsOf = null else back() },
                onDefault = model::setDefault,
                onOpenExceptions = { exceptionsOf = it },
                onException = model::setException,
                onOpenSystem = { activity.openNotificationSettings() },
                onProximity = { on ->
                    model.setProximity(on)
                    if (on && model.proximityBlocked()) activity.openChannelSettings(model.proximityChannel())
                },
                onOpenProximity = { activity.openChannelSettings(model.proximityChannel()) },
                onReset = model::reset,
            )
        }
        Work(model) { busy ->
            val scope = exceptionsOf
            if (scope == null) NotificationSettingsScreen(state, busy, now, actions, proximity) else NotifyExceptionsScreen(scope, state, avatars, busy, now, actions)
        }
    }
    entry<Route.DataStorage> {
        val model = hiltViewModel<DataStorageViewModel>()
        val state by model.state.collectAsStateWithLifecycle()
        val actions = remember(model) { DataStorageActions(onBack = back, onAutoDownload = model::setAutoDownload, onClear = model::clear) }
        Work(model) { busy -> DataStorageScreen(state, busy, actions) }
    }
    entry<Route.Blocked> {
        val model = hiltViewModel<BlockedUsersViewModel>()
        val blocked by model.blocked.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        Work(model) { busy ->
            BlockedUsersScreen(
                blocked, avatars, busy, back,
                onAdd = { navigator.go(Route.BlockPicker) },
                onUnblock = model::unblock,
                onOpenProfile = { user -> navigator.go(Route.PeerProfile(user.key, user.name)) },
                onMore = model::more,
            )
        }
    }
    entry<Route.BlockPicker> {
        val model = hiltViewModel<BlockPickerViewModel>()
        val dialogs by model.dialogs.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.done.collect { back() } }
        Work(model) {
            PickChatScreen(dialogs, avatars, onBack = back, onPick = { dialog -> model.block(dialog.key) }, title = stringResource(cn.ellerotta.core.ui.R.string.blocked_pick_title))
        }
    }
    entry<Route.Wallpapers> { route ->
        val model = hiltViewModel<WallpapersViewModel, WallpapersViewModel.Factory>(key = "wallpapers_" + route.dialogKey) { it.create(route.dialogKey) }
        val choices by model.choices.collectAsStateWithLifecycle()
        val editing by model.editing.collectAsStateWithLifecycle()
        val everyChat by model.everyChat.collectAsStateWithLifecycle()
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::editPhoto) }
        Work(model) { busy ->
            WallpapersScreen(
                choices, busy, back, model::pick,
                onEdit = model::edit,
                editing = editing,
                onChange = model::change,
                onApply = model::apply,
                onCloseEdit = model::closeEdit,
                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                forChat = route.dialogKey != null,
                everyChat = everyChat,
            )
        }
    }
}
