package cn.ellerotta.core.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp as lerpFloat

class PillTabLabel(val color: Color, val weight: FontWeight, val style: TextStyle)

private class TabBounds {
    var lefts = FloatArray(0)
    var widths = FloatArray(0)
}

@Composable
internal fun PillTabRow(
    count: Int,
    selected: Int,
    position: () -> Float,
    onTabClick: (Int) -> Unit,
    tabTag: (Int) -> String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    onTabLongClick: ((Int) -> Unit)? = null,
    longClickLabel: String? = null,
    menu: (@Composable (index: Int) -> Unit)? = null,
    tab: @Composable RowScope.(index: Int, label: PillTabLabel) -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val pillColor = MaterialTheme.colorScheme.secondaryContainer
    val selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium
    val inset = if (small) 0.dp else 8.dp

    val bounds = remember { TabBounds() }
    val insetPx = with(density) { (16.dp + inset).toPx() }
    LaunchedEffect(selected) {
        if (selected !in bounds.lefts.indices) return@LaunchedEffect
        val centre = insetPx + bounds.lefts[selected] + bounds.widths[selected] / 2f
        scrollState.animateScrollTo((centre - scrollState.viewportSize / 2f).roundToInt().coerceIn(0, scrollState.maxValue))
    }

    val spacingPx = with(density) { 4.dp.toPx() }
    val pillHeightPx = with(density) { 36.dp.roundToPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(inset)
                .selectableGroup(),
        ) {
            SubcomposeLayout { _ ->
                val tabPlaceables = List(count) { index ->
                    subcompose("tab_$index") {
                        val distance = abs(index - position()).coerceIn(0f, 1f)
                        val selectionFraction = 1f - distance
                        val label = PillTabLabel(
                            color = lerp(unselectedTextColor, selectedTextColor, selectionFraction),
                            weight = if (selectionFraction > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                            style = style,
                        )
                        val chosen = index == selected
                        Box {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .semantics { this.selected = chosen }
                                    .combinedClickable(
                                        interactionSource = null,
                                        indication = ripple(),
                                        role = Role.Tab,
                                        onLongClickLabel = longClickLabel,
                                        onLongClick = onTabLongClick?.let { { it(index) } },
                                        onClick = { onTabClick(index) },
                                    )
                                    .padding(horizontal = if (small) 16.dp else 20.dp)
                                    .testTag(tabTag(index)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) { tab(index, label) }
                            menu?.invoke(index)
                        }
                    }.first().measure(Constraints())
                }

                val tabLefts = FloatArray(tabPlaceables.size)
                val tabWidths = FloatArray(tabPlaceables.size)
                var xAccum = 0f
                tabPlaceables.forEachIndexed { i, p ->
                    tabLefts[i] = xAccum
                    tabWidths[i] = p.width.toFloat()
                    xAccum += p.width.toFloat() + spacingPx
                }
                bounds.lefts = tabLefts
                bounds.widths = tabWidths

                val totalWidth = if (tabPlaceables.isEmpty()) 0
                else tabPlaceables.sumOf { it.width } + (spacingPx * (tabPlaceables.size - 1).coerceAtLeast(0)).toInt()
                val heightPx = tabPlaceables.maxOfOrNull { it.height } ?: 0

                var pillLeft = 0f
                var pillWidth = 0f
                if (tabLefts.isNotEmpty()) {
                    val maxIdx = tabLefts.size - 1
                    val at = position().coerceIn(0f, maxIdx.toFloat())
                    val from = floor(at).toInt()
                    val to = (from + 1).coerceAtMost(maxIdx)
                    val fraction = at - from
                    pillLeft = lerpFloat(tabLefts[from], tabLefts[to], fraction)
                    pillWidth = lerpFloat(tabWidths[from], tabWidths[to], fraction)
                }
                val pillHeight = if (small) minOf(pillHeightPx, heightPx) else heightPx

                val pillPlaceable = subcompose("pill") {
                    Box(Modifier.clip(CircleShape).background(pillColor))
                }.first().measure(Constraints.fixed(width = pillWidth.toInt().coerceAtLeast(0), height = pillHeight.coerceAtLeast(0)))

                layout(totalWidth, heightPx) {
                    pillPlaceable.placeRelative(pillLeft.toInt(), (heightPx - pillHeight) / 2)
                    var px = 0
                    tabPlaceables.forEach { placeable ->
                        placeable.placeRelative(px, (heightPx - placeable.height) / 2)
                        px += placeable.width + spacingPx.toInt()
                    }
                }
            }
        }
    }
}

@Composable
internal fun PagerPillTabRow(
    count: Int,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    tabTag: (Int) -> String,
    modifier: Modifier = Modifier,
    onTabLongClick: ((Int) -> Unit)? = null,
    longClickLabel: String? = null,
    menu: (@Composable (index: Int) -> Unit)? = null,
    tab: @Composable RowScope.(index: Int, label: PillTabLabel) -> Unit,
) = PillTabRow(
    count = count,
    selected = pagerState.currentPage,
    position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
    onTabClick = onTabClick,
    tabTag = tabTag,
    modifier = modifier,
    onTabLongClick = onTabLongClick,
    longClickLabel = longClickLabel,
    menu = menu,
    tab = tab,
)
