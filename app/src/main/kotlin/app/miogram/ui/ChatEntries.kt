package app.miogram.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import app.miogram.feature.attach.ContactShareViewModel
import app.miogram.feature.attach.LocationViewModel
import app.miogram.feature.attach.NewPollViewModel
import app.miogram.feature.chat.ChatOpening
import app.miogram.feature.chats.RemovalsViewModel
import app.miogram.feature.chat.ChatViewModel
import app.miogram.feature.chat.MediaViewerViewModel
import app.miogram.feature.chat.ScheduledViewModel
import app.miogram.feature.location.ChatLiveViewModel
import app.miogram.feature.location.PlaceViewModel
import app.miogram.nav.Forwarding
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.AttachmentDraft
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.ForwardPreview
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.Membership
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.attach.ContactShareScreen
import cn.ellerotta.core.ui.components.attach.LiveActions
import cn.ellerotta.core.ui.components.attach.LocationPickActions
import cn.ellerotta.core.ui.components.attach.LocationShareScreen
import cn.ellerotta.core.ui.components.attach.PollComposerScreen
import cn.ellerotta.core.ui.components.chat.AttachMenuActions
import cn.ellerotta.core.ui.components.chat.ChatArticleActions
import cn.ellerotta.core.ui.components.chat.ChatPlaces
import cn.ellerotta.core.ui.components.chat.ChatScreen
import cn.ellerotta.core.ui.components.location.LocationViewActions
import cn.ellerotta.core.ui.components.location.LocationViewScreen
import cn.ellerotta.core.ui.components.safety.ChatSafetyActions
import cn.ellerotta.core.ui.components.safety.ChatSafetyState
import cn.ellerotta.core.ui.components.safety.ReportActions
import cn.ellerotta.core.ui.components.chat.ChatInlineActions
import cn.ellerotta.core.ui.components.chat.ChatBotActions
import cn.ellerotta.core.ui.components.chat.ChatPollActions
import cn.ellerotta.core.ui.components.chat.ChatReactorActions
import cn.ellerotta.core.ui.components.chat.ChatPinActions
import cn.ellerotta.core.ui.components.chat.ChatTranslateActions
import cn.ellerotta.core.ui.components.chat.ChatSearchActions
import cn.ellerotta.core.ui.components.chat.ChatStickerActions
import cn.ellerotta.core.ui.components.chat.ChatAttachmentActions
import cn.ellerotta.core.ui.components.chat.ChatSendActions
import cn.ellerotta.core.ui.components.chat.ChatWallpaperActions
import cn.ellerotta.core.ui.components.chat.MediaViewer
import cn.ellerotta.core.ui.components.chat.OwnPhotoActions
import cn.ellerotta.core.ui.components.chat.ScheduledActions
import cn.ellerotta.core.ui.components.chat.ScheduledScreen
import cn.ellerotta.core.ui.components.chat.webAppThemeJson

