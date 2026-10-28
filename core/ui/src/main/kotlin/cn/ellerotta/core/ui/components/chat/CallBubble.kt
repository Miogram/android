package cn.ellerotta.core.ui.components.chat

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.CallEnd
import cn.ellerotta.core.model.CallInfo
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.ui.R

internal fun callWentThrough(call: CallInfo): Boolean =
    call.end != CallEnd.Missed && call.end != CallEnd.Busy && !(call.end == CallEnd.Disconnect && call.duration == 0)

@StringRes
internal fun callTitle(call: CallInfo, out: Boolean): Int = when {
    !callWentThrough(call) -> if (out || call.end == CallEnd.Disconnect) R.string.chat_call_cancelled else R.string.chat_call_missed
    call.video -> if (out) R.string.chat_call_outgoing_video else R.string.chat_call_incoming_video
    else -> if (out) R.string.chat_call_outgoing else R.string.chat_call_incoming
}

internal data class CallLength(@PluralsRes val plural: Int, val count: Int)

internal fun callLength(seconds: Int): CallLength? = when {
    seconds <= 1 -> null
    seconds < 60 -> CallLength(R.plurals.chat_call_seconds, seconds)
    else -> CallLength(R.plurals.chat_call_minutes, seconds / 60)
}

@Composable
internal fun CallBubble(message: MessageItem, call: CallInfo, position: BubblePosition, press: Modifier) {
    val colors = MaterialTheme.colorScheme
    val bubble = remember(colors, message.out) { bubbleColors(colors, message.out) }
    val shape = bubbleShape(message.out, position)
    val time = messageTime(message.date)
    val length = callLength(call.duration)
    Surface(
        shape = shape,
        color = bubble.container,
        contentColor = bubble.content,
        modifier = Modifier.clip(shape).then(press).testTag("message_${message.id}"),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(min = 120.dp)) {
                Text(stringResource(callTitle(call, message.out)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("callTitle_${message.id}"))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(
                        painterResource(if (message.out) R.drawable.ic_call_made else R.drawable.ic_call_received),
                        null,
                        Modifier.size(16.dp).testTag("callArrow_${message.id}"),
                        tint = if (callWentThrough(call)) bubble.success else bubble.danger,
                    )
                    Text(
                        if (length == null) time else stringResource(R.string.chat_call_time_length, time, pluralStringResource(length.plural, length.count, length.count)),
                        style = MaterialTheme.typography.bodySmall,
                        color = bubble.secondary,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Icon(
                painterResource(if (call.video) R.drawable.ic_videocam else R.drawable.ic_call),
                null,
                Modifier.size(24.dp),
                tint = bubble.accent,
            )
        }
    }
}
