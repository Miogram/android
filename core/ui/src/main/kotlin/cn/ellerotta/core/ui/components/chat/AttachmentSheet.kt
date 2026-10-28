package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.AttachmentDraft
import cn.ellerotta.core.model.AttachmentItem
import cn.ellerotta.core.model.AttachmentKind
import cn.ellerotta.core.model.FormattedText
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.trimmed
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.CheckRow
import cn.ellerotta.core.ui.components.common.DecodedPhotos

class ChatAttachmentActions(
    val onAsFile: (Boolean) -> Unit,
    val onSpoiler: (Boolean) -> Unit,
    val onRemove: (Int) -> Unit,
    val onSend: (FormattedText, SendOptions) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun AttachmentSheet(draft: AttachmentDraft, actions: ChatAttachmentActions, whenOnline: Boolean) {
    ModalBottomSheet(onDismissRequest = actions.onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)), modifier = Modifier.testTag("attachmentSheet")) {
        AttachmentContent(draft, actions, whenOnline)
    }
}

@Composable
internal fun AttachmentContent(draft: AttachmentDraft, actions: ChatAttachmentActions, whenOnline: Boolean) {
    var caption by remember { mutableStateOf(FormattedText("")) }
    Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(R.plurals.chat_attachment_title, draft.items.size, draft.items.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp).testTag("attachmentTitle"),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(draft.items, key = { it.id }) { item -> AttachmentTile(item, hidden = draft.spoiler && draft.canSpoiler && item.kind.visual, onRemove = { actions.onRemove(item.id) }) }
        }
        FormattedField(caption, { caption = it }, stringResource(R.string.chat_attachment_caption), "attachmentCaption")
        CheckRow(draft.asFile, actions.onAsFile, stringResource(R.string.chat_attachment_as_file), "attachmentAsFile")
        if (draft.canSpoiler) CheckRow(draft.spoiler, actions.onSpoiler, stringResource(R.string.chat_attachment_spoiler), "attachmentSpoiler")
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.CenterEnd) {
            var menu by remember { mutableStateOf(false) }
            val press = rememberLongPress { menu = true }
            val label = stringResource(R.string.chat_attachment_send)
            Button(
                onClick = { if (!press.consumedClick()) actions.onSend(caption.trimmed(), SendOptions.None) },
                interactionSource = press.source,
                modifier = Modifier.sendMenuAction(stringResource(R.string.chat_send_options)) { menu = true }.testTag("attachmentSend"),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.ic_send), null, Modifier.padding(end = 8.dp))
                Text(label)
            }
            SendMenu(menu, { menu = false }, whenOnline) { actions.onSend(caption.trimmed(), it) }
        }
    }
}

@Composable
private fun AttachmentTile(item: AttachmentItem, hidden: Boolean, onRemove: () -> Unit) {
    val picture = remember(item.preview) { item.preview?.let(DecodedPhotos::get) }
    val remove = stringResource(R.string.chat_attachment_remove, item.name)
    Box(
        Modifier.size(TILE).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = item.name }.testTag("attachmentTile_${item.id}"),
    ) {
        if (picture != null) {
            Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val icon = when (item.kind) {
                    AttachmentKind.Photo -> R.drawable.ic_image
                    AttachmentKind.Video -> R.drawable.ic_movie
                    AttachmentKind.Audio, AttachmentKind.File -> R.drawable.ic_article
                }
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (hidden) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = PILL_ALPHA)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_visibility), null, tint = Color.White, modifier = Modifier.testTag("attachmentHidden_${item.id}"))
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).testTag("attachmentRemove_${item.id}"), shapes = IconButtonDefaults.shapes()) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = PILL_ALPHA)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_close), remove, Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}

private val TILE = 112.dp
