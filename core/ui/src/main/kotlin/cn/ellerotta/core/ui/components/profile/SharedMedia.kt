package cn.ellerotta.core.ui.components.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.SharedList
import cn.ellerotta.core.model.SharedMedia
import cn.ellerotta.core.model.SharedTab
import cn.ellerotta.core.model.byMonth
import cn.ellerotta.core.model.links
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.MediaTile
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class SharedMediaActions(
    val onTab: (SharedTab) -> Unit,
    val onMore: (SharedTab) -> Unit,
    val onDownload: (MediaItem) -> Unit,
    val onOpenMedia: (MessageItem, gif: Boolean) -> Unit,
    val onPlay: (MessageItem) -> Unit,
    val onOpenFile: (MessageItem) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onOpenChat: (DialogItem) -> Unit,
    val onGoTo: (MessageItem) -> Unit,
    val onForward: (MessageItem, DialogItem, dropAuthor: Boolean, dropCaptions: Boolean) -> Unit,
    val onDelete: (MessageItem, revoke: Boolean) -> Unit,
)

private const val SHARED_PREFIX = "shared"
private const val COLUMNS = 3

@Composable
internal fun SharedTab.label(): String = stringResource(
    when (this) {
        SharedTab.Media -> R.string.search_filter_media
        SharedTab.Files -> R.string.search_filter_files
        SharedTab.Links -> R.string.search_filter_links
        SharedTab.Music -> R.string.search_filter_music
        SharedTab.Voice -> R.string.search_filter_voice
        SharedTab.Gifs -> R.string.shared_gifs
        SharedTab.Groups -> R.string.shared_groups
    },
)

@Composable
internal fun SharedPage(
    tab: SharedTab,
    shared: SharedMedia,
    state: LazyListState,
    contentPadding: PaddingValues,
    dialogKey: String,
    actions: SharedMediaActions,
    downloads: Map<String, DownloadState>,
    covers: Map<String, ByteArray>,
    music: MusicState?,
    avatars: Map<String, ByteArray>,
    onMenu: (MessageItem) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(Modifier.fillMaxSize().testTag("profilePage_${tab.name}"), state = state, contentPadding = contentPadding) {
        sharedMediaItems(tab, shared, dialogKey, actions, downloads, covers, music, avatars, locale, onMenu)
    }
}

internal fun LazyListScope.sharedMediaItems(
    tab: SharedTab,
    shared: SharedMedia,
    dialogKey: String,
    actions: SharedMediaActions,
    downloads: Map<String, DownloadState>,
    covers: Map<String, ByteArray>,
    music: MusicState?,
    avatars: Map<String, ByteArray>,
    locale: Locale,
    onMenu: (MessageItem) -> Unit,
) {
    if (tab == SharedTab.Groups) {
        val chats = shared.common
        itemsIndexed(chats, key = { _, chat -> "${SHARED_PREFIX}_group_${chat.key}" }) { index, chat ->
            GroupRow(chat, rowShape(index, chats.size), avatars) { actions.onOpenChat(chat) }
        }
        sharedFooter(tab, loading = shared.commonLoading, complete = shared.commonComplete, failed = false, empty = chats.isEmpty(), actions)
        return
    }
    val list = shared.list(tab)
    val context = SharedRowContext(downloads, covers, music, dialogKey, locale, actions.onDownload, onMenu)
    when (tab) {
        SharedTab.Media, SharedTab.Gifs -> mediaGrid(tab, list, locale, context, actions, onMenu)
        else -> {
            val rows = if (tab == SharedTab.Links) list.items.filter { it.links().isNotEmpty() } else list.items
            itemsIndexed(rows, key = { _, message -> "${SHARED_PREFIX}_${tab.name}_${message.id}" }) { index, message ->
                val shape = rowShape(index, rows.size)
                when (tab) {
                    SharedTab.Files -> FileRow(message, shape, context) { actions.onOpenFile(message) }
                    SharedTab.Links -> LinkRow(message, shape, context, actions.onOpenLink)
                    SharedTab.Music -> MusicRow(message, shape, context) { actions.onPlay(message) }
                    SharedTab.Voice -> if (message.media?.kind == MediaKind.Round) RoundRow(message, shape, context) else VoiceRow(message, shape, context) { actions.onPlay(message) }
                    else -> Unit
                }
            }
        }
    }
    sharedFooter(tab, list.loading, list.complete, list.failed, list.items.isEmpty(), actions)
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.mediaGrid(tab: SharedTab, list: SharedList, locale: Locale, context: SharedRowContext, actions: SharedMediaActions, onMenu: (MessageItem) -> Unit) {
    val now = YearMonth.now()
    val formatter = DateTimeFormatter.ofPattern("LLLL", locale)
    val withYear = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    list.items.filter { it.media != null }.byMonth().forEach { (month, messages) ->
        item(key = "${SHARED_PREFIX}_${tab.name}_month_$month") {
            Text(
                month.format(if (month.year == now.year) formatter else withYear),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() }.testTag("sharedMonth"),
            )
        }
        messages.chunked(COLUMNS).forEach { cells ->
            item(key = "${SHARED_PREFIX}_${tab.name}_row_${cells.first().id}") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    cells.forEach { message ->
                        val media = message.media ?: return@forEach
                        MediaTile(
                            media = media,
                            downloads = context.downloads,
                            onDownload = context.onDownload,
                            onOpen = { actions.onOpenMedia(message, tab == SharedTab.Gifs) },
                            side = 128.dp,
                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                            onLongPress = { onMenu(message) },
                        )
                    }
                    repeat(COLUMNS - cells.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun LazyListScope.sharedFooter(tab: SharedTab, loading: Boolean, complete: Boolean, failed: Boolean, empty: Boolean, actions: SharedMediaActions) {
    if (failed) {
        item(key = "${SHARED_PREFIX}_failed") {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.shared_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { actions.onMore(tab) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp).testTag("sharedRetry")) {
                    Text(stringResource(R.string.shared_retry))
                }
            }
        }
        return
    }
    if (loading || !complete) {
        item(key = "${SHARED_PREFIX}_more") {
            LaunchedEffect(tab, empty, loading) { if (!loading) actions.onMore(tab) }
            Box(Modifier.fillMaxWidth().padding(vertical = if (empty) 32.dp else 16.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.height(48.dp).testTag("sharedLoading"))
            }
        }
    }
}
