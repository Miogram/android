package cn.ellerotta.core.ui.components.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.OwnUsername
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.ProfileEdit
import cn.ellerotta.core.model.ProfileRules
import cn.ellerotta.core.model.UsernameCheck
import cn.ellerotta.core.model.OwnUsernameProblem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.CheckRow
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.TopBarPill
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.animation.AnimatedVisibility

class EditProfileActions(
    val onFirstName: (String) -> Unit = {},
    val onLastName: (String) -> Unit = {},
    val onAbout: (String) -> Unit = {},
    val onUsername: (String) -> Unit = {},
    val onBirthday: (Birthday?) -> Unit = {},
    val onChannel: (PersonalChannel?) -> Unit = {},
    val onLoadChannels: () -> Unit = {},
    val onToggleUsername: (OwnUsername) -> Unit = {},
    val onMoveUsername: (OwnUsername, up: Boolean) -> Unit = { _, _ -> },
    val onGallery: () -> Unit = {},
    val onCamera: () -> Unit = {},
    val onOpenPhotos: () -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
fun EditProfileScreen(
    edit: ProfileEdit?,
    avatars: Map<String, ByteArray>,
    channels: List<PersonalChannel>?,
    busy: Boolean,
    onBack: () -> Unit,
    actions: EditProfileActions,
) {
    var discarding by rememberSaveable { mutableStateOf(false) }
    val unsaved = edit?.changed == true
    BackHandler(enabled = unsaved) { discarding = true }
    val leave = { if (unsaved) discarding = true else onBack() }
    val list = rememberLazyListState()
    val title = stringResource(R.string.profile_edit)
    Page(
        title,
        rememberTopBarState(list),
        onBack = leave,
        actions = {
            TopBarPill(R.drawable.ic_check, stringResource(R.string.profile_edit_save), actions.onSave, Modifier.testTag("editSave"), enabled = edit?.canSave == true && !busy)
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("editProfile"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") {
                AnimatedVisibility(busy || edit == null, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            if (edit != null) {
                item(key = "photo") { PhotoHeader(edit, avatars, actions) }
                item(key = "names") { NameFields(edit, actions) }
                item(key = "about") { AboutField(edit, actions) }
                item(key = "username") { UsernameField(edit, actions) }
                if (edit.saved.usernameList.any { !it.editable }) item(key = "usernames") { UsernameList(edit.saved.usernameList, actions) }
                item(key = "birthday") { BirthdayRow(edit.birthday, actions.onBirthday) }
                item(key = "channel") { ChannelRow(edit.channel, avatars, channels, actions) }
                item(key = "save") {
                    Button(
                        onClick = actions.onSave,
                        enabled = edit.canSave && !busy,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp).testTag("editSaveButton"),
                    ) { Text(stringResource(R.string.profile_edit_save)) }
                }
            }
        }
    }
    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text(stringResource(R.string.profile_edit_discard_title)) },
            text = { Text(stringResource(R.string.profile_edit_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    discarding = false
                    onBack()
                }, modifier = Modifier.testTag("confirmDiscard")) { Text(stringResource(R.string.profile_edit_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.profile_edit_keep)) } },
        )
    }
}

@Composable
private fun PhotoHeader(edit: ProfileEdit, avatars: Map<String, ByteArray>, actions: EditProfileActions) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val key = edit.saved.avatarKey
        Avatar(edit.saved.name, key?.let { avatars.photoOf(it) }, size = 104, seed = edit.saved.id)
        Spacer(Modifier.height(12.dp))
        Box {
            FilledTonalButton(onClick = { menu = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp).testTag("editPhoto")) {
                Icon(painterResource(R.drawable.ic_add_a_photo), null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.profile_edit_photo))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_edit_photo_gallery)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_image), null) },
                    onClick = {
                        menu = false
                        actions.onGallery()
                    },
                    modifier = Modifier.testTag("editPhotoGallery"),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_edit_photo_camera)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_videocam), null) },
                    onClick = {
                        menu = false
                        actions.onCamera()
                    },
                    modifier = Modifier.testTag("editPhotoCamera"),
                )
                if (key != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_edit_photo_view)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_visibility), null) },
                        onClick = {
                            menu = false
                            actions.onOpenPhotos()
                        },
                        modifier = Modifier.testTag("editPhotoView"),
                    )
                }
            }
        }
    }
}

