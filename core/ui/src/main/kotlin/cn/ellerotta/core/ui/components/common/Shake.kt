package cn.ellerotta.core.ui.components.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

fun Modifier.shake(trigger: Int): Modifier = composed {
    val offset = remember { Animatable(0f) }
    val amplitude = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 500
                val swings = 6
                for (i in 0 until swings) {
                    val decay = 1f - i / swings.toFloat()
                    (if (i % 2 == 0) -amplitude else amplitude) * decay at (i + 1) * 500 / (swings + 1)
                }
            },
        )
    }
    graphicsLayer { translationX = offset.value }
}
