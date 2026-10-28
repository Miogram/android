package cn.ellerotta.core.ui.components.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.NO_WALLPAPER
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperChoices
import cn.ellerotta.core.model.WallpaperEdit
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.ChatBackground
import cn.ellerotta.core.ui.components.chat.WallpaperImage
import cn.ellerotta.core.ui.components.common.LargeTitle
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.rememberTopBarState

@Composable
fun WallpapersScreen(
    choices: WallpaperChoices,
    busy: Boolean,
    onBack: () -> Unit,
    onPick: (String?) -> Unit,
    onEdit: (String) -> Unit = onPick,
    editing: WallpaperEdit? = null,
    onChange: (Wallpaper) -> Unit = {},
    onApply: () -> Unit = {},
    onCloseEdit: () -> Unit = {},
    onPickPhoto: (() -> Unit)? = null,
    forChat: Boolean = false,
    everyChat: ChatWallpaper? = null,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val wallpapers = choices.wallpapers.sortedBy { it.dark != dark }
    val grid = rememberLazyGridState()
    val title = stringResource(if (forChat) R.string.wallpapers_chat_title else R.string.profile_wallpaper)
    Page(title, rememberTopBarState(grid), onBack = onBack) { padding ->
        LazyVerticalGrid(
            GridCells.Fixed(COLUMNS),
            Modifier.fillMaxSize().testTag("wallpapers"),
            state = grid,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "largeTitle", span = { GridItemSpan(maxLineSpan) }) { LargeTitle(title, inset = 12.dp) }
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(if (forChat) R.string.wallpapers_chat_hint else R.string.wallpapers_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            if (busy) {
                item(key = "busy", span = { GridItemSpan(maxLineSpan) }) { LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
            }
            item(key = "builtin") {
                val label = stringResource(if (forChat) R.string.wallpapers_chat_default else R.string.wallpapers_builtin)
                WallpaperTile(label, choices.chosen == null, dark = false, "wallpaper_builtin", onClick = { onPick(null) }) {
                    val shared = everyChat?.takeIf { forChat }
                    when {
                        shared?.wallpaper?.kind == WallpaperKind.Plain -> PlainPreview(stringResource(R.string.wallpapers_none))
                        shared != null -> WallpaperImage(shared.wallpaper, shared.file, Modifier.fillMaxSize(), maxSide = PREVIEW_SIDE)
                        else -> ChatBackground(Modifier.fillMaxSize())
                    }
                }
            }
            item(key = "none") {
                val label = stringResource(R.string.wallpapers_none)
                WallpaperTile(label, choices.chosen == NO_WALLPAPER, dark = false, "wallpaper_none", onClick = { onPick(NO_WALLPAPER) }) {
                    PlainPreview(label)
                }
            }
            if (onPickPhoto != null) {
                item(key = "photo") {
                    WallpaperTile(stringResource(R.string.wallpapers_photo), chosen = false, dark = false, "wallpaper_photo", onClick = onPickPhoto) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_image), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(wallpapers, key = { it.key }) { wallpaper ->
                WallpaperTile(describe(wallpaper), choices.chosen == wallpaper.key, wallpaper.dark, "wallpaper_${wallpaper.key}", onClick = { onEdit(wallpaper.key) }) {
                    WallpaperImage(wallpaper, choices.previews[wallpaper.key], Modifier.fillMaxSize(), maxSide = PREVIEW_SIDE)
                }
            }
        }
    }
    if (editing != null) WallpaperEditor(editing, busy, onChange, onApply, onCloseEdit)
}

@Composable
private fun PlainPreview(label: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(colors.surface).border(1.dp, colors.outline, RoundedCornerShape(16.dp)).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun describe(wallpaper: Wallpaper): String = stringResource(
    when (wallpaper.kind) {
        WallpaperKind.Image -> R.string.wallpapers_kind_image
        WallpaperKind.Pattern -> R.string.wallpapers_kind_pattern
        WallpaperKind.Fill -> R.string.wallpapers_kind_fill
        WallpaperKind.Plain -> R.string.wallpapers_none
    },
)

@Composable
private fun WallpaperTile(label: String, chosen: Boolean, dark: Boolean, tag: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .aspectRatio(TILE_ASPECT)
            .clip(shape)
            .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                selected = chosen
            }
            .testTag(tag),
    ) {
        content()
        Row(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (dark) Badge(R.drawable.ic_dark_mode, primary = false)
            if (chosen) Badge(R.drawable.ic_check, primary = true)
        }
    }
}

@Composable
private fun Badge(icon: Int, primary: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = if (primary) colors.primary else colors.surfaceContainerHighest.copy(alpha = 0.85f)) {
        Icon(painterResource(icon), null, Modifier.padding(4.dp).size(16.dp), tint = if (primary) colors.onPrimary else colors.onSurface)
    }
}

private const val COLUMNS = 3
private const val TILE_ASPECT = 9f / 16f
private const val PREVIEW_SIDE = 480
