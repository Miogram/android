package cn.ellerotta.core.ui.components.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ARTICLE_DRAFT_PREVIEW_MAX
import cn.ellerotta.core.model.ARTICLE_PREVIEW_MAX
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.model.ServiceKind
import cn.ellerotta.core.model.mediaLike
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.SPOILER_TAG
import cn.ellerotta.core.ui.components.chat.articlePreview
import cn.ellerotta.core.ui.components.chat.spoilerParticles
import cn.ellerotta.core.ui.components.chat.spoilerPhase
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.savedMessagesPalette

@Composable
fun dialogTitle(dialog: DialogItem): String = if (dialog.isSaved) stringResource(R.string.saved_messages) else dialog.title

@Composable
fun DialogAvatar(dialog: DialogItem, avatars: Map<String, ByteArray>, size: Int = 48) {
    if (dialog.isSaved) {
        Box(Modifier.size(size.dp).clip(CircleShape).background(savedMessagesPalette.brush), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_bookmark), null, Modifier.size((size * 0.45f).dp), tint = savedMessagesPalette.content)
        }
    } else {
        Avatar(dialog.title, dialog.avatarKey?.let { avatars[it] }, size = size, seed = dialog.peerId)
    }
}

@Composable
fun previewText(preview: Preview, kind: PeerKind): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val media = preview.media?.let { mediaLabel(it) }
    val service = preview.service?.let { serviceLabel(it, kind, preview.added) }
    val article = preview.article
    val articleText = article?.let { articlePreview(it, if (preview.draft) ARTICLE_DRAFT_PREVIEW_MAX else ARTICLE_PREVIEW_MAX) }
    val you = stringResource(R.string.chats_you)
    val draft = stringResource(R.string.chats_draft)
    return buildAnnotatedString {
        when {
            preview.draft -> withStyle(SpanStyle(color = colors.error)) { append("$draft: ") }
            preview.sender != null && service == null -> withStyle(SpanStyle(color = colors.onSurface)) { append("${preview.sender?.ifEmpty { you }}: ") }
        }
        when {
            service != null -> {
                val who = preview.sender?.let { it.ifEmpty { you } }
                withStyle(SpanStyle(color = colors.primary)) { append(if (who != null) "$who $service" else service.replaceFirstChar { it.uppercase() }) }
            }
            article != null && articleText != null -> if (article.mediaLike && !preview.draft) {
                withStyle(SpanStyle(color = colors.primary)) { append(articleText.replace('\n', ' ')) }
            } else {
                append(articleText.replace('\n', ' '))
            }
            media != null -> {
                withStyle(SpanStyle(color = colors.primary)) { append(media) }
                if (preview.text.isNotBlank()) {
                    append(if (preview.media == MediaKind.Sticker) " " else ", ")
                    appendHiding(preview.text.lineSequence().first(), preview.hidden)
                }
            }
            else -> appendHiding(preview.text.replace('\n', ' '), preview.hidden)
        }
    }
}

private fun AnnotatedString.Builder.appendHiding(text: String, hidden: List<IntRange>) {
    val start = length
    append(text)
    for (range in hidden) {
        val first = range.first.coerceAtLeast(0)
        val end = minOf(range.last + 1, text.length)
        if (first >= end) continue
        addStyle(SpanStyle(color = Color.Transparent), start + first, start + end)
        addStringAnnotation(SPOILER_TAG, "", start + first, start + end)
    }
}

@Composable
internal fun PreviewLine(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    seed: Int = 0,
    maxLines: Int = 1,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val hidden = remember(text) { text.getStringAnnotations(SPOILER_TAG, 0, text.length).map { it.start until it.end } }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val phase = spoilerPhase(hidden.isNotEmpty())
    Text(
        text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val result = layout ?: return@drawWithContent
            spoilerParticles(result, hidden, color, phase?.value ?: 0f, seed)
        },
    )
}

@Composable
internal fun mediaLabel(kind: MediaKind): String = stringResource(
    when (kind) {
        MediaKind.Photo -> R.string.media_photo
        MediaKind.Video -> R.string.media_video
        MediaKind.Round -> R.string.media_round
        MediaKind.Voice -> R.string.media_voice
        MediaKind.Music -> R.string.media_music
        MediaKind.Gif -> R.string.media_gif
        MediaKind.Sticker -> R.string.media_sticker
        MediaKind.File -> R.string.media_file
        MediaKind.Location -> R.string.media_location
        MediaKind.Contact -> R.string.media_contact
        MediaKind.Poll -> R.string.media_poll
        MediaKind.Link -> R.string.media_link
        MediaKind.Other -> R.string.media_other
    },
)

@Composable
private fun serviceLabel(kind: ServiceKind, peer: PeerKind, added: List<String>): String {
    if (kind == ServiceKind.Added) {
        val you = stringResource(R.string.chats_you_object)
        val names = added.map { it.ifEmpty { you } }
        return when (names.size) {
            0 -> stringResource(R.string.service_added_members)
            1 -> stringResource(R.string.service_added, names[0])
            2 -> stringResource(R.string.service_added_two, names[0], names[1])
            else -> pluralStringResource(R.plurals.service_added_more, names.size - 2, names[0], names[1], names.size - 2)
        }
    }
    return stringResource(
        when (kind) {
            ServiceKind.Added -> R.string.service_added_members
            ServiceKind.Created -> if (peer == PeerKind.Channel) R.string.service_channel_created else R.string.service_created
            ServiceKind.Joined -> R.string.service_joined
            ServiceKind.Left -> R.string.service_left
            ServiceKind.Pinned -> R.string.service_pinned
            ServiceKind.TitleChanged -> R.string.service_title
            ServiceKind.PhotoChanged -> R.string.service_photo
            ServiceKind.Call -> R.string.service_call
            ServiceKind.Gift -> R.string.service_gift
            ServiceKind.Wallpaper -> R.string.service_wallpaper
            ServiceKind.Proximity -> R.string.service_proximity
            ServiceKind.Other -> R.string.service_other
        },
    )
}
