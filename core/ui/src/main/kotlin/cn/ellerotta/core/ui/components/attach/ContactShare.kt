package cn.ellerotta.core.ui.components.attach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.appbar.SearchBar
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState

@Composable
fun ContactShareScreen(
    contacts: List<ContactItem>,
    query: String,
    loaded: Boolean,
    avatars: Map<String, ByteArray>,
    busy: Boolean,
    onQuery: (String) -> Unit,
    onSend: (ContactItem) -> Unit,
    onBack: () -> Unit,
) {
    val list = rememberLazyListState()
    var asked by remember { mutableStateOf<ContactItem?>(null) }
    val title = stringResource(R.string.contact_share_title)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("contactShareList"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            item(key = "search") {
                SearchBar(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = stringResource(R.string.contacts_search),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fieldTag = "contactShareSearch",
                )
            }
            if (loaded && contacts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (query.isBlank()) R.string.contact_share_empty else R.string.contacts_nothing_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).testTag("contactShareEmpty"),
                    )
                }
            }
            items(contacts, key = { it.key }) { person ->
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { asked = person }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("contactShare_${person.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(person.name, person.avatarKey?.let { avatars[it] }, seed = person.userId)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(phoneText(person.phone.orEmpty()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
    asked?.let { person ->
        AlertDialog(
            onDismissRequest = { asked = null },
            title = { Text(stringResource(R.string.contact_share_ask, person.name)) },
            text = { Text(phoneText(person.phone.orEmpty())) },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        asked = null
                        onSend(person)
                    },
                    modifier = Modifier.testTag("contactShareSend"),
                ) { Text(stringResource(R.string.contact_share_send)) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { asked = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

private fun phoneText(phone: String): String = if (phone.startsWith("+")) phone else "+$phone"
