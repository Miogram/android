package cn.ellerotta.core.ui.components.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.MEDIA_SCRIM

object TopBarTokens {
    val PillSize = 48.dp

    val PillGap = 8.dp

    val EdgeInset = 16.dp

    val ContentGap = 8.dp

    val ScrimHeight = 116.dp

    val ScrimOverhang = 8.dp

    val ScrimStops = listOf(0f to 1f, 0.293f to 1f, 0.659f to 0.6f, 1f to 0f)

    val BarFade = 32.dp

    val ScrimFade = 48.dp

    val TitleInset = 80.dp

    const val TITLE_START = 0.5f

    const val TITLE_DONE = 0.9f
}

@Immutable
data class TopBarColors(val pill: Color, val content: Color, val scrim: Color) {
    companion object {
        @Composable
        fun surface() = TopBarColors(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surface)

        val Media = TopBarColors(Color.Black.copy(alpha = MEDIA_SCRIM), Color.White, Color.Black.copy(alpha = 0.6f))
    }
}

@Composable
fun topBarHeight(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarTokens.EdgeInset + TopBarTokens.PillSize + TopBarTokens.ContentGap

@Stable
class TopBarState(val largeTitle: Boolean, val scrim: () -> Float, val title: () -> Float) {
    companion object {
        val Shown = TopBarState(false, { 1f }, { 1f })
    }
}

val LocalTopBarState = staticCompositionLocalOf { TopBarState.Shown }

@Composable
fun rememberTopBarState(list: LazyListState, largeTitle: Boolean = true): TopBarState {
    val fade = with(LocalDensity.current) { TopBarTokens.ScrimFade.toPx() }
    return remember(list, largeTitle, fade) {
        TopBarState(
            largeTitle,
            scrim = { scrimProgress(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, fade) },
            title = {
                if (!largeTitle || list.firstVisibleItemIndex > 0) 1f
                else titleProgress(list.firstVisibleItemScrollOffset, list.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0)
            },
        )
    }
}

@Composable
fun rememberTopBarState(grid: LazyGridState, largeTitle: Boolean = true): TopBarState {
    val fade = with(LocalDensity.current) { TopBarTokens.ScrimFade.toPx() }
    return remember(grid, largeTitle, fade) {
        TopBarState(
            largeTitle,
            scrim = { scrimProgress(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset, fade) },
            title = {
                if (!largeTitle || grid.firstVisibleItemIndex > 0) 1f
                else titleProgress(grid.firstVisibleItemScrollOffset, grid.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0)
            },
        )
    }
}

internal fun scrimProgress(index: Int, offset: Int, fade: Float): Float = if (index > 0) 1f else (offset / fade).coerceIn(0f, 1f)

internal fun titleProgress(offset: Int, size: Int): Float {
    if (size <= 0) return 0f
    val start = size * TopBarTokens.TITLE_START
    val done = size * TopBarTokens.TITLE_DONE
    return ((offset - start) / (done - start)).coerceIn(0f, 1f)
}

@Composable
fun TopBarScrim(
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    stops: List<Pair<Float, Float>> = TopBarTokens.ScrimStops,
    alpha: () -> Float = { 1f },
) {
    Spacer(
        modifier.fillMaxWidth().height(height).graphicsLayer { this.alpha = alpha() }.drawBehind {
            drawRect(Brush.verticalGradient(*stops.map { (at, stop) -> at to color.copy(alpha = color.alpha * stop) }.toTypedArray()))
        },
    )
}

fun Modifier.absorbTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

fun topScrimHeight(barHeight: Dp): Dp = max(TopBarTokens.ScrimHeight, barHeight + TopBarTokens.ScrimOverhang)

@Composable
fun TopBarPill(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TopBarColors = TopBarColors.surface(),
    tint: Color = colors.content,
) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = colors.pill,
            contentColor = tint,
            disabledContainerColor = colors.pill,
            disabledContentColor = tint.copy(alpha = 0.38f),
        ),
        modifier = modifier.size(TopBarTokens.PillSize),
    ) {
        Icon(painterResource(icon), label)
    }
}

@Composable
fun BackPill(onBack: () -> Unit, modifier: Modifier = Modifier, colors: TopBarColors = TopBarColors.surface(), tag: String = "back") =
    TopBarPill(R.drawable.ic_arrow_back, stringResource(R.string.common_back), onBack, modifier.testTag(tag), colors = colors)

@Composable
fun TopBar(
    title: String,
    state: TopBarState,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    colors: TopBarColors = TopBarColors.surface(),
    backTag: String = "back",
    titleTag: String = "barTitle",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth()) {
        val bar = topBarHeight()
        val height = bar + TopBarTokens.BarFade
        TopBarScrim(height, color = colors.scrim, stops = listOf(0f to 1f, (bar - TopBarTokens.ContentGap) / height to 1f, 1f to 0f), alpha = state.scrim)
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(start = TopBarTokens.EdgeInset, end = TopBarTokens.EdgeInset, top = TopBarTokens.EdgeInset).height(TopBarTokens.PillSize)) {
            if (onBack != null) BackPill(onBack, Modifier.align(Alignment.CenterStart), colors, backTag)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = TopBarTokens.TitleInset)
                    .graphicsLayer { alpha = state.title() }
                    .then(if (onTitleClick != null && !state.largeTitle) Modifier.clickable(onClick = onTitleClick) else Modifier)
                    .then(if (state.largeTitle) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() }.testTag(titleTag)),
            )
            Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(TopBarTokens.PillGap), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, inset: Dp = 24.dp, onClick: (() -> Unit)? = null) {
    val bar = LocalTopBarState.current
    Text(
        title,
        style = MaterialTheme.typography.headlineLargeEmphasized,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (bar.largeTitle) 1f - bar.title() else 1f }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = inset, end = inset, top = 4.dp, bottom = 16.dp)
            .semantics { heading() }
            .testTag(if (onClick != null) "title" else "largeTitle"),
    )
}

fun LazyListScope.largeTitle(title: String, onClick: (() -> Unit)? = null) {
    item(key = "largeTitle", contentType = "largeTitle") { LargeTitle(title, onClick = onClick) }
}

@Composable
fun SearchTopBar(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    closeLabel: String,
    modifier: Modifier = Modifier,
    colors: TopBarColors = TopBarColors.surface(),
    fieldTag: String = "searchField",
    closeTag: String = "closeSearch",
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        modifier.fillMaxWidth().padding(horizontal = TopBarTokens.EdgeInset).height(TopBarTokens.PillSize),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TopBarTokens.PillGap),
    ) {
        TopBarPill(R.drawable.ic_close, closeLabel, onClose, Modifier.testTag(closeTag), colors = colors)
        Surface(shape = CircleShape, color = colors.pill, modifier = Modifier.weight(1f).height(TopBarTokens.PillSize)) {
            Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.content),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(fieldTag),
                    )
                }
                if (query.isNotEmpty()) {
                    TopBarPill(R.drawable.ic_close, stringResource(R.string.common_clear_search), { onQuery("") }, Modifier.testTag("clearSearch"), colors = colors.copy(pill = Color.Transparent))
                }
            }
        }
    }
}
