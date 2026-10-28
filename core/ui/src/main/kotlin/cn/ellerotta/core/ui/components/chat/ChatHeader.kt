package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import cn.ellerotta.core.model.ThreadTitle
import cn.ellerotta.core.ui.components.common.TopBarColors
import cn.ellerotta.core.ui.components.chats.ChatsStatus
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.pluralStringResource
import cn.ellerotta.core.ui.components.chats.UnreadBadge
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import cn.ellerotta.core.ui.components.profile.formatPhone
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.ChatAction
import cn.ellerotta.core.ui.components.chats.icon
import cn.ellerotta.core.ui.components.chats.label
import cn.ellerotta.core.ui.components.chats.DialogAvatar
import cn.ellerotta.core.ui.components.common.BackPill
import cn.ellerotta.core.ui.components.common.StoryRingBox
import cn.ellerotta.core.ui.components.common.TopBarTokens

@Composable
internal fun ChatHeader(
    title: String,
    dialog: DialogItem?,
    avatars: Map<String, ByteArray>,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    menu: List<ChatAction> = emptyList(),
    onMenu: (ChatAction) -> Unit = {},
    onLeave: (() -> Unit)? = null,
    removals: List<ChatRemoval> = emptyList(),
    onRemove: (ChatRemoval) -> Unit = {},
    onOpenStories: ((String) -> Unit)? = null,
    onWallpaper: (() -> Unit)? = null,
    blocked: Boolean? = null,
    onBlock: () -> Unit = {},
    onUnblock: () -> Unit = {},
    onReport: (() -> Unit)? = null,
    connection: ChatsStatus = ChatsStatus.Ready,
    emoji: InlineEmoji = InlineEmoji(emptyMap(), emptyMap()),
    unreadChats: Int = 0,
    thread: ThreadTitle? = null,
    subtitle: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val hasMenu = onSearch != null || menu.isNotEmpty() || onLeave != null || onWallpaper != null || removals.isNotEmpty() || blocked != null || onReport != null
    val menuLabel = stringResource(R.string.chat_menu)
    val searchLabel = stringResource(R.string.chat_search)
    val titlePress: (() -> Unit)? = onSearch
    val longPress: (() -> Unit)? = if (hasMenu) ({ open = true }) else null
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = TopBarTokens.PillSize), verticalAlignment = Alignment.CenterVertically) {
        Box {
            BackPill(onBack)
            val unreadLabel = pluralStringResource(R.plurals.chat_back_unread, unreadChats, unreadChats)
            UnreadBadge(
                unreadChats,
                muted = false,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = BADGE_OVERHANG, y = -BADGE_OVERHANG).semantics { contentDescription = unreadLabel }.testTag("backBadge"),
            )
        }
        Box(Modifier.weight(1f).padding(horizontal = TopBarTokens.PillGap), contentAlignment = Alignment.Center) {
            val colors = TopBarColors.surface()
            Column(
                Modifier
                    .widthIn(min = TITLE_MIN_WIDTH)
                    .heightIn(min = TopBarTokens.PillSize)
                    .clip(CircleShape)
                    .background(colors.pill)
                    .then(
                        if (thread == null) {
                            Modifier.combinedClickable(onClick = onOpenProfile, onLongClick = titlePress, onLongClickLabel = searchLabel)
                        } else if (titlePress != null) {
                            Modifier.pointerInput(titlePress) { detectTapGestures(onLongPress = { titlePress() }) }.semantics { onLongClick(searchLabel) { titlePress(); true } }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .testTag("title"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val context = LocalContext.current
                val phone = dialog?.phoneTitle
                val shown = if (phone != null && thread == null) remember(phone) { formatPhone(context, phone) } else title
                ChatTitle(shown, dialog, emoji, colors.content, thread)
                ChatStatus(remember(dialog, connection, subtitle) { statusLine(dialog, connection, subtitle) })
            }
        }
        Box {
            val profileLabel = stringResource(R.string.chat_open_profile)
            val storiesLabel = stringResource(R.string.stories_ring_open)
            val ring = dialog?.story ?: StoryRing.None
            val openStories = onOpenStories?.takeIf { ring != StoryRing.None }?.let { show -> dialog?.key?.let { key -> { show(key) } } }
            val background = MaterialTheme.colorScheme.surfaceContainerHighest
            Box(
                Modifier
                    .size(TopBarTokens.PillSize)
                    .clip(CircleShape)
                    .background(background)
                    .combinedClickable(onClick = openStories ?: onOpenProfile, onLongClick = longPress, onLongClickLabel = menuLabel)
                    .semantics { contentDescription = if (openStories != null) storiesLabel else profileLabel }
                    .testTag("avatar"),
            ) {
                if (dialog != null) {
                    StoryRingBox(ring, TopBarTokens.PillSize.value.toInt(), background = background) { photo -> DialogAvatar(dialog, avatars, size = photo) }
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.testTag("chatMenu")) {
                if (onSearch != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_search)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        onClick = {
                            open = false
                            onSearch()
                        },
                        modifier = Modifier.testTag("chatSearch"),
                    )
                }
                menu.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.label)) },
                        leadingIcon = { Icon(painterResource(action.icon), null) },
                        onClick = {
                            open = false
                            onMenu(action)
                        },
                        modifier = Modifier.testTag("menu_${action.name}"),
                    )
                }
                if (onWallpaper != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_menu_wallpaper)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_image), null) },
                        onClick = {
                            open = false
                            onWallpaper()
                        },
                        modifier = Modifier.testTag("menu_wallpaper"),
                    )
                }
                if (blocked != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (blocked) R.string.blocked_unblock else R.string.block_action), color = if (blocked) Color.Unspecified else MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(painterResource(if (blocked) R.drawable.ic_front_hand else R.drawable.ic_block), null, tint = if (blocked) LocalContentColor.current else MaterialTheme.colorScheme.error) },
                        onClick = {
                            open = false
                            if (blocked) onUnblock() else onBlock()
                        },
                        modifier = Modifier.testTag(if (blocked) "menu_unblock" else "menu_block"),
                    )
                }
                if (onReport != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.report_action), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_report), null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            open = false
                            onReport()
                        },
                        modifier = Modifier.testTag("menu_report"),
                    )
                }
                if (onLeave != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(leaveLabel(dialog?.kind)), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_logout), null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            open = false
                            onLeave()
                        },
                        modifier = Modifier.testTag("menu_leave"),
                    )
                }
                removals.forEach { removal ->
                    val label = dialog?.let { stringResource(removal.label(it)) } ?: return@forEach
                    DropdownMenuItem(
                        text = { Text(label, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(painterResource(removal.icon()), null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            open = false
                            onRemove(removal)
                        },
                        modifier = Modifier.testTag("menu_removal_${removal.name}"),
                    )
                }
            }
        }
    }
}