internal fun EntryProviderScope<Route>.chatEntries(navigator: Navigator, back: () -> Unit, onOpenLink: (String) -> Unit) {
    entry<Route.Chat> { route ->
        val opening = ChatOpening(route.messageId, route.startParameter, forward = route.forward, text = route.draft, topicId = route.topicId, threadId = route.threadId, comments = route.comments, attach = route.attach)
        val key = route.topicId?.let { "${route.dialogKey}/$it" } ?: route.threadId?.let { "${route.dialogKey}/t$it" } ?: route.dialogKey
        val model = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(key = key) { it.create(route.dialogKey, opening) }
        val messages by model.messages.collectAsStateWithLifecycle()
        val downloads by model.downloads.collectAsStateWithLifecycle()
        val stickers by model.stickers.collectAsStateWithLifecycle()
        val customEmoji by model.customEmojiDocuments.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val dialog by model.dialog.collectAsStateWithLifecycle()
        val removals = hiltViewModel<RemovalsViewModel>(LocalActivity.current as ComponentActivity)
        val connection by model.connection.collectAsStateWithLifecycle()
        val unreadChats by model.unreadChats.collectAsStateWithLifecycle()
        val threadTitle by model.threadTitle.collectAsStateWithLifecycle()
        val liveModel = hiltViewModel<ChatLiveViewModel, ChatLiveViewModel.Factory>(key = "live/${route.dialogKey}") { it.create(route.dialogKey) }
        val sharers by liveModel.sharers.collectAsStateWithLifecycle()
        val withLocation = rememberLocationGate()
        val places = remember(liveModel, sharers) {
            ChatPlaces(
                onOpen = { message -> message.place?.let { navigator.go(Route.Place(route.dialogKey, message.id, it, dialog?.title ?: route.title)) } },
                sharers = sharers,
                onOpenLive = { (sharers.firstOrNull { !it.self } ?: sharers.firstOrNull())?.let { navigator.go(Route.Place(route.dialogKey, it.messageId, it.place, dialog?.title ?: route.title)) } },
                onStop = liveModel::stop,
                onAlert = liveModel::setAlert,
                onShareAlert = { seconds, radius -> withLocation { liveModel.share(dialog?.title ?: route.title, seconds, radius) } },
                alertOnOpen = route.proximity,
            )
        }
        val older by model.older.collectAsStateWithLifecycle()
        val recording by model.recording.collectAsStateWithLifecycle()
        val playback by model.playback.collectAsStateWithLifecycle()
        val stickerState by model.stickerState.collectAsStateWithLifecycle()
        val newer by model.newer.collectAsStateWithLifecycle()
        val focus by model.focus.collectAsStateWithLifecycle()
        val latest by model.latest.collectAsStateWithLifecycle()
        val membership by model.membership.collectAsStateWithLifecycle()
        val info by model.info.collectAsStateWithLifecycle()
        val startParameter by model.startParameter.collectAsStateWithLifecycle()
        val reactions by model.reactions.collectAsStateWithLifecycle()
        val draft by model.draft.collectAsStateWithLifecycle()
        val search by model.search.collectAsStateWithLifecycle()
        val searchActions = remember(model) { ChatSearchActions(onQuery = model::search, onMore = model::searchMore, onClose = model::closeSearch) }
        val inline by model.inline.collectAsStateWithLifecycle()
        val mentions by model.mentions.collectAsStateWithLifecycle()
        val inlineActions = remember(model) {
            ChatInlineActions(
                onText = model::inlineText,
                onMore = model::moreInlineResults,
                onSend = model::sendInline,
                onSwitchPm = model::switchToBot,
                onLoad = model::loadSticker,
                onSwitchWebView = model::openInlineApp,
                onMention = model::mentionTyped,
            )
        }
        val callbackAnswer by model.callbackAnswer.collectAsStateWithLifecycle()
        val login by model.login.collectAsStateWithLifecycle()
        val webApp by model.webApp.collectAsStateWithLifecycle()
        val menuButton by model.menuButton.collectAsStateWithLifecycle()
        val attachBots by model.attachBots.collectAsStateWithLifecycle()
        val commands by model.commands.collectAsStateWithLifecycle()
        val loadingArticles by model.loadingArticles.collectAsStateWithLifecycle()
        val stoppableDraft by model.stoppableDraft.collectAsStateWithLifecycle()
        val articleGate by model.articleGate.collectAsStateWithLifecycle()
        val articleUploads by model.articleUploads.collectAsStateWithLifecycle()
        val musicModel = musicModel()
        val unreadAfter by model.unreadAfter.collectAsStateWithLifecycle()
        val music by musicModel.music.collectAsStateWithLifecycle()
        val pollVotes by model.pollVotes.collectAsStateWithLifecycle()
        val pollActions = remember(model) {
            ChatPollActions(onVote = model::vote, onShowVotes = model::openPollVotes, onMoreVotes = model::morePollVotes, onCloseVotes = model::closePollVotes)
        }
        val reactors by model.reactors.collectAsStateWithLifecycle()
        val reactorActions = remember(model) {
            ChatReactorActions(
                onShow = model::openReactors,
                onMore = model::moreReactors,
                onClose = model::closeReactors,
                onOpenPeer = { key ->
                    val name = model.reactors.value?.list?.firstOrNull { it.key == key }?.reactor?.name.orEmpty()
                    model.closeReactors()
                    navigator.go(Route.PeerProfile(key, name))
                },
                onNextUnread = model::nextUnreadReaction,
                onReadAll = model::readReactions,
            )
        }
        val pinned by model.pinned.collectAsStateWithLifecycle()
        val blocked by model.blocked.collectAsStateWithLifecycle()
        val canBlock by model.canBlock.collectAsStateWithLifecycle()
        val peerBar by model.peerBar.collectAsStateWithLifecycle()
        val report by model.report.collectAsStateWithLifecycle()
        val safetyState = ChatSafetyState(blocked, canBlock, peerBar, report)
        val safetyActions = remember(model) {
            ChatSafetyActions(
                onBlock = model::block,
                onUnblock = model::unblock,
                onReportChat = model::reportChat,
                onReportMessages = model::reportMessages,
                onAddContact = model::addContact,
                onReportSpam = model::reportSpam,
                onDismissBar = model::dismissPeerBar,
                report = ReportActions(model::reportChoose, model::reportComment, model::closeReport),
            )
        }
        val forwarding by model.forwarding.collectAsStateWithLifecycle()
        val forwardTargets by model.forwardTargets.collectAsStateWithLifecycle()
        val wallpaper by model.wallpaper.collectAsStateWithLifecycle()
        val wallpaperPreviews by model.wallpaperPreviews.collectAsStateWithLifecycle()
        val wallpaperActions = remember(model, wallpaperPreviews) {
            ChatWallpaperActions(
                previews = wallpaperPreviews,
                onLoad = model::loadWallpaperPreview,
                onApply = model::applyWallpaper,
                onOpen = if (model.canSetWallpaper && route.threadId == null) ({ navigator.go(Route.Wallpapers(route.dialogKey)) }) else null,
            )
        }
        val savedTitle = stringResource(R.string.saved_messages)
        val canPin by model.canPin.collectAsStateWithLifecycle()
        val pinActions = remember(model, canPin) {
            ChatPinActions(canPin = canPin, onTapped = model::pinnedTapped, onPin = model::pin, onUnpin = model::unpin, onUnpinAll = model::unpinAll, onHide = model::hidePinned, onShow = model::showPinned)
        }
        val translateTo = stringResource(R.string.chat_translate_language)
        val translateActions = remember(model, translateTo) {
            ChatTranslateActions(onTranslate = { model.translate(it, translateTo) }, onShowOriginal = model::showOriginal)
        }
        val pickArticlePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { model.uploadArticleMedia(ArticleMediaKind.Photo, it) } }
        val pickArticleVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { model.uploadArticleMedia(ArticleMediaKind.Video, it) } }
        val pickArticleAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.uploadArticleMedia(ArticleMediaKind.Audio, it) } }
        val pickArticleFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.uploadArticleMedia(ArticleMediaKind.File, it) } }
        val articleActions = remember(model, articleGate, articleUploads) {
            articleGate?.let { gate ->
                ChatArticleActions(
                    gate,
                    onSend = model::sendArticle,
                    onClose = model::closeArticle,
                    onDeleteDraft = model::deleteArticleDraft,
                    onEdit = model::editArticle,
                    onToggle = model::toggleCheck,
                    uploads = articleUploads,
                    onPickMedia = { kind ->
                        when (kind) {
                            ArticleMediaKind.Photo -> pickArticlePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            ArticleMediaKind.Video -> pickArticleVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            ArticleMediaKind.Audio -> pickArticleAudio.launch(arrayOf("audio/*"))
                            ArticleMediaKind.File -> pickArticleFile.launch(arrayOf("*/*"))
                        }
                    },
                    onRetryUpload = model::retryArticleUpload,
                    onRemoveUpload = model::removeArticleUpload,
                    onClearUploads = model::clearArticleUploads,
                )
            }
        }
        val webAppDevice = rememberWebAppDevice(model.webAppServices)
        val webAppTheme = webAppThemeJson()
        LaunchedEffect(model) { model.openLinkedAttachApp(webAppTheme) }
        val botActions = remember(model, webAppDevice) {
            ChatBotActions(
                onCallback = model::pressCallback,
                onAnswerShown = model::callbackShown,
                onSwitchInline = { query -> navigator.go(Route.ShareTarget(query)) },
                onLogin = model::pressLogin,
                onAnswerLogin = model::answerLogin,
                onLoginOpened = model::loginOpened,
                onWebApp = model::openWebApp,
                onWebAppData = model::sendWebAppData,
                onCloseWebApp = model::closeWebApp,
                onMenuApp = model::openMenuApp,
                onAttachApp = model::openAttachApp,
                webAppDevice = webAppDevice,
            )
        }
        val stickerActions = remember(model) {
            ChatStickerActions(
                onOpenPanel = model::openStickers,
                onOpenTab = model::openStickerTab,
                onLoad = model::loadSticker,
                onSend = model::sendSticker,
                onDraft = model::stickerDraft,
                onShowSet = model::showStickerSet,
                onInstall = model::installStickerSet,
                onDismissSet = model::dismissStickerSet,
                onOpenEmoji = model::openEmoji,
                onSearchEmoji = model::searchEmoji,
                onPickEmoji = model::pickEmoji,
                onChooseTone = model::chooseTone,
                onSearchStickers = model::searchStickers,
                onOpenGifs = model::openGifs,
                onSearchGifs = model::searchGifs,
                onMoreGifs = model::moreGifs,
                onSendGif = model::sendGif,
                onKeyboardHeight = model::keyboardHeight,
                onSearchEmojiCategory = model::searchEmojiCategory,
                onSearchStickerCategory = model::searchStickerCategory,
                onReadFeatured = model::readFeatured,
                onFave = model::faveSticker,
                onRemoveGif = model::removeGif,
                onReorderSets = model::reorderSets,
            )
        }
        val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachmentDraft.MAX_ITEMS)) { uris -> model.pickAttachments(uris, asFile = false) }
        val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> model.pickAttachments(uris, asFile = true) }
        val context = LocalContext.current
        val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) model.microphoneDenied() }
        val remindedText = stringResource(R.string.chat_reminder_set)
        val attachment by model.attachment.collectAsStateWithLifecycle()
        val scheduledCount by model.scheduledCount.collectAsStateWithLifecycle()
        val attachmentActions = remember(model) {
            ChatAttachmentActions(
                onAsFile = model::setAttachmentsAsFile,
                onSpoiler = model::setAttachmentsSpoiler,
                onRemove = model::removeAttachment,
                onSend = { caption, options -> model.sendAttachments(caption, options) },
                onDismiss = model::dismissAttachments,
            )
        }
        val sendActions = remember(model) {
            ChatSendActions(
                onSend = { text, replyTo, options -> model.send(text, replyTo, options) },
                onSendVoice = { options -> model.stopVoice(true, options) },
            )
        }
        val scheduledText = stringResource(R.string.chat_scheduled_done)
        val scheduledOnlineText = stringResource(R.string.chat_scheduled_done_online)
        LaunchedEffect(model) {
            model.scheduledDates.collect { date -> Toast.makeText(context, if (date == SendOptions.WHEN_ONLINE) scheduledOnlineText else scheduledText, Toast.LENGTH_SHORT).show() }
        }
        LaunchedEffect(model) { model.reminded.collect { Toast.makeText(context, remindedText, Toast.LENGTH_SHORT).show() } }
        LaunchedEffect(model) { model.left.collect { navigator.backTo(Route.Chats) } }
        LaunchedEffect(model) { model.openBot.collect { bot -> navigator.go(Route.Chat(bot.key, bot.name)) } }
        val commentsTitle = stringResource(R.string.chat_comments_title)
        val repliesTitle = stringResource(R.string.chat_replies_title)
        LaunchedEffect(model) {
            model.openThread.collect { thread ->
                navigator.go(Route.Chat(thread.dialogKey, if (thread.comments) commentsTitle else repliesTitle, threadId = thread.topId, comments = thread.comments))
            }
        }
        Work(model) { busy ->
            ChatScreen(
                title = route.title,
                messages = messages,
                downloads = downloads,
                busy = busy,
                onBack = back,
                onSend = { model.send(it) },
                onDownload = model::download,
                onOpenProfile = { navigator.go(Route.PeerProfile(route.dialogKey, dialog?.title ?: route.title)) },
                onOpenStories = { navigator.go(Route.Stories(it)) },
                onOpenSender = { message -> message.senderKey?.let { navigator.go(Route.PeerProfile(it, message.senderName.orEmpty())) } },
                dialog = dialog,
                stickers = stickers,
                avatars = avatars,
                onAttachMedia = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onAttachFile = { pickFile.launch(arrayOf("*/*")) },
                loadingOlder = older.loading,
                onLoadOlder = model::loadOlder,
                onSendReply = { text, replyTo -> model.send(text, replyTo) },
                onEdit = model::edit,
                onDelete = model::delete,
                onRetry = model::retry,
                recording = recording,
                onVoiceStart = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        model.startVoice()
                    } else {
                        microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onVoiceStop = model::stopVoice,
                playback = playback,
                onToggleVoice = model::toggleVoice,
                onVoiceSpeed = model::cycleVoiceSpeed,
                onOpenMedia = { navigator.go(Route.Media(route.dialogKey, it.id)) },
                onOpenArticleMedia = { message, index -> navigator.go(Route.Media(route.dialogKey, message.id, index)) },
                onShowMore = model::showMore,
                loadingArticles = loadingArticles,
                stickerState = stickerState,
                stickerActions = stickerActions,
                initialText = route.draft.orEmpty(),
                focus = focus,
                onFocusShown = model::focusShown,
                newerAvailable = newer,
                onLoadNewer = model::loadNewer,
                latest = latest,
                onLatestShown = model::latestShown,
                onShowLatest = model::showLatest,
                membership = membership,
                onJoin = model::join,
                onStartBot = if (startParameter == null) null else model::startBot,
                onOpenLink = onOpenLink,
                onCommand = { model.send(FormattedText(it)) },
                onRemind = model::remind,
                customEmoji = customEmoji,
                reactions = reactions,
                onReact = model::react,
                onForward = { choice ->
                    val picked = messages.filter { it.id in choice.ids }
                    val forward = Forwarding(route.dialogKey, choice.ids, choice.dropAuthor, choice.dropCaptions, picked, dialog?.title ?: route.title)
                    navigator.go(Route.Chat(choice.to.key, if (choice.to.isSaved) savedTitle else choice.to.title, forward = forward))
                },
                forwardTargets = forwardTargets,
                onShowMessage = model::showMessage,
                onTyping = model::typed,
                draft = draft,
                onDraftShown = model::draftShown,
                onDraftChange = model::draftChanged,
                search = search,
                searchActions = searchActions,
                onDialogAction = model::act,
                onLeave = if (info?.membership == Membership.Member) model::leave else null,
                onRemove = if (route.threadId == null && route.topicId == null) {
                    { removal, revoke ->
                        dialog?.let { removals.remove(it, removal, revoke) }
                        navigator.backTo(Route.Chats)
                    }
                } else {
                    null
                },
                inline = inline,
                inlineActions = inlineActions,
                mentions = mentions,
                botActions = botActions,
                callbackAnswer = callbackAnswer,
                login = login,
                webApp = webApp,
                commands = commands,
                onLoadCommands = model::loadCommands,
                onStopDraft = if (stoppableDraft == null) null else model::stopDraft,
                articles = articleActions,
                onSaveToDownloads = model::saveToDownloads,
                music = music?.takeIf { it.track.dialogKey == route.dialogKey },
                onPlayMusic = { musicModel.play(route.dialogKey, it) },
                player = { MusicBar(keepLast = true) },
                playerShown = music != null,
                onOpenReplies = if (route.threadId == null) model::openReplies else null,
                unreadAfter = unreadAfter,
                polls = pollActions,
                pollVotes = pollVotes,
                pinned = pinned,
                pins = pinActions,
                translations = translateActions,
                forwarding = forwarding?.let { ForwardPreview(it.messages, it.fromTitle) },
                onSendForward = model::sendForward,
                onCancelForward = model::cancelForward,
                wallpaper = wallpaper,
                openSearch = route.search,
                reactors = reactors,
                reactorActions = reactorActions,
                menuButton = menuButton,
                attachBots = attachBots,
                wallpaperActions = wallpaperActions,
                connection = connection,
                unreadChats = unreadChats,
                thread = threadTitle,
                subtitle = if (route.topicId != null) dialog?.title else null,
                safety = safetyState,
                safetyActions = safetyActions,
                sending = sendActions,
                attachment = attachment,
                attachmentActions = attachmentActions,
                scheduledCount = scheduledCount,
                attachMenu = AttachMenuActions(
                    onPoll = { navigator.go(Route.NewPoll(route.dialogKey, dialog?.kind ?: PeerKind.User, model.answeredNow(), route.topicId)) },
                    onLocation = {
                        val person = dialog?.takeIf { it.kind == PeerKind.User && !it.isSaved }?.title
                        navigator.go(Route.SendLocation(route.dialogKey, dialog?.title ?: route.title, model.answeredNow(), route.topicId, alertName = person))
                    },
                    onContact = { navigator.go(Route.ShareContact(route.dialogKey, model.answeredNow(), route.topicId)) },
                ),
                onOpenScheduled = if (route.threadId == null) ({ navigator.go(Route.Scheduled(route.dialogKey)) }) else null,
                onReplying = model::replying,
                places = places,
            )
        }
    }
    entry<Route.NewPoll> { route ->
        val model = hiltViewModel<NewPollViewModel, NewPollViewModel.Factory>(key = "poll/${route.dialogKey}/${route.topicId}") { it.create(route.dialogKey, route.kind, route.replyTo, route.topicId) }
        val draft by model.draft.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.sent.collect { back() } }
        Work(model) { busy -> PollComposerScreen(draft, busy, model::change, model::send, back) }
    }
    entry<Route.Place> { route ->
        val model = hiltViewModel<PlaceViewModel, PlaceViewModel.Factory>(key = "place/${route.dialogKey}/${route.messageId}") { it.create(route.dialogKey, route.messageId, route.place) }
        val state by model.state.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val chooserTitle = stringResource(R.string.location_share)
        val withLocation = rememberLocationGate()
        Work(model) {
            LocationViewScreen(
                state,
                avatars,
                LocationViewActions(
                    onDirections = { openUri(context, state.place.directionsUri) },
                    onShare = {
                        val text = state.place.copy(title = state.title, address = state.address).shareText
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), chooserTitle))
                    },
                    onOpenInMaps = { openUri(context, state.place.copy(title = state.title).geoUri) },
                    onBack = back,
                    onFocus = model::focus,
                    onAlert = model::setAlert,
                    onShareAlert = { seconds, radius -> withLocation { model.share(route.title, seconds, radius) } },
                    onTravel = model::travel,
                ),
            )
        }
    }
    entry<Route.SendLocation> { route ->
        val model = hiltViewModel<LocationViewModel, LocationViewModel.Factory>(key = "location/${route.dialogKey}/${route.topicId}") { it.create(route.dialogKey, route.title, route.replyTo, route.topicId, route.alertName) }
        val state by model.state.collectAsStateWithLifecycle()
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.locate() }
        LaunchedEffect(model) { model.sent.collect { back() } }
        Work(model) { busy ->
            LocationShareScreen(
                state = state,
                busy = busy,
                onAllow = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                onRetry = model::locate,
                onSendCurrent = model::sendCurrent,
                live = LiveActions(onShare = model::startLive, onStop = model::stopLive),
                onBack = back,
                pick = LocationPickActions(onPick = model::pick, onQuery = model::search, onSuggestion = model::sendSuggestion, onSendPlace = model::sendPlace),
            )
        }
    }
    entry<Route.ShareContact> { route ->
        val model = hiltViewModel<ContactShareViewModel, ContactShareViewModel.Factory>(key = "contact/${route.dialogKey}/${route.topicId}") { it.create(route.dialogKey, route.replyTo, route.topicId) }
        val shown by model.shown.collectAsStateWithLifecycle()
        val query by model.query.collectAsStateWithLifecycle()
        val loaded by model.loaded.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        LaunchedEffect(model) { model.sent.collect { back() } }
        Work(model) { busy -> ContactShareScreen(shown, query, loaded, avatars, busy, model::search, model::send, back) }
    }
    entry<Route.Scheduled> { route ->
        val model = hiltViewModel<ScheduledViewModel, ScheduledViewModel.Factory>(key = "scheduled/${route.dialogKey}") { it.create(route.dialogKey) }
        val messages by model.messages.collectAsStateWithLifecycle()
        val loaded by model.loaded.collectAsStateWithLifecycle()
        val actions = remember(model) { ScheduledActions(onSendNow = model::sendNow, onReschedule = model::reschedule, onEdit = model::edit, onDelete = model::delete) }
        Work(model) { ScheduledScreen(stringResource(R.string.chat_scheduled_title), messages, loaded, actions, back) }
    }
    entry<Route.Media> { route ->
        val model = hiltViewModel<MediaViewerViewModel, MediaViewerViewModel.Factory>(key = "${route.dialogKey}/${route.messageId}/${route.articleIndex}/${route.profilePhotos}/${route.gifs}") {
            it.create(route.dialogKey, route.messageId, route.articleIndex, route.profilePhotos, route.gifs)
        }
        val items by model.items.collectAsStateWithLifecycle()
        val initial by model.initial.collectAsStateWithLifecycle()
        val downloads by model.downloads.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val savedText = stringResource(R.string.viewer_saved)
        val shareTitle = stringResource(R.string.viewer_share)
        LaunchedEffect(model) { model.saved.collect { Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show() } }
        LaunchedEffect(model) { model.emptied.collect { back() } }
        val sources = rememberAvatarSources(model::captureUri, onCameraDenied = model::cameraDenied) { uri, captured -> navigator.go(Route.AvatarCrop(uri.toString(), captured)) }
        val own = remember(model, sources) { if (model.own) OwnPhotoActions(model::setMain, model::delete, sources.gallery, sources.camera) else null }
        Work(model) {
            MediaViewer(
                items = items,
                initial = initial,
                downloads = downloads,
                onBack = back,
                onPage = model::onPage,
                onSave = model::save,
                onShare = { media ->
                    model.share(media) { file, mimeType ->
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val send = Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }
                },
                video = { media, active, modifier ->
                    VideoPlayer({ model.stream(media) }, active, looping = media.kind == MediaKind.Gif, modifier = modifier)
                },
                onSaveToDownloads = model::saveToDownloads,
                own = own,
            )
        }
    }
}

@Composable
private fun rememberLocationGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending.value?.invoke()
        pending.value = null
    }
    return remember(context, permission) {
        { action: () -> Unit ->
            val asked = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (asked.any { context.applicationContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                action()
            } else {
                pending.value = action
                permission.launch(asked)
            }
        }
    }
}

private fun openUri(context: Context, uri: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
}
