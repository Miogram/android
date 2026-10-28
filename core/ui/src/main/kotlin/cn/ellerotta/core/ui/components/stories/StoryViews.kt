package cn.ellerotta.core.ui.components.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.model.StoryViewer
import cn.ellerotta.core.model.StoryViewersPage
import cn.ellerotta.core.model.StoryViews
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.InlineEmoji
import cn.ellerotta.core.ui.components.chat.PILL_ALPHA
import cn.ellerotta.core.ui.components.chat.ReactionGlyph
import cn.ellerotta.core.ui.components.chat.compactCount
import cn.ellerotta.core.ui.components.chat.messageTime
import cn.ellerotta.core.ui.components.chat.reactionLabel
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.ConfirmDialog
import cn.ellerotta.core.ui.components.common.TopBarColors
import cn.ellerotta.core.ui.components.common.TopBarPill
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val RECENT_SHOWN = 3

@Composable
fun StoryViewsBar(story: StoryItem, avatars: Map<String, ByteArray>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val views = story.views ?: StoryViews()
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = PILL_ALPHA))
            .clickable(onClickLabel = stringResource(R.string.stories_own_open_viewers), role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp)
            .testTag("storyViews"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val recent = views.recent.take(RECENT_SHOWN)
        if (recent.isNotEmpty()) {
            Box(Modifier.clearAndSetSemantics {}) {
                recent.forEachIndexed { index, key ->
                    Avatar(
                        "",
                        avatars[key],
                        size = 24,
                        seed = key.hashCode().toLong(),
                        modifier = Modifier.padding(start = (index * 16).dp).border(1.5.dp, Color.Black, CircleShape),
                    )
                }
            }
        }
        Icon(painterResource(R.drawable.ic_visibility), null, Modifier.size(20.dp), tint = Color.White)
        Text(
            pluralStringResource(R.plurals.stories_own_views, views.views, views.views),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.testTag("storyViewsCount"),
        )
        if (views.reactions > 0) {
            val reactions = pluralStringResource(R.plurals.stories_own_reactions, views.reactions, views.reactions)
            Row(
                Modifier.testTag("storyReactionsCount").clearAndSetSemantics { contentDescription = reactions },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(painterResource(R.drawable.ic_favorite), null, Modifier.size(20.dp), tint = Color.White)
                Text(compactCount(views.reactions), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
fun StoryOwnMenu(story: StoryItem, onEdit: () -> Unit, onPin: (Boolean) -> Unit, onDelete: () -> Unit, pause: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var open by remember(story.id) { mutableStateOf(false) }
    var asking by remember(story.id) { mutableStateOf(false) }
    fun close() {
        open = false
        if (!asking) pause(false)
    }
    Box(modifier) {
        TopBarPill(
            R.drawable.ic_more_vert,
            stringResource(R.string.stories_own_menu),
            {
                open = true
                pause(true)
            },
            Modifier.testTag("storyOwnMenu"),
            colors = TopBarColors.Media,
        )
        DropdownMenu(expanded = open, onDismissRequest = ::close) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stories_own_edit)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) },
                onClick = {
                    close()
                    onEdit()
                },
                modifier = Modifier.testTag("storyEdit"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (story.pinned) R.string.stories_own_unpin else R.string.stories_own_pin)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_push_pin), null) },
                onClick = {
                    close()
                    onPin(!story.pinned)
                },
                modifier = Modifier.testTag("storyPin"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stories_own_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    asking = true
                    close()
                },
                modifier = Modifier.testTag("storyDelete"),
            )
        }
    }
    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.stories_own_delete_title),
            text = stringResource(R.string.stories_own_delete_text),
            confirm = stringResource(R.string.stories_own_delete),
            tag = "storyDeleteConfirm",
            onConfirm = {
                asking = false
                pause(false)
                onDelete()
            },
            onDismiss = {
                asking = false
                pause(false)
            },
        )
    }
}

