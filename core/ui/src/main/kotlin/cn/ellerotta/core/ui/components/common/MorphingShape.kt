package cn.ellerotta.core.ui.components.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingShape(
    from: RoundedPolygon,
    to: RoundedPolygon,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val morph = remember(from, to) { Morph(from, to) }
    val inspecting = LocalInspectionMode.current
    val progress = remember { Animatable(if (inspecting) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(morph) { progress.animateTo(1f, spec) }
    Box(
        modifier
            .size(size)
            .drawWithCache {
                val path = Path()
                val scale = Matrix().apply { scale(this@drawWithCache.size.width, this@drawWithCache.size.height) }
                onDrawBehind {
                    morph.toPath(progress.value, path)
                    path.transform(scale)
                    drawPath(path, color)
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
