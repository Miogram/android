package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ActivityKind
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.ArticleRow
import cn.ellerotta.core.model.ArticleRowKind
import cn.ellerotta.core.model.ChatSearchState
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PeerReaction
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.Reactor
import cn.ellerotta.core.model.ReactorsState
import cn.ellerotta.core.model.ThreadTitle
import cn.ellerotta.core.model.VoiceRecording
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.ChatAction
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme
import cn.ellerotta.core.ui.components.chat.ChatChromePreviewData as Data

@Composable
private fun SampleComposer(
    text: String = "",
    selection: TextRange = TextRange(text.length),
    recording: VoiceRecording? = null,
    articleDraft: String? = null,
    sendReady: Boolean = false,
    menuButton: String? = null,
) {
    var value by remember { mutableStateOf(TextFieldValue(text, selection)) }
    Composer(
        value,
        onValue = { value = it },
        onSend = {},
        onAttachMedia = {},
        onAttachFile = {},
        recording = recording,
        onVoiceStart = {},
        onToggleStickers = {},
        onArticle = if (menuButton == null) ({}) else null,
        articleDraft = articleDraft,
        sendReady = sendReady,
        menuButton = menuButton,
    )
}

@MioPreviews
@Composable
internal fun ComposerEmptyPreview() {
    PreviewTheme { SampleComposer() }
}

@MioPreviews
@Composable
internal fun ComposerBotMenuButtonPreview() {
    PreviewTheme { SampleComposer(menuButton = "Order food") }
}

@MioPreviews
@Composable
internal fun ComposerTextPreview() {
    PreviewTheme { SampleComposer("See you at the station at six, I'll bring the tickets") }
}

@MioPreviews
@Composable
internal fun ComposerFormattingPreview() {
    PreviewTheme { SampleComposer("See you at the station at six", selection = TextRange(15, 22)) }
}

@MioPreviews
@Composable
internal fun ComposerReplyingPreview() {
    PreviewTheme {
        Column {
            ComposerPreview(replyCard(Data.incoming), MaterialTheme.colorScheme.primary, stringResource(R.string.common_cancel), onClose = {}, modifier = Modifier.padding(top = 8.dp))
            SampleComposer("Yes, see you there")
        }
    }
}

@MioPreviews
@Composable
internal fun ComposerEditingPreview() {
    PreviewTheme {
        Column {
            ComposerPreview(editCard(Data.outgoing), MaterialTheme.colorScheme.primary, stringResource(R.string.common_cancel), onClose = {}, modifier = Modifier.padding(top = 8.dp))
            SampleComposer(Data.outgoing.text)
        }
    }
}

@MioPreviews
@Composable
internal fun ComposerForwardingPreview() {
    PreviewTheme {
        Column {
            ComposerPreview(
                forwardCard(Data.forward),
                MaterialTheme.colorScheme.tertiary,
                stringResource(R.string.chat_cancel_forward),
                onClose = {},
                modifier = Modifier.padding(top = 8.dp),
                tag = "forwardStrip",
                closeTag = "closeForward",
            )
            SampleComposer(sendReady = true)
        }
    }
}

@MioPreviews
@Composable
internal fun ComposerForwardingSeveralPreview() {
    PreviewTheme {
        Column {
            ComposerPreview(forwardCard(Data.forwardSeveral), MaterialTheme.colorScheme.tertiary, stringResource(R.string.chat_cancel_forward), onClose = {}, modifier = Modifier.padding(top = 8.dp))
            SampleComposer(sendReady = true)
        }
    }
}

@MioPreviews
@Composable
internal fun ComposerArticleDraftPreview() {
    PreviewTheme { SampleComposer(articleDraft = "Weekend trip") }
}

@MioPreviews
@Composable
internal fun ComposerRecordingPreview() {
    PreviewTheme { Box(Modifier.padding(vertical = 8.dp)) { SampleComposer(recording = VoiceRecording(elapsedMs = 4_200, level = 0.6f)) } }
}

@MioPreviews
@Composable
internal fun RecordingShellPreview() {
    PreviewTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RecordingShell(VoiceRecording(elapsedMs = 12_300, level = 0.4f), locked = false, drag = -40f, onDiscard = {})
            RecordingShell(VoiceRecording(elapsedMs = 75_000, level = 0.8f), locked = true, drag = 0f, onDiscard = {})
        }
    }
}

@MioPreviews
@Composable
internal fun VoiceHintPreview() {
    PreviewTheme { Box(Modifier.padding(16.dp)) { VoiceHint() } }
}

