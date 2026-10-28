package cn.ellerotta.core.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.Wallpaper
import cn.ellerotta.core.model.WallpaperKind
import cn.ellerotta.core.ui.R

class ChatWallpaperActions(
    val previews: Map<String, String> = emptyMap(),
    val onLoad: (Wallpaper) -> Unit = {},
    val onApply: (MessageItem) -> Unit = {},
    val onOpen: (() -> Unit)? = null,
)

@Composable
internal fun WallpaperChip(message: MessageItem, actions: ChatWallpaperActions?, modifier: Modifier = Modifier) {
    val set = message.wallpaper ?: return
    val who = if (message.out) stringResource(R.string.chats_you) else message.senderName
    val line = stringResource(if (set.same) R.string.service_wallpaper_same else R.string.service_wallpaper)
    val text = if (who == null) line.replaceFirstChar { it.uppercase() } else "$who $line"
    if (actions == null || set.same) {
        CenterChip(text, modifier.testTag("wallpaperChip_${message.id}"))
        return
    }
    val wallpaper = set.wallpaper
    LaunchedEffect(wallpaper.key) { if (wallpaper.kind != WallpaperKind.Fill && wallpaper.key !in actions.previews) actions.onLoad(wallpaper) }
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = GIFT_CHIP_ALPHA),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.widthIn(max = 240.dp).testTag("wallpaperChip_${message.id}"),
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WallpaperImage(wallpaper, actions.previews[wallpaper.key], Modifier.size(width = 96.dp, height = 160.dp).clip(RoundedCornerShape(16.dp)).testTag("wallpaperChipPreview"), maxSide = CHIP_SIDE)
                Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                if (!message.out) {
                    FilledTonalButton(
                        onClick = { actions.onApply(message) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).testTag("applyWallpaper_${message.id}"),
                    ) { Text(stringResource(R.string.wallpaper_action_apply)) }
                }
            }
        }
    }
}

private const val CHIP_SIDE = 320
