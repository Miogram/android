package cn.ellerotta.core.ui.components.chat

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun MessageMeta(message: MessageItem, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val time = messageTime(message.date)
    val bubble = bubbleColors()
    val text = color.takeOrElse { bubble.secondary }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (message.edited) Text(stringResource(R.string.chat_edited), style = MaterialTheme.typography.labelSmall, color = text)
        Text(time, style = MaterialTheme.typography.labelSmall, color = text, modifier = Modifier.testTag("time_${message.id}"))
        if (message.failed) {
            Icon(
                painterResource(R.drawable.ic_error),
                stringResource(R.string.chat_failed),
                Modifier.size(14.dp).testTag("failed_${message.id}"),
                tint = color.takeOrElse { bubble.danger },
            )
        } else if (message.out) {
            Icon(
                painterResource(
                    when {
                        message.pending -> R.drawable.ic_schedule
                        message.read -> R.drawable.ic_done_all
                        else -> R.drawable.ic_check
                    },
                ),
                stringResource(if (message.read) R.string.chats_read else R.string.chats_sent),
                Modifier.size(14.dp),
                tint = color.takeOrElse { bubble.accent },
            )
        }
    }
}

@Composable
internal fun messageTime(date: Int): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        Instant.ofEpochSecond(date.toLong()).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale))
    }
}
