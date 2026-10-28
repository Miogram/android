package cn.ellerotta.core.ui.components.chats

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.FolderTab
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.PagerPillTabRow

class FolderActions(
    val onEdit: (ChatFolder) -> Unit = {},
    val onReorder: () -> Unit = {},
    val onDelete: (ChatFolder) -> Unit = {},
)

@Composable
internal fun FolderPillRow(
    tabs: List<FolderTab>,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actions: FolderActions = FolderActions(),
) {
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val haptics = LocalHapticFeedback.current
    PagerPillTabRow(
        count = tabs.size,
        pagerState = pagerState,
        onTabClick = onTabClick,
        tabTag = { "folder_${tabs.getOrNull(it)?.folder?.id}" },
        modifier = modifier.testTag("folders"),
        onTabLongClick = { index ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            menuFor = index
        },
        longClickLabel = stringResource(R.string.folders_menu_label),
        menu = { index -> tabs.getOrNull(index)?.let { FolderMenu(it.folder, expanded = menuFor == index, onDismiss = { menuFor = null }, actions = actions) } },
    ) { index, label ->
        val tab = tabs.getOrNull(index) ?: return@PagerPillTabRow
        Text(
            text = if (tab.folder.isAll) stringResource(R.string.chats_all) else tab.folder.title,
            style = label.style,
            color = label.color,
            maxLines = 1,
        )
        UnreadBadge(tab.unreadChats, muted = tab.mutedOnly)
    }
}

@Composable
internal fun FolderMenu(folder: ChatFolder, expanded: Boolean, onDismiss: () -> Unit, actions: FolderActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.testTag("folderMenu")) {
        if (!folder.isAll) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folders_edit_title)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) },
                onClick = { onDismiss(); actions.onEdit(folder) },
                modifier = Modifier.testTag("folderMenuEdit"),
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.folders_menu_reorder)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_reorder), null) },
            onClick = { onDismiss(); actions.onReorder() },
            modifier = Modifier.testTag("folderMenuReorder"),
        )
        if (!folder.isAll) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folders_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) },
                onClick = { onDismiss(); actions.onDelete(folder) },
                modifier = Modifier.testTag("folderMenuDelete"),
            )
        }
    }
}
