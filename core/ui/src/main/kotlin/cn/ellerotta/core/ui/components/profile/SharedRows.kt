package cn.ellerotta.core.ui.components.profile

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.cover
import cn.ellerotta.core.model.links
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.MEDIA_SCRIM
import cn.ellerotta.core.ui.components.chat.MediaTile
import cn.ellerotta.core.ui.components.chat.RoundVideo
import cn.ellerotta.core.ui.components.chat.RoundVideoState
import cn.ellerotta.core.ui.components.chat.StillImage
import cn.ellerotta.core.ui.components.chat.VoiceWaveform
import cn.ellerotta.core.ui.components.chat.voiceTime
import cn.ellerotta.core.ui.components.chats.DialogAvatar
import cn.ellerotta.core.ui.components.music.MusicCover
import cn.ellerotta.core.ui.theme.GRAPHIC_CONTRAST
import cn.ellerotta.core.ui.theme.fadedOver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun rowShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 20.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

internal class SharedRowContext(
    val downloads: Map<String, DownloadState>,
    val covers: Map<String, ByteArray>,
    val music: MusicState?,
    val dialogKey: String,
    val locale: Locale,
    val onDownload: (MediaItem) -> Unit,
    val onMenu: (MessageItem) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowShell(
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp)) {
        Box(
            Modifier
                .combinedClickable(
                    role = Role.Button,
                    onLongClick = onLongClick?.let { menu ->
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu()
                        }
                    },
                    onClick = onClick,
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}

internal fun dateText(date: Int, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(Instant.ofEpochSecond(date.toLong()).atZone(zone))

@Composable
private fun Subtitle(text: String, modifier: Modifier = Modifier, underline: Boolean = false, maxLines: Int = 1) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (underline) TextDecoration.Underline else null,
        modifier = modifier,
    )
}

