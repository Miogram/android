package cn.ellerotta.core.ui.components.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.ProfileGifts
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.SharedTab
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.PagerPillTabRow
import cn.ellerotta.core.ui.components.common.PillTabRow

internal sealed interface ProfileTab {
    val key: String

    data object Gifts : ProfileTab {
        override val key = "Gifts"
    }

    data class Shared(val tab: SharedTab) : ProfileTab {
        override val key: String get() = tab.name
    }
}

internal fun profileTabs(gifts: ProfileGifts?, catalogue: Boolean, shared: List<SharedTab>): List<ProfileTab> {
    val page = gifts?.page
    val any = page != null && page.gifts.isNotEmpty()
    val tabs = shared.map { ProfileTab.Shared(it) }
    return when {
        any -> listOf(ProfileTab.Gifts) + tabs
        page != null && catalogue -> tabs + ProfileTab.Gifts
        else -> tabs
    }
}

@Composable
private fun ProfileTab.label(): String = when (this) {
    ProfileTab.Gifts -> stringResource(R.string.profile_gifts)
    is ProfileTab.Shared -> tab.label()
}

internal class ProfileScroll(private val list: LazyListState, private val page: () -> ScrollableState?) {
    val pages = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            return Offset(0f, -list.dispatchRawDelta(-available.y))
        }
    }

    val profile = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            val state = page() ?: return Offset.Zero
            return Offset(0f, -state.dispatchRawDelta(-available.y))
        }
    }
}

@Composable
internal fun ProfilePages(
    tabs: List<ProfileTab>,
    pagerState: PagerState,
    height: Dp,
    scroll: ProfileScroll,
    onTabClick: (Int) -> Unit,
    page: @Composable (ProfileTab) -> Unit,
) {
    val tabsLabel = stringResource(R.string.profile_tabs)
    Column(Modifier.fillMaxWidth().height(height)) {
        PagerPillTabRow(
            count = tabs.size,
            pagerState = pagerState,
            onTabClick = onTabClick,
            tabTag = { "profileTab_${tabs.getOrNull(it)?.key}" },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).semantics { contentDescription = tabsLabel }.testTag("profileTabs"),
        ) { index, label ->
            tabs.getOrNull(index)?.let { Text(it.label(), style = label.style, color = label.color, maxLines = 1) }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f).nestedScroll(scroll.pages).testTag("profilePages"),
            key = { tabs.getOrNull(it)?.key ?: it },
            verticalAlignment = Alignment.Top,
        ) { index -> tabs.getOrNull(index)?.let { page(it) } }
    }
}

@Composable
internal fun GiftsPage(
    gifts: ProfileGifts,
    stickers: Map<String, ByteArray>,
    callbacks: GiftCallbacks,
    state: LazyListState,
    contentPadding: PaddingValues,
    onShowCatalogue: () -> Unit,
    onOpen: (SavedGift) -> Unit,
) {
    val page = gifts.shown
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tile = (maxWidth - 48.dp) / 3
        LazyColumn(Modifier.fillMaxSize().testTag("profilePage_Gifts"), state = state, contentPadding = contentPadding) {
            if (gifts.collections.isNotEmpty()) {
                item(key = "collections") { GiftCollectionRow(gifts, stickers, callbacks.onLoadSticker, callbacks.onCollection) }
            }
            if (page != null) {
                items(page.gifts.chunked(3), key = { row -> row.first().key }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { gift -> GiftTile(gift, stickers, callbacks.onLoadSticker, tile, Modifier.weight(1f)) { onOpen(gift) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (page.nextOffset != null) {
                    item(key = "more") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = callbacks.onMore, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("giftsMore")) { Text(stringResource(R.string.profile_gifts_more)) }
                        }
                    }
                }
            } else {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.height(48.dp)) }
                }
            }
            callbacks.onOpenCatalogue?.let { open ->
                item(key = "catalogue") {
                    InfoCard(Modifier.padding(top = 12.dp)) {
                        InfoRow(
                            null,
                            stringResource(R.string.profile_gifts_catalogue),
                            onClick = {
                                open()
                                onShowCatalogue()
                            },
                            modifier = Modifier.testTag("giftCatalogue"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftCollectionRow(gifts: ProfileGifts, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, onCollection: (Int?) -> Unit) {
    val collections = gifts.collections
    val selected = gifts.collection?.let { id -> collections.indexOfFirst { it.id == id } + 1 }?.takeIf { it > 0 } ?: 0
    val position by animateFloatAsState(selected.toFloat(), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "collection")
    val label = stringResource(R.string.profile_gift_collections)
    PillTabRow(
        count = collections.size + 1,
        selected = selected,
        position = { position },
        onTabClick = { index -> onCollection(collections.getOrNull(index - 1)?.id) },
        tabTag = { index -> collections.getOrNull(index - 1)?.let { "giftCollection_${it.id}" } ?: "giftCollection_all" },
        modifier = Modifier.semantics { contentDescription = label }.testTag("giftCollections"),
        small = true,
    ) { index, style ->
        val collection = collections.getOrNull(index - 1)
        collection?.icon?.let { GiftSticker(it, stickers, onLoadSticker, 20.dp) }
        Text(collection?.title ?: stringResource(R.string.profile_gifts_all), style = style.style, color = style.color, maxLines = 1)
    }
}
