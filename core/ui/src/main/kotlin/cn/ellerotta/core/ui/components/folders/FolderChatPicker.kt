package cn.ellerotta.core.ui.components.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.appbar.SearchBar
import cn.ellerotta.core.ui.components.chats.DialogAvatar
import cn.ellerotta.core.ui.components.chats.dialogTitle
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderChatPicker(
    title: String,
    chats: List<DialogItem>,
    picked: List<String>,
    avatars: Map<String, ByteArray>,
    limit: Int,
    onBack: () -> Unit,
    onDone: (List<String>) -> Unit,
    onEnd: () -> Unit = {},
) {
    val list = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf(ArrayList(picked)) }
    val byKey = remember(chats) { chats.associateBy { it.key } }
    val shown = remember(chats, query) { if (query.isBlank()) chats else chats.filter { it.title.contains(query.trim(), ignoreCase = true) } }
    val full = chosen.size >= limit
    LaunchedEffect(list, shown.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }.collect { (last, total) ->
            if (last != null && total > 0 && last >= total - 3) onEnd()
        }
    }
    fun toggle(key: String) {
        chosen = ArrayList(if (key in chosen) chosen - key else chosen + key)
    }
    Page(
        title,
        rememberTopBarState(list),
        onBack = onBack,
        floating = {
            MediumExtendedFloatingActionButton(
                onClick = { onDone(chosen) },
                icon = { Icon(painterResource(R.drawable.ic_check), null) },
                text = { Text(stringResource(R.string.folders_pick_done)) },
                modifier = Modifier.testTag("folderPickDone"),
                expanded = true,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("folderPickList"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "search") {
                SearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.folders_pick_search),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fieldTag = "folderPickSearch",
                )
            }
            if (chosen.isNotEmpty()) {
                item(key = "chips") {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("folderPickChips"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chosen.forEach { key ->
                            val chat = byKey[key]
                            val name = chat?.let { dialogTitle(it) } ?: key
                            InputChip(
                                selected = false,
                                onClick = { toggle(key) },
                                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) },
                                avatar = chat?.let { { DialogAvatar(it, avatars, size = 24) } },
                                trailingIcon = { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.picker_remove, name), Modifier.size(18.dp)) },
                                modifier = Modifier.testTag("folderPickChip_$key"),
                            )
                        }
                    }
                }
            }
            if (full) {
                item(key = "full") {
                    LimitCard(stringResource(R.string.folders_chats_limit, limit), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.folders_pick_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).testTag("folderPickEmpty"),
                    )
                }
            }
            items(shown, key = { it.key }) { chat ->
                val on = chat.key in chosen
                PickRow(chat, on, enabled = on || !full, avatars, onToggle = { toggle(chat.key) })
            }
            item(key = "end") { Spacer(Modifier.heightIn(min = 72.dp)) }
        }
    }
}

@Composable
private fun PickRow(chat: DialogItem, picked: Boolean, enabled: Boolean, avatars: Map<String, ByteArray>, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = picked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("folderPick_${chat.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = picked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        DialogAvatar(chat, avatars)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(dialogTitle(chat), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kindLabel(chat), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun kindLabel(chat: DialogItem): String = stringResource(
    when {
        chat.isSaved || chat.kind == PeerKind.Saved -> R.string.folders_kind_saved
        chat.kind == PeerKind.Bot -> R.string.folders_kind_bot
        chat.kind == PeerKind.Group -> R.string.folders_kind_group
        chat.kind == PeerKind.Channel -> R.string.folders_kind_channel
        chat.contact -> R.string.folders_kind_contact
        else -> R.string.folders_kind_user
    },
)
