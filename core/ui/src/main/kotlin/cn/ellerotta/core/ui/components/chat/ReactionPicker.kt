package cn.ellerotta.core.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.lottie.Lottie
import cn.ellerotta.core.lottie.LottieData
import cn.ellerotta.core.lottie.lottieAvailable
import cn.ellerotta.core.model.ChatReactionState
import cn.ellerotta.core.model.EmojiSearch
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StickerSetInfo
import cn.ellerotta.core.ui.R
import kotlin.math.abs
import kotlinx.coroutines.delay

internal class PickerEmoji(
    val sets: List<StickerSetInfo>? = null,
    val setEmoji: Map<String, List<MediaItem>> = emptyMap(),
    val search: EmojiSearch? = null,
)

internal class PickerActions(
    val onOpenEmoji: () -> Unit = {},
    val onOpenSet: (String) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onLoad: (MediaItem) -> Unit = {},
)

@Composable
internal fun ReactionPicker(
    state: ChatReactionState,
    chosen: Set<Reaction>,
    emoji: InlineEmoji,
    extra: PickerEmoji,
    actions: PickerActions,
    maxWidth: Dp,
    alignEnd: Boolean,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onPick: (Reaction, Rect?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = CompositionLocalProvider(LocalReactionArt provides state.art) {
    val slots = ((maxWidth - ROW_PADDING * 2) / ITEM).toInt().coerceIn(1, MAX_SLOTS)
    val more = state.allowed.size > slots || state.custom
    val row = if (more) state.allowed.take(slots - 1) else state.allowed
    val motion = MaterialTheme.motionScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .widthIn(max = maxWidth)
            .testTag("reactionPicker"),
    ) {
        AnimatedContent(
            expanded,
            contentAlignment = if (alignEnd) Alignment.BottomEnd else Alignment.BottomStart,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
            },
            label = "picker",
        ) { open ->
            if (open) {
                ReactionGrid(state, chosen, emoji, extra, actions, maxWidth, onPick)
            } else {
                Row(Modifier.height(PILL_HEIGHT).padding(horizontal = ROW_PADDING), verticalAlignment = Alignment.CenterVertically) {
                    val count = row.size + if (more) 1 else 0
                    row.forEachIndexed { index, reaction ->
                        key(reaction) {
                            PickerItem(reaction, reaction in chosen, emoji, actions, appearDelay(index, count, alignEnd), appear = true, onPick = onPick)
                        }
                    }
                    if (more) ExpandButton(appearDelay(row.size, count, alignEnd)) { onExpand(true) }
                }
            }
        }
    }
}

private fun appearDelay(index: Int, count: Int, alignEnd: Boolean): Long {
    val from = if (alignEnd) count - 1 - index else index
    return APPEAR_FIRST_MS + (APPEAR_SPREAD_MS * abs(from) / count.coerceAtLeast(1))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerItem(reaction: Reaction, picked: Boolean, emoji: InlineEmoji, actions: PickerActions, delayMs: Long, appear: Boolean, onPick: (Reaction, Rect?, Boolean) -> Unit, media: MediaItem? = null) {
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(if (appear) 0f else 1f) }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (scale.value >= 1f) return@LaunchedEffect
        delay(delayMs)
        scale.animateTo(1f, spatial)
    }
    val art = LocalReactionArt.current[reaction]
    LaunchedEffect(reaction) {
        listOfNotNull(art?.still, if (appear) art?.appear else null, art?.select, media).filter { emoji.files[it.key] == null }.forEach(actions.onLoad)
    }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var selects by remember { mutableIntStateOf(0) }
    LaunchedEffect(pressed) { if (pressed) selects++ }
    val description = stringResource(R.string.chat_react_with, media?.emoji ?: reactionLabel(reaction, emoji))
    Box(
        Modifier
            .size(ITEM)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = ITEM / 2),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onPick(reaction, bounds, false)
                },
                onLongClick = if (reaction is Reaction.Emoji) ({
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPick(reaction, bounds, true)
                }) else null,
            )
            .semantics {
                contentDescription = description
                selected = picked
            }
            .testTag("react_${reactionTag(reaction)}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(CIRCLE)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(if (picked) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val select = art?.select?.let { emoji.files[it.key] }
            val intro = art?.appear?.takeIf { appear }?.let { emoji.files[it.key] }
            when {
                media != null -> ReactionPicture(media, emoji.files, GLYPH) { ReactionGlyph(reaction, emoji, GLYPH) }
                selects > 0 && select != null -> key(selects) { PlayedOnce(select) { ReactionGlyph(reaction, emoji, GLYPH) } }
                intro != null -> PlayedOnce(intro) { ReactionGlyph(reaction, emoji, GLYPH) }
                else -> ReactionGlyph(reaction, emoji, GLYPH)
            }
        }
    }
}

