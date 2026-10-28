package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PinnedMessages
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.TopBar
import cn.ellerotta.core.ui.components.common.TopBarState
import cn.ellerotta.core.ui.components.common.topBarHeight
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun PinnedListScreen(
    pinned: PinnedMessages,
    dialog: DialogItem?,
    wallpaper: ChatWallpaper?,
    downloads: Map<String, DownloadState>,
    stickers: Map<String, ByteArray>,
    onDownload: (MediaItem) -> Unit,
    customEmoji: Map<Long, MediaItem>,
    avatars: Map<String, ByteArray>,
    canPin: Boolean,
    onOpen: (Int) -> Unit,
    onUnpinAll: () -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
    actionsOf: (MessageItem) -> List<MessageAction> = { emptyList() },
    onAction: (MessageItem, MessageAction) -> Unit = { _, _ -> },
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PinnedListContent(pinned, dialog, wallpaper, downloads, stickers, onDownload, customEmoji, avatars, canPin, onOpen, onUnpinAll, onHide, onClose, actionsOf, onAction)
    }
}

@Composable
internal fun PinnedListContent(
    pinned: PinnedMessages,
    dialog: DialogItem?,
    wallpaper: ChatWallpaper?,
    downloads: Map<String, DownloadState>,
    stickers: Map<String, ByteArray>,
    onDownload: (MediaItem) -> Unit,
    customEmoji: Map<Long, MediaItem>,
    avatars: Map<String, ByteArray>,
    canPin: Boolean,
    onOpen: (Int) -> Unit,
    onUnpinAll: () -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
    actionsOf: (MessageItem) -> List<MessageAction> = { emptyList() },
    onAction: (MessageItem, MessageAction) -> Unit = { _, _ -> },
) {
    val list = rememberLazyListState()
    val oldestFirst = remember(pinned.messages) { pinned.messages.reversed() }
    val byId = remember(oldestFirst) { oldestFirst.associateBy { it.id } }
    val rows = remember(oldestFirst) { entries(oldestFirst, ZoneId.systemDefault()) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val count = pinned.messages.size
    var target by remember { mutableStateOf<ContextTarget?>(null) }
    val reveal = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val lifted = rememberGraphicsLayer()
    var menuJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val haptics = LocalHapticFeedback.current
    val blur = with(LocalDensity.current) { CONTEXT_BLUR.toPx() }
    fun openMenu(message: MessageItem, bounds: Rect) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        menuJob?.cancel()
        target = ContextTarget(message, bounds, end = message.onEndSide(dialog))
        menuJob = scope.launch {
            list.stopScroll()
            launch { lift.animateTo(1f, spatial) }
            reveal.animateTo(1f, effects)
        }
    }
    fun closeMenu() {
        if (target == null) return
        menuJob?.cancel()
        menuJob = scope.launch {
            val down = launch { lift.animateTo(0f, spatial) }
            reveal.animateTo(0f, effects)
            down.join()
            target = null
        }
    }
    Box(Modifier.fillMaxSize().testTag("pinnedListScreen")) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val radius = blur * reveal.value.coerceIn(0f, 1f)
                    renderEffect = if (radius >= 1f) BlurEffect(radius, radius, TileMode.Decal) else null
                }
                .then(if (target != null) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            ChatBackground(Modifier.fillMaxSize(), wallpaper)
            ChatMessageList(
                rows, ZoneId.systemDefault(), list, dialog, byId, downloads, stickers,
                loadingOlder = false,
                playback = null,
                onDownload = onDownload,
                onToggleVoice = {},
                onSelect = ::openMenu,
                onOpenMedia = { onOpen(it.id) },
                onOpenSticker = {},
                customEmoji = customEmoji,
                avatars = avatars,
                onTap = { onOpen(it.id) },
                lifted = lifted,
                liftedId = target?.message?.id,
                onLiftedBounds = { bounds -> target?.let { if (it.bounds != bounds) target = it.copy(bounds = bounds) } },
                contentPadding = PaddingValues(top = topBarHeight() + 8.dp, bottom = bottom + BUTTON_SPACE),
                modifier = Modifier.fillMaxSize(),
            )
            TopBar(pluralStringResource(R.plurals.chat_pinned_count, count, count), TopBarState.Shown, onBack = onClose, backTag = "pinnedListBack", titleTag = "pinnedListTitle")
            Button(
                onClick = if (canPin) onUnpinAll else onHide,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).testTag("pinnedListAction"),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(if (canPin) R.string.chat_unpin_all_messages else R.string.chat_pinned_hide))
            }
        }
        target?.let { shown ->
            val message = byId[shown.message.id] ?: shown.message
            MessageContextMenu(
                shown,
                lifted,
                reveal = { reveal.value },
                lift = { lift.value },
                actions = actionsOf(message),
                reactions = ChatReactionState(),
                chosen = emptySet(),
                emoji = InlineEmoji(emptyMap(), emptyMap()),
                onReact = { _, _, _ -> },
                onAction = { action ->
                    closeMenu()
                    onAction(message, action)
                },
                onDismiss = ::closeMenu,
                avatars = avatars,
            )
        }
    }
}

private val BUTTON_SPACE = 88.dp
