package cn.ellerotta.core.ui.components.chat

import android.text.format.DateFormat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.ui.R
import java.time.Instant
import cn.ellerotta.core.model.FormattedText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class ChatSendActions(
    val onSend: (FormattedText, replyTo: Int?, SendOptions) -> Unit,
    val onSendVoice: (SendOptions) -> Unit,
)

internal class LongPress(val source: MutableInteractionSource) {
    var longPressed = false

    fun consumedClick(): Boolean = longPressed.also { longPressed = false }
}

@Composable
internal fun rememberLongPress(enabled: Boolean = true, onLongPress: () -> Unit): LongPress {
    val press = remember { LongPress(MutableInteractionSource()) }
    val latest by rememberUpdatedState(onLongPress)
    val active by rememberUpdatedState(enabled)
    val haptics = LocalHapticFeedback.current
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    LaunchedEffect(press) {
        press.source.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press && active) {
                press.longPressed = false
                delay(timeout)
                press.longPressed = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                latest()
            }
        }
    }
    return press
}

internal fun Modifier.sendMenuAction(label: String, onOpen: () -> Unit): Modifier = semantics {
    onLongClick(label) {
        onOpen()
        true
    }
}

@Composable
internal fun SendMenu(expanded: Boolean, onDismiss: () -> Unit, whenOnline: Boolean, onSend: (SendOptions) -> Unit) {
    var scheduling by remember { mutableStateOf(false) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp)) {
        SendMenuItems(whenOnline, onDismiss, onSchedule = { scheduling = true }, onSend)
    }
    if (scheduling) {
        ScheduleDialog(
            onConfirm = {
                scheduling = false
                onSend(SendOptions(scheduleDate = it))
            },
            onDismiss = { scheduling = false },
        )
    }
}

@Composable
internal fun SendMenuItems(whenOnline: Boolean, onDismiss: () -> Unit, onSchedule: () -> Unit, onSend: (SendOptions) -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.chat_send_silent)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_volume_off), null) },
        onClick = {
            onDismiss()
            onSend(SendOptions(silent = true))
        },
        modifier = Modifier.testTag("sendSilent"),
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.chat_send_schedule)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_event), null) },
        onClick = {
            onDismiss()
            onSchedule()
        },
        modifier = Modifier.testTag("sendSchedule"),
    )
    if (whenOnline) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.chat_send_when_online)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_schedule), null) },
            onClick = {
                onDismiss()
                onSend(SendOptions(scheduleDate = SendOptions.WHEN_ONLINE))
            },
            modifier = Modifier.testTag("sendWhenOnline"),
        )
    }
}

@Composable
internal fun ScheduleDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    initial: Long? = null,
    now: () -> Long = { System.currentTimeMillis() / 1000 },
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val start = remember(initial) { LocalDateTime.ofInstant(Instant.ofEpochSecond(initial ?: (now() + SCHEDULE_START_AHEAD)), zone) }
    val today = remember { LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val dates = rememberDatePickerState(
        initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
            }
        },
    )
    val times = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    var choosingTime by remember { mutableStateOf(false) }
    fun picked(): Long? {
        val day = dates.selectedDateMillis ?: return null
        val date = Instant.ofEpochMilli(day).atZone(ZoneOffset.UTC).toLocalDate()
        return date.atTime(LocalTime.of(times.hour, times.minute)).atZone(zone).toEpochSecond()
    }
    if (!choosingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { choosingTime = true }, enabled = dates.selectedDateMillis != null, modifier = Modifier.testTag("scheduleDateOk")) { Text(stringResource(R.string.chat_schedule_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            DatePicker(dates, title = { Text(stringResource(R.string.chat_send_schedule), Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    } else {
        val at = picked()
        val ahead = at != null && at >= now() + SendOptions.MIN_AHEAD
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.chat_schedule_time)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (!ahead) Text(stringResource(R.string.chat_schedule_past), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp).testTag("schedulePast"))
                    TimePicker(times)
                }
            },
            confirmButton = {
                TextButton(onClick = { at?.let { onConfirm(it.toInt()) } }, enabled = ahead, modifier = Modifier.testTag("scheduleTimeOk")) { Text(stringResource(R.string.chat_schedule_set)) }
            },
            dismissButton = { TextButton(onClick = { choosingTime = false }) { Text(stringResource(R.string.chat_schedule_back)) } },
        )
    }
}

private const val SCHEDULE_START_AHEAD = 60 * 60
