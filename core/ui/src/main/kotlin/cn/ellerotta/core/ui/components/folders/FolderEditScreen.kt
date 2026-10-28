package cn.ellerotta.core.ui.components.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatFolder
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.FolderEmojis
import cn.ellerotta.core.model.FolderLimits
import cn.ellerotta.core.model.excluding
import cn.ellerotta.core.model.includingOnly
import cn.ellerotta.core.model.savable
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.DialogAvatar
import cn.ellerotta.core.ui.components.chats.dialogTitle
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut

data class FolderEditState(
    val folder: ChatFolder? = null,
    val isNew: Boolean = true,
    val chats: List<DialogItem> = emptyList(),
    val limits: FolderLimits = FolderLimits(),
    val folderCount: Int = 0,
    val changed: Boolean = false,
) {
    val roomForIt: Boolean get() = !isNew || limits.canAddFolder(folderCount)

    val canSave: Boolean get() = folder?.savable() == true && roomForIt && (isNew || changed)
}

private const val PICK_NONE = 0
private const val PICK_INCLUDE = 1
private const val PICK_EXCLUDE = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderEditScreen(
    state: FolderEditState,
    avatars: Map<String, ByteArray>,
    busy: Boolean,
    onBack: () -> Unit,
    onChange: (ChatFolder) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onLoadMoreChats: () -> Unit = {},
) {
    var picking by rememberSaveable { mutableIntStateOf(PICK_NONE) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val folder = state.folder
    val editorList = rememberLazyListState()
    BackHandler(picking != PICK_NONE) { picking = PICK_NONE }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        picking,
        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        label = "folderEditPage",
    ) { page ->
        if (page != PICK_NONE && folder != null) {
            val include = page == PICK_INCLUDE
            FolderChatPicker(
                title = stringResource(if (include) R.string.folders_pick_include else R.string.folders_pick_exclude),
                chats = state.chats,
                picked = if (include) folder.explicit else folder.exclude.toList(),
                avatars = avatars,
                limit = state.limits.chats,
                onBack = { picking = PICK_NONE },
                onDone = { keys ->
                    onChange(if (include) folder.includingOnly(keys) else folder.excluding(keys))
                    picking = PICK_NONE
                },
                onEnd = onLoadMoreChats,
            )
        } else {
            Editor(state, editorList, avatars, busy, onBack, onChange, onSave, askDelete = { confirmDelete = true }, onPick = { picking = it })
        }
    }
    if (confirmDelete && folder != null) {
        ConfirmDialog(
            title = stringResource(R.string.folders_delete_title),
            text = stringResource(R.string.folders_delete_text, folder.title),
            confirm = stringResource(R.string.folders_delete_confirm),
            tag = "confirmFolderDelete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(
    state: FolderEditState,
    list: LazyListState,
    avatars: Map<String, ByteArray>,
    busy: Boolean,
    onBack: () -> Unit,
    onChange: (ChatFolder) -> Unit,
    onSave: () -> Unit,
    askDelete: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val folder = state.folder
    val title = stringResource(if (state.isNew) R.string.folders_new_title else R.string.folders_edit_title)
    val byKey = state.chats.associateBy { it.key }
    Page(
        title,
        rememberTopBarState(list),
        onBack = onBack,
        floating = {
            AnimatedVisibility(state.canSave, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                MediumExtendedFloatingActionButton(
                    onClick = { if (!busy) onSave() },
                    icon = {
                        if (busy) LoadingIndicator(Modifier.size(24.dp)) else Icon(painterResource(R.drawable.ic_check), null)
                    },
                    text = { Text(stringResource(R.string.folders_save)) },
                    modifier = Modifier.testTag("folderSave"),
                    expanded = true,
                )
            }
        },
    ) { padding ->
        if (folder == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp).testTag("folderLoading")) }
            return@Page
        }
        LazyColumn(Modifier.fillMaxSize().testTag("folderEdit"), state = list, contentPadding = padding) {
            largeTitle(title)
            if (!state.roomForIt) {
                item(key = "limit") {
                    LimitCard(
                        stringResource(if (state.limits.premium) R.string.folders_limit_reached_premium else R.string.folders_limit_reached, state.limits.folders),
                        Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    )
                }
            }
            item(key = "name") {
                OutlinedTextField(
                    value = folder.title,
                    onValueChange = { onChange(folder.copy(title = it)) },
                    label = { Text(stringResource(R.string.folders_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("folderName"),
                )
            }
            item(key = "icon") {
                SectionLabel(stringResource(R.string.folders_icon), null, Modifier.padding(top = 8.dp))
                LazyRow(
                    Modifier.fillMaxWidth().selectableGroup().testTag("folderIcons"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "none") {
                        IconChoice(selected = folder.emoticon == null, onClick = { onChange(folder.copy(emoticon = null)) }, label = stringResource(R.string.folders_icon_none), tag = "folderIcon_none") {
                            Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    items(FolderEmojis.size, key = { FolderEmojis[it] }) { index ->
                        val emoji = FolderEmojis[index]
                        IconChoice(selected = folder.emoticon == emoji, onClick = { onChange(folder.copy(emoticon = emoji)) }, label = stringResource(R.string.folders_icon_option, emoji), tag = "folderIcon_$emoji") {
                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            item(key = "included") {
                ChatsCard(
                    heading = stringResource(R.string.folders_included),
                    hint = stringResource(R.string.folders_included_hint),
                    keys = folder.explicit,
                    chats = byKey,
                    avatars = avatars,
                    onRemove = { key -> onChange(folder.includingOnly(folder.explicit - key)) },
                    onAdd = { onPick(PICK_INCLUDE) },
                    tag = "included",
                )
            }
            if (!folder.shared) {
                item(key = "types") {
                    SectionLabel(stringResource(R.string.folders_types), null, Modifier.padding(top = 8.dp))
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("folderTypes"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TypeToggle(stringResource(R.string.folders_type_contacts), folder.contacts, "type_contacts") { onChange(folder.copy(contacts = it)) }
                        TypeToggle(stringResource(R.string.folders_type_non_contacts), folder.nonContacts, "type_nonContacts") { onChange(folder.copy(nonContacts = it)) }
                        TypeToggle(stringResource(R.string.folders_type_groups), folder.groups, "type_groups") { onChange(folder.copy(groups = it)) }
                        TypeToggle(stringResource(R.string.folders_type_channels), folder.broadcasts, "type_channels") { onChange(folder.copy(broadcasts = it)) }
                        TypeToggle(stringResource(R.string.folders_type_bots), folder.bots, "type_bots") { onChange(folder.copy(bots = it)) }
                    }
                }
                item(key = "excluded") {
                    ChatsCard(
                        heading = stringResource(R.string.folders_excluded),
                        hint = stringResource(R.string.folders_excluded_hint),
                        keys = folder.exclude.toList(),
                        chats = byKey,
                        avatars = avatars,
                        onRemove = { key -> onChange(folder.excluding(folder.exclude - key)) },
                        onAdd = { onPick(PICK_EXCLUDE) },
                        tag = "excluded",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item(key = "leave_out") {
                    SectionLabel(stringResource(R.string.folders_leave_out), null, Modifier.padding(top = 8.dp))
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column {
                            SwitchRow(stringResource(R.string.folders_exclude_muted), folder.excludeMuted, "exclude_muted") { onChange(folder.copy(excludeMuted = it)) }
                            SwitchRow(stringResource(R.string.folders_exclude_read), folder.excludeRead, "exclude_read") { onChange(folder.copy(excludeRead = it)) }
                            SwitchRow(stringResource(R.string.folders_exclude_archived), folder.excludeArchived, "exclude_archived") { onChange(folder.copy(excludeArchived = it)) }
                        }
                    }
                }
            } else {
                item(key = "shared") {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("folderShared")) {
                        Text(stringResource(R.string.folders_shared_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            if (!folder.savable()) {
                item(key = "needs") {
                    Text(
                        stringResource(R.string.folders_needs_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).testTag("folderNeeds"),
                    )
                }
            }
            if (!state.isNew) {
                item(key = "delete") {
                    TextButton(onClick = askDelete, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp).testTag("folderDelete")) {
                        Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.folders_delete), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.heightIn(min = 72.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatsCard(
    heading: String,
    hint: String,
    keys: List<String>,
    chats: Map<String, DialogItem>,
    avatars: Map<String, ByteArray>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionLabel(heading, pluralStringResource(R.plurals.folders_chats_count, keys.size, keys.size))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("${tag}_card")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (keys.isNotEmpty()) {
                    FlowRow(Modifier.fillMaxWidth().testTag("${tag}_chips"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.forEach { key ->
                            val chat = chats[key]
                            val name = chat?.let { dialogTitle(it) } ?: key
                            InputChip(
                                selected = false,
                                onClick = { onRemove(key) },
                                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) },
                                avatar = chat?.let { { DialogAvatar(it, avatars, size = 24) } },
                                trailingIcon = { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.picker_remove, name), Modifier.size(18.dp)) },
                                modifier = Modifier.testTag("${tag}_chip_$key"),
                            )
                        }
                    }
                }
                FilledTonalButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_add"), shapes = ButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(20.dp))
                    Text(stringResource(R.string.folders_add_chats), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TypeToggle(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onChange,
        shapes = ToggleButtonDefaults.shapesFor(48.dp),
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
    ) {
        if (checked) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(ToggleButtonDefaults.IconSize))
        Text(label, Modifier.padding(start = if (checked) ToggleButtonDefaults.IconSpacing else 0.dp))
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        CheckedSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun IconChoice(selected: Boolean, onClick: () -> Unit, label: String, tag: String, content: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(if (selected) 24.dp else 12.dp, motion.fastSpatialSpec(), label = "iconCorner")
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        motion.fastEffectsSpec(),
        label = "iconContainer",
    )
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier
            .size(48.dp)
            .clip(shape)
            .background(container)
            .then(if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { content() }
}
