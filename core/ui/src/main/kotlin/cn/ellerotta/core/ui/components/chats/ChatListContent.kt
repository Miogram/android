package cn.ellerotta.core.ui.components.chats

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.list.ChatComponent
import cn.ellerotta.core.ui.components.common.StoryRingBox

@Composable
internal fun ChatListContent(
    dialogs: List<DialogItem>,
    avatars: Map<String, ByteArray>,
    now: Long,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onOpenChat: (DialogItem) -> Unit,
    onMenu: (DialogItem) -> Unit,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    nestedScrollConnection: NestedScrollConnection? = null,
    selectedKey: String? = null,
    header: LazyListScope.() -> Unit = {},
    footer: LazyListScope.() -> Unit = {},
    loading: Boolean = false,
    onEnd: (() -> Unit)? = null,
    onOpenStories: ((String) -> Unit)? = null,
) {
    if (onEnd != null) {
        val nearEnd by remember(listState) {
            derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 10 } ?: false }
        }
        LaunchedEffect(nearEnd, dialogs.size) { if (nearEnd) onEnd() }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .then(if (nestedScrollConnection != null) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
            .testTag("dialogs"),
        contentPadding = contentPadding,
    ) {
        header()
        if (dialogs.isEmpty() && !loading) item(key = "empty") { empty() }
        items(dialogs, key = { it.key }) { dialog ->
            ChatRow(dialog, avatars, now, dialog.key == selectedKey, onOpenChat, onMenu, Modifier.animateItem(), onOpenStories)
        }
        footer()
        if (loading) {
            item(key = "loading") {
                val label = stringResource(R.string.chats_loading)
                Box(Modifier.fillMaxWidth().padding(16.dp).testTag("chatsLoading"), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(32.dp).semantics { contentDescription = label })
                }
            }
        }
    }
}

@Composable
fun DialogComponent(
    dialog: DialogItem,
    avatars: Map<String, ByteArray>,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onOpenStories: ((String) -> Unit)? = null,
    tag: String = "dialog_${dialog.key}",
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val preview = previewText(dialog.listPreview, dialog.kind)
    ChatComponent(
        name = dialogTitle(dialog),
        message = preview,
        time = formatChatDate(dialog.date, now, locale, DateFormat.is24HourFormat(context)),
        unreadCount = dialog.unread,
        unreadMentionCount = dialog.mentions,
        avatar = {
            Box {
                StoryRingBox(
                    dialog.story, 48,
                    background = colors.surface,
                    onClick = onOpenStories?.let { open -> { open(dialog.key) } },
                    modifier = Modifier.testTag("dialogAvatar_${dialog.key}"),
                ) { photo -> DialogAvatar(dialog, avatars, size = photo) }
                OnlineDot(dialog.online, ring = colors.surface, modifier = Modifier.align(Alignment.BottomEnd))
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.testTag(tag),
        isPinned = dialog.pinned,
        isMuted = dialog.muted,
        unreadMark = dialog.unreadMark,
        isSelected = isSelected,
        typing = dialog.typing,
        seed = dialog.peerId.toInt(),
        messageModifier = Modifier.testTag("preview_${dialog.key}"),
        unreadModifier = Modifier.testTag("unread_${dialog.key}"),
        badge = if (dialog.verified || dialog.premium) {
            {
                if (dialog.verified) Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.chats_verified), Modifier.size(16.dp), tint = colors.primary)
                if (dialog.premium) Icon(painterResource(R.drawable.ic_star), stringResource(R.string.chats_premium), Modifier.size(16.dp), tint = colors.primary)
            }
        } else {
            null
        },
        status = if (dialog.outgoing && dialog.typing == null && dialog.draft == null) {
            {
                Icon(
                    painterResource(if (dialog.read) R.drawable.ic_done_all else R.drawable.ic_check),
                    stringResource(if (dialog.read) R.string.chats_read else R.string.chats_sent),
                    Modifier.size(16.dp),
                    tint = colors.primary,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun ChatRow(
    dialog: DialogItem,
    avatars: Map<String, ByteArray>,
    now: Long,
    selected: Boolean,
    onOpenChat: (DialogItem) -> Unit,
    onMenu: (DialogItem) -> Unit,
    modifier: Modifier = Modifier,
    onOpenStories: ((String) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    DialogComponent(
        dialog, avatars, now,
        onClick = { onOpenChat(dialog) },
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onMenu(dialog)
        },
        modifier = modifier,
        isSelected = selected,
        onOpenStories = onOpenStories,
    )
}