@Composable
fun StoryViewersSheet(
    page: StoryViewersPage?,
    avatars: Map<String, ByteArray>,
    loading: Boolean,
    onMore: () -> Unit,
    onOpenPeer: (String) -> Unit,
    onDismiss: () -> Unit,
    customEmoji: Map<Long, MediaItem> = emptyMap(),
    emojiFiles: Map<String, ByteArray> = emptyMap(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("storyViewersSheet")) {
        StoryViewersContent(page, avatars, loading, onMore, onOpenPeer, customEmoji, emojiFiles)
    }
}

@Composable
internal fun StoryViewersContent(
    page: StoryViewersPage?,
    avatars: Map<String, ByteArray>,
    loading: Boolean,
    onMore: () -> Unit,
    onOpenPeer: (String) -> Unit,
    customEmoji: Map<Long, MediaItem> = emptyMap(),
    emojiFiles: Map<String, ByteArray> = emptyMap(),
) {
    val emoji = remember(customEmoji, emojiFiles) { InlineEmoji(customEmoji, emojiFiles) }
    Column {
        val views = page?.views ?: 0
        val reactions = page?.reactions ?: 0
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                pluralStringResource(R.plurals.stories_own_views, views, views),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (reactions > 0) {
                Text(
                    pluralStringResource(R.plurals.stories_own_reactions, reactions, reactions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val viewers = page?.viewers.orEmpty()
        when {
            viewers.isEmpty() && loading -> Loading(Modifier.fillMaxWidth().padding(24.dp))
            viewers.isEmpty() -> Text(
                stringResource(if (views > 0) R.string.stories_own_viewers_unavailable else R.string.stories_own_no_views),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp).testTag("storyViewersEmpty"),
            )
            else -> {
                val list = rememberLazyListState()
                val next = page?.nextOffset
                LaunchedEffect(list, next, loading) {
                    if (next == null || loading) return@LaunchedEffect
                    snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
                        .collect { (last, total) -> if (last != null && last >= total - 3) onMore() }
                }
                LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().testTag("storyViewersList"), state = list) {
                    items(viewers) { viewer -> ViewerRow(viewer, avatars, emoji, onOpenPeer) }
                    if (loading) item(key = "loading") { Loading(Modifier.fillMaxWidth().padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ViewerRow(viewer: StoryViewer, avatars: Map<String, ByteArray>, emoji: InlineEmoji, onOpenPeer: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onOpenPeer(viewer.key) }
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("storyViewer_${viewer.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(viewer.name, viewer.avatarKey?.let(avatars::get), size = 40, seed = viewer.peerId)
        Column(Modifier.weight(1f)) {
            Text(viewer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (viewer.date > 0) {
                Text(viewedAt(viewer.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val reaction = viewer.reaction
        if (reaction != null) {
            val label = reactionLabel(reaction, emoji)
            Box(Modifier.testTag("storyViewerReaction_${viewer.key}").clearAndSetSemantics { contentDescription = label }) {
                ReactionGlyph(reaction, emoji, 24.dp)
            }
        }
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    val label = stringResource(R.string.stories_own_loading)
    Box(modifier, contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.semantics { contentDescription = label }.testTag("storyViewersLoading"))
    }
}

@Composable
private fun viewedAt(date: Int): String {
    val time = messageTime(date)
    val locale = LocalConfiguration.current.locales[0]
    val day = remember(date) { Instant.ofEpochSecond(date.toLong()).atZone(ZoneId.systemDefault()).toLocalDate() }
    return when (ChronoUnit.DAYS.between(day, LocalDate.now())) {
        0L -> stringResource(R.string.stories_own_viewed_today, time)
        1L -> stringResource(R.string.stories_own_viewed_yesterday, time)
        else -> stringResource(R.string.stories_own_viewed_date, day.format(DateTimeFormatter.ofPattern(if (locale.language == "en") "MMM d" else "d MMM", locale)), time)
    }
}
