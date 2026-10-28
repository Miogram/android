package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.common.CheckRow

enum class MessageAction(val icon: Int, val label: Int, val tag: String) {
    GoTo(R.drawable.ic_chat, R.string.chat_go_to_message, "actionGoTo"),
    Reply(R.drawable.ic_reply, R.string.chat_reply, "actionReply"),
    Forward(R.drawable.ic_forward, R.string.chat_forward, "actionForward"),
    Select(R.drawable.ic_check_circle, R.string.chat_select, "actionSelect"),
    Copy(R.drawable.ic_content_copy, R.string.chat_copy, "actionCopy"),
    Translate(R.drawable.ic_translate, R.string.chat_translate, "actionTranslate"),
    ShowOriginal(R.drawable.ic_translate, R.string.chat_show_original, "actionShowOriginal"),
    Pin(R.drawable.ic_push_pin, R.string.chat_pin, "actionPin"),
    Unpin(R.drawable.ic_push_pin, R.string.chat_unpin, "actionUnpin"),
    SaveToDownloads(R.drawable.ic_download, R.string.chat_save_downloads, "actionSaveDownloads"),
    Edit(R.drawable.ic_edit, R.string.chat_edit, "actionEdit"),
    Report(R.drawable.ic_report, R.string.report_action, "actionReport"),
    Delete(R.drawable.ic_delete, R.string.chat_delete, "actionDelete"),
}

@Composable
fun DeleteMessageDialog(revokeLabel: String?, onConfirm: (revoke: Boolean) -> Unit, onDismiss: () -> Unit) {
    var revoke by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_delete_title)) },
        text = {
            if (revokeLabel != null) {
                CheckRow(revoke, { revoke = it }, revokeLabel, "revoke")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(revokeLabel != null && revoke) }, modifier = Modifier.testTag("confirmDelete")) {
                Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun ReplyQuote(
    title: String?,
    text: String,
    accent: Color,
    onClick: (() -> Unit)? = null,
    entities: List<TextEntity> = emptyList(),
    emoji: InlineEmoji = InlineEmoji(emptyMap(), emptyMap()),
) {
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier
            .padding(bottom = 4.dp)
            .clip(QuoteShape)
            .background(accent.copy(alpha = REPLY_TINT))
            .then(click)
            .height(IntrinsicSize.Min)
            .testTag("replyQuote"),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent).testTag("replyBar"))
        Column(Modifier.padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            QuoteText(text, entities, emoji, maxLines = if (title != null) 1 else 2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("replyText"))
        }
    }
}

internal const val REPLY_TINT = 0.08f

private val QuoteShape = RoundedCornerShape(8.dp)
