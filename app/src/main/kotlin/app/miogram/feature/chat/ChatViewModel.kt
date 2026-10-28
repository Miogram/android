package app.miogram.feature.chat

import android.net.Uri
import androidx.lifecycle.viewModelScope
import app.miogram.connection.ConnectionStatus
import app.miogram.data.Attachment
import app.miogram.data.Attachments
import app.miogram.data.MediaFileInfo
import app.miogram.data.AvatarStore
import app.miogram.data.BotDraftsStore
import app.miogram.data.EphemeralStore
import app.miogram.music.MusicPlayer
import app.miogram.push.PushNotifier
import app.miogram.data.DownloadsSaver
import app.miogram.data.DialogsStore
import app.miogram.data.DialogsState
import app.miogram.data.SettingsStore
import app.miogram.connection.NetworkMonitor
import app.miogram.data.VoiceRecorder
import app.miogram.di.ApplicationScope
import app.miogram.data.telegram.ArticlesRepository
import app.miogram.data.telegram.ContactsRepository
import app.miogram.data.telegram.SafetyRepository
import app.miogram.data.telegram.ChatEvent
import app.miogram.data.telegram.DialogsRepository
import app.miogram.data.telegram.EphemeralRepository
import app.miogram.data.telegram.ChannelsRepository
import app.miogram.data.telegram.ForumRepository
import app.miogram.data.telegram.LinksRepository
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.MessagesRepository
import app.miogram.data.telegram.PollsRepository
import app.miogram.data.telegram.PinsRepository
import app.miogram.data.telegram.TranslationsRepository
import app.miogram.data.telegram.ReactionsRepository
import app.miogram.data.telegram.StickersRepository
import app.miogram.data.telegram.StoriesRepository
import app.miogram.data.telegram.UpdatesRepository
import app.miogram.data.telegram.BotsRepository
import app.miogram.data.telegram.OutgoingMedia
import app.miogram.data.telegram.ScheduledRepository
import cn.ellerotta.core.model.AttachmentDraft
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.albumsOf
import app.miogram.data.telegram.WallpapersRepository
import app.miogram.feature.common.WorkViewModel
import app.miogram.nav.Forwarding
import app.miogram.feature.chats.act
import app.miogram.feature.chats.withStory
import cn.ellerotta.core.model.ArticleGate
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleUpload
import cn.ellerotta.core.model.asFormattedText
import cn.ellerotta.core.model.toggled
import cn.ellerotta.core.model.ChatAction
import cn.ellerotta.core.model.ChatInfo
import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.MemberCounts
import cn.ellerotta.core.model.Membership
import cn.ellerotta.core.ui.components.chats.ChatAction as DialogAction
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.model.ChatSearchState
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.Draft
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.ReactorsState
import cn.ellerotta.core.model.PickedEmoji
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.InlineResult
import cn.ellerotta.core.model.InlineState
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.CallbackAnswer
import cn.ellerotta.core.model.LoginAnswer
import cn.ellerotta.core.model.LoginState
import cn.ellerotta.core.model.OpenWebApp
import app.miogram.data.WebAppServices
import app.miogram.nav.AttachLaunch
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.BotMenuButton
import cn.ellerotta.core.model.PeerBar
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ReportState
import cn.ellerotta.core.model.BotCommand
import cn.ellerotta.core.model.UiError
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.model.VoiceRecording
import cn.ellerotta.core.model.UploadKind
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.trimmed
import cn.ellerotta.core.model.PollVotesState
import cn.ellerotta.core.model.PinnedMessages
import cn.ellerotta.core.model.RepliesInfo
import cn.ellerotta.core.model.ReplyThread
import cn.ellerotta.core.model.ThreadTitle
import cn.ellerotta.core.model.timelineOf
import cn.ellerotta.core.model.EPHEMERAL_IDS
import cn.ellerotta.core.model.EmojiCategoryGroup
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.ephemeralCommandOf
import cn.ellerotta.core.model.ChatStickerState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel @AssistedInject constructor(
    @Assisted private val dialogKey: String,
    @Assisted opening: ChatOpening,
    private val messagesRepository: MessagesRepository,
    private val dialogs: DialogsRepository,
    private val updates: UpdatesRepository,
    private val links: LinksRepository,
    private val channels: ChannelsRepository,
    private val forum: ForumRepository,
    private val bots: BotsRepository,
    private val store: DialogsStore,
    private val connectionStatus: ConnectionStatus,
    private val botDrafts: BotDraftsStore,
    private val ephemeralStore: EphemeralStore,
    private val ephemeral: EphemeralRepository,
    private val articles: ArticlesRepository,
    private val attachments: Attachments,
    private val scheduledRepository: ScheduledRepository,
    mediaRepository: MediaRepository,
    stickersRepository: StickersRepository,
    reactionsRepository: ReactionsRepository,
    pollsRepository: PollsRepository,
    pinsRepository: PinsRepository,
    translationsRepository: TranslationsRepository,
    safetyRepository: SafetyRepository,
    contactsRepository: ContactsRepository,
    wallpapers: WallpapersRepository,
    recorder: VoiceRecorder,
    private val avatarStore: AvatarStore,
    private val saver: DownloadsSaver,
    private val notifier: PushNotifier,
    music: MusicPlayer,
    settings: SettingsStore,
    network: NetworkMonitor,
    stories: StoriesRepository,
    @ApplicationScope appScope: CoroutineScope,
    val webAppServices: WebAppServices,
) : WorkViewModel() {
    private val messagesState = MutableStateFlow<List<MessageItem>>(emptyList())
    private val media = ChatMedia(viewModelScope, mediaRepository, ::fail, saver::downloaded) { settings.autoDownload.value.allows(it, network.type()) }
    private val voice = ChatVoice(viewModelScope, dialogKey, recorder, music, messagesRepository, { messagesState.value }, ::fail)
    private val stickerPanel = ChatStickers(viewModelScope, stickersRepository, settings, { shown, block -> refreshWith(shown, block) }) { workWith(it) }
    private val customEmoji = ChatCustomEmoji(viewModelScope, stickersRepository) { media.loadSticker(it, preview = false) }
    private val reacting = ChatReacting(viewModelScope, dialogKey, reactionsRepository, messagesState, { workWith(it) }, customEmoji::requestIds, { fail(it) }) { media.loadSticker(it, preview = false) }
    private val polling = ChatPolls(dialogKey, pollsRepository, messagesState, { workWith(it) }, avatarStore::load)
    private val reactorsList = ChatReactors(dialogKey, reactionsRepository, store, { workWith(it) }, avatarStore::load, ::showMessage)

    val reactors: StateFlow<ReactorsState?> = reactorsList.ui

    fun openReactors(id: Int) {
        val total = messages.value.firstOrNull { it.id == id }?.reactions?.sumOf { it.count } ?: return
        reactorsList.open(id, total)
    }

    fun moreReactors() = reactorsList.more()

    fun closeReactors() = reactorsList.close()

    fun nextUnreadReaction() = reactorsList.nextUnread(dialog.value?.reactions ?: 0)

    fun readReactions() = reactorsList.readAll()

    private val chatWallpapers = ChatWallpapers(viewModelScope, dialogKey, wallpapers) { workWith(it) }

    val wallpaper: StateFlow<ChatWallpaper?> = chatWallpapers.shown

    val canSetWallpaper: Boolean get() = chatWallpapers.allowsOwn

    val wallpaperPreviews: StateFlow<Map<String, String>> = chatWallpapers.previews

    fun loadWallpaperPreview(wallpaper: Wallpaper) = chatWallpapers.loadPreview(wallpaper)

    fun applyWallpaper(message: MessageItem) = chatWallpapers.apply(message)

    val pollVotes: StateFlow<PollVotesState?> = polling.votes

    fun vote(id: Int, options: List<String>) = polling.vote(id, options)

    fun openPollVotes(id: Int) = polling.openVotes(id)

    fun morePollVotes() = polling.moreVotes()

    fun closePollVotes() = polling.closeVotes()

    private val threadId = opening.threadId
    private val topicId = opening.topicId ?: threadId
    private val olderState = MutableStateFlow(OlderState())
    private val threadState = MutableStateFlow<ReplyThread?>(null)

    private val attach = ChatAttachments(viewModelScope, attachments)
    private val scheduled = ChatScheduled(viewModelScope, dialogKey, scheduledRepository, enabled = threadId == null)

    val scheduledCount: StateFlow<Int> = scheduled.count

    val scheduledDates: SharedFlow<Int> = scheduled.dates

    val thread: StateFlow<ReplyThread?> = threadState.asStateFlow()

    private val threadGone = MutableStateFlow(0)

    val threadTitle: StateFlow<ThreadTitle?> = combine(threadState, messagesState, threadGone) { thread, list, gone ->
        if (threadId == null) return@combine null
        if (thread == null) return@combine ThreadTitle(opening.comments)
        val base = thread.messages.firstOrNull { it.id == thread.topId }?.replies?.count ?: 0
        val newest = thread.maxId ?: thread.topId
        val newer = list.count { it.id > newest && !it.pending && !it.failed }
        ThreadTitle(opening.comments, (base + newer - gone).coerceAtLeast(0))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, if (threadId == null) null else ThreadTitle(opening.comments))

    private val unreadAfterState = MutableStateFlow<Int?>(null)

    val unreadAfter: StateFlow<Int?> = unreadAfterState.asStateFlow()
    private val searching = ChatSearch(viewModelScope, dialogKey, messagesRepository, ::fail, topicId)
    private val inlineMode: ChatInline = ChatInline(viewModelScope, dialogKey, bots) { mentioning.person(it) }
    private val mentioning: ChatMentions = ChatMentions(viewModelScope, dialogKey, { dialog.value?.kind }, channels, { inlineMode.recentBots() }) {
        messagesState.value.asReversed().mapNotNull { it.senderKey }.distinct()
    }
    private val pins = ChatPins(viewModelScope, dialogKey, topicId, pinsRepository, { workWith(it) }, ::prepare)

    val pinned: StateFlow<PinnedMessages> = pins.ui

    private val safety = ChatSafety(
        viewModelScope, dialogKey, dialogKey.startsWith("u") && threadId == null && topicId == null, safetyRepository, contactsRepository, { workWith(it) },
        contactAdded = { store.change(dialogKey) { it.copy(contact = true) } },
    )

    val blocked: StateFlow<Boolean> = safety.blocked

    val peerBar: StateFlow<PeerBar> = safety.bar

    val report: StateFlow<ReportState?> = safety.report

    fun block() = safety.block()

    fun unblock() = safety.unblock()

    fun reportChat() = safety.reportChat()

    fun reportMessages(ids: List<Int>) = safety.reportMessages(ids)

    fun reportChoose(id: String) = safety.reportChoose(id)

    fun reportComment(text: String) = safety.reportComment(text)

    fun closeReport() = safety.closeReport()

    fun reportSpam() = safety.reportSpam()

    fun dismissPeerBar() = safety.dismissBar()

    fun addContact(firstName: String, lastName: String, sharePhone: Boolean) = safety.addContact(firstName, lastName, sharePhone)

    fun pinnedTapped(): Int? = pins.tapped()

    fun showPinned(id: Int) = pins.show(id)

    fun pin(id: Int, silent: Boolean, forBoth: Boolean) = pins.pin(id, silent, forBoth)

    fun unpin(id: Int) = pins.unpin(id)

    fun unpinAll() = pins.unpinAll()

    fun hidePinned() = pins.hide()

    private val translations = ChatTranslations(dialogKey, translationsRepository) { workWith(it) }

    fun translate(id: Int, language: String) {
        messagesState.value.firstOrNull { it.id == id }?.let { translations.translate(it, language) }
    }

    fun showOriginal(id: Int) = translations.showOriginal(id)

    private val ephemeralHere = ephemeralStore.state
        .map { state -> state[dialogKey].orEmpty().filter(::shown) }
        .distinctUntilChanged()

    private val queued: StateFlow<List<MessageItem>> = messagesRepository.outgoing(dialogKey, topicId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val messages: StateFlow<List<MessageItem>> = combine(combine(messagesState, queued) { loaded, own -> loaded + own }, ephemeralHere, botDrafts.state, threadState, olderState) { list, shownEphemeral, drafts, thread, older ->
        val replaced = drafts.replaced[dialogKey].orEmpty()
        val here = drafts.drafts.filter { it.key == dialogKey && shown(it.message) }
        val ids = if (here.any { it.replacedBy != null }) list.mapTo(HashSet()) { it.id } else emptySet()
        val streaming = here.filter { it.replacedBy == null || it.replacedBy !in ids }.map { it.message }
        val real = if (replaced.isEmpty()) list else list.map { message -> replaced[message.id]?.let { message.copy(replacedDraft = it) } ?: message }
        val start = thread?.messages?.takeIf { !older.more }.orEmpty()
        timelineOf(start + real.filter { message -> start.none { it.id == message.id } }, shownEphemeral, streaming)
    }.combine(translations.shown) { list, shown -> translations.apply(list, shown) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun ephemeralAt(id: Int): MessageItem? = ephemeralStore.state.value[dialogKey]?.firstOrNull { it.id == id }

    val stoppableDraft: StateFlow<Long?> = botDrafts.state
        .map { state -> state.drafts.lastOrNull { it.key == dialogKey && shown(it.message) && it.canStop && it.replacedBy == null }?.randomId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun stopDraft() {
        val randomId = stoppableDraft.value ?: return
        val draft = botDrafts.stop(randomId) ?: return
        work { messagesRepository.setTyping(dialogKey, ChatAction.StopDraft(randomId), draft.topicId) }
    }

    val search: StateFlow<ChatSearchState> = searching.ui

    fun search(query: String) = searching.search(query)

    fun searchMore() = searching.loadMore()

    fun closeSearch() = searching.close()

    val inline: StateFlow<InlineState> = inlineMode.ui

    fun inlineText(text: String) = inlineMode.text(text)

    val mentions: StateFlow<List<ChatMember>> = mentioning.ui

    fun mentionTyped(query: String?, atStart: Boolean) = mentioning.typed(query, atStart)

    fun moreInlineResults() = inlineMode.loadMore()

    private val openBotFlow = MutableSharedFlow<InlineBot>(extraBufferCapacity = 1)

    val openBot: SharedFlow<InlineBot> = openBotFlow.asSharedFlow()

    fun sendInline(result: InlineResult, replyTo: Int? = null) {
        inlineMode.clear()
        typing.cancel()
        drafts.sent()
        store.change(dialogKey) { it.copy(draft = null) }
        work {
            try {
                bots.sendInlineResult(dialogKey, result.queryId, result.id, replyTo?.takeIf { it < EPHEMERAL_IDS }, topicId)
            } finally {
                load()
            }
        }
    }

    fun switchToBot() {
        val state = inlineMode.ui.value
        val bot = state.bot ?: return
        val switch = state.results?.switchPm ?: return
        work {
            links.startBot(bot.key, switch.startParameter)
            openBotFlow.tryEmit(bot)
        }
    }

    private val callbackState = MutableStateFlow<CallbackAnswer?>(null)

    val callbackAnswer: StateFlow<CallbackAnswer?> = callbackState.asStateFlow()

    fun pressCallback(messageId: Int, data: ByteArray) {
        val shownOnlyHere = ephemeralAt(messageId)?.ephemeral
        work {
            val answer = if (shownOnlyHere != null) ephemeral.callback(dialogKey, shownOnlyHere.id, data) else bots.callback(dialogKey, messageId, data)
            callbackState.value = answer.takeIf { it.message != null || it.url != null }
        }
    }

    fun callbackShown() {
        callbackState.value = null
    }

    private val loginState = MutableStateFlow(LoginState())
    private var loginButton: Pair<Int, BotButton.LoginUrl>? = null

    val login: StateFlow<LoginState> = loginState.asStateFlow()

    fun pressLogin(messageId: Int, button: BotButton.LoginUrl) {
        if (ephemeralAt(messageId) != null) {
            loginState.value = LoginState(url = button.url, confirmed = false)
            return
        }
        work {
            when (val answer = bots.requestLogin(dialogKey, messageId, button)) {
                is LoginAnswer.Ask -> {
                    loginButton = messageId to button
                    loginState.value = LoginState(request = answer.request)
                }
                is LoginAnswer.Open -> loginState.value = LoginState(url = answer.url)
                LoginAnswer.Plain -> loginState.value = LoginState(url = button.url, confirmed = false)
            }
        }
    }

    fun answerLogin(accepted: Boolean?, writeAllowed: Boolean = false, sharePhone: Boolean = false) {
        val (messageId, button) = loginButton ?: return
        loginButton = null
        loginState.value = LoginState()
        when (accepted) {
            null -> Unit
            false -> loginState.value = LoginState(url = button.url)
            true -> work { loginState.value = LoginState(url = bots.acceptLogin(dialogKey, messageId, button, writeAllowed, sharePhone)) }
        }
    }

    fun loginOpened() {
        loginState.value = LoginState()
    }

    private val webApps = ChatWebApps(viewModelScope, dialogKey, bots) { workWith(it) }

    val webApp: StateFlow<OpenWebApp?> = webApps.app

    val menuButton: StateFlow<BotMenuButton?> = webApps.menuButton

    fun openWebApp(message: MessageItem, button: BotButton.WebApp, theme: String) {
        val bot = bots.webAppBot(dialogKey, message.senderId, message.viaBot) ?: return
        webApps.openButton(bot, button, theme)
    }

    fun openMenuApp(theme: String) = webApps.openMenu(theme)

    fun openAttachApp(bot: AttachMenuBot, theme: String, writeAllowed: Boolean) = webApps.openAttach(bot, theme, writeAllowed)

    private var linkedAttachApp = opening.attach

    fun openLinkedAttachApp(theme: String) {
        val launch = linkedAttachApp ?: return
        linkedAttachApp = null
        webApps.openAttach(launch.bot, theme, writeAllowed = false, startParameter = launch.startParameter)
    }

    fun openInlineApp(theme: String) {
        val state = inlineMode.ui.value
        val bot = state.bot ?: return
        val switch = state.results?.switchWebView ?: return
        webApps.openInline(bot, switch, theme)
    }

    fun sendWebAppData(data: String) = webApps.sendData(data)

    fun closeWebApp() = webApps.close()

    private val commandsState = MutableStateFlow<List<BotCommand>>(emptyList())
    private var commandsLoad: Deferred<List<BotCommand>>? = null

    val commands: StateFlow<List<BotCommand>> = commandsState.asStateFlow()

    fun loadCommands() {
        viewModelScope.launch { commandsNow() }
    }

    private suspend fun commandsNow(): List<BotCommand> {
        if (dialog.value?.isSaved == true) return emptyList()
        val load = commandsLoad ?: viewModelScope.async { bots.commands(dialogKey) }.also { commandsLoad = it }
        return try {
            load.await().also { commandsState.value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (commandsLoad === load) commandsLoad = null
            emptyList()
        }
    }

    private val threadsFlow = MutableSharedFlow<ReplyThread>(extraBufferCapacity = 1)

    val openThread: SharedFlow<ReplyThread> = threadsFlow.asSharedFlow()

    fun openReplies(id: Int) {
        work { threadsFlow.tryEmit(messagesRepository.discussion(dialogKey, id)) }
    }

    private var storedTop: Int? = null

    val older: StateFlow<OlderState> = olderState.asStateFlow()

    private val newerState = MutableStateFlow(false)
    private var loadingNewer = false
    private var newerJob: Job? = null
    private var loadingLatest = false

    val newer: StateFlow<Boolean> = newerState.asStateFlow()

    private val focusState = MutableStateFlow(opening.messageId)

    val focus: StateFlow<Int?> = focusState.asStateFlow()

    private val latestState = MutableStateFlow<Int?>(null)

    val latest: StateFlow<Int?> = latestState.asStateFlow()

    private val infoState = MutableStateFlow<ChatInfo?>(null)
    private val membershipState = MutableStateFlow(if (dialogKey.startsWith("c") && threadId == null) null else Membership.Member)

    val info: StateFlow<ChatInfo?> = infoState.asStateFlow()

    val canPin: StateFlow<Boolean> = infoState
        .map { info -> !dialogKey.startsWith("c") || info?.canPin == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, !dialogKey.startsWith("c"))

    val membership: StateFlow<Membership?> = membershipState.asStateFlow()

    private val leftFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val left: SharedFlow<Unit> = leftFlow.asSharedFlow()

    private val startState = MutableStateFlow(opening.startParameter)

    val startParameter: StateFlow<String?> = startState.asStateFlow()
    val downloads: StateFlow<Map<String, DownloadState>> = media.downloads
    val stickers: StateFlow<Map<String, ByteArray>> = media.stickers

    val customEmojiDocuments: StateFlow<Map<Long, MediaItem>> = customEmoji.documents
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars

    val stickerState: StateFlow<ChatStickerState> = stickerPanel.ui

    val reactions: StateFlow<ChatReactionState> = reacting.ui

    val recording: StateFlow<VoiceRecording?> = voice.recording

    val playback: StateFlow<VoicePlayback?> = voice.playback

    private val countsState = MutableStateFlow<MemberCounts?>(null)

    val dialog: StateFlow<DialogItem?> = combine(store.decorated, stories.rings, countsState) { state, rings, counts -> state.find(dialogKey)?.withStory(rings)?.withCounts(counts) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.decorated.value.find(dialogKey)?.withStory(stories.rings.value))

    val canBlock: StateFlow<Boolean> = dialog.map { dialogKey.startsWith("u") && threadId == null && topicId == null && it?.isSaved != true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, dialogKey.startsWith("u") && threadId == null && topicId == null && dialog.value?.isSaved != true)

    private fun DialogItem.withCounts(counts: MemberCounts?) = if (counts == null) this else copy(members = counts.members ?: members, membersOnline = counts.online)

    val connection: StateFlow<ChatsStatus> = connectionStatus.chat.stateIn(viewModelScope, SharingStarted.Eagerly, connectionStatus.chatNow)

    val unreadChats: StateFlow<Int> = store.state
        .map { it.unreadChatsBesides(dialogKey) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.state.value.unreadChatsBesides(dialogKey))

    private fun DialogsState.find(key: String) = (dialogs + archived).firstOrNull { it.key == key }

    val attachBots: StateFlow<List<AttachMenuBot>> = combine(webApps.attachBots, dialog) { list, dialog ->
        val kind = dialog?.kind ?: when {
            dialogKey.startsWith("u") -> PeerKind.User
            dialogKey.startsWith("c") && !dialogKey.startsWith("ch") -> PeerKind.Group
            else -> null
        }
        if (kind == null) emptyList() else list.filter { it.allowedIn(dialogKey, kind) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val forwardTargets: StateFlow<List<DialogItem>> = store.decorated
        .map { state -> state.dialogs.sortedWith(compareByDescending<DialogItem> { it.pinned }.thenByDescending { it.date }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val typing = ChatTyping(viewModelScope, { action -> if (dialog.value?.isSaved != true) messagesRepository.setTyping(dialogKey, action, topicId) })

    private val drafts = ChatDraft(viewModelScope, appScope, Draft(FormattedText(opening.text.orEmpty()))) { draft ->
        if (topicId == null) {
            messagesRepository.saveDraft(dialogKey, draft)
            store.change(dialogKey) { it.copy(draft = draft.takeIf { !draft.isEmpty }) }
        }
    }

    val draft: StateFlow<Draft?> = drafts.restore

    private val articleGateState = MutableStateFlow<ArticleGate?>(null)

    val articleGate: StateFlow<ArticleGate?> = articleGateState.asStateFlow()

    init {
        if (dialogKey.startsWith("c")) {
            val known = channels.storedInfo(dialogKey)
            if (known != null) {
                infoState.value = known
                if (threadId == null) membershipState.value = known.membership
            }
            refresh(known != null) {
                val loaded = channels.info(dialogKey)
                infoState.value = loaded
                if (threadId == null) membershipState.value = loaded?.membership ?: Membership.Member
            }
        }
        threadId?.let { top ->
            work {
                val thread = messagesRepository.discussion(dialogKey, top)
                threadState.value = thread
                unreadAfterState.value = thread.readInboxMaxId?.takeIf { thread.unread > 0 }
                thread.readOutboxMaxId?.let { readOwn(it) }
                prepare(thread.messages)
            }
        }
        val linked = opening.messageId
        if (linked != null) {
            work {
                loadAround(linked)
                markRead()
            }
        } else {
            viewModelScope.launch {
                showStored()
                refresh(messagesState.value.isNotEmpty()) {
                    load()
                    markRead()
                }
            }
        }
        chatWallpapers.load()
        viewModelScope.launch {
            try {
                articleGateState.value = articles.gate()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        if (dialogKey.startsWith("c") && threadId == null && topicId == null && dialog.value?.kind != PeerKind.Channel) {
            viewModelScope.launch {
                try {
                    countsState.value = channels.memberCounts(dialogKey)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
        viewModelScope.launch { dialog.map { it?.emojiStatus }.distinctUntilChanged().collect { id -> if (id != null) customEmoji.requestIds(listOf(id)) } }
        updates.follow(dialogKey, open = true)
        scheduled.changed()
        pins.load()
        safety.load()
        botDrafts.start()
        ephemeralStore.start()
        viewModelScope.launch { ephemeralHere.collect(::prepare) }
        if (topicId == null) {
            store.reading(dialogKey, open = true)
            viewModelScope.launch { notifier.clearChat(dialogKey) }
        }
        viewModelScope.launch { updates.events().collect(::apply) }
        viewModelScope.launch { messagesRepository.outgoingProgress.collect(media::uploads) }
        viewModelScope.launch { messagesRepository.outgoingSent.filter { it == dialogKey }.collect { runCatching { load() } } }
        viewModelScope.launch {
            updates.events().filter { it == ChatEvent.Reload }.debounce(RELOAD_PAUSE).collect {
                runCatching {
                    load()
                    pins.load()
                    markRead()
                }
            }
        }
        if (topicId == null) viewModelScope.launch { dialog.map { it?.draft }.distinctUntilChanged().collect(drafts::remote) }
        viewModelScope.launch { mentioning.ui.map { list -> list.mapNotNull { it.avatarKey } }.distinctUntilChanged().collect(avatarStore::load) }
        val kind = dialog.value?.kind
        if (dialogKey.startsWith("u") && (kind == null || kind == PeerKind.Bot)) webApps.loadMenuButton()
        webApps.loadAttachBots()
    }

    private fun shown(message: MessageItem) = when {
        threadId != null -> message.threadId == threadId
        topicId != null -> (message.topicId ?: ForumTopic.GENERAL_TOPIC) == topicId
        else -> true
    }

    private fun readOwn(maxId: Int) {
        messagesState.update { list -> list.map { if (it.out && !it.pending && !it.read && it.id <= maxId) it.copy(read = true) else it } }
    }

    private fun repliesRead(id: Int, maxId: Int) {
        messagesState.update { list -> list.map { message -> message.replies?.takeIf { message.id == id }?.let { message.copy(replies = it.copy(readMaxId = maxOf(it.readMaxId ?: 0, maxId))) } ?: message } }
    }

    private fun threadsHere() = dialogKey.startsWith("ch") && dialog.value?.kind != PeerKind.Channel

    private fun repliedTo(top: MessageItem, reply: MessageItem): RepliesInfo? {
        val replies = top.replies ?: return RepliesInfo(count = 1, maxId = reply.id, readMaxId = if (reply.out) reply.id else null)
        return replies.copy(count = replies.count + 1, maxId = maxOf(replies.maxId ?: 0, reply.id), readMaxId = if (reply.out) maxOf(replies.readMaxId ?: 0, reply.id) else replies.readMaxId)
    }

    private fun apply(event: ChatEvent) {
        when (event) {
            is ChatEvent.NewMessage -> if (event.key == dialogKey) {
                val message = event.message
                if (threadsHere()) message.threadId?.let { top -> messagesState.update { list -> list.map { if (it.id == top) it.copy(replies = repliedTo(it, message)) else it } } }
                if (!shown(message)) return
                if (!message.out) viewModelScope.launch { runCatching { markRead(incoming = true) } }
                if (newerState.value) return
                messagesState.update { list ->
                    val pending = if (message.out) list.firstOrNull { it.pending && !it.failed && it.text == message.text } else null
                    (list.filter { it !== pending && it.id != message.id } + message).sortedBy { it.id }
                }
                prepare(listOf(message))
            }
            is ChatEvent.Edited -> if (event.key == dialogKey && shown(event.message)) {
                messagesState.update { list -> list.map { if (it.id == event.message.id) event.message else it } }
                prepare(listOf(event.message))
                pins.edited(event.message)
                translations.edited(event.message)
            }
            is ChatEvent.Deleted -> if (event.key == null || event.key == dialogKey) {
                if (threadId != null) {
                    val newest = threadState.value?.let { it.maxId ?: it.topId } ?: 0
                    val gone = messagesState.value.count { it.id in event.ids && it.id <= newest }
                    if (gone > 0) threadGone.update { it + gone }
                }
                messagesState.update { list -> list.filter { it.id !in event.ids } }
                pins.deleted(event.ids)
            }
            is ChatEvent.ReadOutbox -> if (event.key == dialogKey) {
                readOwn(event.maxId)
            }
            is ChatEvent.ThreadRead -> {
                if (event.key == dialogKey) {
                    if (event.outbox && event.topId == threadId) readOwn(event.maxId)
                    if (!event.outbox) repliesRead(event.topId, event.maxId)
                }
                if (!event.outbox && event.postKey == dialogKey) event.postId?.let { repliesRead(it, event.maxId) }
            }
            is ChatEvent.Reactions -> if (event.key == dialogKey) {
                reacting.apply(event)
                messagesState.value.firstOrNull { it.id == event.id }?.let { prepare(listOf(it)) }
            }
            is ChatEvent.PollChanged -> prepare(polling.apply(event))
            is ChatEvent.MessagesPinned -> pins.apply(event)
            is ChatEvent.ChatWallpaperChanged -> if (event.key == dialogKey) chatWallpapers.changed()
            is ChatEvent.Blocked, is ChatEvent.PeerBarChanged -> safety.apply(event)
            is ChatEvent.ScheduledNew, is ChatEvent.ScheduledDeleted -> scheduled.apply(event)
            else -> Unit
        }
    }

    private fun prepare(messages: List<MessageItem>) {
        messages.mapNotNull { it.media ?: it.gift?.sticker }.forEach(media::autoLoad)
        customEmoji.request(messages)
        reacting.prepare(messages)
        avatarStore.load(
            messages.flatMap { message ->
                listOf(message.senderAvatar, message.place?.live?.senderAvatar) + message.reactions.flatMap { reaction -> reaction.recent.map { it.avatarKey } } + message.replies?.recent.orEmpty().map { it.avatarKey } +
                    message.poll?.results?.recentVoters.orEmpty().map { it.avatarKey }
            },
        )
    }

    private suspend fun showStored() {
        val stored = messagesRepository.stored(dialogKey, PAGE, topicId) ?: return
        if (messagesState.value.isNotEmpty() || stored.messages.isEmpty()) return
        messagesState.value = stored.messages
        storedTop = stored.messages.maxOf { it.id }
        olderState.value = OlderState(more = !stored.complete, loading = false, first = false)
        prepare(stored.messages)
    }

    private suspend fun load() {
        val shown = messagesState.value.mapTo(HashSet()) { it.id }
        val loaded = messagesRepository.history(dialogKey, PAGE, topicId = topicId)
        val oldestLoaded = loaded.minOfOrNull { it.id } ?: Int.MAX_VALUE
        val newestLoaded = loaded.maxOfOrNull { it.id } ?: Int.MAX_VALUE
        val top = storedTop
        storedTop = null
        val replaced = top != null && (loaded.size < PAGE || oldestLoaded > top)
        if (newerState.value) {
            newerState.value = false
            messagesState.update { current -> loaded + current.filter { it.failed } }
            olderState.value = OlderState(more = loaded.size >= PAGE, loading = false, first = false)
        } else {
            messagesState.update { current ->
                val kept = current.filter { it.failed || (!it.pending && ((!replaced && it.id < oldestLoaded) || (it.id > newestLoaded && it.id !in shown))) }
                (kept + loaded).sortedBy { it.id }
            }
        }
        if (olderState.value.first || replaced) olderState.value = OlderState(more = loaded.size >= PAGE, loading = false, first = false)
        prepare(loaded)
    }

    private suspend fun loadAround(id: Int) {
        val page = messagesRepository.historyAround(dialogKey, id, PAGE, topicId)
        messagesState.value = page
        storedTop = null
        olderState.value = OlderState(more = page.count { it.id <= id } >= PAGE / 2, loading = false, first = false)
        newerState.value = page.count { it.id > id } >= PAGE / 2
        prepare(page)
    }

    fun loadNewer() {
        if (!newerState.value || loadingNewer || loadingLatest) return
        val newest = messagesState.value.filter { !it.pending }.maxOfOrNull { it.id } ?: return
        loadingNewer = true
        newerJob = viewModelScope.launch {
            try {
                val page = messagesRepository.historyAfter(dialogKey, newest, PAGE, topicId)
                messagesState.update { current -> (current + page).distinctBy { it.id }.sortedBy { it.id } }
                prepare(page)
                newerState.value = page.size >= PAGE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(UiError.Message(e.message ?: "cannot load newer messages"))
            } finally {
                loadingNewer = false
            }
        }
    }

    fun focusShown() {
        focusState.value = null
    }

    fun showLatest() {
        if (!newerState.value || loadingLatest) return
        newerJob?.cancel()
        loadingLatest = true
        work {
            try {
                load()
                latestState.value = messagesState.value.filter { !it.pending }.maxOfOrNull { it.id }
            } finally {
                loadingLatest = false
            }
        }
    }

    fun latestShown() {
        latestState.value = null
    }

    fun showMessage(id: Int) {
        if (messages.value.any { it.id == id }) {
            focusState.value = id
            return
        }
        if (id >= EPHEMERAL_IDS) return
        work {
            loadAround(id)
            focusState.value = id
        }
    }

    fun join() {
        work {
            val joined = channels.join(dialogKey)
            membershipState.value = joined
            infoState.value = infoState.value?.copy(membership = joined)
            if (joined == Membership.Member) store.reload(dialogKey)
        }
    }

    fun leave() {
        work {
            channels.leave(dialogKey)
            store.forget(dialogKey)
            notifier.forget(dialogKey)
            leftFlow.tryEmit(Unit)
        }
    }

    fun act(action: DialogAction) {
        val current = dialog.value ?: return
        act(store, dialogs, current, action)
        if (action == DialogAction.MarkRead) viewModelScope.launch { notifier.clearChat(dialogKey) }
    }

    fun startBot() {
        val parameter = startState.value ?: return
        work {
            links.startBot(dialogKey, parameter)
            startState.value = null
            load()
        }
    }

    fun loadOlder() {
        val state = olderState.value
        if (!state.more || state.loading) return
        val oldest = messagesState.value.filter { !it.pending }.minOfOrNull { it.id } ?: return
        olderState.value = state.copy(loading = true)
        viewModelScope.launch {
            try {
                val page = messagesRepository.history(dialogKey, PAGE, before = oldest, topicId = topicId)
                messagesState.update { current -> (page + current).distinctBy { it.id }.sortedBy { it.id } }
                prepare(page)
                olderState.value = OlderState(more = page.size >= PAGE, loading = false, first = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                olderState.value = state.copy(loading = false)
                fail(UiError.Message(e.message ?: "cannot load older messages"))
            }
        }
    }

    private suspend fun markRead(incoming: Boolean = false) {
        if (topicId != null) {
            val newest = messagesState.value.filter { !it.pending }.maxOfOrNull { it.id } ?: return
            if (newest > topicRead) {
                topicRead = newest
                forum.read(dialogKey, topicId, newest)
            }
            return
        }
        notifier.clearChat(dialogKey)
        val current = dialog.value ?: return
        if (!current.hasUnread && !incoming) return
        store.change(dialogKey) { it.copy(unread = 0, unreadMark = false, mentions = 0) }
        dialogs.markRead(dialogKey, clearMark = current.unreadMark)
    }

    fun typed(text: String) = typing.typed(text)

    fun draftChanged(draft: Draft) {
        customEmoji.requestIds(draft.text.entities.mapNotNull { (it.type as? EntityType.CustomEmoji)?.documentId })
        drafts.changed(if ((draft.replyTo ?: 0) >= EPHEMERAL_IDS) draft.copy(replyTo = null) else draft)
    }

    fun draftShown() = drafts.restored()

    private val forwardState = MutableStateFlow(opening.forward)

    val forwarding: StateFlow<Forwarding?> = forwardState.asStateFlow()

    fun sendForward(comment: FormattedText, replyTo: Int? = null) {
        val forward = forwardState.value ?: return
        forwardState.value = null
        val message = comment.trimmed()
        if (message.text.isNotEmpty()) {
            typing.cancel()
            drafts.sent()
            store.change(dialogKey) { it.copy(draft = null) }
        }
        queued {
            if (message.text.isNotEmpty()) messagesRepository.queueText(dialogKey, message.text, replyTo, message.entities, topicId)
            messagesRepository.queueForward(forward.fromKey, forward.ids, dialogKey, forward.dropAuthor, forward.dropCaptions, topicId)
        }
    }

    fun cancelForward() {
        forwardState.value = null
    }

    fun send(text: FormattedText, replyTo: Int? = null, options: SendOptions = SendOptions.None) {
        val message = text.trimmed()
        if (message.text.isEmpty()) return
        typing.cancel()
        drafts.sent()
        store.change(dialogKey) { it.copy(draft = null) }
        val answered = replyTo?.let(::ephemeralAt)?.ephemeral
        if (answered != null) {
            work { ephemeral.send(dialogKey, answered.botKey, message.text, message.entities, replyToEphemeral = answered.id, topicId = topicId) }
            return
        }
        if (message.text.startsWith("/") && commandsLoad?.isCompleted != true) {
            work { sendText(message, replyTo, ephemeralCommandOf(message.text, commandsNow()), options) }
            return
        }
        sendText(message, replyTo, ephemeralCommandOf(message.text, commandsState.value), options)
    }

    private fun sendText(message: FormattedText, replyTo: Int?, command: BotCommand?, options: SendOptions = SendOptions.None) {
        val bot = command?.botKey
        if (bot != null) {
            work { ephemeral.send(dialogKey, bot, message.text, message.entities, replyTo = replyTo, topicId = topicId) }
            return
        }
        queued(options) { messagesRepository.queueText(dialogKey, message.text, replyTo, message.entities, topicId, options = options) }
    }

    private fun queued(options: SendOptions = SendOptions.None, enqueue: suspend () -> Long) = work {
        messagesRepository.awaitOutgoing(enqueue())
        options.scheduleDate?.let { scheduled.sent(it) }
    }

    private class Resend(val ids: List<Int>, val send: suspend () -> Unit)

    private val resends = HashMap<Int, Resend>()

    private var localIds = 0

    private fun localId(): Int = Int.MAX_VALUE - ARTICLE_IDS - localIds++

    private fun sendPending(id: Int, send: suspend () -> Unit) {
        val ids = listOf(id)
        val resend = Resend(ids, send)
        ids.forEach { resends[it] = resend }
        work {
            try {
                send()
                ids.forEach { resends -= it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messagesState.update { list -> list.map { if (it.id in ids) it.copy(failed = true) else it } }
                throw e
            } finally {
                load()
            }
        }
    }

    fun retry(id: Int) {
        val resend = resends[id]
        if (resend == null) {
            work { messagesRepository.retryOutgoing(dialogKey, id)?.let { messagesRepository.awaitOutgoing(it) } }
            return
        }
        if (messagesState.value.none { it.id == id && it.failed }) return
        messagesState.update { list -> list.map { if (it.id in resend.ids) it.copy(failed = false) else it } }
        sendPending(id, resend.send)
    }

    fun edit(id: Int, text: FormattedText) {
        val message = text.trimmed()
        if (message.text.isEmpty()) return
        messagesState.update { list -> list.map { if (it.id == id) it.copy(text = message.text, entities = message.entities, edited = true) else it } }
        queued { messagesRepository.queueEdit(dialogKey, id, message.text, message.entities) }
    }

    fun sendArticle(article: ArticleMessage, replyTo: Int? = null) {
        if (article.blocks.isEmpty()) return
        article.asFormattedText()?.let { return send(it, replyTo) }
        typing.cancel()
        drafts.sent()
        store.change(dialogKey) { it.copy(draft = null) }
        val answered = replyTo?.takeIf { it < EPHEMERAL_IDS }
        val pending = MessageItem(
            id = localId(),
            text = "",
            out = true,
            date = (System.currentTimeMillis() / 1000).toInt(),
            pending = true,
            replyTo = answered,
            topicId = topicId,
            article = article,
        )
        messagesState.update { it + pending }
        sendPending(pending.id) { articles.send(dialogKey, article, answered, topicId) }
    }

    fun editArticle(id: Int, article: ArticleMessage) {
        if (article.blocks.isEmpty()) return
        messagesState.update { list -> list.map { if (it.id == id) it.copy(article = article, edited = true) else it } }
        work {
            try {
                articles.edit(dialogKey, id, article)
            } finally {
                load()
            }
        }
    }

    fun toggleCheck(id: Int, item: ArticleListItem) {
        val article = messagesState.value.firstOrNull { it.id == id }?.article ?: return
        val toggled = article.toggled(item)
        if (toggled != article) editArticle(id, toggled)
    }

    fun closeArticle(article: ArticleMessage, replyTo: Int? = null) {
        if (topicId != null) return
        if (article.blocks.isEmpty()) return deleteArticleDraft()
        val draft = Draft(FormattedText(""), replyTo?.takeIf { it < EPHEMERAL_IDS }, article)
        drafts.kept(draft)
        store.change(dialogKey) { it.copy(draft = draft) }
        work { articles.saveDraft(dialogKey, article, draft.replyTo) }
    }

    fun deleteArticleDraft() {
        if (topicId != null) return
        drafts.kept(Draft.EMPTY)
        store.change(dialogKey) { it.copy(draft = null) }
        work { messagesRepository.saveDraft(dialogKey, Draft.EMPTY) }
    }

    private val articleUploadsState = MutableStateFlow<Map<Long, ArticleUpload>>(emptyMap())

    val articleUploads: StateFlow<Map<Long, ArticleUpload>> = articleUploadsState.asStateFlow()

    private val articleUploadJobs = HashMap<Long, Job>()
    private val articleUploadUris = HashMap<Long, Uri>()
    private var nextArticleUpload = 1L

    fun uploadArticleMedia(kind: ArticleMediaKind, uri: Uri) {
        val id = nextArticleUpload++
        articleUploadUris[id] = uri
        startArticleUpload(id, kind)
    }

    fun retryArticleUpload(id: Long) {
        val upload = articleUploadsState.value[id] ?: return
        if (upload.failed) startArticleUpload(id, upload.kind)
    }

    fun removeArticleUpload(id: Long) {
        articleUploadJobs.remove(id)?.cancel()
        articleUploadUris.remove(id)
        articleUploadsState.update { it - id }
    }

    fun clearArticleUploads() {
        articleUploadJobs.values.forEach { it.cancel() }
        articleUploadJobs.clear()
        articleUploadUris.clear()
        articleUploadsState.value = emptyMap()
    }

    private fun startArticleUpload(id: Long, kind: ArticleMediaKind) {
        val uri = articleUploadUris[id] ?: return
        articleUploadJobs.remove(id)?.cancel()
        fun set(upload: ArticleUpload) = articleUploadsState.update { it + (id to upload) }
        if (id !in articleUploadsState.value) set(ArticleUpload(id, kind, uri.lastPathSegment.orEmpty()))
        articleUploadJobs[id] = viewModelScope.launch {
            var attachment: Attachment? = null
            var name = articleUploadsState.value[id]?.name.orEmpty()
            var total = 0L
            try {
                val opened = if (kind == ArticleMediaKind.Photo) attachments.openUpload(uri) else withContext(Dispatchers.IO) { attachments.open(uri) }
                attachment = opened
                name = opened.name
                total = opened.source.size
                val info = if (kind == ArticleMediaKind.Video || kind == ArticleMediaKind.Audio) withContext(Dispatchers.IO) { attachments.mediaInfo(uri) } else MediaFileInfo()
                set(ArticleUpload(id, kind, name, 0, total))
                val media = articles.upload(kind, name, opened.mimeType, opened.source, info) { uploaded, all ->
                    if (id in articleUploadsState.value) set(ArticleUpload(id, kind, name, uploaded, all))
                }
                set(ArticleUpload(id, kind, name, total, total, media))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (id in articleUploadsState.value) set(ArticleUpload(id, kind, name, 0, total, failed = true))
            } finally {
                attachment?.close()
            }
        }
    }

    private val remindedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val reminded: SharedFlow<Unit> = remindedFlow.asSharedFlow()

    fun remind(id: Int, date: Int) = work {
        messagesRepository.remind(dialogKey, id, date)
        remindedFlow.tryEmit(Unit)
    }

    fun delete(id: Int, revoke: Boolean) {
        val shownOnlyHere = ephemeralAt(id)
        val info = shownOnlyHere?.ephemeral
        if (shownOnlyHere != null && info != null) {
            ephemeralStore.remove(dialogKey, listOf(info.id))
            work { ephemeral.delete(dialogKey, info, shownOnlyHere.out) }
            return
        }
        if (queued.value.any { it.id == id && it.failed }) {
            work { messagesRepository.dropOutgoing(dialogKey, id) }
            return
        }
        if (messagesState.value.any { it.id == id && it.failed }) {
            resends -= id
            messagesState.update { list -> list.filter { it.id != id } }
            return
        }
        messagesState.update { list -> list.filter { it.id != id } }
        work {
            try {
                messagesRepository.delete(dialogKey, listOf(id), revoke)
            } finally {
                load()
            }
        }
    }

    val attachment: StateFlow<AttachmentDraft?> = attach.draft

    fun pickAttachments(uris: List<Uri>, asFile: Boolean) = attach.pick(uris, asFile)

    fun setAttachmentsAsFile(on: Boolean) = attach.asFile(on)

    fun setAttachmentsSpoiler(on: Boolean) = attach.spoiler(on)

    fun removeAttachment(id: Int) = attach.remove(id)

    fun dismissAttachments() = attach.close()

    fun sendAttachments(caption: FormattedText, options: SendOptions = SendOptions.None) {
        val (draft, picked) = attach.take() ?: return
        val answered = answered()
        val text = caption.trimmed()
        val spoiler = draft.spoiler && draft.canSpoiler
        work {
            albumsOf(picked, draft.asFile) { it.item.kind }.forEachIndexed { index, run ->
                val runCaption = if (index == 0) text else FormattedText("")
                sendFiles(run.map { it.uri }, draft.asFile, spoiler, runCaption, answered, options)
            }
        }
    }

    private suspend fun outgoing(uri: Uri, asFile: Boolean, spoiler: Boolean): Pair<Attachment, OutgoingMedia> {
        val file = attachments.openUpload(uri, asFile)
        try {
            val video = !asFile && file.mimeType.startsWith("video/")
            val audio = !asFile && file.mimeType.startsWith("audio/")
            val (info, thumb) = withContext(Dispatchers.IO) {
                (if (video || audio) attachments.mediaInfo(uri) else MediaFileInfo()) to (if (video) attachments.preview(uri, video = true, side = VIDEO_THUMB) else null)
            }
            return file to OutgoingMedia(file.name, file.mimeType, file.source, asFile, spoiler, info, thumb)
        } catch (e: Throwable) {
            file.close()
            throw e
        }
    }

    private suspend fun sendFiles(uris: List<Uri>, asFile: Boolean, spoiler: Boolean, caption: FormattedText, answered: Int?, options: SendOptions) {
        val opened = ArrayList<Pair<Attachment, OutgoingMedia>>()
        val batch = try {
            uris.forEach { opened += outgoing(it, asFile, spoiler) }
            val files = opened.map { it.second }
            if (files.size == 1) {
                messagesRepository.queueFile(dialogKey, files.single(), caption, answered, topicId, options = options)
            } else {
                messagesRepository.queueAlbum(dialogKey, files, caption, answered, topicId, options = options)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(UiError.Message(e.message ?: "cannot open the file"))
            return
        } finally {
            opened.forEach { it.first.close() }
        }
        val kinds = opened.map { (file, _) -> messagesRepository.uploadKindOf(file.mimeType, file.source.size, asFile) }
        work { awaitUpload(batch, kinds, options) }
    }

    private suspend fun awaitUpload(batch: Long, kinds: List<UploadKind>, options: SendOptions) {
        val keys = messagesRepository.outgoingKeys(batch, kinds.size)
        val telling = viewModelScope.launch {
            messagesRepository.outgoingProgress.collect { all ->
                val index = keys.indexOfLast { it in all }
                if (index >= 0) typing.uploading(ChatAction.Upload(kinds[index], percentOf(all.getValue(keys[index]))))
            }
        }
        try {
            messagesRepository.awaitOutgoing(batch)
            options.scheduleDate?.let { scheduled.sent(it) }
        } finally {
            telling.cancel()
            typing.cancel()
        }
    }

    private var topicRead = 0

    fun startVoice() {
        voice.start()
        if (voice.recording.value != null) typing.startRecording()
    }

    fun stopVoice(send: Boolean, options: SendOptions = SendOptions.None) {
        typing.stopRecording()
        val clip = voice.stop(send) ?: return typing.cancel()
        val answered = answered()
        work {
            val batch = try {
                messagesRepository.queueVoice(dialogKey, clip.file, clip.duration, clip.waveform, topicId, replyTo = answered, options = options)
            } catch (e: Exception) {
                clip.file.delete()
                throw e
            }
            awaitUpload(batch, listOf(UploadKind.Voice), options)
        }
    }

    fun microphoneDenied() = fail(UiError.MicrophoneDenied)

    fun toggleVoice(media: MediaItem) {
        voice.toggle(media)
        messagesState.update { list -> list.map { if (!it.out && it.mediaUnread && it.media?.key == media.key) it.copy(mediaUnread = false) else it } }
    }

    fun cycleVoiceSpeed() = voice.cycleSpeed()

    fun download(media: MediaItem) = this.media.download(media)

    fun saveToDownloads(media: MediaItem) = work { saver.save(this.media.file(media), media) }

    private val loadingArticlesState = MutableStateFlow<Set<Int>>(emptySet())
    private val articleLoads = HashMap<Int, Job>()

    val loadingArticles: StateFlow<Set<Int>> = loadingArticlesState.asStateFlow()

    fun showMore(id: Int) {
        articleLoads.remove(id)?.let { running ->
            running.cancel()
            return
        }
        loadingArticlesState.update { it + id }
        val job = work {
            val full = messagesRepository.richMessage(dialogKey, id) ?: return@work
            val article = full.article ?: return@work
            messagesState.update { list -> list.map { if (it.id == id) it.copy(article = article) else it } }
            prepare(listOf(full))
        }
        articleLoads[id] = job
        job.invokeOnCompletion {
            if (articleLoads[id] === job) articleLoads.remove(id)
            if (id !in articleLoads) loadingArticlesState.update { it - id }
        }
    }

    fun openStickers() = stickerPanel.openPanel()

    fun openStickerTab(setKey: String) = stickerPanel.openTab(setKey)

    fun openEmoji() {
        stickerPanel.openEmoji()
        customEmoji.requestIds(stickerPanel.ui.value.recentEmoji.filter { it.custom }.map { it.documentId })
    }

    fun searchEmoji(query: String) = stickerPanel.searchEmoji(query, searchLanguages(), reacting.ui.value.premium)

    fun pickEmoji(emoji: PickedEmoji) {
        stickerPanel.pickEmoji(emoji)
        if (!emoji.custom) return
        val panel = stickerPanel.ui.value
        val featured = panel.featuredEmoji?.sets.orEmpty().flatMap { it.covers }
        val known = (panel.setStickers.values.flatten() + panel.emojiSearch?.custom.orEmpty() + featured).firstOrNull { it.documentId == emoji.documentId }
        if (known != null) customEmoji.add(known) else customEmoji.requestIds(listOf(emoji.documentId))
    }

    fun chooseTone(base: String, toned: String) = stickerPanel.chooseTone(base, toned)

    fun keyboardHeight(dp: Int) = stickerPanel.keyboardHeight(dp)

    fun searchStickers(query: String) = stickerPanel.searchStickers(query, searchLanguages())

    fun searchEmojiCategory(category: EmojiCategoryGroup) = stickerPanel.searchEmojiCategory(category, searchLanguages(), reacting.ui.value.premium)

    fun searchStickerCategory(category: EmojiCategoryGroup) = stickerPanel.searchStickerCategory(category, searchLanguages())

    fun readFeatured(setKeys: List<String>) = stickerPanel.readFeatured(setKeys)

    fun faveSticker(sticker: MediaItem, fave: Boolean) = stickerPanel.fave(sticker, fave)

    fun removeGif(gif: MediaItem) = stickerPanel.removeGif(gif)

    fun reorderSets(setKeys: List<String>, emoji: Boolean) = stickerPanel.reorderSets(setKeys, emoji)

    private fun searchLanguages() = listOf(Locale.getDefault().language, "en")

    fun openGifs() = stickerPanel.openGifs()

    fun searchGifs(query: String) = stickerPanel.searchGifs(query)

    fun moreGifs() = stickerPanel.moreGifs()

    fun loadSticker(sticker: MediaItem) = media.loadSticker(sticker)

    fun stickerDraft(text: String) = stickerPanel.draft(text)

    fun showStickerSet(sticker: MediaItem) = stickerPanel.showSet(sticker)

    fun installStickerSet(setKey: String, install: Boolean) = stickerPanel.install(setKey, install)

    fun dismissStickerSet() = stickerPanel.dismissSet()

    fun sendSticker(sticker: MediaItem) = sendDocument(sticker)

    fun sendGif(gif: MediaItem) {
        sendDocument(gif)
        stickerPanel.sentGif(gif)
    }

    private fun sendDocument(document: MediaItem) {
        typing.cancel()
        val answered = answered()
        queued { messagesRepository.queueDocument(dialogKey, document, topicId, answered) }
    }

    private fun answered(): Int? = replyTarget?.takeIf { it < EPHEMERAL_IDS }

    private var replyTarget: Int? = null

    fun answeredNow(): Int? = answered()

    fun replying(id: Int?) {
        replyTarget = id
    }

    fun react(id: Int, reaction: Reaction, picked: Boolean = false, big: Boolean = false) = reacting.react(id, reaction, picked, big)

    override fun onCleared() {
        updates.follow(dialogKey, open = false)
        if (topicId == null) store.reading(dialogKey, open = false)
        voice.close()
        drafts.leave()
    }

    @AssistedFactory
    interface Factory {
        fun create(dialogKey: String, opening: ChatOpening): ChatViewModel
    }

    data class OlderState(val more: Boolean = false, val loading: Boolean = false, val first: Boolean = true)

    private companion object {
        const val PAGE = 50

        const val VIDEO_THUMB = 320
        const val RELOAD_PAUSE = 400L

        const val ARTICLE_IDS = 1_000_000
    }
}

private fun percentOf(progress: DownloadState.Progress): Int {
    val total = progress.total ?: return 0
    return if (total <= 0) 0 else (progress.downloaded * 100 / total).toInt().coerceIn(0, 100)
}

data class ChatOpening(
    val messageId: Int? = null,
    val startParameter: String? = null,
    val forward: Forwarding? = null,
    val text: String? = null,
    val topicId: Int? = null,
    val threadId: Int? = null,
    val comments: Boolean = false,
    val attach: AttachLaunch? = null,
)
