package cn.ellerotta.core.ui.components.chat

import android.graphics.Paint
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import cn.ellerotta.core.model.ChatStickerState
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.EmojiCatalog
import cn.ellerotta.core.model.EmojiCategory
import cn.ellerotta.core.model.EmojiCategoryGroup
import cn.ellerotta.core.model.EmojiGroup
import cn.ellerotta.core.model.GifResults
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.PickedEmoji
import cn.ellerotta.core.model.preview
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.LoopingVideo
import cn.ellerotta.core.ui.components.common.PlaybackSlots
import kotlin.math.roundToInt
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class PanelTab { Emoji, Stickers, Gifs }

@Composable
internal fun MediaPanel(
    tab: PanelTab,
    onTab: (PanelTab) -> Unit,
    state: ChatStickerState,
    stickers: Map<String, ByteArray>,
    customEmoji: Map<Long, MediaItem>,
    downloads: Map<String, DownloadState>,
    actions: ChatStickerActions,
    premium: Boolean,
    compact: Boolean,
    bottomInset: Dp,
    onEmoji: (PickedEmoji) -> Unit,
    onBackspace: () -> Unit,
    onDownload: (MediaItem) -> Unit,
    onSearchFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(initialPage = tab.ordinal) { PanelTab.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { onTab(PanelTab.entries[it]) }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().filter { it == PanelTab.Gifs.ordinal }.collect { actions.onOpenGifs() }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface, modifier = modifier.fillMaxWidth().testTag("stickerPanel")) {
        Column {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = !compact, key = { PanelTab.entries[it] }) { page ->
                when (PanelTab.entries[page]) {
                    PanelTab.Emoji -> EmojiPage(state, stickers, customEmoji, actions, premium, compact, onEmoji, onSearchFocus)
                    PanelTab.Stickers -> StickerPage(state, stickers, actions, compact, onSearchFocus)
                    PanelTab.Gifs -> GifPage(state, stickers, downloads, actions, compact, onDownload, onSearchFocus)
                }
            }
            if (!compact) {
                val shown = PanelTab.entries[pager.targetPage]
                TabBar(
                    shown,
                    onSelect = { scope.launch { pager.animateScrollToPage(it.ordinal) } },
                    onBackspace = onBackspace.takeIf { shown == PanelTab.Emoji },
                    modifier = Modifier.padding(bottom = bottomInset),
                )
            }
        }
    }
}

@Composable
private fun EmojiPage(
    state: ChatStickerState,
    stickers: Map<String, ByteArray>,
    customEmoji: Map<Long, MediaItem>,
    actions: ChatStickerActions,
    premium: Boolean,
    compact: Boolean,
    onEmoji: (PickedEmoji) -> Unit,
    onSearchFocus: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var categoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val category = state.emojiCategories.firstOrNull { it.title == categoryTitle }
    LaunchedEffect(Unit) { if (state.emojiSets == null) actions.onOpenEmoji() }
    val names = EmojiCategory.entries.associateWith { stringResource(CATEGORY_NAMES.getValue(it)) }
    val recentTitle = stringResource(R.string.chat_emoji_recent)
    val trendingTitle = stringResource(R.string.chat_emoji_trending)
    val shownQuery = category?.searchKey ?: query
    var reordering by rememberSaveable { mutableStateOf(false) }
    val content = remember(state.recentEmoji, state.emojiTones, state.emojiSets, state.setStickers, state.emojiSearch, state.featuredEmoji, customEmoji, shownQuery, names) {
        emojiContent(state, customEmoji, shownQuery, names, recentTitle, trendingTitle, category = category != null)
    }
    val pick = { emoji: PickedEmoji ->
        if (emoji.custom && !premium) Toast.makeText(context, R.string.chat_emoji_premium, Toast.LENGTH_SHORT).show() else onEmoji(emoji)
    }
    PanelPage(
        content,
        compact,
        search = { modifier ->
            Column(modifier) {
                EmojiSearchField(
                    query,
                    onQuery = {
                        query = it
                        categoryTitle = null
                        actions.onSearchEmoji(it)
                    },
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onFocus = onSearchFocus,
                )
                if (!compact && query.isEmpty()) {
                    CategoryRow(state.emojiCategories, categoryTitle, stickers, actions.onLoad) { chosen ->
                        categoryTitle = chosen?.title
                        if (chosen == null) actions.onSearchEmoji("") else actions.onSearchEmojiCategory(chosen)
                    }
                }
            }
        },
        columns = { width -> maxOf(MIN_EMOJI_COLUMNS, ((width - EMOJI_SIDE * 2 + EMOJI_GAP) / (EMOJI_CELL + EMOJI_GAP)).toInt()) },
        sidePadding = EMOJI_SIDE,
        stickers = stickers,
        onOpenSet = actions.onOpenTab,
        onLoad = actions.onLoad,
        onAddSet = { actions.onInstall(it, true) },
        onReadSet = { actions.onReadFeatured(listOf(it)) },
        onReorder = { reordering = true }.takeIf { state.emojiSets.orEmpty().size > 1 },
    ) { cell ->
        when (cell) {
            is Cell.Emoji -> {
                val tones = EmojiCatalog.tones[cell.base]
                EmojiCell(
                    cell.emoji,
                    "emoji_${cell.key}",
                    tones = tones?.let { listOf(cell.base) + it },
                    onTone = { toned ->
                        actions.onChooseTone(cell.base, toned)
                        pick(PickedEmoji(toned))
                    },
                ) { pick(PickedEmoji(cell.emoji)) }
            }
            is Cell.Custom -> CustomEmojiCell(cell, stickers, actions.onLoad) { pick(PickedEmoji(cell.alt, cell.documentId)) }
            else -> Unit
        }
    }
    if (reordering) {
        SetOrderSheet(state.emojiSets.orEmpty(), stickers, actions.onLoad, onOrder = { actions.onReorderSets(it, true) }, onDismiss = { reordering = false })
    }
}

