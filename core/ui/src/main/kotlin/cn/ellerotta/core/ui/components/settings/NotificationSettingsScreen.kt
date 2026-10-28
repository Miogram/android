package cn.ellerotta.core.ui.components.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.NotificationSettingsState
import cn.ellerotta.core.model.NotifyException
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.ScopeNotifySettings
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.components.profile.InfoCard
import cn.ellerotta.core.ui.components.profile.InfoDivider
import cn.ellerotta.core.ui.components.profile.InfoRow
import cn.ellerotta.core.ui.components.profile.label
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class NotificationActions(
    val onBack: () -> Unit = {},
    val onDefault: (NotifyScope, ScopeNotifySettings) -> Unit = { _, _ -> },
    val onOpenExceptions: (NotifyScope) -> Unit = {},
    val onException: (String, Int?) -> Unit = { _, _ -> },
    val onOpenSystem: () -> Unit = {},
    val onProximity: (Boolean) -> Unit = {},
    val onOpenProximity: () -> Unit = {},
    val onReset: () -> Unit = {},
)

@get:StringRes
internal val NotifyScope.title: Int
    get() = when (this) {
        NotifyScope.Users -> R.string.notifications_users
        NotifyScope.Chats -> R.string.notifications_groups
        NotifyScope.Broadcasts -> R.string.notifications_channels
    }

@get:DrawableRes
private val NotifyScope.icon: Int
    get() = when (this) {
        NotifyScope.Users -> R.drawable.ic_account_circle
        NotifyScope.Chats -> R.drawable.ic_group
        NotifyScope.Broadcasts -> R.drawable.ic_campaign
    }

@Composable
fun NotificationSettingsScreen(state: NotificationSettingsState, busy: Boolean, now: Long, actions: NotificationActions, proximity: Boolean = true) {
    var resetting by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val title = stringResource(R.string.settings_notifications)
    Page(title, rememberTopBarState(list), onBack = actions.onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("notifications"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") {
                AnimatedVisibility(busy, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            NotifyScope.entries.forEach { scope ->
                item(key = scope.name) {
                    val settings = state.scopes[scope]
                    InfoCard(Modifier.padding(top = 8.dp), title = stringResource(scope.title)) {
                        val on = settings?.muted(now) == false
                        InfoRow(
                            scope.icon,
                            stringResource(R.string.notifications_show),
                            settings?.let { mutedText(it.muteUntil, now) },
                            onClick = { settings?.let { actions.onDefault(scope, it.copy(muteUntil = if (on) Int.MAX_VALUE else 0)) } },
                            trailing = {
                                CheckedSwitch(on, { checked -> settings?.let { actions.onDefault(scope, it.copy(muteUntil = if (checked) 0 else Int.MAX_VALUE)) } }, Modifier.testTag("notify_${scope.name}"), enabled = settings != null)
                            },
                        )
                        InfoDivider()
                        val previews = settings?.showPreviews ?: true
                        InfoRow(
                            R.drawable.ic_chat,
                            stringResource(R.string.notifications_preview),
                            stringResource(R.string.notifications_preview_hint),
                            onClick = { settings?.let { actions.onDefault(scope, it.copy(showPreviews = !previews)) } },
                            trailing = {
                                CheckedSwitch(previews, { checked -> settings?.let { actions.onDefault(scope, it.copy(showPreviews = checked)) } }, Modifier.testTag("preview_${scope.name}"), enabled = settings != null)
                            },
                        )
                        InfoDivider()
                        val exceptions = state.exceptionsOf(scope, now)
                        InfoRow(
                            R.drawable.ic_settings,
                            stringResource(R.string.notifications_exceptions),
                            exceptions?.let { if (it.isEmpty()) stringResource(R.string.notifications_exceptions_none) else pluralStringResource(R.plurals.notifications_exceptions_count, it.size, it.size) },
                            onClick = { actions.onOpenExceptions(scope) },
                            modifier = Modifier.testTag("exceptions_${scope.name}"),
                        )
                    }
                }
            }
            item(key = "proximity") {
                InfoCard(Modifier.padding(top = 8.dp), title = stringResource(R.string.notifications_proximity)) {
                    InfoRow(
                        R.drawable.ic_distance,
                        stringResource(R.string.notifications_show),
                        stringResource(R.string.notifications_proximity_hint),
                        onClick = { actions.onProximity(!proximity) },
                        trailing = { CheckedSwitch(proximity, actions.onProximity, Modifier.testTag("notifyProximity")) },
                    )
                    InfoDivider()
                    InfoRow(
                        R.drawable.ic_volume_up,
                        stringResource(R.string.notifications_proximity_system),
                        stringResource(R.string.notifications_proximity_system_hint),
                        onClick = actions.onOpenProximity,
                        modifier = Modifier.testTag("notificationsProximitySystem"),
                    )
                }
            }
            item(key = "system") {
                InfoCard(Modifier.padding(top = 16.dp)) {
                    InfoRow(
                        R.drawable.ic_volume_up,
                        stringResource(R.string.notifications_system),
                        stringResource(R.string.notifications_system_hint),
                        onClick = actions.onOpenSystem,
                        modifier = Modifier.testTag("notificationsSystem"),
                    )
                }
            }
            item(key = "reset") {
                InfoCard(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                    InfoRow(
                        R.drawable.ic_delete,
                        stringResource(R.string.notifications_reset),
                        stringResource(R.string.notifications_reset_hint),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        container = MaterialTheme.colorScheme.errorContainer,
                        valueColor = MaterialTheme.colorScheme.error,
                        onClick = { resetting = true },
                        modifier = Modifier.testTag("notificationsReset"),
                    )
                }
            }
        }
    }
    if (resetting) {
        ConfirmDialog(
            title = stringResource(R.string.notifications_reset_title),
            text = stringResource(R.string.notifications_reset_text),
            confirm = stringResource(R.string.notifications_reset_confirm),
            tag = "notificationsResetConfirm",
            onConfirm = {
                resetting = false
                actions.onReset()
            },
            onDismiss = { resetting = false },
        )
    }
}

@Composable
fun NotifyExceptionsScreen(scope: NotifyScope, state: NotificationSettingsState, avatars: Map<String, ByteArray>, busy: Boolean, now: Long, actions: NotificationActions) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val title = stringResource(scope.title)
    val exceptions = state.exceptionsOf(scope, now)
    Page(title, rememberTopBarState(list), onBack = actions.onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("notifyExceptions"), state = list, contentPadding = padding) {
            largeTitle(title)
            item(key = "busy") {
                AnimatedVisibility(busy || exceptions == null, enter = MaterialTheme.motionScheme.expandFadeIn(), exit = MaterialTheme.motionScheme.shrinkFadeOut()) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            item(key = "hint") {
                Text(
                    stringResource(if (exceptions?.isEmpty() == true) R.string.notifications_exceptions_empty else R.string.notifications_exceptions_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).testTag("exceptionsHint"),
                )
            }
            items(exceptions.orEmpty(), key = { it.key }) { exception ->
                ExceptionRow(exception, exception.avatarKey?.let { avatars[it] }, statusText(exception, now)) { editing = exception.key }
            }
        }
    }
    exceptions?.firstOrNull { it.key == editing }?.let { exception ->
        ExceptionDialog(
            exception,
            onChoose = { muteUntil ->
                editing = null
                actions.onException(exception.key, muteUntil)
            },
            onDismiss = { editing = null },
            now = now,
        )
    }
}

