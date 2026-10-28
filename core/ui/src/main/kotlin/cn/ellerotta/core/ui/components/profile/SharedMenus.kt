package cn.ellerotta.core.ui.components.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.DeleteMessageDialog
import cn.ellerotta.core.ui.components.chat.ForwardSheet
import cn.ellerotta.core.ui.components.chat.MessageAction

@Stable
internal class SharedMenuState {
    var menu by mutableStateOf<MessageItem?>(null)
    var forwarding by mutableStateOf<MessageItem?>(null)
    var deleting by mutableStateOf<MessageItem?>(null)
}

@Composable
internal fun SharedMessageMenus(
    state: SharedMenuState,
    actions: SharedMediaActions,
    forwardTargets: List<DialogItem>,
    avatars: Map<String, ByteArray>,
    canDelete: (MessageItem) -> Boolean,
    revokeLabel: @Composable (MessageItem) -> String?,
) {
    state.menu?.let { message ->
        ModalBottomSheet(onDismissRequest = { state.menu = null }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.testTag("sharedMenu").padding(bottom = 16.dp)) {
                MenuLine(MessageAction.GoTo) {
                    state.menu = null
                    actions.onGoTo(message)
                }
                MenuLine(MessageAction.Forward) {
                    state.menu = null
                    state.forwarding = message
                }
                if (canDelete(message)) {
                    MenuLine(MessageAction.Delete, danger = true) {
                        state.menu = null
                        state.deleting = message
                    }
                }
            }
        }
    }
    state.forwarding?.let { message ->
        ForwardSheet(
            targets = forwardTargets,
            avatars = avatars,
            onPick = { to, dropAuthor, dropCaptions ->
                state.forwarding = null
                actions.onForward(message, to, dropAuthor, dropCaptions)
            },
            onDismiss = { state.forwarding = null },
        )
    }
    state.deleting?.let { message ->
        DeleteMessageDialog(
            revokeLabel = revokeLabel(message),
            onConfirm = { revoke ->
                state.deleting = null
                actions.onDelete(message, revoke)
            },
            onDismiss = { state.deleting = null },
        )
    }
}

@Composable
private fun MenuLine(action: MessageAction, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(stringResource(action.label)) },
        leadingContent = { Icon(painterResource(action.icon), null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = color, leadingIconColor = color),
        modifier = Modifier.heightIn(min = 56.dp).clickable(onClick = onClick).testTag("shared${action.tag.removePrefix("action")}"),
    )
}
