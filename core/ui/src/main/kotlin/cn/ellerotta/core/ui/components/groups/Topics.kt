package cn.ellerotta.core.ui.components.groups

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.StickerView
import cn.ellerotta.core.ui.components.chats.PreviewLine
import cn.ellerotta.core.ui.components.chats.UnreadBadge
import cn.ellerotta.core.ui.components.chats.formatChatDate
import cn.ellerotta.core.ui.components.chats.previewText
import cn.ellerotta.core.ui.components.common.EmptyState
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState
import cn.ellerotta.core.ui.theme.legibleOn

@Composable
fun TopicsScreen(
    title: String,
    topics: List<ForumTopic>,
    emoji: Map<Long, MediaItem>,
    files: Map<String, ByteArray>,
    more: Boolean,
    loaded: Boolean,
    busy: Boolean,
    canCreate: Boolean,
    onBack: () -> Unit,
    onOpenTopic: (ForumTopic) -> Unit,
    onMore: () -> Unit,
    onCreate: (String, Int) -> Unit,
    onOpenProfile: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val list = rememberLazyListState()
    LaunchedEffect(list, more) {
        if (!more) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - MORE_AHEAD) onMore() }
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Page(
        title,
        rememberTopBarState(list),
        onBack = onBack,
        floating = {
            if (canCreate) {
                val expanded by remember { derivedStateOf { !list.canScrollBackward || list.lastScrolledBackward } }
                val label = stringResource(R.string.topics_create)
                MediumExtendedFloatingActionButton(
                    text = { Text(label) },
                    icon = { Icon(painterResource(R.drawable.ic_add), if (expanded) null else label) },
                    onClick = { creating = true },
                    expanded = expanded,
                    modifier = Modifier.testTag("createTopic"),
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("topicList"), state = list, contentPadding = padding) {
            largeTitle(title, onClick = onOpenProfile)
            item(key = "busy") { if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            if (loaded && topics.isEmpty()) {
                item(key = "empty") {
                    EmptyState(R.drawable.ic_mark_chat_read, stringResource(R.string.topics_empty), "noTopics")
                }
            }
            items(topics.filter { !it.hidden }, key = { it.id }) { topic ->
                val icon = topic.iconEmojiId?.let { emoji[it] }
                TopicRow(
                    topic = topic,
                    icon = icon,
                    iconFile = icon?.let { files[it.key] },
                    date = formatChatDate(topic.date, now, locale, DateFormat.is24HourFormat(context)),
                    onClick = { onOpenTopic(topic) },
                )
            }
            item(key = "end") { Spacer(Modifier.height(112.dp)) }
        }
    }
    if (creating) {
        CreateTopicDialog(
            onDismiss = { creating = false },
            onCreate = { name, color ->
                creating = false
                onCreate(name, color)
            },
        )
    }
}

@Composable
private fun TopicRow(topic: ForumTopic, icon: MediaItem?, iconFile: ByteArray?, date: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("topic_${topic.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopicIcon(topic, icon, iconFile, size = 40)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        topic.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (topic.closed) {
                        Icon(painterResource(R.drawable.ic_lock), stringResource(R.string.topics_closed), Modifier.padding(start = 4.dp).size(16.dp).testTag("closed_${topic.id}"), tint = colors.outline)
                    }
                }
                Text(date, style = MaterialTheme.typography.labelMedium, color = if (topic.unread > 0) colors.primary else colors.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewLine(previewText(topic.preview, PeerKind.Group), Modifier.weight(1f), seed = topic.id)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                    UnreadBadge(topic.mentions, muted = false, text = "@")
                    UnreadBadge(topic.unread, muted = false, modifier = Modifier.testTag("topicUnread_${topic.id}"))
                    if (topic.pinned && topic.unread == 0 && topic.mentions == 0) {
                        Icon(painterResource(R.drawable.ic_push_pin), stringResource(R.string.chats_pinned), Modifier.size(18.dp).testTag("pinned_${topic.id}"), tint = colors.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun TopicIcon(topic: ForumTopic, icon: MediaItem?, iconFile: ByteArray?, size: Int, modifier: Modifier = Modifier) {
    if (icon != null && iconFile != null) {
        StickerView(icon, iconFile, modifier.testTag("topicEmoji_${topic.id}"), size = size.dp)
        return
    }
    val color = if (topic.general) MaterialTheme.colorScheme.outline else topicColor(topic.iconColor)
    val glyph = with(LocalDensity.current) { (size * 0.45f).dp.toSp() }
    Box(modifier.size(size.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(
            if (topic.general) "#" else topic.title.trim().take(1).uppercase(),
            color = legibleOn(color, topicContent),
            fontWeight = FontWeight.Bold,
            fontSize = glyph,
            lineHeight = glyph,
        )
    }
}

@Composable
internal fun CreateTopicDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableIntStateOf(ForumTopic.COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.topics_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_TITLE) },
                    label = { Text(stringResource(R.string.topics_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("topicName"),
                )
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ForumTopic.COLORS.forEachIndexed { index, option ->
                        val selected = option == color
                        val fill = topicColor(option)
                        val label = stringResource(topicColorNames[index])
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { color = option })
                                .semantics { contentDescription = label }
                                .testTag("topicColor_${Integer.toHexString(option)}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(fill, CircleShape)
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                            )
                            if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = legibleOn(fill, topicContent))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), color) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("confirmCreateTopic")) {
                Text(stringResource(R.string.topics_create_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun topicColor(rgb: Int) = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

private val topicContent = listOf(Color.White, Color.Black)

private val topicColorNames = listOf(
    R.string.topics_color_blue,
    R.string.topics_color_yellow,
    R.string.topics_color_violet,
    R.string.topics_color_green,
    R.string.topics_color_pink,
    R.string.topics_color_red,
)

private const val MORE_AHEAD = 5

private const val MAX_TITLE = 128