@Composable
private fun NameFields(edit: ProfileEdit, actions: EditProfileActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = edit.firstName,
            onValueChange = actions.onFirstName,
            label = { Text(stringResource(R.string.profile_edit_first_name)) },
            isError = edit.firstName.isBlank(),
            supportingText = if (edit.firstName.isBlank()) ({ Text(stringResource(R.string.profile_edit_name_required)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth().testTag("editFirstName"),
        )
        OutlinedTextField(
            value = edit.lastName,
            onValueChange = actions.onLastName,
            label = { Text(stringResource(R.string.profile_edit_last_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth().testTag("editLastName"),
        )
    }
}

@Composable
private fun AboutField(edit: ProfileEdit, actions: EditProfileActions) {
    OutlinedTextField(
        value = edit.about,
        onValueChange = actions.onAbout,
        label = { Text(stringResource(R.string.profile_edit_about)) },
        placeholder = { Text(stringResource(R.string.profile_edit_about_hint)) },
        supportingText = { Text(stringResource(R.string.profile_edit_counter, edit.about.length, edit.aboutLimit), Modifier.testTag("editAboutCounter")) },
        minLines = 2,
        maxLines = 5,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag("editAbout"),
    )
}

@Composable
private fun UsernameField(edit: ProfileEdit, actions: EditProfileActions) {
    val check = edit.usernameCheck
    val error = check is UsernameCheck.Invalid || check == UsernameCheck.Taken || check == UsernameCheck.ForSale
    OutlinedTextField(
        value = edit.username,
        onValueChange = actions.onUsername,
        label = { Text(stringResource(R.string.profile_edit_username)) },
        prefix = { Text("@") },
        isError = error,
        supportingText = { Text(usernameVerdict(edit.username, check), Modifier.testTag("editUsernameVerdict")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag("editUsername"),
    )
}

@Composable
private fun usernameVerdict(username: String, check: UsernameCheck): String = when (check) {
    UsernameCheck.Idle, UsernameCheck.Unknown -> stringResource(R.string.profile_edit_username_hint)
    UsernameCheck.Checking -> stringResource(R.string.profile_edit_username_checking)
    UsernameCheck.Free -> stringResource(R.string.profile_edit_username_free, "@$username")
    UsernameCheck.Taken -> stringResource(R.string.profile_edit_username_taken)
    UsernameCheck.ForSale -> stringResource(R.string.profile_edit_username_for_sale)
    is UsernameCheck.Invalid -> stringResource(
        when (check.problem) {
            OwnUsernameProblem.TooShort -> R.string.profile_edit_username_short
            OwnUsernameProblem.TooLong -> R.string.profile_edit_username_long
            OwnUsernameProblem.BadSymbols -> R.string.profile_edit_username_symbols
            OwnUsernameProblem.StartsWithDigit -> R.string.profile_edit_username_digit
            null -> R.string.profile_edit_username_invalid
        },
    )
}

@Composable
private fun UsernameList(usernames: List<OwnUsername>, actions: EditProfileActions) {
    val active = usernames.filter { it.active }
    InfoCard(Modifier.padding(top = 16.dp).testTag("editUsernames"), title = stringResource(R.string.profile_edit_usernames)) {
        usernames.forEachIndexed { index, username ->
            if (index > 0) InfoDivider(start = PlainInset)
            val position = active.indexOfFirst { it.username == username.username }
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp).testTag("username_${username.username}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text("@${username.username}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(
                            when {
                                username.editable -> R.string.profile_edit_username_public
                                username.active -> R.string.profile_edit_username_collectible
                                else -> R.string.profile_edit_username_off
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (username.active && active.size > 1) {
                    IconButton(onClick = { actions.onMoveUsername(username, true) }, enabled = position > 0, modifier = Modifier.testTag("usernameUp_${username.username}"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.profile_edit_username_up, "@${username.username}"))
                    }
                    IconButton(onClick = { actions.onMoveUsername(username, false) }, enabled = position < active.lastIndex, modifier = Modifier.testTag("usernameDown_${username.username}"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.profile_edit_username_down, "@${username.username}"))
                    }
                }
                if (!username.editable) {
                    CheckedSwitch(
                        checked = username.active,
                        onCheckedChange = { actions.onToggleUsername(username) },
                        modifier = Modifier.semantics { contentDescription = "@${username.username}" }.testTag("usernameSwitch_${username.username}"),
                    )
                }
            }
        }
    }
    Text(
        stringResource(R.string.profile_edit_usernames_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
    )
}

@Composable
private fun BirthdayRow(birthday: Birthday?, onBirthday: (Birthday?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    InfoCard(Modifier.padding(top = 16.dp)) {
        InfoRow(
            icon = R.drawable.ic_cake,
            value = birthday?.let { birthdayText(it, locale) } ?: stringResource(R.string.profile_edit_birthday_none),
            label = stringResource(R.string.profile_birthday),
            modifier = Modifier.heightIn(min = 64.dp).testTag("editBirthday"),
            onClick = { picking = true },
            trailing = {
                if (birthday != null) {
                    IconButton(onClick = { onBirthday(null) }, modifier = Modifier.testTag("editBirthdayRemove"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.profile_edit_birthday_remove))
                    }
                }
            },
        )
    }
    if (picking) {
        BirthdayDialog(birthday, onPick = {
            picking = false
            onBirthday(it)
        }, onDismiss = { picking = false })
    }
}

@Composable
private fun BirthdayDialog(birthday: Birthday?, onPick: (Birthday) -> Unit, onDismiss: () -> Unit) {
    var withYear by rememberSaveable { mutableStateOf(birthday?.year != null) }
    val today = LocalDate.now(ZoneOffset.UTC)
    val shown = birthday?.let { LocalDate.of(it.year ?: LEAP_YEAR, it.month, it.day) } ?: LocalDate.of(LEAP_YEAR, 1, 1)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = birthday?.let { shown.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        initialDisplayedMonthMillis = shown.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 1900..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: return@TextButton
                    onPick(Birthday(picked.dayOfMonth, picked.monthValue, picked.year.takeIf { withYear }))
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("birthdaySet"),
            ) { Text(stringResource(R.string.profile_edit_birthday_set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(state, title = null, headline = null)
            CheckRow(withYear, { withYear = it }, stringResource(R.string.profile_edit_birthday_year), "birthdayYear", Modifier.padding(horizontal = 24.dp))
            Text(
                stringResource(R.string.profile_edit_birthday_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

private const val LEAP_YEAR = 2000

@Composable
private fun ChannelRow(channel: PersonalChannel?, avatars: Map<String, ByteArray>, choices: List<PersonalChannel>?, actions: EditProfileActions) {
    var picking by rememberSaveable { mutableStateOf(false) }
    InfoCard(Modifier.padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                actions.onLoadChannels()
                picking = true
            }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("editChannel"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel != null) {
                Avatar(channel.title, channel.avatarKey?.let { avatars[it] }, size = 40, seed = channel.key.hashCode().toLong())
                Spacer(Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(channel?.title ?: stringResource(R.string.profile_edit_channel_none), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.profile_edit_channel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(
        stringResource(R.string.profile_edit_channel_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
    )
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.profile_edit_channel_pick)) },
            text = {
                Column {
                    if (choices == null) {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
                    } else if (choices.isEmpty()) {
                        Text(stringResource(R.string.profile_edit_channel_empty), Modifier.testTag("channelsEmpty"))
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp).testTag("channelChoices")) {
                            items(choices, key = { it.key }) { choice ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
                                        value = choice.key == channel?.key,
                                        role = Role.RadioButton,
                                        onValueChange = {
                                            picking = false
                                            actions.onChannel(choice)
                                        },
                                    ).testTag("channel_${choice.key}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(choice.title, choice.avatarKey?.let { avatars[it] }, size = 40, seed = choice.key.hashCode().toLong())
                                    Text(choice.title, Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
                                    if (choice.key == channel?.key) Icon(painterResource(R.drawable.ic_check), null)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (channel != null) {
                    TextButton(onClick = {
                        picking = false
                        actions.onChannel(null)
                    }, modifier = Modifier.testTag("channelRemove")) { Text(stringResource(R.string.profile_edit_channel_remove)) }
                }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
