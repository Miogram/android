package cn.ellerotta.core.ui.components.profile

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.AppPrivacy
import cn.ellerotta.core.model.GlobalPrivacy
import cn.ellerotta.core.model.PrivacyBase
import cn.ellerotta.core.model.PrivacyKey
import cn.ellerotta.core.model.PrivacySetting
import cn.ellerotta.core.model.PrivacyState
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import kotlin.math.roundToInt

@Composable
fun PrivacyScreen(
    state: PrivacyState,
    app: AppPrivacy,
    busy: Boolean,
    onBack: () -> Unit,
    onApp: (AppPrivacy) -> Unit,
    onPrivacy: (PrivacyKey, PrivacyBase) -> Unit,
    onGlobal: (GlobalPrivacy) -> Unit,
    onAccountTtl: (Int) -> Unit,
    onOpenBlocked: () -> Unit = {},
    onOpenTwoStep: () -> Unit = {},
) {
    var choosing by rememberSaveable { mutableStateOf<PrivacyKey?>(null) }
    var choosingTtl by rememberSaveable { mutableStateOf(false) }
    var choosingLockAfter by rememberSaveable { mutableStateOf(false) }

    val list = rememberLazyListState()
    val title = stringResource(R.string.privacy_title)
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("privacy"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") {
                AnimatedVisibility(busy, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            item(key = "app") {
                InfoCard(title = stringResource(R.string.privacy_section_app)) {
                    InfoRow(
                        R.drawable.ic_lock,
                        stringResource(R.string.privacy_app_lock),
                        stringResource(R.string.privacy_app_lock_hint),
                        onClick = { onApp(app.copy(lock = !app.lock)) },
                        trailing = { CheckedSwitch(app.lock, { onApp(app.copy(lock = it)) }, Modifier.testTag("appLock")) },
                    )
                    if (app.lock) {
                        InfoDivider()
                        InfoRow(
                            R.drawable.ic_schedule,
                            lockAfterText(app.lockAfterMinutes),
                            stringResource(R.string.privacy_lock_after),
                            onClick = { choosingLockAfter = true },
                            modifier = Modifier.testTag("lockAfter"),
                        )
                    }
                    InfoDivider()
                    InfoRow(
                        R.drawable.ic_visibility_off,
                        stringResource(R.string.privacy_secure_window),
                        stringResource(R.string.privacy_secure_window_hint),
                        onClick = { onApp(app.copy(secureWindow = !app.secureWindow)) },
                        trailing = { CheckedSwitch(app.secureWindow, { onApp(app.copy(secureWindow = it)) }, Modifier.testTag("secureWindow")) },
                    )
                    InfoDivider()
                    InfoRow(
                        R.drawable.ic_notifications,
                        stringResource(R.string.privacy_hide_notification_content),
                        stringResource(R.string.privacy_hide_notification_content_hint),
                        onClick = { onApp(app.copy(hideNotificationContent = !app.hideNotificationContent)) },
                        trailing = { CheckedSwitch(app.hideNotificationContent, { onApp(app.copy(hideNotificationContent = it)) }, Modifier.testTag("hideNotificationContent")) },
                    )
                }
            }
            item(key = "blocked") {
                InfoCard(Modifier.padding(top = 16.dp)) {
                    InfoRow(
                        R.drawable.ic_shield,
                        stringResource(R.string.privacy_two_step),
                        state.twoStepEnabled?.let { stringResource(if (it) R.string.privacy_two_step_on else R.string.privacy_two_step_off) },
                        onClick = onOpenTwoStep,
                        modifier = Modifier.testTag("twoStepRow"),
                    )
                    InfoDivider()
                    InfoRow(
                        R.drawable.ic_block,
                        stringResource(R.string.privacy_blocked_users),
                        state.blockedCount?.let { if (it == 0) stringResource(R.string.privacy_blocked_none) else pluralStringResource(R.plurals.privacy_blocked_count, it, it) },
                        onClick = onOpenBlocked,
                        modifier = Modifier.testTag("blockedUsersRow"),
                    )
                }
            }
            if (state.settings.isNotEmpty()) {
                item(key = "keys") {
                    InfoCard(Modifier.padding(top = 16.dp), title = stringResource(R.string.privacy_section_privacy)) {
                        state.settings.forEachIndexed { index, setting ->
                            if (index > 0) InfoDivider()
                            InfoRow(
                                iconOf(setting.key),
                                stringResource(titleOf(setting.key)),
                                privacyValueText(setting),
                                onClick = { choosing = setting.key },
                                modifier = Modifier.testTag("privacy_${setting.key.name}"),
                            )
                        }
                    }
                }
            }
            state.global?.let { global ->
                item(key = "global") {
                    InfoCard(Modifier.padding(top = 16.dp), title = stringResource(R.string.privacy_section_messages)) {
                        InfoRow(
                            R.drawable.ic_archive,
                            stringResource(R.string.privacy_archive_non_contacts),
                            stringResource(R.string.privacy_archive_non_contacts_hint),
                            onClick = { onGlobal(global.copy(archiveAndMuteNonContacts = !global.archiveAndMuteNonContacts)) },
                            trailing = { CheckedSwitch(global.archiveAndMuteNonContacts, { onGlobal(global.copy(archiveAndMuteNonContacts = it)) }, Modifier.testTag("archiveNonContacts")) },
                        )
                        InfoDivider()
                        InfoRow(
                            R.drawable.ic_visibility_off,
                            stringResource(R.string.privacy_hide_read_time),
                            stringResource(R.string.privacy_hide_read_time_hint),
                            onClick = { onGlobal(global.copy(hideReadMarks = !global.hideReadMarks)) },
                            trailing = { CheckedSwitch(global.hideReadMarks, { onGlobal(global.copy(hideReadMarks = it)) }, Modifier.testTag("hideReadTime")) },
                        )
                    }
                }
            }
            state.accountTtlDays?.let { days ->
                item(key = "account") {
                    InfoCard(Modifier.padding(top = 16.dp), title = stringResource(R.string.privacy_section_account)) {
                        InfoRow(
                            R.drawable.ic_delete,
                            ttlText(days),
                            stringResource(R.string.privacy_account_ttl),
                            onClick = { choosingTtl = true },
                            modifier = Modifier.testTag("accountTtl"),
                        )
                    }
                }
            }
        }
    }

    choosing?.let { key ->
        val current = state.settings.firstOrNull { it.key == key }
        ChoiceDialog(
            title = stringResource(titleOf(key)),
            hint = current?.takeIf { it.allowed + it.disallowed > 0 }?.let { stringResource(R.string.privacy_exceptions_kept) },
            choices = key.choices,
            selected = current?.base,
            label = { stringResource(baseText(it)) },
            tag = { "privacyChoice_${it.name}" },
            onChoose = {
                choosing = null
                if (it != current?.base) onPrivacy(key, it)
            },
            onDismiss = { choosing = null },
        )
    }
    if (choosingTtl) {
        ChoiceDialog(
            title = stringResource(R.string.privacy_account_ttl),
            hint = null,
            choices = PrivacyState.TTL_CHOICES,
            selected = state.accountTtlDays,
            label = { ttlText(it) },
            tag = { "ttl_$it" },
            onChoose = {
                choosingTtl = false
                if (it != state.accountTtlDays) onAccountTtl(it)
            },
            onDismiss = { choosingTtl = false },
        )
    }
    if (choosingLockAfter) {
        ChoiceDialog(
            title = stringResource(R.string.privacy_lock_after),
            hint = null,
            choices = AppPrivacy.LOCK_AFTER_CHOICES,
            selected = app.lockAfterMinutes,
            label = { lockAfterText(it) },
            tag = { "lockAfter_$it" },
            onChoose = {
                choosingLockAfter = false
                onApp(app.copy(lockAfterMinutes = it))
            },
            onDismiss = { choosingLockAfter = false },
        )
    }
}

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    hint: String?,
    choices: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                choices.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == selected, onClick = { onChoose(choice) }, role = Role.RadioButton)
                            .padding(vertical = 12.dp)
                            .testTag(tag(choice)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == selected, onClick = null)
                        Text(label(choice), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun privacyValueText(setting: PrivacySetting): String {
    val base = stringResource(baseText(setting.base))
    val exceptions = listOfNotNull(
        setting.disallowed.takeIf { it > 0 }?.let { stringResource(R.string.privacy_disallowed, it) },
        setting.allowed.takeIf { it > 0 }?.let { stringResource(R.string.privacy_allowed, it) },
    )
    return if (exceptions.isEmpty()) base else stringResource(R.string.privacy_with_exceptions, base, exceptions.joinToString(", "))
}

@Composable
private fun lockAfterText(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.privacy_lock_immediately) else pluralStringResource(R.plurals.privacy_lock_minutes, minutes, minutes)

@Composable
private fun ttlText(days: Int): String =
    if (days % 365 == 0) pluralStringResource(R.plurals.profile_years, days / 365, days / 365)
    else (days / 30.4).roundToInt().let { pluralStringResource(R.plurals.privacy_months, it, it) }

@StringRes
internal fun baseText(base: PrivacyBase): Int = when (base) {
    PrivacyBase.Everybody -> R.string.privacy_base_everybody
    PrivacyBase.Contacts -> R.string.privacy_base_contacts
    PrivacyBase.CloseFriends -> R.string.privacy_base_close_friends
    PrivacyBase.Nobody -> R.string.privacy_base_nobody
}

@StringRes
internal fun titleOf(key: PrivacyKey): Int = when (key) {
    PrivacyKey.LastSeen -> R.string.privacy_key_last_seen
    PrivacyKey.PhoneNumber -> R.string.privacy_key_phone_number
    PrivacyKey.FindByPhone -> R.string.privacy_key_find_by_phone
    PrivacyKey.ProfilePhoto -> R.string.privacy_key_profile_photo
    PrivacyKey.Forwards -> R.string.privacy_key_forwards
    PrivacyKey.Calls -> R.string.privacy_key_calls
    PrivacyKey.PeerToPeer -> R.string.privacy_key_p2p
    PrivacyKey.Invites -> R.string.privacy_key_invites
    PrivacyKey.VoiceMessages -> R.string.privacy_key_voice
    PrivacyKey.Bio -> R.string.privacy_key_bio
    PrivacyKey.Birthday -> R.string.privacy_key_birthday
    PrivacyKey.Gifts -> R.string.privacy_key_gifts
}

@DrawableRes
private fun iconOf(key: PrivacyKey): Int = when (key) {
    PrivacyKey.LastSeen -> R.drawable.ic_schedule
    PrivacyKey.PhoneNumber -> R.drawable.ic_smartphone
    PrivacyKey.FindByPhone -> R.drawable.ic_search
    PrivacyKey.ProfilePhoto -> R.drawable.ic_account_circle
    PrivacyKey.Forwards -> R.drawable.ic_share
    PrivacyKey.Calls -> R.drawable.ic_call
    PrivacyKey.PeerToPeer -> R.drawable.ic_public
    PrivacyKey.Invites -> R.drawable.ic_chat
    PrivacyKey.VoiceMessages -> R.drawable.ic_mic
    PrivacyKey.Bio -> R.drawable.ic_info
    PrivacyKey.Birthday -> R.drawable.ic_cake
    PrivacyKey.Gifts -> R.drawable.ic_redeem
}