@Composable
private fun StickerPage(state: ChatStickerState, stickers: Map<String, ByteArray>, actions: ChatStickerActions, compact: Boolean, onSearchFocus: (Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var categoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val category = state.stickerCategories.firstOrNull { it.title == categoryTitle }
    val faved = stringResource(R.string.stickers_faved)
    val recent = stringResource(R.string.stickers_recent)
    val trending = stringResource(R.string.chat_stickers_trending)
    val shownQuery = category?.searchKey ?: query
    var previewed by remember { mutableStateOf<MediaItem?>(null) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    val content = remember(state.panel, state.setStickers, state.stickerSearch, state.featured, shownQuery, faved, recent, trending) {
        stickerContent(state, shownQuery, faved, recent, trending)
    }
    PanelPage(
        content,
        compact,
        search = { modifier ->
            Column(modifier) {
                EmojiSearchField(
                    query,
                    onQuery = {
                        query = it
                        categoryTitle = null
                        actions.onSearchStickers(it)
                    },
                    placeholder = stringResource(R.string.chat_stickers_search),
                    tag = "stickerSearch",
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onFocus = onSearchFocus,
                )
                if (!compact && query.isEmpty()) {
                    CategoryRow(state.stickerCategories, categoryTitle, stickers, actions.onLoad) { chosen ->
                        categoryTitle = chosen?.title
                        if (chosen == null) actions.onSearchStickers("") else actions.onSearchStickerCategory(chosen)
                    }
                }
            }
        },
        columns = { width -> maxOf(MIN_STICKER_COLUMNS, ((width - STICKER_SIDE * 2 + STICKER_GAP) / (STICKER_CELL + STICKER_GAP)).toInt()) },
        sidePadding = STICKER_SIDE,
        stickers = stickers,
        onOpenSet = actions.onOpenTab,
        onLoad = actions.onLoad,
        onAddSet = { actions.onInstall(it, true) },
        onReadSet = { actions.onReadFeatured(listOf(it)) },
        onReorder = { reordering = true }.takeIf { state.panel?.sets.orEmpty().size > 1 },
    ) { cell ->
        if (cell is Cell.Sticker) StickerCell(cell, stickers, actions) { previewed = it }
    }
    previewed?.let { sticker ->
        val isFaved = state.panel?.faved.orEmpty().any { it.key == sticker.key }
        MediaPreviewDialog(
            sticker,
            stickers,
            emptyMap(),
            onSend = { actions.onSend(sticker) },
            onShowSet = { actions.onShowSet(sticker) },
            onDismiss = { previewed = null },
            faved = isFaved,
            onFave = { actions.onFave(sticker, !isFaved) },
        )
    }
    if (reordering) {
        SetOrderSheet(state.panel?.sets.orEmpty(), stickers, actions.onLoad, onOrder = { actions.onReorderSets(it, false) }, onDismiss = { reordering = false })
    }
}

@Composable
private fun GifPage(
    state: ChatStickerState,
    stickers: Map<String, ByteArray>,
    downloads: Map<String, DownloadState>,
    actions: ChatStickerActions,
    compact: Boolean,
    onDownload: (MediaItem) -> Unit,
    onSearchFocus: (Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val gifs = state.gifs
    var trendingChosen by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val categories = state.emojiCategories.filter { !it.greeting }
    var categoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categories.firstOrNull { it.title == categoryTitle }
    val trending = trendingChosen ?: (gifs.saved?.isEmpty() == true)
    val search = gifs.search?.takeIf { query.isNotBlank() || category != null }
    val shown: GifResults? = when {
        search != null -> search
        trending -> gifs.trending
        else -> gifs.saved?.let { GifResults("", it) }
    }
    val grid = rememberLazyGridState()
    var previewed by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(shown?.query, trending) { grid.scrollToItem(0) }
    LaunchedEffect(grid, shown?.gifs?.size) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .filter { last -> shown != null && (search != null || trending) && last >= grid.layoutInfo.totalItemsCount - GIF_PRELOAD }
            .collect { actions.onMoreGifs() }
    }
    Column(Modifier.fillMaxSize()) {
        if (!compact) {
            val packs = listOf(
                PackItem("recent", icon = R.drawable.ic_schedule, label = R.string.stickers_recent),
                PackItem("trending", icon = R.drawable.ic_trending_up, label = R.string.chat_gifs_trending),
            )
            PackBar(packs, if (search != null) -1 else if (trending) 1 else 0, emptyMap(), {}) { trendingChosen = it == 1 }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            EmojiSearchField(
                query,
                onQuery = {
                    query = it
                    categoryTitle = null
                    actions.onSearchGifs(it)
                },
                placeholder = stringResource(R.string.chat_gifs_search),
                tag = "gifSearch",
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                onFocus = onSearchFocus,
            )
            if (!compact && query.isEmpty()) {
                CategoryRow(categories, categoryTitle, stickers, actions.onLoad) { chosen ->
                    categoryTitle = chosen?.title
                    actions.onSearchGifs(chosen?.emoticons?.firstOrNull().orEmpty())
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val columns = maxOf(MIN_GIF_COLUMNS, (maxWidth / GIF_CELL).toInt())
            val side = (maxWidth - GIF_GAP * (columns - 1)) / columns
            when {
                shown == null || (shown.gifs.isEmpty() && shown.loading) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularWavyProgressIndicator() }
                shown.gifs.isEmpty() -> PanelEmpty(stringResource(R.string.chat_gifs_none))
                else -> LazyVerticalGrid(
                    GridCells.Fixed(columns),
                    Modifier.fillMaxSize().testTag("gifGrid"),
                    state = grid,
                    horizontalArrangement = Arrangement.spacedBy(GIF_GAP),
                    verticalArrangement = Arrangement.spacedBy(GIF_GAP),
                ) {
                    items(shown.gifs, key = { it.key }) { gif -> GifTile(gif, side, downloads, onDownload, onPreview = { previewed = gif }) { actions.onSendGif(gif) } }
                }
            }
        }
    }
    previewed?.let { gif ->
        val saved = state.gifs.saved?.any { it.key == gif.key } == true
        MediaPreviewDialog(
            gif,
            stickers,
            downloads,
            onSend = { actions.onSendGif(gif) },
            onShowSet = null,
            onDismiss = { previewed = null },
            onRemove = { actions.onRemoveGif(gif) }.takeIf { saved },
        )
    }
}

@Composable
private fun GifTile(gif: MediaItem, side: Dp, downloads: Map<String, DownloadState>, onDownload: (MediaItem) -> Unit, onPreview: () -> Unit, onSend: () -> Unit) {
    val px = with(LocalDensity.current) { side.roundToPx() }
    val preview = remember(gif, px) { gif.preview(px) }
    LaunchedEffect(gif.key) {
        if (preview != null && downloads[preview.key] == null) onDownload(preview)
        if (downloads[gif.key] == null) onDownload(gif)
    }
    val file = (downloads[gif.key] as? DownloadState.Done)?.file
    val description = stringResource(R.string.chat_gif)
    Box(
        Modifier
            .aspectRatio(1f)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onLongClickLabel = stringResource(R.string.chat_preview), onLongClick = onPreview, onClick = onSend)
            .semantics { contentDescription = description }
            .testTag("gif_${gif.key}"),
        contentAlignment = Alignment.Center,
    ) {
        StillImage(gif, preview?.let { downloads[it.key] })
        if (file != null) {
            var playing by remember(file) { mutableStateOf(false) }
            LaunchedEffect(file) {
                panelGifSlots.play(waiting = {}) {
                    playing = true
                    try {
                        awaitCancellation()
                    } finally {
                        playing = false
                    }
                }
            }
            if (playing) {
                val ratio = if (gif.width > 0 && gif.height > 0) gif.width.toFloat() / gif.height else 1f
                val size = if (ratio >= 1f) Modifier.requiredSize(side * ratio, side) else Modifier.requiredSize(side, side / ratio)
                LoopingVideo(file, size.testTag("gifVideo_${gif.key}"))
            }
        }
    }
}

private class PanelContent(val cells: List<Cell>, val packs: List<PackItem>, val packStarts: List<Int>, val searching: Boolean)

private sealed interface Cell {
    val key: String

    data class Header(override val key: String, val title: String, val setKey: String? = null, val addSet: String? = null, val readKey: String? = null) : Cell

    data class Emoji(override val key: String, val emoji: String, val base: String = EmojiCatalog.baseOf(emoji)) : Cell

    data class Custom(override val key: String, val documentId: Long, val alt: String, val media: MediaItem?) : Cell

    data class Sticker(override val key: String, val media: MediaItem, val section: String, val opensSet: Boolean = false) : Cell

    data class Loading(override val key: String) : Cell

    data class Empty(override val key: String, @param:StringRes val text: Int) : Cell
}

private class PackItem(
    val key: String,
    @param:DrawableRes val icon: Int? = null,
    @param:StringRes val label: Int? = null,
    val thumb: MediaItem? = null,
    val title: String? = null,
    val dot: Boolean = false,
)

private fun emojiContent(
    state: ChatStickerState,
    customEmoji: Map<Long, MediaItem>,
    query: String,
    names: Map<EmojiCategory, String>,
    recentTitle: String,
    trendingTitle: String,
    category: Boolean,
): PanelContent {
    val known = HashMap<Long, MediaItem>(customEmoji)
    state.setStickers.values.forEach { list -> list.forEach { if (it.documentId != 0L) known.putIfAbsent(it.documentId, it) } }
    fun toned(emoji: String) = state.emojiTones[emoji] ?: emoji
    if (query.isNotBlank()) {
        val found = state.emojiSearch?.takeIf { it.query.trim().equals(query.trim(), ignoreCase = true) }
        val cells = when {
            found == null -> listOf(Cell.Loading("loading"))
            found.emoji.isEmpty() && found.custom.isEmpty() -> listOf(Cell.Empty("none", R.string.chat_emoji_none))
            else -> {
                val emoji = found.emoji.map { Cell.Emoji("found/$it", toned(it), EmojiCatalog.baseOf(it)) }
                val custom = found.custom.filter { it.documentId != 0L }.distinctBy { it.documentId }.map { Cell.Custom("found/c${it.documentId}", it.documentId, it.emoji.orEmpty(), it) }
                if (category) custom + emoji else emoji + custom
            }
        }
        return PanelContent(cells, emptyList(), emptyList(), searching = true)
    }
    val cells = mutableListOf<Cell>()
    val packs = mutableListOf<PackItem>()
    val starts = mutableListOf<Int>()
    fun section(pack: PackItem, header: Cell.Header) {
        packs += pack
        starts += cells.size
        cells += header
    }
    if (state.recentEmoji.isNotEmpty()) {
        section(PackItem("recent", icon = R.drawable.ic_schedule, label = R.string.chat_emoji_recent), Cell.Header("h_recent", recentTitle))
        state.recentEmoji.forEach { picked ->
            cells += if (picked.custom) Cell.Custom("recent/c${picked.documentId}", picked.documentId, picked.emoji, known[picked.documentId]) else Cell.Emoji("recent/${picked.emoji}", picked.emoji)
        }
    }
    drawableEmoji.forEach { group ->
        val name = names.getValue(group.category)
        section(PackItem(group.category.name, icon = CATEGORY_ICONS.getValue(group.category), label = CATEGORY_NAMES.getValue(group.category)), Cell.Header("h_${group.category.name}", name))
        group.emoji.forEach { cells += Cell.Emoji("${group.category.name}/$it", toned(it), it) }
    }
    state.emojiSets.orEmpty().forEach { set ->
        section(PackItem(set.key, thumb = set.thumb, title = set.title), Cell.Header("h_${set.key}", set.title, set.key))
        val list = state.setStickers[set.key]
        if (list == null) {
            cells += Cell.Loading("loading_${set.key}")
        } else {
            list.filter { it.documentId != 0L }.forEach { cells += Cell.Custom("${set.key}/c${it.documentId}", it.documentId, it.emoji.orEmpty(), it) }
        }
    }
    val featured = state.featuredEmoji
    val trending = featured?.sets.orEmpty().filter { !it.info.installed && it.covers.isNotEmpty() }
    if (featured != null && trending.isNotEmpty()) {
        val unread = trending.any { it.info.key in featured.unread }
        section(PackItem("featuredEmoji", icon = R.drawable.ic_trending_up, label = R.string.chat_emoji_trending, dot = unread), Cell.Header("h_trending", trendingTitle))
        trending.forEach { set ->
            val key = set.info.key
            cells += Cell.Header("h_trending/$key", set.info.title, addSet = key, readKey = key)
            set.covers.filter { it.documentId != 0L }.take(FEATURED_EMOJI_COVERS).forEach { cells += Cell.Custom("trending/$key/c${it.documentId}", it.documentId, it.emoji.orEmpty(), it) }
        }
    }
    return PanelContent(cells, packs, starts, searching = false)
}

private fun stickerContent(state: ChatStickerState, query: String, favedTitle: String, recentTitle: String, trendingTitle: String): PanelContent {
    if (query.isNotBlank()) {
        val found = state.stickerSearch?.takeIf { it.query.trim().equals(query.trim(), ignoreCase = true) }?.stickers
        val cells = when {
            found == null -> listOf(Cell.Loading("loading"))
            found.isEmpty() -> listOf(Cell.Empty("none", R.string.chat_stickers_none))
            else -> found.map { Cell.Sticker("found/${it.key}", it, "found") }
        }
        return PanelContent(cells, emptyList(), emptyList(), searching = true)
    }
    val panel = state.panel ?: return PanelContent(listOf(Cell.Loading("loading")), emptyList(), emptyList(), searching = false)
    val cells = mutableListOf<Cell>()
    val packs = mutableListOf<PackItem>()
    val starts = mutableListOf<Int>()
    fun section(pack: PackItem, header: Cell.Header) {
        packs += pack
        starts += cells.size
        cells += header
    }
    if (panel.faved.isNotEmpty()) {
        section(PackItem("faved", icon = R.drawable.ic_star, label = R.string.stickers_faved), Cell.Header("h_faved", favedTitle))
        panel.faved.forEach { cells += Cell.Sticker("faved/${it.key}", it, "faved") }
    }
    if (panel.recent.isNotEmpty()) {
        section(PackItem("recent", icon = R.drawable.ic_schedule, label = R.string.stickers_recent), Cell.Header("h_recent", recentTitle))
        panel.recent.forEach { cells += Cell.Sticker("recent/${it.key}", it, "recent") }
    }
    panel.sets.forEach { set ->
        val list = state.setStickers[set.key]
        section(PackItem(set.key, thumb = set.thumb ?: list?.firstOrNull(), title = set.title), Cell.Header("h_${set.key}", set.title, set.key))
        if (list == null) cells += Cell.Loading("loading_${set.key}") else list.forEach { cells += Cell.Sticker("${set.key}/${it.key}", it, set.key) }
    }
    val featured = state.featured
    val trending = featured?.sets.orEmpty().filter { !it.info.installed && it.covers.isNotEmpty() }
    if (cells.isEmpty() && trending.isEmpty()) cells += Cell.Empty("none", R.string.stickers_empty)
    if (featured != null && trending.isNotEmpty()) {
        val unread = trending.any { it.info.key in featured.unread }
        section(PackItem("featuredStickers", icon = R.drawable.ic_trending_up, label = R.string.chat_stickers_trending, dot = unread), Cell.Header("h_trending", trendingTitle))
        trending.forEach { set ->
            val key = set.info.key
            cells += Cell.Header("h_trending/$key", set.info.title, addSet = key, readKey = key)
            set.covers.forEach { cells += Cell.Sticker("trending/$key/${it.key}", it, "trending", opensSet = true) }
        }
    }
    return PanelContent(cells, packs, starts, searching = false)
}

@Composable
private fun PanelPage(
    content: PanelContent,
    compact: Boolean,
    search: @Composable (Modifier) -> Unit,
    columns: (Dp) -> Int,
    sidePadding: Dp,
    stickers: Map<String, ByteArray>,
    onOpenSet: (String) -> Unit,
    onLoad: (MediaItem) -> Unit,
    onAddSet: (String) -> Unit,
    onReadSet: (String) -> Unit,
    onReorder: (() -> Unit)? = null,
    cell: @Composable (Cell) -> Unit,
) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(content.searching) { grid.scrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        if (!compact && content.packs.isNotEmpty()) {
            val selected = selectedPack(grid, content.packStarts)
            PackBar(content.packs, selected, stickers, onLoad, onReorder) { index -> scope.launch { grid.animateScrollToItem(content.packStarts[index]) } }
        }
        search(Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                GridCells.Fixed(columns(maxWidth)),
                Modifier.fillMaxSize().testTag("panelGrid"),
                state = grid,
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, bottom = 8.dp),
            ) {
                items(content.cells, key = { it.key }, span = { if (it is Cell.Header || it is Cell.Loading || it is Cell.Empty) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { item ->
                    when (item) {
                        is Cell.Header -> {
                            item.setKey?.let { key -> LaunchedEffect(key) { onOpenSet(key) } }
                            item.readKey?.let { key -> LaunchedEffect(key) { onReadSet(key) } }
                            SectionHeader(item.title, item.addSet?.let { set -> { onAddSet(set) } }, addTag = "addSet_${item.addSet}")
                        }
                        is Cell.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularWavyProgressIndicator(Modifier.size(32.dp)) }
                        is Cell.Empty -> PanelEmpty(stringResource(item.text))
                        else -> cell(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun selectedPack(grid: LazyGridState, starts: List<Int>): Int {
    val first = grid.firstVisibleItemIndex
    return starts.indexOfLast { it <= first }.coerceAtLeast(0)
}

@Composable
private fun SectionHeader(title: String, onAdd: (() -> Unit)? = null, addTag: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 6.dp).semantics { heading() },
        )
        if (onAdd != null) {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.padding(top = 6.dp).height(ADD_BUTTON).testTag(addTag), contentPadding = PaddingValues(horizontal = 14.dp), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.chat_panel_add_set), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CategoryRow(categories: List<EmojiCategoryGroup>, chosen: String?, stickers: Map<String, ByteArray>, onLoad: (MediaItem) -> Unit, onChoose: (EmojiCategoryGroup?) -> Unit) {
    if (categories.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 4.dp).height(CATEGORY_TARGET).testTag("categoryRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(categories, key = { _, category -> category.title }) { _, category ->
            val on = category.title == chosen
            val fill by animateColorAsState(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.motionScheme.fastEffectsSpec(), label = "category")
            Box(
                Modifier
                    .size(CATEGORY_TARGET)
                    .clip(CircleShape)
                    .clickable { onChoose(category.takeIf { !on }) }
                    .semantics {
                        contentDescription = category.title
                        selected = on
                        role = Role.Tab
                    }
                    .testTag("category_${category.title}"),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(CATEGORY_CELL).background(fill, CircleShape), contentAlignment = Alignment.Center) {
                    val icon = category.icon
                    if (icon != null) {
                        LaunchedEffect(icon.key) { if (stickers[icon.key] == null) onLoad(icon) }
                        StickerView(icon, stickers[icon.key], size = CATEGORY_ICON, preview = stickers.previewOf(icon))
                    } else {
                        Text(category.emoticons.firstOrNull().orEmpty(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PackBar(packs: List<PackItem>, current: Int, stickers: Map<String, ByteArray>, onLoad: (MediaItem) -> Unit, onReorder: (() -> Unit)? = null, onSelect: (Int) -> Unit) {
    val row = rememberLazyListState()
    LaunchedEffect(current, packs.size) {
        val visible = row.layoutInfo.visibleItemsInfo
        if (current >= 0 && visible.isNotEmpty() && (current <= visible.first().index || current >= visible.last().index)) {
            row.animateScrollToItem((current - 2).coerceAtLeast(0))
        }
    }
    LazyRow(
        Modifier.fillMaxWidth().height(PACK_BAR).testTag("packBar"),
        state = row,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(packs, key = { _, pack -> pack.key }) { index, pack ->
            val chosen = index == current
            val fill by animateColorAsState(if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.motionScheme.fastEffectsSpec(), label = "pack")
            val tint = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            val label = pack.label?.let { stringResource(it) } ?: pack.title.orEmpty()
            Box(
                Modifier
                    .size(PACK_CELL)
                    .clip(MaterialTheme.shapes.medium)
                    .background(fill)
                    .clickable { onSelect(index) }
                    .semantics {
                        contentDescription = label
                        selected = chosen
                        role = Role.Tab
                    }
                    .testTag("tab_${pack.key}"),
                contentAlignment = Alignment.Center,
            ) {
                val thumb = pack.thumb
                when {
                    pack.icon != null -> Icon(painterResource(pack.icon), null, Modifier.size(PACK_ICON), tint = tint)
                    thumb != null -> {
                        LaunchedEffect(thumb.key) { if (stickers[thumb.key] == null) onLoad(thumb) }
                        StickerView(thumb, stickers[thumb.key], size = PACK_THUMB, preview = stickers.previewOf(thumb))
                    }
                    else -> Text(label.take(1), style = MaterialTheme.typography.titleMedium, color = tint)
                }
                if (pack.dot) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(PACK_DOT).background(MaterialTheme.colorScheme.primary, CircleShape).testTag("dot_${pack.key}"))
            }
        }
        if (onReorder != null) {
            item(key = "reorder") {
                IconButton(onClick = onReorder, modifier = Modifier.testTag("reorderSets"), shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_list), stringResource(R.string.chat_sets_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TabBar(shown: PanelTab, onSelect: (PanelTab) -> Unit, onBackspace: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(TAB_BAR)) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelTab.entries.forEach { tab ->
                val chosen = tab == shown
                val fill by animateColorAsState(if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.motionScheme.fastEffectsSpec(), label = "tab")
                Box(
                    Modifier
                        .heightIn(min = TAB_BAR)
                        .clip(CircleShape)
                        .clickable { onSelect(tab) }
                        .semantics {
                            selected = chosen
                            role = Role.Tab
                        }
                        .testTag("panelTab_${tab.name}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(TAB_NAMES.getValue(tab)),
                        style = if (chosen) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        color = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.background(fill, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (onBackspace != null) {
            IconButton(onClick = onBackspace, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).testTag("panelBackspace"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.chat_panel_backspace), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(emoji: String, tag: String, tones: List<String>? = null, onTone: (String) -> Unit = {}, onClick: () -> Unit) {
    val size = with(LocalDensity.current) { EMOJI_GLYPH.toSp() }
    var toning by remember { mutableStateOf(false) }
    Box(
        Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.medium)
            .combinedClickable(onLongClick = tones?.let { { toning = true } }, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = size, maxLines = 1)
        if (toning && tones != null) {
            Popup(popupPositionProvider = AboveAnchor, onDismissRequest = { toning = false }, properties = PopupProperties(focusable = true)) {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.testTag("emojiTones")) {
                    Row(Modifier.padding(4.dp)) {
                        tones.forEachIndexed { index, toned ->
                            Box(
                                Modifier.size(EMOJI_CELL).clip(MaterialTheme.shapes.medium).clickable {
                                    toning = false
                                    onTone(toned)
                                }.testTag("emojiTone_$index"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(toned, fontSize = size, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun CustomEmojiCell(cell: Cell.Custom, stickers: Map<String, ByteArray>, onLoad: (MediaItem) -> Unit, onClick: () -> Unit) {
    val media = cell.media
    if (media != null) LaunchedEffect(media.key) { if (stickers[media.key] == null) onLoad(media) }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cell.alt }
            .testTag("customEmoji_${cell.key}"),
        contentAlignment = Alignment.Center,
    ) {
        if (media != null) {
            StickerView(media, stickers[media.key], size = EMOJI_PICTURE, preview = stickers.previewOf(media))
        } else {
            Text(cell.alt, fontSize = with(LocalDensity.current) { EMOJI_GLYPH.toSp() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(cell: Cell.Sticker, stickers: Map<String, ByteArray>, actions: ChatStickerActions, onPreview: (MediaItem) -> Unit) {
    val sticker = cell.media
    LaunchedEffect(sticker.key) { if (stickers[sticker.key] == null) actions.onLoad(sticker) }
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClickLabel = stringResource(if (cell.opensSet) R.string.chat_preview_set else R.string.chat_preview_send),
                onLongClickLabel = stringResource(R.string.chat_preview),
                onLongClick = { onPreview(sticker) },
                onClick = { if (cell.opensSet) actions.onShowSet(sticker) else actions.onSend(sticker) },
            )
            .semantics { sticker.emoji?.let { contentDescription = it } }
            .testTag("pick_${cell.section}_${sticker.key}"),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            StickerView(sticker, stickers[sticker.key], size = maxWidth, preview = stickers.previewOf(sticker))
        }
    }
}

@Composable
internal fun PanelArea(
    open: Boolean,
    compact: Boolean,
    panelHeight: Dp,
    hold: Int,
    onHeld: () -> Unit,
    onSize: (Int) -> Unit,
    panel: @Composable (bottomInset: Dp) -> Unit,
) {
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val nav = WindowInsets.navigationBars
    LaunchedEffect(hold) {
        if (hold <= 0) return@LaunchedEffect
        withTimeoutOrNull(KEYBOARD_WAIT_MS) { snapshotFlow { ime.getBottom(density) }.first { it >= hold } }
        onHeld()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val keyboard = ime.getBottom(this)
                val bar = nav.getBottom(this)
                val base = maxOf(keyboard, bar, hold)
                val height = when {
                    !open -> base
                    compact -> maxOf(keyboard, bar) + COMPACT_PANEL_HEIGHT.roundToPx()
                    else -> maxOf(base, panelHeight.roundToPx() + bar)
                }
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                layout(constraints.maxWidth, height) { placeable.place(0, 0) }
            }
            .onSizeChanged { onSize(it.height) },
    ) {
        if (open) {
            val inset = with(density) { nav.getBottom(density).toDp() }
            Box(if (compact) Modifier.fillMaxWidth().height(COMPACT_PANEL_HEIGHT) else Modifier.fillMaxSize()) { panel(if (compact) 0.dp else inset) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeyboardWatcher(onHeight: (Int) -> Unit, onRise: () -> Unit) {
    val density = LocalDensity.current
    val target = WindowInsets.imeAnimationTarget
    val nav = WindowInsets.navigationBars
    val height by rememberUpdatedState(onHeight)
    val rise by rememberUpdatedState(onRise)
    LaunchedEffect(density) {
        snapshotFlow { (target.getBottom(density) - nav.getBottom(density)).coerceAtLeast(0) }
            .distinctUntilChanged()
            .collect { above ->
                val dp = with(density) { above.toDp().value.roundToInt() }
                if (dp >= MIN_KEYBOARD_DP) height(dp)
            }
    }
    LaunchedEffect(density) {
        snapshotFlow { target.getBottom(density) > nav.getBottom(density) }
            .distinctUntilChanged()
            .drop(1)
            .filter { it }
            .collect { rise() }
    }
}

private val drawableEmoji: List<EmojiGroup> by lazy {
    val paint = Paint()
    val tells = paint.hasGlyph("\uD83D\uDE00")
    if (!tells) EmojiCatalog.groups else EmojiCatalog.groups.map { group -> group.copy(emoji = group.emoji.filter(paint::hasGlyph)) }
}

private val CATEGORY_NAMES = mapOf(
    EmojiCategory.People to R.string.chat_emoji_people,
    EmojiCategory.Animals to R.string.chat_emoji_animals,
    EmojiCategory.Food to R.string.chat_emoji_food,
    EmojiCategory.Activity to R.string.chat_emoji_activity,
    EmojiCategory.Travel to R.string.chat_emoji_travel,
    EmojiCategory.Objects to R.string.chat_emoji_objects,
    EmojiCategory.Symbols to R.string.chat_emoji_symbols,
    EmojiCategory.Flags to R.string.chat_emoji_flags,
)

private val CATEGORY_ICONS = mapOf(
    EmojiCategory.People to R.drawable.ic_mood,
    EmojiCategory.Animals to R.drawable.ic_emoji_nature,
    EmojiCategory.Food to R.drawable.ic_emoji_food_beverage,
    EmojiCategory.Activity to R.drawable.ic_emoji_events,
    EmojiCategory.Travel to R.drawable.ic_emoji_transportation,
    EmojiCategory.Objects to R.drawable.ic_emoji_objects,
    EmojiCategory.Symbols to R.drawable.ic_emoji_symbols,
    EmojiCategory.Flags to R.drawable.ic_emoji_flags,
)

private val TAB_NAMES = mapOf(
    PanelTab.Emoji to R.string.chat_panel_emoji,
    PanelTab.Stickers to R.string.chat_panel_stickers,
    PanelTab.Gifs to R.string.chat_panel_gifs,
)

internal val DEFAULT_PANEL_HEIGHT = 300.dp
internal val COMPACT_PANEL_HEIGHT = 170.dp

private val TAB_BAR = 48.dp
private val PACK_BAR = 48.dp
private val PACK_CELL = 40.dp
private val PACK_ICON = 24.dp
private val PACK_THUMB = 28.dp
private val PACK_DOT = 7.dp
private val CATEGORY_CELL = 40.dp
private val CATEGORY_TARGET = 48.dp
private val CATEGORY_ICON = 24.dp
private val ADD_BUTTON = 32.dp

private const val FEATURED_EMOJI_COVERS = 16
private val EMOJI_CELL = 40.dp
private val EMOJI_GAP = 8.dp
private val EMOJI_SIDE = 7.dp
private val EMOJI_GLYPH = 30.dp
private val EMOJI_PICTURE = 32.dp
private const val MIN_EMOJI_COLUMNS = 8
private val STICKER_CELL = 70.dp
private val STICKER_GAP = 12.dp
private val STICKER_SIDE = 10.dp
private const val MIN_STICKER_COLUMNS = 5
private val GIF_CELL = 120.dp
private val GIF_GAP = 2.dp
private const val MIN_GIF_COLUMNS = 3
private const val GIF_PRELOAD = 6

internal const val MAX_PLAYING_PANEL_GIFS = 6

internal val panelGifSlots = PlaybackSlots(MAX_PLAYING_PANEL_GIFS)

private const val KEYBOARD_WAIT_MS = 600L

private const val MIN_KEYBOARD_DP = 120