@MioPreviews
@Composable
internal fun FormattingBarPreview() {
    PreviewTheme { Box(Modifier.padding(16.dp)) { FormattingBar(onAction = {}) } }
}

@MioPreviews
@Composable
internal fun EmojiSearchFieldPreview() {
    PreviewTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var empty by remember { mutableStateOf("") }
            EmojiSearchField(empty, onQuery = { empty = it })
            var typed by remember { mutableStateOf("cat") }
            EmojiSearchField(typed, onQuery = { typed = it }, placeholder = stringResource(R.string.chat_stickers_search), color = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}

@Composable
private fun ContextMenuSample(message: MessageItem, actions: List<MessageAction>) {
    val layer = rememberGraphicsLayer()
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.fillMaxSize()) {
        ChatBackground(Modifier.fillMaxSize())
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (message.out) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .align(if (message.out) Alignment.CenterEnd else Alignment.CenterStart)
                .offset(y = 40.dp)
                .padding(horizontal = 12.dp)
                .widthIn(max = 260.dp)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        bounds?.let { shown ->
            MessageContextMenu(
                ContextTarget(message, shown),
                layer,
                reveal = { 1f },
                lift = { 1f },
                actions = actions,
                reactions = Data.reactions,
                chosen = Data.chosen,
                emoji = InlineEmoji(emptyMap(), emptyMap()),
                onReact = { _, _, _ -> },
                onAction = {},
                onDismiss = {},
            )
        }
    }
}

@MioPreviews
@Composable
internal fun MessageContextMenuPreview() {
    PreviewTheme { ContextMenuSample(Data.incoming, Data.incomingActions) }
}

@MioPreviews
@Composable
internal fun MessageContextMenuOwnPreview() {
    PreviewTheme { ContextMenuSample(Data.outgoing, Data.messageActions) }
}

@MioPreviews
@Composable
internal fun ReactionPickerRowPreview() {
    PreviewTheme {
        Box(Modifier.padding(12.dp)) {
            ReactionPicker(
                Data.reactions,
                Data.chosen,
                InlineEmoji(emptyMap(), emptyMap()),
                PickerEmoji(),
                PickerActions(),
                maxWidth = 296.dp,
                alignEnd = false,
                expanded = false,
                onExpand = {},
                onPick = { _, _, _ -> },
            )
        }
    }
}

@MioPreviews
@Composable
internal fun ReactionPickerExpandedPreview() {
    PreviewTheme {
        Box(Modifier.padding(12.dp)) {
            var expanded by remember { mutableStateOf(true) }
            ReactionPicker(
                Data.reactions.copy(custom = true, premium = true),
                Data.chosen,
                InlineEmoji(Data.customEmojiById, Data.stickerFiles),
                PickerEmoji(sets = listOf(Data.emojiSet), setEmoji = mapOf(Data.emojiSet.key to Data.emojiSetStickers)),
                PickerActions(),
                maxWidth = 296.dp,
                alignEnd = false,
                expanded = expanded,
                onExpand = { expanded = it },
                onPick = { _, _, _ -> },
            )
        }
    }
}

@MioPreviews
@Composable
internal fun ReplyQuotePreview() {
    PreviewTheme {
        Box(Modifier.padding(16.dp).widthIn(max = 280.dp)) {
            ReplyQuote(Data.incoming.senderName, Data.incoming.text, MaterialTheme.colorScheme.primary, onClick = {})
        }
    }
}

@Composable
private fun MediaPanelSample(tab: PanelTab) {
    MediaPanel(
        tab,
        onTab = {},
        state = Data.stickerState,
        stickers = Data.stickerFiles,
        customEmoji = Data.customEmojiById,
        downloads = emptyMap(),
        actions = Data.stickerActions,
        premium = true,
        compact = false,
        bottomInset = 0.dp,
        onEmoji = {},
        onBackspace = {},
        onDownload = {},
        onSearchFocus = {},
        modifier = Modifier.height(360.dp),
    )
}

@MioPreviews
@Composable
internal fun MediaPanelEmojiPreview() {
    PreviewTheme { MediaPanelSample(PanelTab.Emoji) }
}

@MioPreviews
@Composable
internal fun MediaPanelStickersPreview() {
    PreviewTheme { MediaPanelSample(PanelTab.Stickers) }
}

@MioPreviews
@Composable
internal fun MediaPanelGifsPreview() {
    PreviewTheme { MediaPanelSample(PanelTab.Gifs) }
}

