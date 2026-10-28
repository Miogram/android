package cn.ellerotta.core.ui.components.chats

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.profile.label
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut

enum class ChatAction(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Pin(R.string.action_pin, R.drawable.ic_push_pin),
    Unpin(R.string.action_unpin, R.drawable.ic_push_pin),
    Mute(R.string.action_mute, R.drawable.ic_volume_off),
    Unmute(R.string.action_unmute, R.drawable.ic_notifications),
    MarkRead(R.string.action_read, R.drawable.ic_mark_chat_read),
    MarkUnread(R.string.action_unread, R.drawable.ic_chat),
    Archive(R.string.action_archive, R.drawable.ic_archive),
    Unarchive(R.string.action_unarchive, R.drawable.ic_unarchive),
    ;

    companion object {
        fun of(dialog: DialogItem): List<ChatAction> = listOfNotNull(
            if (dialog.pinned) Unpin else Pin,
            if (dialog.hasUnread) MarkRead else MarkUnread,
            if (dialog.isSaved) null else if (dialog.muted) Unmute else Mute,
            if (dialog.isSaved) null else if (dialog.archived) Unarchive else Archive,
        )
    }
}

@Composable
fun ChatActionsSheet(
    dialog: DialogItem,
    avatars: Map<String, ByteArray>,
    onAction: (ChatAction) -> Unit,
    onDismiss: () -> Unit,
    onRemove: ((ChatRemoval, Boolean) -> Unit)? = null,
    onMute: ((MuteDuration) -> Unit)? = null,
) {
    var removing by remember { mutableStateOf<ChatRemoval?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ChatActionsContent(dialog, avatars, onAction, onDismiss, onRemove = if (onRemove != null) ({ removing = it }) else null, onMute = onMute)
    }
    removing?.let { removal ->
        ChatRemovalDialog(
            dialog,
            removal,
            onConfirm = { revoke ->
                removing = null
                onDismiss()
                onRemove?.invoke(removal, revoke)
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
internal fun ColumnScope.ChatActionsContent(
    dialog: DialogItem,
    avatars: Map<String, ByteArray>,
    onAction: (ChatAction) -> Unit,
    onDismiss: () -> Unit,
    onRemove: ((ChatRemoval) -> Unit)?,
    onMute: ((MuteDuration) -> Unit)?,
) {
    var muteOpen by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DialogAvatar(dialog, avatars, size = 44)
        Spacer(Modifier.width(16.dp))
        Text(dialogTitle(dialog), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        ChatAction.of(dialog).forEach { action ->
            val picksDuration = action == ChatAction.Mute && onMute != null
            ListItem(
                onClick = { if (picksDuration) muteOpen = !muteOpen else onAction(action) },
                leadingContent = { Icon(painterResource(action.icon), null) },
                trailingContent = if (picksDuration) {
                    {
                        val turn by animateFloatAsState(if (muteOpen) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "muteChoices")
                        Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.rotate(turn))
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().testTag("action_${action.name}"),
            ) {
                Text(stringResource(action.label))
            }
            if (picksDuration && onMute != null) {
                AnimatedVisibility(muteOpen, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                    Column {
                        MuteDuration.entries.forEach { duration ->
                            ListItem(
                                onClick = {
                                    onDismiss()
                                    onMute(duration)
                                },
                                modifier = Modifier.fillMaxWidth().padding(start = 40.dp).testTag("muteFor${duration.name}"),
                            ) {
                                Text(stringResource(duration.label))
                            }
                        }
                    }
                }
            }
        }
        if (onRemove != null) {
            dialog.removals.forEach { removal ->
                ListItem(
                    onClick = { onRemove(removal) },
                    leadingContent = { Icon(painterResource(removal.icon()), null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.fillMaxWidth().testTag("removal_${removal.name}"),
                ) {
                    Text(stringResource(removal.label(dialog)), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().padding(bottom = 16.dp))
    }
}
