package cn.ellerotta.core.ui.components.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.SavedGift
import cn.ellerotta.core.model.UniqueGift
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.TEXT_CONTRAST
import cn.ellerotta.core.ui.theme.contrastRatio
import cn.ellerotta.core.ui.theme.fadedOver
import cn.ellerotta.core.ui.theme.legibleOn
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TILE_CORNER_PERCENT = 17

private const val STICKER_SHARE = 0.8f

private const val RIBBON_INNER = 0.6f
private const val RIBBON_OUTER = 0.8f

private const val RIBBON_OVERHANG = 0.012f

private fun rgb(value: Int) = Color(0xFF000000.toInt() or value)

@Composable
internal fun GiftTile(gift: SavedGift, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val unique = gift.unique
    val press = remember { MutableInteractionSource() }
    Box(modifier.aspectRatio(0.85f).clickable(press, indication = null, onClick = onClick).testTag("gift_${gift.key}")) {
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(TILE_CORNER_PERCENT)).indication(press, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            GiftBackdrop(unique, stickers, onLoadSticker, Modifier.matchParentSize())
            GiftSticker(unique?.model?.sticker ?: gift.sticker, stickers, onLoadSticker, width * STICKER_SHARE)
            if (!gift.saved) {
                Icon(painterResource(R.drawable.ic_visibility_off), stringResource(R.string.gift_hidden), Modifier.align(Alignment.TopStart).padding(10.dp).size(16.dp), tint = unique?.backdrop?.let { rgb(it.textColor) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (unique != null) GiftRibbon("#${unique.num}", unique)
    }
}

@Composable
internal fun GiftBackdrop(unique: UniqueGift?, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val backdrop = unique?.backdrop
    val center = backdrop?.let { rgb(it.centerColor) } ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val edge = backdrop?.let { rgb(it.edgeColor) } ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val symbol = unique?.symbol?.sticker?.takeIf { backdrop != null }
    if (symbol != null) LaunchedEffect(symbol.key) { if (stickers[symbol.key] == null) onLoadSticker(symbol) }
    val image = symbol?.let { rememberPatternImage(it, stickers[it.key]) }
    val tint = remember(backdrop) { backdrop?.let { ColorFilter.tint(rgb(it.patternColor)) } }
    Canvas(if (symbol != null) modifier.patternState(image) else modifier) {
        val middle = Offset(size.width / 2, size.height / 2)
        drawRect(Brush.radialGradient(listOf(center, edge), middle, maxOf(size.width, size.height) * 0.6f))
        if (image == null || tint == null) return@Canvas
        val unit = min(size.width, size.height)
        SYMBOL_RINGS.forEachIndexed { ring, (radius, count) ->
            val side = unit * (0.13f - ring * 0.02f)
            val alpha = 0.32f - ring * 0.12f
            repeat(count) { i ->
                val angle = 2 * PI * i / count + ring * PI / count
                val x = middle.x + (cos(angle) * radius * unit).toFloat() - side / 2
                val y = middle.y + (sin(angle) * radius * unit).toFloat() - side / 2
                drawImage(image, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(side.roundToInt(), side.roundToInt()), alpha = alpha, colorFilter = tint)
            }
        }
    }
}

private val SYMBOL_RINGS = listOf(0.36f to 6, 0.5f to 10)

@Composable
private fun BoxScope.GiftRibbon(label: String, unique: UniqueGift) {
    val backdrop = unique.backdrop
    val edge = backdrop?.let { rgb(it.edgeColor) } ?: Color(0xFFBDBDBD)
    val fill = lerp(edge, Color.Black, 0.18f)
    val ink = backdrop?.let { rgb(it.textColor) }?.takeIf { contrastRatio(it, fill) >= TEXT_CONTRAST } ?: legibleOn(fill, listOf(Color.White, Color.Black))
    val text = ink.fadedOver(fill, 0.85f, TEXT_CONTRAST)
    Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val o = w * RIBBON_OVERHANG
        val band = Path().apply {
            moveTo(RIBBON_INNER * w - o, -o)
            lineTo(RIBBON_OUTER * w - o, -o)
            lineTo(w + o, w + o - RIBBON_OUTER * w)
            lineTo(w + o, w + o - RIBBON_INNER * w)
            close()
        }
        drawIntoCanvas { canvas ->
            canvas.drawPath(band, Paint().apply { color = fill; pathEffect = PathEffect.cornerPathEffect(w * 0.025f) })
        }
    }
    Layout(
        content = {
            Text(
                label,
                color = text,
                fontSize = with(LocalDensity.current) { 40.dp.toSp() },
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.testTag("giftRibbon"),
            )
        },
        modifier = Modifier.matchParentSize(),
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints())
        val w = constraints.maxWidth.toFloat()
        val middle = (RIBBON_INNER + RIBBON_OUTER) / 2
        val cx = (middle * w + w) / 2
        val cy = (1 - middle) * w / 2
        val thickness = (RIBBON_OUTER - RIBBON_INNER) * w / sqrt(2f)
        val length = (1 - middle) * w * sqrt(2f) * 0.85f
        val scale = min(thickness * 0.78f / placeable.height, length / placeable.width)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer((cx - placeable.width / 2f).roundToInt(), (cy - placeable.height / 2f).roundToInt()) {
                rotationZ = 45f
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

@Composable
internal fun GiftHeader(unique: UniqueGift?, stickers: Map<String, ByteArray>, onLoadSticker: (MediaItem) -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.Center) {
        GiftBackdrop(unique, stickers, onLoadSticker, Modifier.matchParentSize())
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center, content = content)
    }
}