@MioPreviews
@Composable
internal fun StickerSuggestionsPreview() {
    PreviewTheme { StickerSuggestions(Data.stickerList, Data.stickerFiles, onLoadSticker = {}, onSend = {}) }
}

@MioPreviews
@Composable
internal fun StickerSetContentPreview() {
    PreviewTheme { StickerSetContent(Data.stickerSetDetail, Data.stickerFiles, onLoadSticker = {}, onSend = {}, onInstall = {}) }
}

@MioPreviews
@Composable
internal fun StickerPreviewContentPreview() {
    PreviewTheme { MediaPreviewContent(Data.stickerList.first(), Data.stickerFiles, emptyMap(), onSend = {}, onShowSet = {}, faved = true, onFave = {}) }
}

@MioPreviews
@Composable
internal fun GifPreviewContentPreview() {
    PreviewTheme { MediaPreviewContent(Data.gifs.first(), emptyMap(), emptyMap(), onSend = {}, onShowSet = null, onRemove = {}) }
}

@MioPreviews
@Composable
internal fun ReactorsContentPreview() {
    val like = Reaction.Emoji("\uD83D\uDC4D")
    val heart = Reaction.Emoji("\u2764\uFE0F")
    val state = ReactorsState(
        messageId = 1,
        total = 3,
        list = listOf(
            PeerReaction(Reactor(1, "Ada Lovelace"), "u1", like, 0),
            PeerReaction(Reactor(2, "Charles Babbage"), "u2", heart, 0),
            PeerReaction(Reactor(3, "Mary Somerville"), "u3", like, 0),
        ),
        count = 3,
        loading = false,
    )
    PreviewTheme { ReactorsContent(state, emptyMap(), InlineEmoji(emptyMap(), emptyMap()), onMore = {}, onOpenPeer = {}) }
}

@MioPreviews
@Composable
internal fun UnreadReactionsButtonPreview() {
    PreviewTheme { Box(Modifier.padding(16.dp)) { UnreadReactionsButton(3, onNext = {}, onReadAll = {}) } }
}

@MioPreviews
@Composable
internal fun ScrollDownButtonPreview() {
    PreviewTheme { Box(Modifier.padding(16.dp)) { ScrollDownButton(true, onClick = {}) } }
}

@MioPreviews
@Composable
internal fun SetOrderContentPreview() {
    PreviewTheme { SetOrderContent(listOf(Data.stickerSet, Data.emojiSet), Data.stickerFiles, onLoad = {}, onOrder = {}) }
}

@MioPreviews
@Composable
internal fun PinnedBarSinglePreview() {
    PreviewTheme {
        Box(Modifier.padding(8.dp)) {
            PinnedBar(Data.pinnedOne, emptyMap(), onDownload = {}, onTap = {}, onList = {}, onClose = {}, closeLabel = stringResource(R.string.chat_unpin))
        }
    }
}

@MioPreviews
@Composable
internal fun PinnedBarSeveralPreview() {
    PreviewTheme {
        Box(Modifier.padding(8.dp)) {
            PinnedBar(Data.pinnedSeveral, emptyMap(), onDownload = {}, onTap = {}, onList = {}, onClose = {}, closeLabel = stringResource(R.string.chat_unpin))
        }
    }
}

@MioPreviews
@Composable
internal fun PinnedBarStickerPreview() {
    PreviewTheme {
        Box(Modifier.padding(8.dp)) {
            PinnedBar(Data.pinnedSticker, emptyMap(), onDownload = {}, onTap = {}, onList = {}, onClose = {}, closeLabel = stringResource(R.string.chat_unpin), stickers = Data.stickerFiles)
        }
    }
}

@MioPreviews
@Composable
internal fun PinnedListPreview() {
    PreviewTheme {
        PinnedListContent(
            Data.pinnedSeveral,
            Data.group,
            wallpaper = null,
            downloads = emptyMap(),
            stickers = emptyMap(),
            onDownload = {},
            customEmoji = emptyMap(),
            avatars = Data.avatars,
            canPin = true,
            onOpen = {},
            onUnpinAll = {},
            onHide = {},
            onClose = {},
        )
    }
}

@MioPreviews
@Composable
internal fun MediaViewerPreview() {
    PreviewTheme {
        MediaViewer(Data.viewerItems, initial = 1, downloads = emptyMap(), onBack = {}, onPage = {}, onSave = {}, onShare = {}, onSaveToDownloads = {})
    }
}

