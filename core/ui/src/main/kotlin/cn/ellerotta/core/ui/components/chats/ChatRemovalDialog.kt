package cn.ellerotta.core.ui.components.chats

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PendingRemoval
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.CheckRow

@StringRes
internal fun ChatRemoval.label(dialog: DialogItem): Int = when (this) {
    ChatRemoval.Clear -> R.string.action_clear_history
    ChatRemoval.Delete -> when (dialog.kind) {
        PeerKind.Channel -> R.string.chat_leave_channel
        PeerKind.Group -> R.string.action_delete_leave_group
        else -> R.string.action_delete_chat
    }
    ChatRemoval.DeleteForEveryone -> if (dialog.kind == PeerKind.Channel) R.string.action_delete_channel else R.string.action_delete_group
}

@DrawableRes
internal fun ChatRemoval.icon(): Int = if (this == ChatRemoval.Clear) R.drawable.ic_delete_sweep else R.drawable.ic_delete

@Composable
private fun ChatRemoval.text(dialog: DialogItem, title: String): String = when (this) {
    ChatRemoval.Clear -> stringResource(R.string.removal_clear_text, title)
    ChatRemoval.Delete -> when (dialog.kind) {
        PeerKind.Channel -> stringResource(R.string.chat_leave_channel_text, title)
        PeerKind.Group -> stringResource(R.string.removal_leave_group_text, title)
        else -> stringResource(R.string.removal_delete_text, title)
    }
    ChatRemoval.DeleteForEveryone -> stringResource(if (dialog.kind == PeerKind.Channel) R.string.removal_delete_channel_text else R.string.removal_delete_group_text, title)
}

@Composable
fun ChatRemovalDialog(dialog: DialogItem, removal: ChatRemoval, onConfirm: (revoke: Boolean) -> Unit, onDismiss: () -> Unit) {
    var revoke by rememberSaveable { mutableStateOf(false) }
    val title = dialogTitle(dialog)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(removal.label(dialog))) },
        text = {
            Column {
                Text(removal.text(dialog, title))
                if (dialog.revokable(removal)) {
                    CheckRow(revoke, { revoke = it }, stringResource(R.string.chat_delete_for, title), "removalRevoke", Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { onConfirm(revoke && dialog.revokable(removal)) }, modifier = Modifier.testTag("confirmRemoval")) {
                Text(
                    stringResource(
                        when {
                            removal == ChatRemoval.Clear -> R.string.removal_clear_confirm
                            removal == ChatRemoval.Delete && (dialog.kind == PeerKind.Group || dialog.kind == PeerKind.Channel) -> R.string.chat_leave
                            else -> R.string.chat_delete
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun RemovalSnackbar(removal: PendingRemoval, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val title = dialogTitle(removal.dialog)
    val text = when (removal.removal) {
        ChatRemoval.Clear -> stringResource(R.string.removal_snack_cleared)
        ChatRemoval.Delete -> if (removal.dialog.kind == PeerKind.Group || removal.dialog.kind == PeerKind.Channel) stringResource(R.string.removal_snack_left, title) else stringResource(R.string.removal_snack_deleted)
        ChatRemoval.DeleteForEveryone -> stringResource(R.string.removal_snack_deleted_everyone, title)
    }
    Snackbar(
        modifier = modifier.navigationBarsPadding().imePadding().padding(16.dp).testTag("removalSnackbar"),
        action = {
            TextButton(
                onClick = onUndo,
                colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                modifier = Modifier.testTag("undoRemoval"),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.removal_undo)) }
        },
    ) { Text(text) }
}