internal fun leaveLabel(kind: PeerKind?): Int = if (kind == PeerKind.Channel) R.string.chat_leave_channel else R.string.chat_leave_group

@Composable
private fun ChatTitle(title: String, dialog: DialogItem?, emoji: InlineEmoji, color: Color, thread: ThreadTitle?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val count = thread?.count
        if (thread != null && count != null && count > 0) {
            ThreadCount(thread.comments, count, color, Modifier.weight(1f, fill = false))
        } else {
            val text = if (thread != null && count == 0) stringResource(if (thread.comments) R.string.chat_comments_title else R.string.chat_replies_title) else title
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (dialog != null) TitleBadges(dialog, emoji)
    }
}

@Composable
private fun ThreadCount(comments: Boolean, count: Int, color: Color, modifier: Modifier = Modifier) {
    val word = pluralStringResource(if (comments) R.plurals.chat_thread_comments else R.plurals.chat_thread_replies, count)
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val roll = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val style = MaterialTheme.typography.titleMedium
    val spoken = "$count $word"
    Row(modifier.clearAndSetSemantics { text = AnnotatedString(spoken) }.testTag("threadTitle"), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(roll) { if (up) it else -it } + fadeIn(effects)) togetherWith (slideOutVertically(roll) { if (up) -it else it } + fadeOut(effects))
            },
            label = "threadCount",
        ) { shown -> Text(shown.toString(), style = style, color = color, maxLines = 1) }
        Text(" $word", style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TitleBadges(dialog: DialogItem, emoji: InlineEmoji) {
    val primary = MaterialTheme.colorScheme.primary
    val verified = dialog.verified
    val statusId = dialog.emojiStatus
    val status = statusId?.let { emoji.documents[it] }
    val bytes = status?.let { emoji.files[it.key] }
    when {
        verified -> Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.chats_verified), Modifier.size(BADGE_SIZE).testTag("verified"), tint = primary)
        dialog.scam -> TitleTag(stringResource(R.string.chat_scam), Modifier.testTag("scam"))
        dialog.fake -> TitleTag(stringResource(R.string.chat_fake), Modifier.testTag("fake"))
        status != null && bytes != null -> {
            val label = stringResource(R.string.profile_emoji_status)
            StickerView(status, bytes, Modifier.then(if (status.textColor) Modifier.tinted(primary) else Modifier).semantics { contentDescription = label }.testTag("emojiStatus"), size = STATUS_ICON)
        }
        dialog.premium -> Icon(painterResource(R.drawable.ic_star), stringResource(R.string.chats_premium), Modifier.size(BADGE_SIZE).testTag("premium"), tint = primary)
    }
    if (dialog.muted && !verified) {
        Icon(painterResource(R.drawable.ic_volume_off), stringResource(R.string.chats_muted), Modifier.size(MUTE_SIZE).testTag("muted"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TitleTag(text: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier.border(1.dp, color, MaterialTheme.shapes.extraSmall).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private val TITLE_MIN_WIDTH = 150.dp
private val BADGE_SIZE = 16.dp

private val BADGE_OVERHANG = 4.dp
private val MUTE_SIZE = 14.dp
private val STATUS_ICON = 18.dp