@Composable
private fun PlayedOnce(bytes: ByteArray, still: @Composable () -> Unit) {
    val json = remember(bytes) { runCatching { if (LottieData.isGzip(bytes)) LottieData.fromTgs(bytes) else bytes }.getOrNull() }
    var framed by remember(bytes) { mutableStateOf(false) }
    if (!framed || json == null || !lottieAvailable) still()
    if (json != null && lottieAvailable) Lottie(json, Modifier.size(GLYPH), repeat = false, onFrame = { framed = true })
}

@Composable
private fun ExpandButton(delayMs: Long, onClick: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(delayMs)
        scale.animateTo(1f, spatial)
    }
    IconButton(onClick = onClick, modifier = Modifier.size(ITEM).testTag("moreReactions"), shapes = IconButtonDefaults.shapes()) {
        Box(
            Modifier.size(EXPAND_CIRCLE).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }.background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.chat_reactions_more), Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ReactionGrid(state: ChatReactionState, chosen: Set<Reaction>, emoji: InlineEmoji, extra: PickerEmoji, actions: PickerActions, width: Dp, onPick: (Reaction, Rect?, Boolean) -> Unit) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(state.custom) { if (state.custom && extra.sets == null) actions.onOpenEmoji() }
    Column(Modifier.width(width).height(PANEL_HEIGHT).testTag("reactionGrid")) {
        EmojiSearchField(
            query,
            onQuery = {
                query = it
                actions.onSearch(it)
            },
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
        )
        val cells = remember(state, extra, query) { gridCells(state, extra, query) }
        LazyVerticalGrid(GridCells.Adaptive(ITEM), Modifier.fillMaxWidth().padding(horizontal = 6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            items(cells, key = { it.key }, span = { if (it is GridCell.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { cell ->
                when (cell) {
                    is GridCell.Header -> {
                        cell.setKey?.let { key -> LaunchedEffect(key) { if (extra.setEmoji[key] == null) actions.onOpenSet(key) } }
                        Text(
                            cell.title ?: stringResource(R.string.chat_emoji_recent),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                    is GridCell.Item -> PickerItem(cell.reaction, cell.reaction in chosen, emoji, actions, 0, appear = false, onPick = onPick, media = cell.media)
                }
            }
        }
    }
}

private sealed interface GridCell {
    val key: String

    data class Header(val title: String?, val setKey: String?) : GridCell {
        override val key = "h${setKey ?: "recent"}"
    }

    data class Item(val reaction: Reaction, val media: MediaItem?, val section: String) : GridCell {
        override val key = "$section/${reactionTag(reaction)}"
    }
}

private fun gridCells(state: ChatReactionState, extra: PickerEmoji, query: String): List<GridCell> {
    val needle = query.trim().lowercase()
    if (needle.isNotEmpty()) {
        val found = extra.search?.takeIf { it.query.trim().lowercase() == needle }
        val reactions = state.allowed.filter { reaction ->
            val title = state.art[reaction]?.title?.lowercase().orEmpty()
            title.contains(needle) || (reaction is Reaction.Emoji && found?.emoji?.contains(reaction.emoji) == true)
        }
        val custom = if (state.custom) found?.custom.orEmpty().filter { it.documentId != 0L } else emptyList()
        return reactions.map { GridCell.Item(it, null, "found") } + custom.map { GridCell.Item(Reaction.CustomEmoji(it.documentId), it, "found") }
    }
    val cells = mutableListOf<GridCell>()
    state.allowed.mapTo(cells) { GridCell.Item(it, null, "all") }
    if (!state.custom) return cells
    val recent = state.recent.filterIsInstance<Reaction.CustomEmoji>().filter { it !in state.allowed }
    if (recent.isNotEmpty()) {
        cells += GridCell.Header(null, null)
        recent.mapTo(cells) { GridCell.Item(it, null, "recent") }
    }
    extra.sets.orEmpty().forEach { set ->
        cells += GridCell.Header(set.title, set.key)
        extra.setEmoji[set.key].orEmpty().filter { it.documentId != 0L }.mapTo(cells) { GridCell.Item(Reaction.CustomEmoji(it.documentId), it, set.key) }
    }
    return cells
}

private val ITEM = 48.dp
private val CIRCLE = 40.dp
private val GLYPH = 32.dp
private val EXPAND_CIRCLE = 32.dp
private val ROW_PADDING = 6.dp
private val PANEL_HEIGHT = 340.dp

private val PILL_HEIGHT = 56.dp
private const val MAX_SLOTS = 8
private const val APPEAR_FIRST_MS = 10L
private const val APPEAR_SPREAD_MS = 300L
