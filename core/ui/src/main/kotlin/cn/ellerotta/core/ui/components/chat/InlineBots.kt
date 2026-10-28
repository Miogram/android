package cn.ellerotta.core.ui.components.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.InlineBot
import cn.ellerotta.core.model.InlineResult
import cn.ellerotta.core.model.InlineResultKind
import cn.ellerotta.core.model.InlineResults
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.ui.components.common.Avatar

class ChatInlineActions(
    val onText: (String) -> Unit,
    val onMore: () -> Unit,
    val onSend: (InlineResult, Int?) -> Unit,
    val onSwitchPm: () -> Unit,
    val onLoad: (MediaItem) -> Unit,
    val onSwitchWebView: (theme: String) -> Unit = {},
)

@Composable
internal fun InlineBotList(bots: List<InlineBot>, avatars: Map<String, ByteArray>, onPick: (InlineBot) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("inlineBots")) {
        LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(bots, key = { it.key }) { bot ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(bot) }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("inlineBot_${bot.username}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(bot.name, bot.avatarKey?.let(avatars::get), size = 36)
                    Column {
                        Text(bot.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${bot.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun InlineResultsPanel(
    results: InlineResults,
    more: Boolean,
    files: Map<String, ByteArray>,
    onLoad: (MediaItem) -> Unit,
    onPick: (InlineResult) -> Unit,
    onMore: () -> Unit,
    onSwitchPm: () -> Unit,
    onSwitchWebView: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("inlineResults")) {
        Column {
            results.switchPm?.let { switch ->
                TextButton(onClick = onSwitchPm, modifier = Modifier.fillMaxWidth().testTag("inlineSwitchPm")) { Text(switch.text) }
            }
            results.switchWebView?.let { switch ->
                TextButton(onClick = onSwitchWebView, modifier = Modifier.fillMaxWidth().testTag("inlineSwitchWebView")) { Text(switch.text) }
            }
            val tiles = results.gallery || results.results.none { it.kind == InlineResultKind.Article }
            if (tiles) {
                LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("inlineTiles")) {
                    items(results.results, key = { "${it.queryId}/${it.id}" }) { result -> InlineTile(result, files, onLoad) { onPick(result) } }
                    if (more) item(key = "more") { MoreIndicator(onMore) }
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 260.dp).testTag("inlineList")) {
                    items(results.results, key = { "${it.queryId}/${it.id}" }) { result -> InlineRow(result, files, onLoad) { onPick(result) } }
                    if (more) item(key = "more") { MoreIndicator(onMore) }
                }
            }
        }
    }
}

@Composable
private fun MoreIndicator(onMore: () -> Unit) {
    LaunchedEffect(Unit) { onMore() }
    Box(Modifier.padding(12.dp).testTag("inlineMore"), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
}

@Composable
private fun InlineTile(result: InlineResult, files: Map<String, ByteArray>, onLoad: (MediaItem) -> Unit, onClick: () -> Unit) {
    val thumb = result.thumb
    Box(Modifier.size(TILE).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).testTag("inline_${result.id}"), contentAlignment = Alignment.Center) {
        if (thumb != null && result.kind == InlineResultKind.Sticker) {
            LaunchedEffect(thumb.key) { if (files[thumb.key] == null) onLoad(thumb) }
            StickerView(thumb, files[thumb.key], size = TILE, preview = files.previewOf(thumb))
        } else {
            Thumb(result, files, onLoad, Modifier.size(TILE))
        }
    }
}

@Composable
private fun InlineRow(result: InlineResult, files: Map<String, ByteArray>, onLoad: (MediaItem) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag("inline_${result.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(result, files, onLoad, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f)) {
            result.title?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            result.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Thumb(result: InlineResult, files: Map<String, ByteArray>, onLoad: (MediaItem) -> Unit, modifier: Modifier) {
    val thumb = result.thumb
    if (thumb != null) LaunchedEffect(thumb.key) { if (files[thumb.key] == null) onLoad(thumb) }
    val bytes = thumb?.let { files[it.key] ?: it.thumb }
    val image = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    if (image != null) {
        Image(image, contentDescription = result.title, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(
                (result.title ?: result.description ?: "?").take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private val TILE = 96.dp
