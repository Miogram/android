package cn.ellerotta.core.ui.components.chat

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.animation.core.Animatable
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.ForwardPreview
import androidx.compose.material3.MaterialTheme
import cn.ellerotta.core.ui.theme.MioShapes
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.components.location.LiveAlertSheet
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.ChatSearchState
import cn.ellerotta.core.model.ChatStickerState
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.Draft
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.PickedEmoji
import cn.ellerotta.core.model.deletedBackwards
import cn.ellerotta.core.model.withEmoji
import cn.ellerotta.core.model.withMention
import cn.ellerotta.core.model.mentionAt
import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.model.edited
import cn.ellerotta.core.model.plainText
import cn.ellerotta.core.model.toggled
import cn.ellerotta.core.model.trimmed
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Membership
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.AttachmentDraft
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.VoicePlayback
import cn.ellerotta.core.model.VoiceRecording
import cn.ellerotta.core.model.isSingleEmoji
import cn.ellerotta.core.model.savable
import cn.ellerotta.core.model.PollVotesState
import cn.ellerotta.core.model.PinnedMessages
import cn.ellerotta.core.model.translatable
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.ArticleRow
import cn.ellerotta.core.model.asFormattedText
import cn.ellerotta.core.model.ARTICLE_DRAFT_PREVIEW_MAX
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.toRows
import cn.ellerotta.core.model.InlineState
import cn.ellerotta.core.model.inlineQueryOf
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotCommand
import cn.ellerotta.core.model.commandsFor
import cn.ellerotta.core.model.OpenWebApp
import cn.ellerotta.core.model.AttachMenuBot
import cn.ellerotta.core.model.BotMenuButton
import cn.ellerotta.core.model.CallbackAnswer
import cn.ellerotta.core.model.KeyboardChange
import cn.ellerotta.core.model.LoginState
import cn.ellerotta.core.model.ReactorsState
import cn.ellerotta.core.model.ThreadTitle
import cn.ellerotta.core.model.isTelegramUrl
import cn.ellerotta.core.model.keyboardMessage
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.ChatAction
import cn.ellerotta.core.ui.components.chats.ChatRemovalDialog
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.contacts.AddProfileContactDialog
import cn.ellerotta.core.ui.components.safety.BlockedBanner
import cn.ellerotta.core.ui.components.safety.ChatSafetyActions
import cn.ellerotta.core.ui.components.safety.ChatSafetyState
import cn.ellerotta.core.ui.components.safety.PeerActionBar
import cn.ellerotta.core.ui.components.safety.PeerBarActions
import cn.ellerotta.core.ui.components.safety.ReportSheet
import cn.ellerotta.core.ui.components.common.SearchTopBar
import cn.ellerotta.core.ui.components.common.absorbTouches
import cn.ellerotta.core.ui.components.common.TopBarScrim
import cn.ellerotta.core.ui.components.common.TopBarTokens
import cn.ellerotta.core.ui.components.common.topScrimHeight
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    title: String,
    messages: List<MessageItem>,
    downloads: Map<String, DownloadState>,
    busy: Boolean,
    onBack: () -> Unit,
    onSend: (FormattedText) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onOpenProfile: () -> Unit = {},
    dialog: DialogItem? = null,
    stickers: Map<String, ByteArray> = emptyMap(),
    avatars: Map<String, ByteArray> = emptyMap(),
    onAttachMedia: (() -> Unit)? = null,
    onAttachFile: (() -> Unit)? = null,
    attachMenu: AttachMenuActions = AttachMenuActions(),
    loadingOlder: Boolean = false,
    onLoadOlder: () -> Unit = {},
    onSendReply: (FormattedText, Int) -> Unit = { message, _ -> onSend(message) },
    onEdit: ((Int, FormattedText) -> Unit)? = null,
    onDelete: ((Int, Boolean) -> Unit)? = null,
    recording: VoiceRecording? = null,
    onVoiceStart: (() -> Unit)? = null,
    onVoiceStop: (Boolean) -> Unit = {},
    playback: VoicePlayback? = null,
    onToggleVoice: (MediaItem) -> Unit = {},
    onVoiceSpeed: () -> Unit = {},
    onOpenMedia: (MessageItem) -> Unit = {},
    onOpenArticleMedia: (MessageItem, Int) -> Unit = { _, _ -> },
    onShowMore: ((Int) -> Unit)? = null,
    loadingArticles: Set<Int> = emptySet(),
    stickerState: ChatStickerState = ChatStickerState(),
    stickerActions: ChatStickerActions? = null,
    initialText: String = "",
    focus: Int? = null,
    onFocusShown: () -> Unit = {},
    newerAvailable: Boolean = false,
    onLoadNewer: () -> Unit = {},
    latest: Int? = null,
    onLatestShown: () -> Unit = {},
    onShowLatest: (() -> Unit)? = null,
    membership: Membership? = Membership.Member,
    onJoin: () -> Unit = {},
    onStartBot: (() -> Unit)? = null,
    onOpenLink: (String) -> Unit = {},
    onCommand: ((String) -> Unit)? = null,
    onRemind: ((Int, Int) -> Unit)? = null,
    customEmoji: Map<Long, MediaItem> = emptyMap(),
    reactions: ChatReactionState = ChatReactionState(),
    onReact: ((id: Int, reaction: Reaction, picked: Boolean, big: Boolean) -> Unit)? = null,
    onForward: ((ForwardChoice) -> Unit)? = null,
    forwardTargets: List<DialogItem> = emptyList(),
    onShowMessage: (Int) -> Unit = {},
    onTyping: (String) -> Unit = {},
    draft: Draft? = null,
    onDraftShown: () -> Unit = {},
    onDraftChange: (Draft) -> Unit = {},
    search: ChatSearchState = ChatSearchState(),
    searchActions: ChatSearchActions? = null,
    onDialogAction: ((ChatAction) -> Unit)? = null,
    onLeave: (() -> Unit)? = null,
    onRemove: ((ChatRemoval, Boolean) -> Unit)? = null,
    inline: InlineState = InlineState(),
    inlineActions: ChatInlineActions? = null,
    mentions: List<ChatMember> = emptyList(),
    botActions: ChatBotActions? = null,
    callbackAnswer: CallbackAnswer? = null,
    login: LoginState = LoginState(),
    webApp: OpenWebApp? = null,
    commands: List<BotCommand> = emptyList(),
    onLoadCommands: (() -> Unit)? = null,
    onStopDraft: (() -> Unit)? = null,
    articles: ChatArticleActions? = null,
    onSaveToDownloads: ((MediaItem) -> Unit)? = null,
    music: MusicState? = null,
    onPlayMusic: ((MessageItem) -> Unit)? = null,
    player: (@Composable () -> Unit)? = null,
    playerShown: Boolean = true,
    onOpenReplies: ((Int) -> Unit)? = null,
    unreadAfter: Int? = null,
    polls: ChatPollActions? = null,
    pollVotes: PollVotesState? = null,
    pinned: PinnedMessages = PinnedMessages(),
    pins: ChatPinActions? = null,
    translations: ChatTranslateActions? = null,
    forwarding: ForwardPreview? = null,
    onSendForward: ((FormattedText, Int?) -> Unit)? = null,
    onCancelForward: () -> Unit = {},
    wallpaper: ChatWallpaper? = null,
    openSearch: Boolean = false,
    onRetry: ((Int) -> Unit)? = null,
    onOpenStories: ((String) -> Unit)? = null,
    onOpenSender: ((MessageItem) -> Unit)? = null,
    reactors: ReactorsState? = null,
    reactorActions: ChatReactorActions? = null,
    menuButton: BotMenuButton? = null,
    attachBots: List<AttachMenuBot> = emptyList(),
    wallpaperActions: ChatWallpaperActions? = null,
    connection: ChatsStatus = ChatsStatus.Ready,
    unreadChats: Int = 0,
    thread: ThreadTitle? = null,
    subtitle: String? = null,
    safety: ChatSafetyState = ChatSafetyState(),
    safetyActions: ChatSafetyActions? = null,
    sending: ChatSendActions? = null,
    attachment: AttachmentDraft? = null,
    attachmentActions: ChatAttachmentActions? = null,
    scheduledCount: Int = 0,
    onOpenScheduled: (() -> Unit)? = null,
    onReplying: (Int?) -> Unit = {},
    places: ChatPlaces? = null,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var entities by rememberSaveable(stateSaver = EntitiesSaver) { mutableStateOf(emptyList<TextEntity>()) }
    var linking by remember { mutableStateOf<TextRange?>(null) }
    val text = field.text
    fun setDraft(draft: FormattedText) {
        field = TextFieldValue(draft.text, TextRange(draft.text.length))
        entities = draft.entities
    }
    var contextTarget by remember { mutableStateOf<ContextTarget?>(null) }
    val reveal = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val lifted = rememberGraphicsLayer()
    val backdrop = rememberChatBackdrop()
    val reactionEffects = remember { ReactionEffects() }
    fun react(message: MessageItem, reaction: Reaction, from: Rect?, picked: Boolean, big: Boolean) {
        val adding = message.reactions.none { it.reaction == reaction && it.chosen }
        if (adding && reaction != Reaction.Paid) reactionEffects.play(ReactionEffect(message.id, reaction, from, big))
        onReact?.invoke(message.id, reaction, picked, big)
    }
    var contextJob by remember { mutableStateOf<Job?>(null) }
    var forwardIds by remember { mutableStateOf<List<Int>?>(null) }
    var selectionLimited by remember { mutableStateOf(false) }
    var replying by remember { mutableStateOf<MessageItem?>(null) }
    LaunchedEffect(replying?.id) { onReplying(replying?.id) }
    var editing by remember { mutableStateOf<MessageItem?>(null) }
    var beforeEdit by remember { mutableStateOf<Draft?>(null) }
    var deleting by remember { mutableStateOf<MessageItem?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ChatRemoval?>(null) }
    var blockingChat by remember { mutableStateOf(false) }
    var addingContact by remember { mutableStateOf(false) }
    var pinning by remember { mutableStateOf<MessageItem?>(null) }
    var unpinningAll by remember { mutableStateOf(false) }
    var unpinningOne by remember { mutableStateOf<Int?>(null) }
    var alertingLive by rememberSaveable { mutableStateOf(places?.alertOnOpen == true) }
    val livePrivate = dialog?.let { it.kind == PeerKind.User && !it.isSaved } == true
    val liveAlerts = livePrivate || dialog?.kind == PeerKind.Group
    var pinnedListOpen by rememberSaveable { mutableStateOf(false) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var panelTab by rememberSaveable { mutableStateOf(PanelTab.Emoji) }
    var panelSearching by remember { mutableStateOf(false) }
    var bottomArea by remember { mutableIntStateOf(0) }
    var bottomHold by remember { mutableIntStateOf(0) }
    fun panelToKeyboard() {
        bottomHold = bottomArea
        panelOpen = false
    }
    fun putEmoji(emoji: PickedEmoji) {
        val (next, cursor) = FormattedText(field.text, entities).withEmoji(emoji, field.selection.min, field.selection.max)
        entities = next.entities
        field = TextFieldValue(next.text, TextRange(cursor))
        if (editing == null) onTyping(next.text)
        stickerActions?.onPickEmoji(emoji)
    }
    fun deleteBackwards() {
        val (next, cursor) = FormattedText(field.text, entities).deletedBackwards(field.selection.min, field.selection.max)
        entities = next.entities
        field = TextFieldValue(next.text, TextRange(cursor))
    }
    var articleRows by remember { mutableStateOf<List<ArticleRow>?>(null) }
    var articleEditing by remember { mutableStateOf<MessageItem?>(null) }
    var richDraft by remember { mutableStateOf<ArticleMessage?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(text) { stickerActions?.onDraft(text) }
    LaunchedEffect(text, editing) { inlineActions?.onText(if (editing == null) text else "") }
    val typedMention = remember(text, field.selection) { if (inlineActions != null && field.selection.collapsed) mentionAt(text, field.selection.start) else null }
    val mentionAtStart = typedMention?.start == 0 && editing == null
    LaunchedEffect(typedMention?.query, mentionAtStart) { inlineActions?.onMention(typedMention?.query, mentionAtStart) }
    val commandTyped = editing == null && text.startsWith("/")
    LaunchedEffect(commandTyped) { if (commandTyped) onLoadCommands?.invoke() }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    var selection by remember { mutableStateOf(emptySet<Int>()) }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }
    val byId = remember(messages) { messages.associateBy { it.id } }
    val inlineEmoji = remember(customEmoji, stickers) { InlineEmoji(customEmoji, stickers) }
    LaunchedEffect(draft) {
        val incoming = draft ?: return@LaunchedEffect
        if (editing != null) {
            beforeEdit = incoming
        } else {
            setDraft(incoming.text)
            richDraft = incoming.article
            replying = incoming.replyTo?.let { id -> byId[id] ?: MessageItem(id = id, text = "", out = false, date = 0) }
        }
        onDraftShown()
    }
    LaunchedEffect(byId) { replying?.let { reply -> byId[reply.id]?.let { if (it != reply) replying = it } } }
    val draftChange by rememberUpdatedState(onDraftChange)
    LaunchedEffect(Unit) {
        snapshotFlow { if (editing == null) Draft(FormattedText(field.text, entities), replying?.id, richDraft) else null }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { draftChange(it) }
    }
    fun endEdit() {
        editing = null
        val restored = beforeEdit ?: Draft.EMPTY
        beforeEdit = null
        setDraft(restored.text)
        richDraft = restored.article
        replying = restored.replyTo?.let { id -> byId[id] ?: MessageItem(id = id, text = "", out = false, date = 0) }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmingUrl by remember { mutableStateOf<String?>(null) }
    var alert by remember { mutableStateOf<String?>(null) }
    var addingAttachBot by remember { mutableStateOf<AttachMenuBot?>(null) }
    val webAppTheme = webAppThemeJson()
    fun openUrl(url: String) {
        if (isTelegramUrl(url)) onOpenLink(url) else confirmingUrl = url
    }
    fun unsupported() = Toast.makeText(context, R.string.chat_button_unsupported, Toast.LENGTH_SHORT).show()
    val keyboardSource = remember(messages) { keyboardMessage(messages) }
    var usedKeyboards by remember { mutableStateOf(emptySet<Int>()) }
    val keyboardChange = keyboardSource?.takeIf { it.id !in usedKeyboards }?.keyboard
    val replyKeyboard = (keyboardChange as? KeyboardChange.Show)?.keyboard
    val forceReply = keyboardChange as? KeyboardChange.ForceReply
    var botKeyboardOpen by remember(keyboardSource?.id) { mutableStateOf(true) }
    val asksReply = forceReply != null || replyKeyboard?.forceReply == true
    LaunchedEffect(keyboardSource?.id, asksReply) {
        if (asksReply && editing == null && replying == null) replying = keyboardSource
    }
    fun press(message: MessageItem?, button: BotButton) {
        when (button) {
            is BotButton.Text -> {
                onSend(FormattedText(button.text))
                if (replyKeyboard?.singleUse == true) keyboardSource?.let { usedKeyboards = usedKeyboards + it.id }
            }
            is BotButton.Url -> openUrl(button.url)
            is BotButton.LoginUrl -> if (message != null && botActions != null) botActions.onLogin(message.id, button) else openUrl(button.url)
            is BotButton.WebApp -> if (message != null && botActions != null) botActions.onWebApp(message, button, webAppTheme) else unsupported()
            is BotButton.Callback -> if (message != null && botActions != null) botActions.onCallback(message.id, button.data) else unsupported()
            is BotButton.SwitchInline -> {
                val bot = button.bot ?: return unsupported()
                val query = "@$bot ${button.query}"
                if (button.samePeer) setDraft(FormattedText(query)) else botActions?.onSwitchInline(query) ?: unsupported()
            }
            is BotButton.Copy -> scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("button", button.copyText)))
                Toast.makeText(context, R.string.chat_copied, Toast.LENGTH_SHORT).show()
            }
            is BotButton.UserProfile -> onOpenLink("tg://user?id=${button.userId}")
            is BotButton.Disabled -> Unit
            is BotButton.Unsupported -> unsupported()
        }
    }
    LaunchedEffect(callbackAnswer) {
        val answer = callbackAnswer ?: return@LaunchedEffect
        answer.message?.let { if (answer.alert) alert = it else Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        answer.url?.let(::openUrl)
        botActions?.onAnswerShown()
    }
    LaunchedEffect(login.url) {
        val url = login.url ?: return@LaunchedEffect
        if (login.confirmed) onOpenLink(url) else openUrl(url)
        botActions?.onLoginOpened()
    }
    val list = rememberLazyListState()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val haptics = LocalHapticFeedback.current
    fun openContext(message: MessageItem, bounds: Rect) {
        keyboard?.hide()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        contextJob?.cancel()
        contextTarget = ContextTarget(message, bounds, end = message.onEndSide(dialog))
        contextJob = scope.launch {
            list.stopScroll()
            launch { lift.animateTo(1f, spatial) }
            reveal.animateTo(1f, effects)
        }
    }
    fun closeContext() {
        if (contextTarget == null) return
        contextJob?.cancel()
        contextJob = scope.launch {
            val down = launch { lift.animateTo(0f, spatial) }
            reveal.animateTo(0f, effects)
            down.join()
            contextTarget = null
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - OLDER_AHEAD) onLoadOlder() }
    }
    LaunchedEffect(list, newerAvailable) {
        if (!newerAvailable) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.firstOrNull()?.index }
            .collect { first -> if (first != null && first <= NEWER_AHEAD) onLoadNewer() }
    }
    val zone = ZoneId.systemDefault()
    val rows = entries(messages, zone, unreadAfter)
    val last = messages.lastOrNull()
    var previous by remember { mutableStateOf(last) }
    LaunchedEffect(last?.id) {
        val before = previous
        previous = last
        if (last == null || focus != null || newerAvailable || contextTarget != null) return@LaunchedEffect
        val sent = last.out && before != null && last.id != before.id &&
            (before.pending || messages.getOrNull(messages.size - 2)?.id == before.id)
        if (sent || list.firstVisibleItemIndex <= FOLLOW_BOTTOM) list.animateScrollToItem(0)
    }
    var highlighted by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(focus, rows.size) {
        val target = focus ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is ChatEntry.Bubble && it.shows(target) }
        if (index < 0) return@LaunchedEffect
        list.scrollToItem(index)
        highlighted = target
        onFocusShown()
    }
    LaunchedEffect(highlighted) {
        if (highlighted == null) return@LaunchedEffect
        snapshotFlow { list.isScrollInProgress }.first { it }
        highlighted = null
    }
    fun openMessage(id: Int) {
        val index = rows.indexOfFirst { it is ChatEntry.Bubble && it.shows(id) }
        if (index < 0) {
            onShowMessage(id)
        } else {
            scope.launch {
                list.animateScrollToItem(index)
                highlighted = id
            }
        }
    }
    val awayFromBottom by remember { derivedStateOf { list.firstVisibleItemIndex > FOLLOW_BOTTOM } }
    fun goDown() {
        if (newerAvailable && onShowLatest != null) return onShowLatest()
        scope.launch { list.animateScrollToItem(0) }
    }
    LaunchedEffect(latest, rows.size, last?.id) {
        val target = latest ?: return@LaunchedEffect
        if (rows.none { it is ChatEntry.Bubble && it.shows(target) }) return@LaunchedEffect
        list.scrollToItem(0)
        onLatestShown()
    }
    var searching by rememberSaveable { mutableStateOf(openSearch && searchActions != null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchIndex by remember { mutableStateOf<Int?>(null) }
    var resultsShown by remember { mutableStateOf(true) }
    fun searchFor(query: String) {
        searching = true
        searchQuery = query
        searchIndex = null
        resultsShown = true
        searchActions?.onQuery(query)
    }
    fun closeSearch() {
        searching = false
        searchQuery = ""
        searchIndex = null
        searchActions?.onClose()
    }
    fun showResult(index: Int) {
        val found = search.results.getOrNull(index) ?: return
        searchIndex = index
        resultsShown = false
        keyboard?.hide()
        openMessage(found.id)
        if (index >= search.results.size - SEARCH_AHEAD) searchActions?.onMore()
    }
    BackHandler(enabled = searching) { closeSearch() }
    val openHashtag: ((String) -> Unit)? = remember(searchActions) { searchActions?.let { { tag -> searchFor(tag) } } }
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    var bottomHeight by remember { mutableStateOf(0.dp) }
    val blur = with(density) { CONTEXT_BLUR.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val radius = blur * reveal.value.coerceIn(0f, 1f)
                renderEffect = if (radius >= 1f) BlurEffect(radius, radius, TileMode.Decal) else null
            }
            .then(if (contextTarget != null) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        ChatBackground(Modifier.fillMaxSize().backdropSource(backdrop), wallpaper)
        ChatMessageList(
            rows, zone, list, dialog, byId, downloads, stickers, loadingOlder, playback, onDownload, onToggleVoice,
            highlighted = highlighted,
            onVoiceSpeed = onVoiceSpeed,
            onOpenLink = onOpenLink,
            onCommand = onCommand,
            onRemind = onRemind,
            customEmoji = customEmoji,
            avatars = avatars,
            onReact = onReact?.let { { message, reaction -> react(message, reaction, null, picked = false, big = false) } },
            defaultReaction = reactions.default,
            onSelect = ::openContext,
            onOpenReply = ::openMessage,
            selection = selection,
            onToggleSelect = { message ->
                selectionLimited = message.id !in selection && selection.size >= MAX_SELECTED
                selection = when {
                    message.id in selection -> selection - message.id
                    selectionLimited -> selection
                    else -> selection + message.id
                }
            },
            lifted = lifted,
            liftedId = contextTarget?.message?.id,
            onLiftedBounds = { bounds -> contextTarget?.let { if (it.bounds != bounds) contextTarget = it.copy(bounds = bounds) } },
            onOpenMedia = onOpenMedia,
            onOpenArticleMedia = onOpenArticleMedia,
            onShowMore = onShowMore,
            loadingArticles = loadingArticles,
            onToggleCheck = articles?.onToggle?.takeIf { articles.gate.allowed },
            onOpenSticker = { media -> stickerActions?.onShowSet(media) },
            onHashtag = openHashtag,
            onViaBot = { username -> if (membership == Membership.Member && onStartBot == null && editing == null) setDraft(FormattedText("@$username ")) },
            onButton = ::press,
            music = music,
            onPlayMusic = onPlayMusic,
            onOpenReplies = onOpenReplies,
            onVote = polls?.onVote,
            onShowVotes = polls?.onShowVotes,
            onShowOriginal = translations?.onShowOriginal,
            contentPadding = PaddingValues(top = headerHeight + ListGap, bottom = bottomHeight + ListGap),
            modifier = Modifier.fillMaxSize(),
            backdrop = backdrop,
            reactionArt = reactions.art,
            reactionEffects = reactionEffects,
            onReply = if (membership == Membership.Member && onStartBot == null) ({ message ->
                if (editing != null) endEdit()
                replying = message
            }) else null,
            onRetry = onRetry?.let { retry -> { message -> retry(message.id) } },
            onOpenSender = onOpenSender?.takeIf { selection.isEmpty() },
            wallpapers = wallpaperActions,
            onOpenPlace = places?.onOpen,
            themedBackground = wallpaper.isThemed(),
        )
        TopBarScrim(topScrimHeight(headerHeight), Modifier.align(Alignment.TopCenter))
        TopBarScrim(max(BottomScrimHeight, bottomHeight + TopBarTokens.ScrimOverhang), Modifier.align(Alignment.BottomCenter), stops = BOTTOM_SCRIM_STOPS)
        if (searching && resultsShown && searchQuery.isNotBlank()) {
            Surface(Modifier.fillMaxSize().padding(top = headerHeight, bottom = bottomHeight)) {
                ChatSearchResults(search, title, onOpen = ::showResult, onMore = { searchActions?.onMore() })
            }
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                .absorbTouches()
                .statusBarsPadding()
                .testTag("chatHeader"),
        ) {
            when {
                selection.isNotEmpty() -> SelectionHeader(selection.size, onClose = { selection = emptySet() }, modifier = Modifier.padding(top = HeaderGap))
                searching -> SearchTopBar(
                    searchQuery, onQuery = ::searchFor, onClose = ::closeSearch,
                    placeholder = stringResource(R.string.chat_search_hint),
                    closeLabel = stringResource(R.string.chat_search_close),
                    modifier = Modifier.padding(top = HeaderGap),
                )
                else -> {
                    val menu = dialog?.takeIf { onDialogAction != null && !it.isSaved }?.let { current ->
                        listOf(if (current.muted) ChatAction.Unmute else ChatAction.Mute, if (current.archived) ChatAction.Unarchive else ChatAction.Archive)
                    }.orEmpty()
                    ChatHeader(
                        title, dialog, avatars, onBack, onOpenProfile,
                        modifier = Modifier.padding(top = HeaderGap),
                        onSearch = searchActions?.let { { searchFor("") } },
                        menu = menu,
                        onMenu = { onDialogAction?.invoke(it) },
                        onLeave = onLeave?.let { { leaving = true } },
                        removals = if (onRemove == null) emptyList() else dialog?.removals?.filter { !(onLeave != null && it == ChatRemoval.Delete && (dialog.kind == PeerKind.Group || dialog.kind == PeerKind.Channel)) }.orEmpty(),
                        onRemove = { removing = it },
                        onOpenStories = onOpenStories,
                        onWallpaper = wallpaperActions?.onOpen,
                        blocked = safety.blocked.takeIf { safetyActions != null && safety.canBlock },
                        onBlock = { blockingChat = true },
                        onUnblock = { safetyActions?.onUnblock?.invoke() },
                        onReport = safetyActions?.takeIf { dialog?.isSaved != true }?.let { { it.onReportChat() } },
                        connection = connection,
                        unreadChats = unreadChats,
                        thread = thread,
                        subtitle = subtitle,
                        emoji = inlineEmoji,
                    )
                }
            }
            val panels = MaterialTheme.motionScheme
            val lastPinned = remember { LastPinned() }
            if (pinned.shown != null) lastPinned.pinned = pinned
            AnimatedVisibility(!searching && selection.isEmpty() && pinned.shown != null, enter = panels.expandFadeIn(), exit = panels.shrinkFadeOut()) {
                PinnedBar(
                    if (pinned.shown != null) pinned else lastPinned.pinned ?: pinned, downloads, onDownload,
                    onTap = { (pins?.onTapped?.invoke() ?: pinned.shown?.id)?.let(::openMessage) },
                    onList = { pinnedListOpen = true },
                    onClose = pins?.let { actions ->
                        if (actions.canPin) ({ unpinningOne = pinned.shown?.id }) else actions.onHide
                    },
                    closeLabel = stringResource(if (pins?.canPin == true) R.string.chat_unpin else R.string.chat_pinned_hide_one),
                    modifier = Modifier.padding(start = HeaderGap, end = HeaderGap, top = HeaderGap),
                    stickers = stickers,
                )
            }
            AnimatedVisibility(!searching && selection.isEmpty() && places?.sharers.orEmpty().isNotEmpty(), enter = panels.expandFadeIn(), exit = panels.shrinkFadeOut()) {
                val lastSharers = remember { LastSharers() }
                places?.sharers?.takeIf { it.isNotEmpty() }?.let { lastSharers.sharers = it }
                LiveBar(
                    lastSharers.sharers,
                    onOpen = { places?.onOpenLive?.invoke() },
                    onStop = { places?.onStop?.invoke() },
                    modifier = Modifier.padding(start = HeaderGap, end = HeaderGap, top = HeaderGap),
                    onAlert = places?.onAlert?.takeIf { liveAlerts }?.let { { alertingLive = true } },
                    private = livePrivate,
                )
            }
            AnimatedVisibility(player != null && playerShown, enter = panels.expandFadeIn(), exit = panels.shrinkFadeOut()) {
                Box(Modifier.padding(start = HeaderGap, end = HeaderGap, top = HeaderGap)) {
                    Box(Modifier.clip(MioShapes.previewBar)) { player?.invoke() }
                }
            }
            AnimatedVisibility(busy, enter = panels.expandFadeIn(), exit = panels.shrinkFadeOut()) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = HeaderGap))
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }
                .consumeWindowInsets(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            if (searching) {
                ChatSearchBar(
                    search,
                    searchIndex,
                    onOlder = { showResult((searchIndex ?: -1) + 1) },
                    onNewer = { searchIndex?.let { showResult(it - 1) } },
                    onList = { resultsShown = true },
                )
            } else if (selection.isNotEmpty()) {
                SelectionBar(
                    selection.size,
                    selectionLimited,
                    onForward = onForward?.let { { forwardIds = selection.filter { it in byId }.sorted() } },
                    onReport = safetyActions?.takeIf { selection.all { byId[it]?.out != true } }?.let {
                        {
                            it.onReportMessages(selection.filter { id -> id in byId }.sorted())
                            selection = emptySet()
                        }
                    },
                )
            } else {
                val suggestions = stickerState.suggestions.takeIf { stickerActions != null && editing == null && isSingleEmoji(text) }.orEmpty()
                val motion = MaterialTheme.motionScheme
                AnimatedVisibility(suggestions.isNotEmpty(), enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) {
                    StickerSuggestions(suggestions, stickers, { stickerActions?.onLoad(it) }) { sticker ->
                        stickerActions?.onSend(sticker)
                        replying = null
                        setDraft(FormattedText(""))
                    }
                }
                val matching = remember(text, commands, editing) { if (editing == null && onLoadCommands != null) commandsFor(text, commands) else emptyList() }
                AnimatedVisibility(matching.isNotEmpty(), enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) {
                    BotCommandList(
                        matching,
                        onSend = { command ->
                            val answered = replying
                            if (answered != null) onSendReply(FormattedText(command.text), answered.id) else onSend(FormattedText(command.text))
                            setDraft(FormattedText(""))
                            replying = null
                        },
                        onInsert = { command -> setDraft(FormattedText("${command.text} ")) },
                    )
                }
                val query = remember(text) { inlineQueryOf(text) }
                val inlineBot = inline.bot?.takeIf { inlineActions != null && editing == null && query != null && it.username.equals(query.username, ignoreCase = true) }
                AnimatedVisibility(typedMention != null && inlineBot == null && mentions.isNotEmpty(), enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) {
                    MentionList(mentions, avatars) { member ->
                        val at = typedMention ?: return@MentionList
                        val (next, cursor) = FormattedText(field.text, entities).withMention(at, member)
                        entities = next.entities
                        field = TextFieldValue(next.text, TextRange(cursor))
                        if (editing == null) onTyping(next.text)
                    }
                }
                val results = inline.results?.takeIf { inlineBot != null && (it.results.isNotEmpty() || it.switchPm != null || it.switchWebView != null) }
                AnimatedVisibility(results != null, enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) {
                    if (results != null && inlineActions != null) {
                        InlineResultsPanel(
                            results,
                            inline.more,
                            stickers,
                            onLoad = inlineActions.onLoad,
                            onPick = { result ->
                                inlineActions.onSend(result, replying?.id)
                                setDraft(FormattedText(""))
                                replying = null
                            },
                            onMore = inlineActions.onMore,
                            onSwitchPm = inlineActions.onSwitchPm,
                            onSwitchWebView = { inlineActions.onSwitchWebView(webAppTheme) },
                        )
                    }
                }
                AnimatedVisibility(onStopDraft != null && membership == Membership.Member, enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) {
                    StopDraftBar { onStopDraft?.invoke() }
                }
                if (membership == Membership.Member) ScheduledBarHost(scheduledCount, onOpenScheduled)
                AnimatedVisibility(safetyActions != null && safety.bar.shown && !safety.blocked, enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) {
                    if (safetyActions != null) {
                        PeerActionBar(
                            safety.bar,
                            PeerBarActions(
                                onReportSpam = safetyActions.onReportSpam,
                                onAddContact = { addingContact = true },
                                onBlock = { blockingChat = true },
                                onDismiss = safetyActions.onDismissBar,
                            ),
                        )
                    }
                }
                if (safetyActions != null && safety.blocked) {
                    BlockedBanner(safetyActions.onUnblock)
                } else if (onStartBot != null) {
                    ChatActionBar(stringResource(R.string.chat_start_bot), "startBot", onStartBot)
                } else if (membership != Membership.Member) {
                    when (membership) {
                        Membership.CanJoin -> ChatActionBar(stringResource(R.string.links_join), "joinChat", onJoin)
                        Membership.RequestToJoin -> ChatActionBar(stringResource(R.string.chat_request_to_join), "joinChat", onJoin)
                        Membership.RequestSent -> ChatActionBar(stringResource(R.string.chat_request_sent), "requestSent", onClick = {}, enabled = false)
                        Membership.Removed -> ChatActionBar(stringResource(R.string.chat_not_member), "notMember", onClick = {}, enabled = false)
                        else -> Unit
                    }
                } else {
                    val waitingForward = forwarding?.takeIf { onSendForward != null && editing == null }
                    ComposerPreview(
                        forwardCard(waitingForward),
                        MaterialTheme.colorScheme.tertiary,
                        stringResource(R.string.chat_cancel_forward),
                        onClose = onCancelForward,
                        modifier = Modifier.padding(top = 8.dp),
                        tag = "forwardStrip",
                        closeTag = "closeForward",
                    )
                    ComposerPreview(
                        editCard(editing) ?: replyCard(replying),
                        MaterialTheme.colorScheme.primary,
                        stringResource(R.string.common_cancel),
                        onClose = { if (editing != null) endEdit() else replying = null },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    fun submit(options: SendOptions) {
                        val rich = richDraft
                        if (rich != null && articles != null) {
                            if (articles.gate.allowed || rich.asFormattedText() != null) {
                                articles.onSend(rich, replying?.id)
                                replying = null
                            } else {
                                articleRows = rich.toRows()
                            }
                            richDraft = null
                            return
                        }
                        if (waitingForward != null) {
                            onSendForward?.invoke(FormattedText(field.text, entities).trimmed(), replying?.id)
                            setDraft(FormattedText(""))
                            replying = null
                            return
                        }
                        val edited = editing
                        val answered = replying
                        val message = FormattedText(field.text, entities).trimmed()
                        when {
                            edited != null -> onEdit?.invoke(edited.id, message)
                            sending != null && options != SendOptions.None -> sending.onSend(message, answered?.id, options)
                            answered != null -> onSendReply(message, answered.id)
                            else -> onSend(message)
                        }
                        if (edited != null) {
                            endEdit()
                        } else {
                            setDraft(FormattedText(""))
                            replying = null
                        }
                    }
                    Composer(
                        field,
                        onValue = { value ->
                            entities = FormattedText(field.text, entities).edited(value.text).entities
                            if (value.text != field.text && editing == null) onTyping(value.text)
                            field = value
                        },
                        entities = entities,
                        emoji = inlineEmoji,
                        onFormat = { action ->
                            val selection = field.selection
                            if (action == FormatAction.Link) {
                                linking = selection
                            } else {
                                entities = formatted(FormattedText(field.text, entities), action, selection).entities
                            }
                        },
                        onSend = { submit(SendOptions.None) },
                        onSendWith = sending?.takeIf { richDraft == null && waitingForward == null && editing == null }?.let { _ -> { options -> submit(options) } },
                        onVoiceSendWith = sending?.let { actions ->
                            { options ->
                                actions.onSendVoice(options)
                                replying = null
                            }
                        },
                        whenOnline = dialog?.let { it.kind == PeerKind.User && it.presence is Presence.Offline && !it.isSaved } == true,
                        onAttachMedia = onAttachMedia,
                        onAttachFile = onAttachFile,
                        attachMenu = attachMenu,
                        recording = recording,
                        onVoiceStart = onVoiceStart.takeIf { editing == null },
                        onVoiceStop = { send ->
                            onVoiceStop(send)
                            if (send) replying = null
                        },
                        stickersOpen = panelOpen,
                        onToggleStickers = stickerActions?.let { actions ->
                            {
                                if (panelOpen) {
                                    panelToKeyboard()
                                    keyboard?.show()
                                } else {
                                    panelOpen = true
                                    keyboard?.hide()
                                    actions.onOpenPanel()
                                }
                            }
                        },
                        sendReady = waitingForward != null,
                        trailingHint = inlineBot?.placeholder?.takeIf { query?.query?.isEmpty() == true },
                        placeholder = forceReply?.placeholder ?: replyKeyboard?.placeholder,
                        botKeyboardOpen = botKeyboardOpen && !panelOpen,
                        onArticle = articles?.takeIf { it.gate.available && editing == null && replying?.ephemeral == null }?.let {
                            {
                                articleRows = richDraft?.toRows() ?: FormattedText(field.text, entities).toRows()
                                richDraft = null
                                setDraft(FormattedText(""))
                            }
                        },
                        articleDraft = richDraft?.let { articlePreview(it, ARTICLE_DRAFT_PREVIEW_MAX) },
                        onOpenArticleDraft = {
                            articleRows = richDraft?.toRows()
                            richDraft = null
                        },
                        onDeleteArticleDraft = {
                            richDraft = null
                            articles?.onDeleteDraft?.invoke()
                        },
                        onToggleBotKeyboard = replyKeyboard?.let {
                            {
                                if (botKeyboardOpen && !panelOpen) {
                                    botKeyboardOpen = false
                                    keyboard?.show()
                                } else {
                                    botKeyboardOpen = true
                                    panelOpen = false
                                    keyboard?.hide()
                                }
                            }
                        },
                        menuButton = menuButton?.text?.takeIf { botActions != null },
                        onMenuButton = { botActions?.onMenuApp(webAppTheme) },
                        attachBots = if (botActions != null) attachBots else emptyList(),
                        onAttachBot = { bot -> if (bot.consent != null) addingAttachBot = bot else botActions?.onAttachApp(bot, webAppTheme, false) },
                    )
                }
                if (replyKeyboard != null && botKeyboardOpen && !panelOpen && editing == null && membership == Membership.Member && onStartBot == null) {
                    ReplyKeyboardPanel(replyKeyboard) { press(keyboardSource, it) }
                }
            }
            val panelShown = panelOpen && stickerActions != null && membership == Membership.Member && onStartBot == null && !searching && selection.isEmpty()
            if (stickerActions != null) {
                KeyboardWatcher(
                    onHeight = stickerActions.onKeyboardHeight,
                    onRise = { if (panelOpen && !panelSearching) panelToKeyboard() },
                )
            }
            PanelArea(
                open = panelShown,
                compact = panelSearching,
                panelHeight = stickerState.keyboardHeight.takeIf { it > 0 }?.dp ?: DEFAULT_PANEL_HEIGHT,
                hold = bottomHold,
                onHeld = { bottomHold = 0 },
                onSize = { bottomArea = it },
            ) { bottomInset ->
                if (stickerActions != null) {
                    MediaPanel(
                        panelTab,
                        onTab = { panelTab = it },
                        state = stickerState,
                        stickers = stickers,
                        customEmoji = customEmoji,
                        downloads = downloads,
                        actions = remember(stickerActions) {
                            stickerActions.copy(
                                onSend = {
                                    stickerActions.onSend(it)
                                    replying = null
                                },
                                onSendGif = {
                                    stickerActions.onSendGif(it)
                                    replying = null
                                },
                            )
                        },
                        premium = reactions.premium,
                        compact = panelSearching,
                        bottomInset = bottomInset,
                        onEmoji = ::putEmoji,
                        onBackspace = ::deleteBackwards,
                        onDownload = onDownload,
                        onSearchFocus = { panelSearching = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottomHeight + 12.dp)) {
            if (reactorActions != null) {
                UnreadReactionsButton(
                    dialog?.reactions ?: 0,
                    onNext = reactorActions.onNextUnread,
                    onReadAll = reactorActions.onReadAll,
                )
            }
            ScrollDownButton((awayFromBottom || newerAvailable) && !(searching && resultsShown && searchQuery.isNotBlank()), onClick = ::goDown)
        }
    }
    fun actionsOf(message: MessageItem): List<MessageAction> {
        val ephemeral = message.ephemeral != null
        val failed = message.failed
        return buildList {
            if (!failed) add(MessageAction.Reply)
            if (onForward != null && !ephemeral && !failed) {
                add(MessageAction.Forward)
                add(MessageAction.Select)
            }
            if (message.text.isNotEmpty() || message.article != null) add(MessageAction.Copy)
            if (translations != null && message.translatable && !failed) add(if (message.translation != null) MessageAction.ShowOriginal else MessageAction.Translate)
            if (pins != null && pins.canPin && !ephemeral && !message.pending && !message.streaming) add(if (message.id in pinned.ids) MessageAction.Unpin else MessageAction.Pin)
            if (onSaveToDownloads != null && !message.pending && !message.streaming && message.media?.savable == true) add(MessageAction.SaveToDownloads)
            val article = message.article
            val richEdit = article != null && !article.part && articles?.onEdit != null && articles.gate.allowed
            if (!ephemeral && !failed && message.out && (richEdit || (onEdit != null && message.text.isNotEmpty() && message.media == null && article == null))) add(MessageAction.Edit)
            if (safetyActions != null && !message.out && !ephemeral && !failed && !message.pending && !message.streaming) add(MessageAction.Report)
            if (onDelete != null) add(MessageAction.Delete)
        }
    }
    fun runAction(message: MessageItem, action: MessageAction) {
        when (action) {
            MessageAction.GoTo -> {
                if (message.id in pinned.ids) pins?.onShow?.invoke(message.id)
                openMessage(message.id)
            }
            MessageAction.Reply -> {
                if (editing != null) endEdit()
                replying = message
            }
            MessageAction.Forward -> forwardIds = listOf(message.id)
            MessageAction.Select -> selection = setOf(message.id)
            MessageAction.Copy -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", message.article?.plainText() ?: message.text))) }
            MessageAction.SaveToDownloads -> message.media?.let { onSaveToDownloads?.invoke(it) }
            MessageAction.Pin -> pinning = message
            MessageAction.Unpin -> pins?.onUnpin?.invoke(message.id)
            MessageAction.Translate -> translations?.onTranslate?.invoke(message.id)
            MessageAction.ShowOriginal -> translations?.onShowOriginal?.invoke(message.id)
            MessageAction.Edit -> message.article?.let { article ->
                articleEditing = message
                articleRows = article.toRows()
            } ?: run {
                if (editing == null) beforeEdit = Draft(FormattedText(field.text, entities), replying?.id)
                editing = message
                replying = null
                setDraft(FormattedText(message.text, message.entities))
            }
            MessageAction.Report -> safetyActions?.onReportMessages?.invoke(listOf(message.id))
            MessageAction.Delete -> deleting = message
        }
    }
    contextTarget?.let { target ->
        val message = byId[target.message.id] ?: target.message
        val ephemeral = message.ephemeral != null
        val failed = message.failed
        MessageContextMenu(
            target,
            lifted,
            reveal = { reveal.value },
            lift = { lift.value },
            actions = actionsOf(message),
            reactions = if (onReact != null && !ephemeral && !failed) reactions.offeredFor(message.reactions) else ChatReactionState(),
            chosen = message.reactions.filter { it.chosen }.map { it.reaction }.toSet(),
            emoji = inlineEmoji,
            onReact = { reaction, from, big ->
                closeContext()
                react(message, reaction, from, picked = true, big = big)
            },
            onAction = { action ->
                closeContext()
                runAction(message, action)
            },
            onDismiss = ::closeContext,
            pickerEmoji = PickerEmoji(stickerState.emojiSets, stickerState.setStickers, stickerState.emojiSearch),
            pickerActions = stickerActions?.let { PickerActions(it.onOpenEmoji, it.onOpenTab, it.onSearchEmoji, it.onLoad) } ?: PickerActions(),
            avatars = avatars,
            onShowReactors = reactorActions?.takeIf { message.reactorsListed && reactionTotal(message) > 0 }?.let { actions ->
                {
                    closeContext()
                    actions.onShow(message.id)
                }
            },
        )
    }
    ReactionEffectLayer(reactionEffects, inlineEmoji, reactions.art, Modifier.fillMaxSize())
    forwardIds?.let { ids ->
        ForwardSheet(
            forwardTargets,
            avatars,
            onPick = { to, dropAuthor, dropCaptions ->
                forwardIds = null
                selection = emptySet()
                onForward?.invoke(ForwardChoice(ids, to, dropAuthor, dropCaptions))
            },
            onDismiss = { forwardIds = null },
        )
    }
    val articleActions = articles
    articleRows?.let { initial ->
        if (articleActions == null) return@let
        val edited = articleEditing
        ArticleEditor(
            initial,
            articleActions.gate,
            onSend = { article ->
                articleRows = null
                articleEditing = null
                articleActions.onClearUploads()
                if (edited != null) {
                    articleActions.onEdit?.invoke(edited.id, article)
                } else {
                    articleActions.onSend(article, replying?.id)
                    replying = null
                }
            },
            onClose = { article ->
                articleRows = null
                articleEditing = null
                articleActions.onClearUploads()
                if (edited == null) {
                    richDraft = article.takeIf { it.blocks.isNotEmpty() }
                    articleActions.onClose(article, replying?.id)
                }
            },
            editing = edited != null,
            uploads = articleActions.uploads,
            onPickMedia = articleActions.onPickMedia,
            onRetryUpload = articleActions.onRetryUpload,
            onRemoveUpload = articleActions.onRemoveUpload,
        )
    }
    if (attachment != null && attachmentActions != null) {
        val sheetActions = remember(attachmentActions) {
            ChatAttachmentActions(
                attachmentActions.onAsFile,
                attachmentActions.onSpoiler,
                attachmentActions.onRemove,
                { caption, options ->
                    attachmentActions.onSend(caption, options)
                    replying = null
                },
                attachmentActions.onDismiss,
            )
        }
        AttachmentSheet(attachment, sheetActions, whenOnline = dialog?.let { it.kind == PeerKind.User && it.presence is Presence.Offline && !it.isSaved } == true)
    }
    linking?.let { selection ->
        LinkDialog(
            initial = linkAt(FormattedText(field.text, entities), selection),
            onConfirm = { url ->
                linking = null
                entities = FormattedText(field.text, entities).toggled(EntityType.TextUrl(url), selection.min, selection.max).entities
            },
            onDismiss = { linking = null },
        )
    }
    deleting?.let { message ->
        val revoke = when {
            message.ephemeral != null -> null
            dialog?.kind == PeerKind.User || dialog?.kind == PeerKind.Bot -> stringResource(R.string.chat_delete_for, title)
            dialog?.kind == PeerKind.Group -> if (message.out) stringResource(R.string.chat_delete_everyone) else null
            else -> null
        }
        DeleteMessageDialog(
            revokeLabel = revoke,
            onConfirm = { all ->
                deleting = null
                onDelete?.invoke(message.id, all)
            },
            onDismiss = { deleting = null },
        )
    }
    confirmingUrl?.let { url ->
        OpenLinkDialog(
            url,
            onOpen = {
                confirmingUrl = null
                onOpenLink(url)
            },
            onDismiss = { confirmingUrl = null },
        )
    }
    alert?.let { text -> BotAlert(text) { alert = null } }
    login.request?.let { request -> LoginDialog(request) { accepted, write, phone -> botActions?.onAnswerLogin(accepted, write, phone) } }
    webApp?.let { app ->
        WebAppScreen(
            app,
            onClose = { botActions?.onCloseWebApp() },
            onData = { data -> botActions?.onWebAppData(data) },
            onOpenLink = onOpenLink,
            onSwitchInline = { query, chatTypes ->
                val bot = app.inlineBot
                botActions?.onCloseWebApp()
                if (bot != null) {
                    val text = "@$bot $query"
                    if (chatTypes.isEmpty()) setDraft(FormattedText(text)) else botActions?.onSwitchInline(text)
                }
            },
            device = botActions?.webAppDevice ?: WebAppDevice.None,
        )
    }
    addingAttachBot?.let { bot ->
        bot.consent?.let { consent ->
            WebAppConsentDialog(
                consent,
                onAnswer = { write ->
                    addingAttachBot = null
                    botActions?.onAttachApp(bot, webAppTheme, write)
                },
                onDismiss = { addingAttachBot = null },
            )
        }
    }
    if (pollVotes != null && polls != null) PollVotesSheet(pollVotes, avatars, polls.onMoreVotes, polls.onCloseVotes)
    val setAlert = places?.onAlert
    val liveSharers = places?.sharers.orEmpty()
    if (alertingLive && liveAlerts && setAlert != null && liveAlertOffered(liveSharers, livePrivate)) {
        LiveAlertSheet(liveSharers, person = dialog?.title?.takeIf { livePrivate }, me = null, onAlert = setAlert, onShare = places?.onShareAlert, onDismiss = { alertingLive = false })
    }
    if (reactors != null && reactorActions != null) ReactorsSheet(reactors, avatars, inlineEmoji, reactorActions)
    pinning?.let { message ->
        val privateChat = dialog?.kind == PeerKind.User || dialog?.kind == PeerKind.Bot
        PinDialog(
            peerName = title.takeIf { privateChat && dialog?.isSaved != true },
            notifies = dialog?.kind == PeerKind.Group || dialog?.kind == PeerKind.Channel,
            onConfirm = { silent, forBoth ->
                pinning = null
                pins?.onPin?.invoke(message.id, silent, forBoth)
            },
            onDismiss = { pinning = null },
        )
    }
    if (pinnedListOpen && pinned.messages.isNotEmpty()) {
        PinnedListScreen(
            pinned, dialog, wallpaper, downloads, stickers, onDownload, customEmoji, avatars,
            canPin = pins?.canPin == true,
            onOpen = { id ->
                pinnedListOpen = false
                pins?.onShow?.invoke(id)
                openMessage(id)
            },
            actionsOf = { message -> listOf(MessageAction.GoTo) + actionsOf(message) },
            onAction = { message, action ->
                if (action != MessageAction.Copy && action != MessageAction.Unpin) pinnedListOpen = false
                runAction(message, action)
            },
            onUnpinAll = { unpinningAll = true },
            onHide = {
                pinnedListOpen = false
                pins?.onHide?.invoke()
            },
            onClose = { pinnedListOpen = false },
        )
    }
    unpinningOne?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.chat_unpin_title),
            text = null,
            confirm = stringResource(R.string.chat_unpin),
            tag = "confirmUnpin",
            onConfirm = {
                unpinningOne = null
                pins?.onUnpin?.invoke(id)
            },
            onDismiss = { unpinningOne = null },
        )
    }
    if (unpinningAll) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.chat_unpin_all_title, pinned.messages.size, pinned.messages.size),
            text = null,
            confirm = stringResource(R.string.chat_unpin_all),
            tag = "confirmUnpinAll",
            onConfirm = {
                unpinningAll = false
                pinnedListOpen = false
                pins?.onUnpinAll?.invoke()
            },
            onDismiss = { unpinningAll = false },
        )
    }
    removing?.let { removal ->
        dialog?.let { current ->
            ChatRemovalDialog(
                current,
                removal,
                onConfirm = { revoke ->
                    removing = null
                    onRemove?.invoke(removal, revoke)
                },
                onDismiss = { removing = null },
            )
        }
    }
    if (leaving) {
        ConfirmDialog(
            title = stringResource(leaveLabel(dialog?.kind)),
            text = stringResource(if (dialog?.kind == PeerKind.Channel) R.string.chat_leave_channel_text else R.string.chat_leave_group_text, title),
            confirm = stringResource(R.string.chat_leave),
            tag = "confirmLeave",
            onConfirm = {
                leaving = false
                onLeave?.invoke()
            },
            onDismiss = { leaving = false },
        )
    }
    if (blockingChat && safetyActions != null) {
        ConfirmDialog(
            title = stringResource(R.string.block_confirm_title, title),
            text = stringResource(R.string.block_confirm_text, title),
            confirm = stringResource(R.string.block_action),
            tag = "confirmBlock",
            onConfirm = {
                blockingChat = false
                safetyActions.onBlock()
            },
            onDismiss = { blockingChat = false },
        )
    }
    if (addingContact && safetyActions != null) {
        AddProfileContactDialog(
            name = title,
            onAdd = { first, last, share ->
                addingContact = false
                safetyActions.onAddContact(first, last, share)
            },
            onDismiss = { addingContact = false },
        )
    }
    if (safetyActions != null && safety.report != null) ReportSheet(safety.report, safetyActions.report)
    val set = stickerState.openSet
    if (set != null && stickerActions != null) {
        StickerSetSheet(
            set,
            stickers,
            onLoadSticker = stickerActions.onLoad,
            onSend = { sticker ->
                stickerActions.onSend(sticker)
                replying = null
                stickerActions.onDismissSet()
            },
            onInstall = { stickerActions.onInstall(set.info.key, it) },
            onDismiss = stickerActions.onDismissSet,
        )
    }
}

private const val OLDER_AHEAD = 5
private const val NEWER_AHEAD = 3
private const val FOLLOW_BOTTOM = 3
private const val SEARCH_AHEAD = 3
private val ListGap = 8.dp
private val HeaderGap = 8.dp
private val BottomScrimHeight = 84.dp

private class LastPinned {
    var pinned: PinnedMessages? = null
}

@Composable
private fun ChatActionBar(label: String, tag: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp).testTag(tag),
    ) {
        Text(label)
    }
}