@MioPreviews
@Composable
internal fun ChatHeaderUserPreview() {
    PreviewTheme {
        Box(Modifier.padding(vertical = 16.dp)) {
            ChatHeader(
                Data.user.title,
                Data.user,
                Data.avatars,
                onBack = {},
                onOpenProfile = {},
                onSearch = {},
                menu = listOf(ChatAction.Mute, ChatAction.Archive),
                onOpenStories = {},
            )
        }
    }
}

@MioPreviews
@Composable
internal fun ChatHeaderGroupPreview() {
    PreviewTheme {
        Box(Modifier.padding(vertical = 16.dp)) {
            ChatHeader(Data.group.title, Data.group.copy(typing = null, members = 48, membersOnline = 7), Data.avatars, onBack = {}, onOpenProfile = {}, onSearch = {}, menu = listOf(ChatAction.Unmute), onLeave = {})
        }
    }
}

@MioPreviews
@Composable
internal fun ChatHeaderBadgesPreview() {
    PreviewTheme {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ChatHeader("Anna Petrova", Data.user.copy(premium = true, muted = true), Data.avatars, onBack = {}, onOpenProfile = {}, connection = ChatsStatus.Connecting, unreadChats = 3)
            ChatHeader("Anna Petrova", Data.user, Data.avatars, onBack = {}, onOpenProfile = {}, connection = ChatsStatus.WaitingForNetwork)
            ChatHeader("Free coins", Data.user.copy(verified = false, scam = true, kind = PeerKind.Channel, members = 1_200, online = false), Data.avatars, onBack = {}, onOpenProfile = {}, unreadChats = 1_240)
            ChatHeader("Weekend hiking club", Data.group.copy(typing = "Mark", typingMore = listOf("Eve"), activity = ActivityKind.RecordVoice, members = 48, membersOnline = 7), Data.avatars, onBack = {}, onOpenProfile = {})
            ChatHeader("Anna Petrova", Data.user.copy(verified = true, muted = true, typing = "", activity = ActivityKind.UploadPhoto), Data.avatars, onBack = {}, onOpenProfile = {})
        }
    }
}

@MioPreviews
@Composable
internal fun ChatHeaderTopicPreview() {
    PreviewTheme { Box(Modifier.padding(vertical = 16.dp)) { ChatHeader("Releases", Data.group.copy(typing = null), Data.avatars, onBack = {}, onOpenProfile = {}, subtitle = Data.group.title) } }
}

@MioPreviews
@Composable
internal fun ChatHeaderPhonePreview() {
    PreviewTheme {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ChatHeader("Anna Petrova", Data.user.copy(contact = false, phone = "79991234567"), Data.avatars, onBack = {}, onOpenProfile = {})
            ChatHeader("Anna Petrova", Data.user.copy(contact = true, phone = "79991234567"), Data.avatars, onBack = {}, onOpenProfile = {})
            ChatHeader("Helper", Data.user.copy(kind = PeerKind.Bot, contact = false, phone = "79991234567"), Data.avatars, onBack = {}, onOpenProfile = {})
            ChatHeader("Telegram support", Data.user.copy(support = true, contact = false, phone = "42777"), Data.avatars, onBack = {}, onOpenProfile = {})
        }
    }
}

@MioPreviews
@Composable
internal fun ChatHeaderThreadsPreview() {
    PreviewTheme {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ChatHeader("Comments", Data.group, Data.avatars, onBack = {}, onOpenProfile = {}, thread = ThreadTitle(comments = true, count = 12))
            ChatHeader("Replies", Data.group.copy(typing = null, members = 48, membersOnline = 7), Data.avatars, onBack = {}, onOpenProfile = {}, thread = ThreadTitle(comments = false, count = 1))
            ChatHeader("Replies", Data.group.copy(typing = null, members = 48, membersOnline = 7), Data.avatars, onBack = {}, onOpenProfile = {}, thread = ThreadTitle(comments = false, count = 0))
        }
    }
}

@MioPreviews
@Composable
internal fun SelectionHeaderPreview() {
    PreviewTheme { Box(Modifier.padding(vertical = 16.dp)) { SelectionHeader(count = 3, onClose = {}) } }
}

@MioPreviews
@Composable
internal fun SelectionBarPreview() {
    PreviewTheme {
        Column {
            SelectionBar(count = 3, limited = false, onForward = {})
            SelectionBar(count = MAX_SELECTED, limited = true, onForward = {})
        }
    }
}

