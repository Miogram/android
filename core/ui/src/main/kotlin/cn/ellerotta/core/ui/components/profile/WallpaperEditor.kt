package cn.ellerotta.core.ui.components.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.WALLPAPER_SWATCHES
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperEdit
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chat.WallpaperImage
import cn.ellerotta.core.ui.components.common.CheckedSwitch
import kotlin.math.roundToInt

@Composable
internal fun WallpaperEditor(edit: WallpaperEdit, busy: Boolean, onChange: (Wallpaper) -> Unit, onApply: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val wallpaper = edit.wallpaper
    var slot by rememberSaveable(wallpaper.key) { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("wallpaperEditor")) {
        WallpaperImage(wallpaper, edit.file, Modifier.fillMaxSize().testTag("wallpaperEditPreview"))
        FilledTonalIconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(12.dp).testTag("wallpaperEditClose"), shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.wallpapers_edit_close))
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wallpaper.kind == WallpaperKind.Pattern) {
                    Text(stringResource(R.string.wallpapers_edit_intensity, wallpaper.intensity), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = wallpaper.intensity.toFloat(),
                        onValueChange = { onChange(wallpaper.copy(intensity = it.roundToInt().coerceIn(-100, 100))) },
                        valueRange = -100f..100f,
                        modifier = Modifier.testTag("wallpaperIntensity"),
                    )
                }
                if (wallpaper.kind != WallpaperKind.Image) {
                    Colors(wallpaper, slot, onSlot = { slot = it }, onChange)
                }
                if (wallpaper.kind == WallpaperKind.Image) {
                    Toggle(stringResource(R.string.wallpapers_edit_blur), wallpaper.blur, "wallpaperBlur") { onChange(wallpaper.copy(blur = it)) }
                }
                Toggle(stringResource(R.string.wallpapers_edit_motion), wallpaper.motion, "wallpaperMotion") { onChange(wallpaper.copy(motion = it)) }
                Button(
                    onClick = onApply,
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wallpaperApply"),
                ) { Text(stringResource(R.string.wallpapers_apply)) }
            }
        }
    }
}

@Composable
private fun Colors(wallpaper: Wallpaper, slot: Int, onSlot: (Int) -> Unit, onChange: (Wallpaper) -> Unit) {
    val colors = wallpaper.colors
    val picked = slot.coerceIn(0, (colors.size - 1).coerceAtLeast(0))
    Text(stringResource(R.string.wallpapers_edit_colors), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { index, color ->
            Swatch(color, index == picked, "wallpaperColor_$index", stringResource(R.string.wallpapers_edit_color_slot, index + 1)) { onSlot(index) }
        }
        if (colors.size == 2) {
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onChange(wallpaper.copy(rotation = (wallpaper.rotation + 45) % 360)) }, modifier = Modifier.testTag("wallpaperRotate"), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.wallpapers_edit_rotate))
            }
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("wallpaperSwatches")) {
        items(WALLPAPER_SWATCHES) { swatch ->
            Swatch(swatch, colors.getOrNull(picked) == swatch, "swatch_%06x".format(swatch), "#%06X".format(swatch)) {
                val changed = if (colors.isEmpty()) listOf(swatch) else colors.toMutableList().apply { set(picked, swatch) }
                onChange(wallpaper.copy(colors = changed))
            }
        }
    }
}

@Composable
private fun Swatch(color: Int, selected: Boolean, tag: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xff000000.toInt() or color))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .testTag(tag),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        CheckedSwitch(checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
