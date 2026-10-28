package cn.ellerotta.core.ui.components.chats

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MessageHit
import cn.ellerotta.core.model.SearchFilter
import cn.ellerotta.core.ui.R

data class GlobalSearchState(
    val hits: List<MessageHit> = emptyList(),
    val hitsLoading: Boolean = false,
    val recent: List<String> = emptyList(),
    val topPeers: List<DialogItem> = emptyList(),
)

data class GlobalSearchActions(
    val onFilter: (SearchFilter) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpenHit: (MessageHit) -> Unit = {},
    val onOpened: (String) -> Unit = {},
    val onRemoveRecent: (String) -> Unit = {},
    val onClearRecent: () -> Unit = {},
)

internal val searchFilters = listOf(SearchFilter.Media, SearchFilter.Links, SearchFilter.Files, SearchFilter.Music, SearchFilter.Voice)

@StringRes
internal fun SearchFilter.label(): Int = when (this) {
    SearchFilter.All -> R.string.chats_all
    SearchFilter.Media -> R.string.search_filter_media
    SearchFilter.Links -> R.string.search_filter_links
    SearchFilter.Files -> R.string.search_filter_files
    SearchFilter.Music -> R.string.search_filter_music
    SearchFilter.Voice -> R.string.search_filter_voice
}

internal fun LazyListScope.searchFilterRow(filter: SearchFilter, onFilter: (SearchFilter) -> Unit) {
    item(key = "searchFilters") {
        val label = stringResource(R.string.chats_search_filters)
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { contentDescription = label }
                .testTag("searchFilters"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            searchFilters.forEach { kind ->
                val selected = filter == kind
                FilterChip(
                    selected = selected,
                    onClick = { onFilter(if (selected) SearchFilter.All else kind) },
                    label = { Text(stringResource(kind.label()), style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.ic_check), null) }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("searchFilter_${kind.name}"),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp).semantics { heading() },
    )
}

internal fun LazyListScope.topPeersRow(peers: List<DialogItem>, avatars: Map<String, ByteArray>, onOpen: (DialogItem) -> Unit) {
    if (peers.isEmpty()) return
    item(key = "topPeers") {
        Column(Modifier.testTag("topPeers")) {
            SectionTitle(stringResource(R.string.chats_search_people))
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                items(peers, key = { it.key }) { peer ->
                    Column(
                        Modifier
                            .width(80.dp)
                            .clickable { onOpen(peer) }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("topPeer_${peer.key}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DialogAvatar(peer, avatars, size = 56)
                        Text(
                            dialogTitle(peer),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.recentSearches(recent: List<String>, onSearch: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) return
    item(key = "recentTitle") {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.chats_search_recent), Modifier.weight(1f))
            TextButton(onClick = onClear, modifier = Modifier.testTag("clearRecent"), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.chats_search_recent_clear)) }
        }
    }
    items(recent, key = { "recent_$it" }) { query ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onSearch(query) }
                .padding(start = 20.dp)
                .testTag("recent_$query"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(query, Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { onRemove(query) }, modifier = Modifier.testTag("removeRecent_$query"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.chats_search_recent_remove, query))
            }
        }
    }
}

internal fun LazyListScope.messageHits(hits: List<MessageHit>, avatars: Map<String, ByteArray>, now: Long, onOpen: (MessageHit) -> Unit) {
    if (hits.isEmpty()) return
    item(key = "messagesTitle") {
        SectionTitle(stringResource(R.string.chats_search_messages), Modifier.testTag("messagesTitle"))
    }
    items(hits, key = { "hit_${it.key}" }) { hit ->
        DialogComponent(hit.dialog, avatars, now, onClick = { onOpen(hit) }, onLongClick = {}, modifier = Modifier.animateItem(), tag = "hit_${hit.key}")
    }
}
