package cn.ellerotta.core.ui.components.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.MemberRole
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.profile.presenceText

@Composable
fun MembersScreen(
    title: String,
    members: List<ChatMember>,
    avatars: Map<String, ByteArray>,
    query: String,
    more: Boolean,
    loaded: Boolean,
    busy: Boolean,
    canBan: Boolean,
    basicGroup: Boolean,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onMore: () -> Unit,
    onOpenProfile: (ChatMember) -> Unit,
    onRemove: (ChatMember, Boolean) -> Unit,
    onAdd: (() -> Unit)? = null,
    now: Long = System.currentTimeMillis(),
) {
    val list = rememberLazyListState()
    LaunchedEffect(list, more) {
        if (!more) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - MORE_AHEAD) onMore() }
    }
    var removing by remember { mutableStateOf<Pair<ChatMember, Boolean>?>(null) }
    var field by rememberSaveable { mutableStateOf(query) }
    Page(
        title,
        rememberTopBarState(list),
        onBack = onBack,
        floating = {
            if (onAdd != null) {
                MediumExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(painterResource(R.drawable.ic_person_add), null) },
                    text = { Text(stringResource(R.string.members_add)) },
                    modifier = Modifier.testTag("addMembers"),
                    expanded = true,
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("memberList"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "search") {
                OutlinedTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onQuery(it)
                    },
                    placeholder = { Text(stringResource(R.string.members_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("memberSearch"),
                )
            }
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("membersLoading")) }
            if (loaded && members.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.members_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).testTag("noMembers"),
                    )
                }
            }
            items(members, key = { it.userKey }) { member ->
                MemberRow(
                    member = member,
                    avatar = member.avatarKey?.let { avatars[it] },
                    status = presenceText(member.presence, now),
                    removable = canBan && !member.self && member.role == MemberRole.Member,
                    basicGroup = basicGroup,
                    onOpen = { onOpenProfile(member) },
                    onRemove = { ban -> removing = member to ban },
                )
            }
            if (onAdd != null) item(key = "end") { Spacer(Modifier.heightIn(min = 72.dp)) }
        }
    }
    removing?.let { (member, ban) ->
        ConfirmDialog(
            title = stringResource(if (ban) R.string.members_ban_title else R.string.members_remove_title, member.name),
            text = stringResource(if (ban) R.string.members_ban_text else R.string.members_remove_text, member.name),
            confirm = stringResource(if (ban) R.string.members_ban else R.string.members_remove),
            tag = "confirmRemove",
            onConfirm = {
                removing = null
                onRemove(member, ban)
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun MemberRow(
    member: ChatMember,
    avatar: ByteArray?,
    status: String,
    removable: Boolean,
    basicGroup: Boolean,
    onOpen: () -> Unit,
    onRemove: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).testTag("member_${member.userKey}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(member.name, avatar, size = 44, seed = member.userKey.drop(1).toLongOrNull() ?: 0)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        roleLabel(member)?.let { role ->
            Text(
                role,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp).testTag("role_${member.userKey}"),
            )
        }
        if (removable) {
            Box {
                var open by remember { mutableStateOf(false) }
                IconButton(onClick = { open = true }, modifier = Modifier.testTag("memberMenu_${member.userKey}"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.chat_menu))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.members_remove)) },
                        onClick = {
                            open = false
                            onRemove(false)
                        },
                        modifier = Modifier.testTag("remove_${member.userKey}"),
                    )
                    if (!basicGroup) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.members_ban), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                open = false
                                onRemove(true)
                            },
                            modifier = Modifier.testTag("ban_${member.userKey}"),
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun roleLabel(member: ChatMember): String? = member.rank ?: when (member.role) {
    MemberRole.Owner -> stringResource(R.string.members_owner)
    MemberRole.Admin -> stringResource(R.string.members_admin)
    MemberRole.Member -> null
}

private const val MORE_AHEAD = 5