@Composable
private fun ExceptionRow(exception: NotifyException, avatar: ByteArray?, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 56.dp).testTag("exception_${exception.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(exception.name, avatar, size = 44, seed = exception.key.filter(Char::isDigit).toLongOrNull() ?: 0)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(exception.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ExceptionDialog(exception: NotifyException, onChoose: (Int?) -> Unit, onDismiss: () -> Unit, now: Long) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exception.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DialogChoice(stringResource(R.string.notifications_turn_on), "exceptionOn") { onChoose(0) }
                MuteDuration.entries.forEach { duration ->
                    DialogChoice(stringResource(duration.label), "exceptionMute${duration.name}") {
                        onChoose(duration.seconds?.let { (now + it).toInt() } ?: Int.MAX_VALUE)
                    }
                }
                DialogChoice(stringResource(R.string.notifications_follow_default), "exceptionRemove") { onChoose(null) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        modifier = Modifier.testTag("exceptionDialog"),
    )
}

@Composable
private fun DialogChoice(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp).testTag(tag),
    )
}

@Composable
private fun mutedText(muteUntil: Int, now: Long): String = when {
    muteUntil <= now -> stringResource(R.string.notifications_on)
    muteUntil == Int.MAX_VALUE -> stringResource(R.string.notifications_off)
    else -> stringResource(R.string.notifications_off_until, untilText(muteUntil, now))
}

@Composable
private fun statusText(exception: NotifyException, now: Long): String = exception.muteUntil?.let { mutedText(it, now) }
    ?: stringResource(if (exception.showPreviews == false) R.string.notifications_no_preview else R.string.notifications_preview)

private fun untilText(until: Int, now: Long): String {
    val zone = ZoneId.systemDefault()
    val end = Instant.ofEpochSecond(until.toLong()).atZone(zone)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val format = if (end.toLocalDate() == today) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    return end.format(format)
}
