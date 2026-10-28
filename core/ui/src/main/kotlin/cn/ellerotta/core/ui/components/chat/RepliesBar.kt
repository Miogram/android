package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.RepliesInfo
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar

private const val REPLIER_AVATARS = 3

@Composable
internal fun RepliesBar(message: MessageItem, replies: RepliesInfo, avatars: Map<String, ByteArray>, color: Color, onOpen: () -> Unit) {
    Column {
        if (replies.comments) HorizontalDivider(Modifier.padding(top = 6.dp))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp).testTag("replies_${message.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (replies.recent.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    replies.recent.take(REPLIER_AVATARS).forEach { replier ->
                        Avatar(replier.name, replier.avatarKey?.let(avatars::get), size = 22, seed = replier.peerId)
                    }
                }
            } else {
                Icon(painterResource(R.drawable.ic_reply), null, Modifier.size(18.dp), tint = color)
            }
            val text = when {
                replies.comments && replies.count == 0 -> stringResource(R.string.chat_leave_comment)
                replies.comments -> pluralStringResource(R.plurals.chat_comments, replies.count, replies.count)
                else -> pluralStringResource(R.plurals.chat_replies, replies.count, replies.count)
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f, fill = false))
            if (replies.unread) Box(Modifier.size(8.dp).background(color, CircleShape).testTag("repliesUnread_${message.id}"))
        }
    }
}
