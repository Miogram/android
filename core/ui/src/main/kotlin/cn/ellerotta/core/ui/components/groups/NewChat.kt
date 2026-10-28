package cn.ellerotta.core.ui.components.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.CHAT_ABOUT_MAX
import cn.ellerotta.core.model.CHAT_TITLE_MAX
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.MissingInvitee
import cn.ellerotta.core.model.UsernameAvailability
import cn.ellerotta.core.model.UsernameProblem
import cn.ellerotta.core.model.UsernameStatus
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.profile.copyToClipboard

@Composable
fun ChatPhotoButton(photo: ByteArray?, name: String, onPick: () -> Unit, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val label = stringResource(if (photo == null) R.string.chat_photo_set else R.string.chat_photo_change)
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onPick)
                .testTag("chatPhoto"),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Avatar(name, photo, size = 96, seed = name.hashCode().toLong(), modifier = Modifier.semantics { contentDescription = label })
            } else {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_add_a_photo), label, Modifier.size(40.dp)) }
                }
            }
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp).testTag("removePhoto")) { Text(stringResource(R.string.chat_photo_remove)) }
        }
    }
}

@Composable
fun NewGroupScreen(
    name: String,
    photo: ByteArray?,
    members: List<ContactItem>,
    avatars: Map<String, ByteArray>,
    busy: Boolean,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onCreate: () -> Unit,
) {
    val list = rememberLazyListState()
    val title = stringResource(R.string.new_group_title)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("newGroup"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            item(key = "photo") { ChatPhotoButton(photo, name, onPickPhoto, onRemovePhoto.takeIf { photo != null }, Modifier.padding(top = 8.dp)) }
            item(key = "name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { onName(it.take(CHAT_TITLE_MAX)) },
                    label = { Text(stringResource(R.string.group_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("groupName"),
                )
            }
            item(key = "create") {
                Button(
                    onClick = onCreate,
                    enabled = name.isNotBlank() && !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp).testTag("createGroup"),
                ) { Text(stringResource(R.string.chat_create)) }
            }
            item(key = "membersTitle") {
                Text(
                    pluralStringResource(R.plurals.new_group_members, members.size, members.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            items(members, key = { it.key }) { person ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp).testTag("member_${person.key}"), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person.name, person.avatarKey?.let { avatars[it] }, size = 40, seed = person.userId)
                    Spacer(Modifier.width(12.dp))
                    Text(person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun NewChannelScreen(
    name: String,
    about: String,
    photo: ByteArray?,
    busy: Boolean,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onAbout: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onCreate: () -> Unit,
) {
    val list = rememberLazyListState()
    val title = stringResource(R.string.new_channel_title)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("newChannel"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            item(key = "photo") { ChatPhotoButton(photo, name, onPickPhoto, onRemovePhoto.takeIf { photo != null }, Modifier.padding(top = 8.dp)) }
            item(key = "fields") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { onName(it.take(CHAT_TITLE_MAX)) },
                        label = { Text(stringResource(R.string.channel_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("channelName"),
                    )
                    OutlinedTextField(
                        value = about,
                        onValueChange = { onAbout(it.take(CHAT_ABOUT_MAX)) },
                        label = { Text(stringResource(R.string.channel_about_hint)) },
                        supportingText = { Text(stringResource(R.string.chat_about_counter, about.length, CHAT_ABOUT_MAX)) },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().testTag("channelAbout"),
                    )
                }
            }
            item(key = "create") {
                Button(
                    onClick = onCreate,
                    enabled = name.isNotBlank() && !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp).testTag("createChannel"),
                ) { Text(stringResource(R.string.chat_create)) }
            }
        }
    }
}

@Composable
fun ChannelTypeScreen(
    public: Boolean,
    username: String,
    status: UsernameStatus,
    link: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onPublic: (Boolean) -> Unit,
    onUsername: (String) -> Unit,
    onDone: () -> Unit,
) {
    val list = rememberLazyListState()
    val title = stringResource(R.string.channel_type_title)
    val context = LocalContext.current
    val ready = if (public) status is UsernameStatus.Checked && status.username == username && status.availability == UsernameAvailability.Available else true
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("channelType"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            item(key = "choice") {
                Column(Modifier.selectableGroup()) {
                    TypeChoice(!public, R.string.channel_private, R.string.channel_private_text, "typePrivate") { onPublic(false) }
                    TypeChoice(public, R.string.channel_public, R.string.channel_public_text, "typePublic") { onPublic(true) }
                }
            }
            item(key = "detail") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (public) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { onUsername(it.trim().removePrefix("@").take(40)) },
                            label = { Text(stringResource(R.string.channel_username_hint)) },
                            prefix = { Text("t.me/") },
                            singleLine = true,
                            isError = isUsernameError(status),
                            supportingText = { UsernameStatusText(username, status) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().testTag("channelUsername"),
                        )
                    } else if (link != null) {
                        Text(stringResource(R.string.channel_link_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(link, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).testTag("channelLink"))
                            FilledTonalButton(onClick = { copyToClipboard(context, "link", link) }, modifier = Modifier.heightIn(min = 48.dp).testTag("copyChannelLink"), shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.chat_copy))
                            }
                        }
                    }
                }
            }
            item(key = "done") {
                Button(
                    onClick = onDone,
                    enabled = ready && !busy && (public || link != null),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp).testTag("channelTypeDone"),
                ) { Text(stringResource(R.string.common_next)) }
            }
        }
    }
}

