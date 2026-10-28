package cn.ellerotta.core.ui.components.safety

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.BlockedList
import cn.ellerotta.core.model.BlockedUser
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.profile.InfoCard
import cn.ellerotta.core.ui.components.profile.InfoRow

@Composable
fun BlockedUsersScreen(
    blocked: BlockedList,
    avatars: Map<String, ByteArray>,
    busy: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onUnblock: (String) -> Unit,
    onOpenProfile: (BlockedUser) -> Unit,
    onMore: () -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(list, blocked.more) {
        if (!blocked.more) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - MORE_AHEAD) onMore() }
    }
    val title = stringResource(R.string.privacy_blocked_users)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("blockedUsers"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy || !blocked.loaded) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            item(key = "add") {
                InfoCard {
                    InfoRow(R.drawable.ic_block, stringResource(R.string.blocked_add), onClick = onAdd, modifier = Modifier.testTag("blockUser"))
                }
            }
            item(key = "hint") {
                Text(
                    stringResource(if (blocked.loaded && blocked.users.isEmpty()) R.string.blocked_empty else R.string.blocked_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).testTag("blockedHint"),
                )
            }
            items(blocked.users, key = { it.key }) { user ->
                BlockedRow(user, user.avatarKey?.let { avatars[it] }, onOpen = { onOpenProfile(user) }, onUnblock = { onUnblock(user.key) })
            }
        }
    }
}

@Composable
private fun BlockedRow(user: BlockedUser, avatar: ByteArray?, onOpen: () -> Unit, onUnblock: () -> Unit) {
    val label = stringResource(R.string.blocked_unblock_user, user.name)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 56.dp).testTag("blocked_${user.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.name, avatar, size = 44, seed = user.key.drop(1).toLongOrNull() ?: 0)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            user.username?.let { Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        TextButton(onClick = onUnblock, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label }.testTag("unblock_${user.key}")) {
            Text(stringResource(R.string.blocked_unblock))
        }
    }
}

private const val MORE_AHEAD = 5