@MioPreviews
@Composable
internal fun SelectableRowPreview() {
    PreviewTheme {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Data.incoming to true, Data.outgoing to false).forEach { (message, selected) ->
                SelectableRow(active = true, selected = selected, out = message.out, selectable = true, onToggle = {}, tag = "select_${message.id}") {
                    Box(Modifier.fillMaxWidth()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (message.out) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .align(if (message.out) Alignment.CenterEnd else Alignment.CenterStart)
                                .padding(horizontal = 12.dp)
                                .widthIn(max = 240.dp),
                        ) {
                            Text(message.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@MioPreviews
@Composable
internal fun ForwardHeaderPreview() {
    PreviewTheme { Box(Modifier.padding(16.dp)) { ForwardHeader(Data.forwardInfo, MaterialTheme.colorScheme.primary, onOpen = {}) } }
}

@MioPreviews
@Composable
internal fun ForwardPickerPreview() {
    PreviewTheme {
        var dropAuthor by remember { mutableStateOf(false) }
        var dropCaptions by remember { mutableStateOf(false) }
        Box(Modifier.height(480.dp)) {
            ForwardPicker(
                Data.forwardTargets,
                Data.avatars,
                dropAuthor,
                dropCaptions,
                onDropAuthor = { dropAuthor = it },
                onDropCaptions = { dropCaptions = it },
                onPick = { _, _, _ -> },
            )
        }
    }
}

@MioPreviews
@Composable
internal fun ChatSearchBarPreview() {
    PreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatSearchBar(Data.search, index = 0, onOlder = {}, onNewer = {}, onList = {})
            ChatSearchBar(Data.search, index = null, onOlder = {}, onNewer = {}, onList = {})
            ChatSearchBar(ChatSearchState(query = "tram"), index = null, onOlder = {}, onNewer = {}, onList = {})
        }
    }
}

@MioPreviews
@Composable
internal fun ChatSearchResultsPreview() {
    PreviewTheme {
        Box(Modifier.height(400.dp)) {
            ChatSearchResults(Data.search, Data.user.title, onOpen = {}, onMore = {})
        }
    }
}

@MioPreviews
@Composable
internal fun MentionListPreview() {
    PreviewTheme { MentionList(Data.mentions, Data.avatars, onPick = {}) }
}

@MioPreviews
@Composable
internal fun InlineResultsListPreview() {
    PreviewTheme {
        InlineResultsPanel(Data.inlineArticles, more = false, files = emptyMap(), onLoad = {}, onPick = {}, onMore = {}, onSwitchPm = {}, onSwitchWebView = {})
    }
}

@MioPreviews
@Composable
internal fun InlineResultsGalleryPreview() {
    PreviewTheme {
        InlineResultsPanel(Data.inlineGallery, more = false, files = emptyMap(), onLoad = {}, onPick = {}, onMore = {}, onSwitchPm = {}, onSwitchWebView = {})
    }
}

@MioPreviews
@Composable
internal fun ArticleEditorPreview() {
    PreviewTheme {
        ArticleEditorContent(
            Data.articleRows,
            Data.articleGate,
            onSend = {},
            onClose = {},
            uploads = mapOf(Data.articleUpload.id to Data.articleUpload),
            onPickMedia = {},
        )
    }
}

@MioPreviews
@Composable
internal fun ArticleEditorEmptyPreview() {
    PreviewTheme { ArticleEditorContent(emptyList(), Data.articleGate, onSend = {}, onClose = {}) }
}

@MioPreviews
@Composable
internal fun EditorMediaPreview() {
    PreviewTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val uploading = Data.articleUpload
            val failed = uploading.copy(id = 8, name = "trail.mp4", kind = ArticleMediaKind.Video, failed = true)
            EditorMedia(ArticleRow(ArticleRowKind.Media, id = 1, upload = uploading), uploading, onRetry = {}, onRemove = {})
            EditorMedia(ArticleRow(ArticleRowKind.Media, id = 2, upload = failed), failed, onRetry = {}, onRemove = {})
        }
    }
}

@MioPreviews
@Composable
internal fun WallpaperFillPreview() {
    PreviewTheme {
        Row(Modifier.fillMaxWidth().height(200.dp).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Data.wallpaperSolid, Data.wallpaperGradient, Data.wallpaperFreeform).forEach { wallpaper ->
                WallpaperImage(wallpaper, file = null, modifier = Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}
