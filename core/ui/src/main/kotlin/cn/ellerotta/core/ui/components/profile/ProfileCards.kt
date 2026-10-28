package cn.ellerotta.core.ui.components.profile

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import cn.ellerotta.core.model.ChannelPost
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.Preview
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.RichColors
import cn.ellerotta.core.ui.components.chat.buildRichText
import cn.ellerotta.core.ui.components.chat.dateEntityText
import cn.ellerotta.core.ui.components.chats.formatChatDate
import cn.ellerotta.core.ui.components.chats.previewText
import cn.ellerotta.core.ui.components.common.Avatar
import java.time.ZoneId

@Composable
internal fun ProfileCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(ProfileCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .profileCardOutline(),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileField(
    label: String?,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null || onLongClick != null) Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick) else Modifier.semantics(mergeDescendants = true) {})
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (label != null) Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
internal fun ProfileFieldDivider() = HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

@Composable
internal fun ProfileSectionLabel(start: String, end: String?, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(start.uppercase(locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (end != null) Text(end, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("channelSubscribers"))
    }
}

@Composable
internal fun PersonalChannelSection(channel: PersonalChannel, avatars: Map<String, ByteArray>, nowMillis: Long, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier) {
        ProfileSectionLabel(
            stringResource(R.string.profile_channel),
            channel.subscribers?.let { pluralStringResource(R.plurals.presence_subscribers, it, it) },
        )
        ProfileCard(Modifier.testTag("personalChannel")) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .then(if (onClick != null) Modifier.clickable(onClickLabel = stringResource(R.string.profile_open_channel), onClick = onClick) else Modifier)
                    .padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(channel.title, channel.avatarKey?.let { avatars[it] }, size = 64, seed = channel.key.removePrefix("ch").toLongOrNull() ?: 0)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ChannelTitleRow(channel, nowMillis)
                    channel.post?.let { ChannelPostText(it, nowMillis) }
                }
            }
        }
    }
}

@Composable
private fun ChannelTitleRow(channel: PersonalChannel, nowMillis: Long) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                channel.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).testTag("channelTitle"),
            )
            if (channel.verified) Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.chats_verified), Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        channel.post?.let {
            Text(
                formatChatDate(it.date, nowMillis, locale, DateFormat.is24HourFormat(context)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp).testTag("channelPostDate"),
            )
        }
    }
}

@Composable
private fun ChannelPostText(post: ChannelPost, nowMillis: Long) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val text = if (post.text.isBlank()) {
        previewText(Preview(media = post.media), PeerKind.Channel)
    } else {
        remember(post, colors.primary, colors.surfaceContainerHighest, locale) {
            val zone = ZoneId.systemDefault()
            val rich = buildRichText(post.text, post.entities, RichColors(colors.primary, colors.surfaceContainerHighest), revealed = false, dateText = { dateEntityText(it, nowMillis / 1000, locale, zone) }, onTap = {})
            AnnotatedString(rich.text.text, rich.text.spanStyles)
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("channelPost"))
}
