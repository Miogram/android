package cn.ellerotta.core.ui.components.chat

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ChatWallpaper
import cn.ellerotta.core.model.WallpaperKind

internal class ChatBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalChatBackdrop = staticCompositionLocalOf<ChatBackdrop?> { null }

internal val LocalThemedBackground = staticCompositionLocalOf { false }

internal fun ChatWallpaper?.isThemed(): Boolean = this == null || wallpaper.kind == WallpaperKind.Plain

@Composable
internal fun rememberChatBackdrop(): ChatBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ChatBackdrop(layer) }
}

internal fun Modifier.backdropSource(backdrop: ChatBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@Composable
internal fun GlassPill(
    shape: Shape,
    modifier: Modifier = Modifier,
    backdrop: ChatBackdrop? = LocalChatBackdrop.current,
    fill: Color = Color.Black.copy(alpha = PILL_ALPHA),
    content: @Composable BoxScope.() -> Unit,
) {
    val behind = backdrop.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val blurred = if (behind != null) rememberGraphicsLayer() else null
    var position by remember { mutableStateOf(Offset.Zero) }
    val track = if (behind != null) Modifier.onGloballyPositioned { position = it.positionInRoot() } else Modifier
    Box(
        modifier
            .then(track)
            .clip(shape)
            .drawBehind {
                if (behind != null && blurred != null) {
                    val radius = GLASS_BLUR.toPx()
                    val offset = position - behind.origin
                    blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    blurred.record { translate(-offset.x, -offset.y) { drawLayer(behind.layer) } }
                    drawLayer(blurred)
                }
                drawRect(fill)
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

internal const val PILL_ALPHA = 0.55f

internal const val MEDIA_SCRIM = 0.45f

private val GLASS_BLUR = 16.dp
