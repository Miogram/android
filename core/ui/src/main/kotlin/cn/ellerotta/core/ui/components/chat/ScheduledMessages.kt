package cn.ellerotta.core.ui.components.chat

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.trimmed
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.EmptyState
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.theme.expandFadeIn
import cn.ellerotta.core.ui.theme.shrinkFadeOut
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduledActions(
    val onSendNow: (MessageItem) -> Unit,
    val onReschedule: (MessageItem, Int) -> Unit,
    val onEdit: (MessageItem, FormattedText) -> Unit,
    val onDelete: (MessageItem) -> Unit,
)

@Composable
internal fun ScheduledBar(count: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("scheduledBar")) {
            Icon(painterResource(R.drawable.ic_event), null, Modifier.size(18.dp))
            Text(pluralStringResource(R.plurals.chat_scheduled_bar, count, count), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
internal fun ScheduledBarHost(count: Int, onOpen: (() -> Unit)?) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(count > 0 && onOpen != null, enter = motion.expandFadeIn(), exit = motion.shrinkFadeOut()) {
        ScheduledBar(count) { onOpen?.invoke() }
    }
}

@Composable
fun ScheduledScreen(title: String, messages: List<MessageItem>, loaded: Boolean, actions: ScheduledActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    var menu by remember { mutableStateOf<MessageItem?>(null) }
    var rescheduling by remember { mutableStateOf<MessageItem?>(null) }
    var editing by remember { mutableStateOf<MessageItem?>(null) }
    var deleting by remember { mutableStateOf<MessageItem?>(null) }
    Page(title, rememberTopBarState(list), onBack) { padding ->
        LazyColumn(modifier.fillMaxWidth().testTag("scheduledList"), state = list, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            largeTitle(title)
            if (loaded && messages.isEmpty()) {
                item(key = "empty") {
                    EmptyState(R.drawable.ic_schedule, stringResource(R.string.chat_scheduled_empty), "scheduledEmpty")
                }
            }
            items(messages, key = { it.id }) { message ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ScheduledItem(message) { menu = message }
                    DropdownMenu(expanded = menu?.id == message.id, onDismissRequest = { menu = null }, shape = RoundedCornerShape(20.dp)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_scheduled_send_now)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_send), null) },
                            onClick = {
                                menu = null
                                actions.onSendNow(message)
                            },
                            modifier = Modifier.testTag("scheduledSendNow"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_scheduled_reschedule)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_event), null) },
                            onClick = {
                                menu = null
                                rescheduling = message
                            },
                            modifier = Modifier.testTag("scheduledReschedule"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_scheduled_edit)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) },
                            onClick = {
                                menu = null
                                editing = message
                            },
                            modifier = Modifier.testTag("scheduledEdit"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_scheduled_delete)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                            onClick = {
                                menu = null
                                deleting = message
                            },
                            modifier = Modifier.testTag("scheduledDelete"),
                        )
                    }
                }
            }
        }
    }
    rescheduling?.let { message ->
        ScheduleDialog(
            initial = message.date.takeIf { it != SendOptions.WHEN_ONLINE }?.toLong(),
            onConfirm = {
                rescheduling = null
                actions.onReschedule(message, it)
            },
            onDismiss = { rescheduling = null },
        )
    }
    editing?.let { message -> EditScheduledDialog(message, onSave = { editing = null; actions.onEdit(message, it) }, onDismiss = { editing = null }) }
    deleting?.let { message ->
        ConfirmDialog(
            stringResource(R.string.chat_scheduled_delete_title),
            null,
            stringResource(R.string.chat_scheduled_delete),
            "confirmDeleteScheduled",
            onConfirm = {
                deleting = null
                actions.onDelete(message)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ScheduledItem(message: MessageItem, onClick: () -> Unit) {
    val colors = bubbleColors(MaterialTheme.colorScheme, out = true)
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = colors.container, contentColor = colors.content, modifier = Modifier.fillMaxWidth().testTag("scheduled_${message.id}")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(scheduledDateText(message.date), style = MaterialTheme.typography.labelLarge, color = colors.accent, modifier = Modifier.testTag("scheduledDate_${message.id}"))
            message.media?.let { media ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_attach_file), null, Modifier.size(18.dp))
                    Text(media.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (message.text.isNotEmpty()) {
                QuoteText(message.text, message.entities, InlineEmoji(emptyMap(), emptyMap()), maxLines = SCHEDULED_LINES, style = MaterialTheme.typography.bodyLarge.copy(color = colors.content))
            }
        }
    }
}

@Composable
private fun EditScheduledDialog(message: MessageItem, onSave: (FormattedText) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(FormattedText(message.text, message.entities)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_scheduled_edit)) },
        text = { FormattedField(text, { text = it }, stringResource(R.string.chat_message), "scheduledEditField") },
        confirmButton = {
            TextButton(onClick = { onSave(text.trimmed()) }, enabled = text.text.isNotBlank() || message.media != null, modifier = Modifier.testTag("scheduledEditSave")) {
                Text(stringResource(R.string.chat_article_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun scheduledDateText(date: Int, zone: ZoneId = ZoneId.systemDefault()): String {
    if (date == SendOptions.WHEN_ONLINE) return stringResource(R.string.chat_scheduled_when_online)
    val locale = LocalConfiguration.current.locales[0]
    val text = remember(date, locale, zone) { scheduledDayTime(date, locale, zone) }
    return stringResource(R.string.chat_scheduled_for, text)
}

internal fun scheduledDayTime(date: Int, locale: Locale, zone: ZoneId): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMMyjmm"), locale).format(Instant.ofEpochSecond(date.toLong()).atZone(zone))

private const val SCHEDULED_LINES = 6