@Composable
internal fun FileRow(message: MessageItem, shape: RoundedCornerShape, context: SharedRowContext, onOpen: () -> Unit) {
    val media = message.media ?: return
    val state = context.downloads[media.key]
    val size = media.size?.let { Formatter.formatShortFileSize(LocalContext.current, it) }
    RowShell(shape, onOpen, { context.onMenu(message) }, Modifier.testTag("sharedFile_${message.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_article), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(media.fileName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Subtitle(listOfNotNull(size, dateText(message.date, context.locale)).joinToString(" · "))
            }
            if (state is DownloadState.Progress) {
                val total = state.total
                if (total != null && total > 0) {
                    CircularWavyProgressIndicator(progress = { state.downloaded.toFloat() / total }, modifier = Modifier.size(32.dp))
                } else {
                    CircularWavyProgressIndicator(Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
internal fun LinkRow(message: MessageItem, shape: RoundedCornerShape, context: SharedRowContext, onOpenLink: (String) -> Unit) {
    val urls = message.links()
    val first = urls.firstOrNull() ?: return
    val preview = message.link
    val title = preview?.title ?: preview?.siteName ?: Uri.parse(cn.ellerotta.core.model.openableUrl(first)).host ?: first
    RowShell(shape, { onOpenLink(first) }, { context.onMenu(message) }, Modifier.testTag("sharedLink_${message.id}")) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            val photo = preview?.photo
            Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                if (photo != null) {
                    val side = with(LocalDensity.current) { 48.dp.roundToPx() }
                    val small = remember(photo, side) { photo.preview(side) }
                    LaunchedEffect(small?.key) { if (small != null && context.downloads[small.key] == null) context.onDownload(small) }
                    StillImage(photo, small?.let { context.downloads[it.key] })
                } else {
                    Icon(painterResource(R.drawable.ic_link), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                preview?.description?.let { Subtitle(it, maxLines = 2) }
                urls.take(MAX_URLS).forEachIndexed { index, url ->
                    Subtitle(url, Modifier.padding(top = 2.dp).then(if (index == 0) Modifier else Modifier.clickable(role = Role.Button) { onOpenLink(url) }), underline = true)
                }
                Subtitle(dateText(message.date, context.locale), Modifier.padding(top = 2.dp))
            }
        }
    }
}

private const val MAX_URLS = 3

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayToggle(playing: Boolean, buffering: Boolean, onToggle: () -> Unit, tag: String) {
    FilledIconToggleButton(
        checked = playing,
        onCheckedChange = { onToggle() },
        shapes = IconButtonDefaults.toggleableShapes(),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.size(48.dp).testTag(tag),
    ) {
        if (playing && buffering) {
            LoadingIndicator(Modifier.size(32.dp), color = LocalContentColor.current)
        } else {
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                stringResource(if (playing) R.string.music_pause else R.string.music_play),
            )
        }
    }
}

@Composable
internal fun MusicRow(message: MessageItem, shape: RoundedCornerShape, context: SharedRowContext, onPlay: () -> Unit) {
    val media = message.media ?: return
    val current = context.music?.takeIf { it.isTrack(context.dialogKey, message.id) }
    val playing = current?.playing == true
    val cover = media.cover()?.let { context.covers[it.key] } ?: media.thumb
    val length = voiceTime((current?.lengthMs ?: (media.duration * 1000L)))
    RowShell(shape, onPlay, { context.onMenu(message) }, Modifier.testTag("sharedMusic_${message.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MusicCover(cover, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    media.title?.takeIf { it.isNotBlank() } ?: media.fileName.substringBeforeLast('.').ifEmpty { media.fileName },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("sharedMusicTitle"),
                )
                Subtitle(listOf(media.performer?.takeIf { it.isNotBlank() } ?: stringResource(R.string.music_unknown_performer), length, dateText(message.date, context.locale)).joinToString(" · "))
            }
            PlayToggle(playing, current?.buffering == true, onPlay, "sharedMusicToggle_${message.id}")
        }
    }
}

@Composable
internal fun VoiceRow(message: MessageItem, shape: RoundedCornerShape, context: SharedRowContext, onPlay: () -> Unit) {
    val media = message.media ?: return
    val current = context.music?.takeIf { it.isTrack(context.dialogKey, message.id) }
    val total = current?.lengthMs ?: (media.duration * 1000L)
    val progress = if (current != null && total > 0) (current.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    val container = MaterialTheme.colorScheme.surfaceContainer
    val time = if (current != null) stringResource(R.string.chat_voice_progress, voiceTime(current.positionMs), voiceTime(total)) else voiceTime(total)
    RowShell(shape, onPlay, { context.onMenu(message) }, Modifier.testTag("sharedVoice_${message.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayToggle(current?.playing == true, current?.buffering == true, onPlay, "sharedVoiceToggle_${message.id}")
            Column(Modifier.weight(1f)) {
                VoiceWaveform(
                    media.waveform,
                    progress,
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.onSurface.fadedOver(container, 0.38f, GRAPHIC_CONTRAST),
                    Modifier.size(width = 150.dp, height = 28.dp),
                )
                Subtitle(listOfNotNull(time, message.senderName, dateText(message.date, context.locale)).joinToString(" · "), Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
internal fun RoundRow(message: MessageItem, shape: RoundedCornerShape, context: SharedRowContext) {
    val media = message.media ?: return
    val state = remember(message.id) { RoundVideoState() }
    RowShell(shape, state::toggle, { context.onMenu(message) }, Modifier.testTag("sharedRound_${message.id}")) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MediaTile(media, context.downloads, context.onDownload, onOpen = state::toggle, side = 48.dp, modifier = Modifier.size(48.dp).clip(CircleShape))
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = MEDIA_SCRIM)) {
                        Icon(
                            painterResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                            stringResource(if (state.playing) R.string.shared_pause_round else R.string.shared_play_round),
                            Modifier.padding(6.dp).size(20.dp),
                            tint = Color.White,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.media_round), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Subtitle(listOfNotNull(voiceTime(media.duration * 1000L), message.senderName, dateText(message.date, context.locale)).joinToString(" · "))
                }
            }
            if (state.playing) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    RoundVideo(media, context.downloads, context.onDownload, state, Modifier.testTag("sharedRoundVideo_${message.id}"))
                }
            }
        }
    }
}

@Composable
internal fun GroupRow(chat: DialogItem, shape: RoundedCornerShape, avatars: Map<String, ByteArray>, onOpen: () -> Unit) {
    RowShell(shape, onOpen, null, Modifier.testTag("sharedGroup_${chat.key}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DialogAvatar(chat, avatars, size = 48)
            Column(Modifier.weight(1f)) {
                Text(chat.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chat.members > 0) {
                    val channel = chat.kind == PeerKind.Channel
                    Subtitle(pluralStringResource(if (channel) R.plurals.presence_subscribers else R.plurals.presence_members, chat.members, chat.members))
                }
            }
        }
    }
}
