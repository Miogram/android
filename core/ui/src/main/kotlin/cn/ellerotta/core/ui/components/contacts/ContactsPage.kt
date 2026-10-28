package cn.ellerotta.core.ui.components.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.appbar.SearchBar
import cn.ellerotta.core.ui.components.common.CheckRow
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.EmptyState

data class ContactsPageState(
    val contacts: List<ContactItem> = emptyList(),
    val loading: Boolean = false,
    val globalResults: List<ContactItem> = emptyList(),
    val notFound: Boolean = false,
    val added: Int = 0,
)

class ContactsActions(
    val onOpen: (ContactItem) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onAdd: (phone: String, firstName: String, lastName: String) -> Unit = { _, _, _ -> },
    val onDelete: (ContactItem) -> Unit = {},
    val onShown: () -> Unit = {},
    val onEditNumber: () -> Unit = {},
    val onNewGroup: (() -> Unit)? = null,
    val onNewChannel: (() -> Unit)? = null,
)

@Composable
fun ContactsTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    searchRevealProgress: Float = 1f,
    searchFocus: FocusRequester? = null,
) {
    val revealProgress = searchRevealProgress.coerceIn(0f, 1f)
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.contacts_title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onAdd, modifier = Modifier.testTag("addContact"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_person_add), stringResource(R.string.contacts_add))
                }
            }

            if (revealProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = lerp(0.dp, 8.dp, revealProgress))
                        .height(lerp(0.dp, 48.dp, revealProgress))
                        .clipToBounds(),
                ) {
                    SearchBar(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = stringResource(R.string.contacts_search),
                        focusRequester = searchFocus,
                        modifier = Modifier
                            .fillMaxHeight()
                            .graphicsLayer { alpha = revealProgress },
                        fieldTag = "contactsSearch",
                    )
                }
            }
        }
    }
}

@Composable
fun ContactsContent(
    contacts: List<ContactItem>,
    loading: Boolean,
    query: String,
    globalResults: List<ContactItem>,
    avatars: Map<String, ByteArray>,
    now: Long,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onOpen: (ContactItem) -> Unit,
    onDelete: (ContactItem) -> Unit,
    modifier: Modifier = Modifier,
    onNewGroup: (() -> Unit)? = null,
    onNewChannel: (() -> Unit)? = null,
) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val searching = query.isNotBlank()
    val creating = !searching && (onNewGroup != null || onNewChannel != null)
    val shown = if (searching) contacts.filter { it.matches(query) } else contacts
    val shownKeys = shown.map { it.key }.toSet()
    val global = if (searching) globalResults.filter { it.key !in shownKeys } else emptyList()

    if (shown.isEmpty() && global.isEmpty() && !loading && !creating) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            EmptyState(
                if (searching) R.drawable.ic_search else R.drawable.ic_group,
                stringResource(if (searching) R.string.contacts_nothing_found else R.string.contacts_empty),
                "contactsEmpty",
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().testTag("contactList"),
            contentPadding = contentPadding,
        ) {
            if (creating) {
                onNewGroup?.let { open -> item(key = "newGroup") { NewChatRow(R.drawable.ic_group, R.string.contacts_new_group, "newGroup", open) } }
                onNewChannel?.let { open -> item(key = "newChannel") { NewChatRow(R.drawable.ic_campaign, R.string.contacts_new_channel, "newChannel", open) } }
            }
            if (shown.isEmpty() && global.isEmpty() && !loading) {
                item(key = "empty") {
                    EmptyState(R.drawable.ic_group, stringResource(R.string.contacts_empty), "contactsEmpty")
                }
            }
            items(shown, key = { it.key }) { contact ->
                ContactComponent(
                    contact = contact,
                    avatars = avatars,
                    now = now,
                    onClick = { onOpen(contact) },
                    onLongClick = { deleting = contact.key },
                    isSelected = deleting == contact.key,
                    modifier = Modifier.animateItem(),
                )
            }
            if (global.isNotEmpty()) {
                item(key = "globalTitle") {
                    Text(
                        stringResource(R.string.chats_global_search),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).testTag("contactsGlobalTitle"),
                    )
                }
                items(global, key = { "global_${it.key}" }) { person ->
                    ContactComponent(person, avatars, now, onClick = { onOpen(person) }, onLongClick = {}, modifier = Modifier.animateItem())
                }
            }
        }
    }

    val contact = deleting?.let { key -> contacts.firstOrNull { it.key == key } }
    if (contact != null) {
        ConfirmDialog(
            title = stringResource(R.string.contacts_delete),
            text = stringResource(R.string.contacts_delete_text, contact.name),
            confirm = stringResource(R.string.chat_delete),
            tag = "confirmDeleteContact",
            onConfirm = {
                deleting = null
                onDelete(contact)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun NewChatRow(icon: Int, label: Int, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(24.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun AddContactDialog(
    notFound: Boolean,
    onAdd: (phone: String, firstName: String, lastName: String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    val valid = phone.count { it.isDigit() } >= 5 && firstName.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        onEdit()
                    },
                    label = { Text(stringResource(R.string.contacts_phone)) },
                    singleLine = true,
                    isError = notFound,
                    supportingText = if (notFound) {
                        { Text(stringResource(R.string.error_phone_unknown)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("contactPhone"),
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(R.string.sign_up_first_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("contactFirstName"),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(R.string.sign_up_last_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("contactLastName"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(phone, firstName, lastName) }, enabled = valid, modifier = Modifier.testTag("confirmAddContact")) {
                Text(stringResource(R.string.contacts_add_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun AddProfileContactDialog(
    name: String,
    onAdd: (firstName: String, lastName: String, sharePhone: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by rememberSaveable { mutableStateOf(name.substringBefore(' ')) }
    var lastName by rememberSaveable { mutableStateOf(name.substringAfter(' ', "")) }
    var sharePhone by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_add_contact)) },
        text = {
            Column {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(R.string.sign_up_first_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("contactFirstName"),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(R.string.sign_up_last_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("contactLastName"),
                )
                CheckRow(sharePhone, { sharePhone = it }, stringResource(R.string.contacts_share_phone), "sharePhone", Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(firstName, lastName, sharePhone) }, enabled = firstName.isNotBlank(), modifier = Modifier.testTag("confirmAddContact")) {
                Text(stringResource(R.string.contacts_add_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
