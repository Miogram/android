package cn.ellerotta.core.ui.components.groups

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.JoinRequest
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.formatChatDate
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.EmptyState
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState

@Composable
fun JoinRequestsScreen(
    requests: List<JoinRequest>,
    avatars: Map<String, ByteArray>,
    more: Boolean,
    loaded: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onDecide: (JoinRequest, Boolean) -> Unit,
    onMore: () -> Unit,
    onOpenProfile: (JoinRequest) -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val list = rememberLazyListState()
    LaunchedEffect(list, more) {
        if (!more) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - MORE_AHEAD) onMore() }
    }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val title = stringResource(R.string.join_requests_title)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("joinRequestList"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            if (loaded && requests.isEmpty()) {
                item(key = "empty") {
                    EmptyState(R.drawable.ic_person_add, stringResource(R.string.join_requests_empty), "noRequests")
                }
            }
            items(requests, key = { it.userKey }) { request ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("request_${request.userKey}"), verticalAlignment = Alignment.Top) {
                    Avatar(request.name, request.avatarKey?.let { avatars[it] }, size = 48, seed = request.userKey.drop(1).toLongOrNull() ?: 0, modifier = Modifier.clickable { onOpenProfile(request) })
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(request.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenProfile(request) })
                        val asked = formatChatDate(request.date, now, locale, DateFormat.is24HourFormat(context))
                        Text(stringResource(R.string.join_requests_asked, asked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        request.about?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { onDecide(request, true) }, modifier = Modifier.testTag("approve_${request.userKey}"), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.join_requests_approve)) }
                            OutlinedButton(onClick = { onDecide(request, false) }, modifier = Modifier.testTag("decline_${request.userKey}"), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.join_requests_decline)) }
                        }
                    }
                }
            }
        }
    }
}

private const val MORE_AHEAD = 5