@Composable
private fun TypeChoice(selected: Boolean, title: Int, text: Int, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun isUsernameError(status: UsernameStatus): Boolean = when (status) {
    is UsernameStatus.Problem -> true
    is UsernameStatus.Checked -> status.availability != UsernameAvailability.Available
    else -> false
}

@Composable
private fun UsernameStatusText(username: String, status: UsernameStatus) {
    val message = when (status) {
        UsernameStatus.Idle -> stringResource(R.string.username_hint)
        UsernameStatus.Checking -> stringResource(R.string.username_checking)
        is UsernameStatus.Problem -> stringResource(
            when (status.problem) {
                UsernameProblem.TooShort -> R.string.username_too_short
                UsernameProblem.TooLong -> R.string.username_too_long
                UsernameProblem.BadStart -> R.string.username_bad_start
                UsernameProblem.BadCharacter -> R.string.username_bad_character
                UsernameProblem.BadEnd -> R.string.username_bad_end
            },
        )
        is UsernameStatus.Checked -> when (status.availability) {
            UsernameAvailability.Available -> stringResource(R.string.username_available, status.username)
            UsernameAvailability.Taken -> stringResource(R.string.username_taken, status.username)
            UsernameAvailability.Invalid -> stringResource(R.string.username_invalid)
            UsernameAvailability.TooManyPublic -> stringResource(R.string.username_too_many)
        }
    }
    val good = status is UsernameStatus.Checked && status.availability == UsernameAvailability.Available
    val bad = isUsernameError(status)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("usernameStatus")) {
        if (good || bad) {
            Icon(
                painterResource(if (good) R.drawable.ic_check else R.drawable.ic_error),
                null,
                Modifier.size(16.dp),
                tint = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(message, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MissingInviteesDialog(missing: List<MissingInvitee>, onSend: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.missing_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.missing_text, missing.size, missing.size))
                Text(
                    missing.joinToString(", ") { it.name.ifBlank { it.userKey } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("missingNames"),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSend, modifier = Modifier.testTag("sendInviteLink")) { Text(stringResource(R.string.missing_send)) } },
        dismissButton = { TextButton(onClick = onSkip, modifier = Modifier.testTag("skipInviteLink")) { Text(stringResource(R.string.missing_skip)) } },
    )
}
