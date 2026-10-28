package cn.ellerotta.core.ui.components.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.components.common.closeFriendsGreens
import cn.ellerotta.core.ui.theme.legibleOn
import java.util.Locale

fun compactCount(count: Int, locale: Locale = Locale.getDefault()): String = when {
    count < 1000 -> count.toString()
    count < 1_000_000 -> trimmed(count / 1000.0, locale) + "K"
    else -> trimmed(count / 1_000_000.0, locale) + "M"
}

private fun trimmed(value: Double, locale: Locale): String {
    val tenths = (value * 10).toInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else String.format(locale, "%.1f", tenths / 10.0)
}

@Composable
fun UnreadBadge(count: Int, muted: Boolean, modifier: Modifier = Modifier, text: String? = null, visible: Boolean = count > 0) {
    val colors = MaterialTheme.colorScheme
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val roll = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    AnimatedVisibility(
        visible,
        modifier = modifier,
        enter = scaleIn(spatial) + fadeIn(effects),
        exit = scaleOut(spatial) + fadeOut(effects),
    ) {
        val pulse = remember { Animatable(1f) }
        val last = remember { intArrayOf(count) }
        LaunchedEffect(count) {
            if (count > last[0]) {
                pulse.animateTo(1.2f, spatial)
                pulse.animateTo(1f, spatial)
            }
            last[0] = count
        }
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                }
                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                .background(if (muted) colors.surfaceContainerHighest else colors.tertiary, CircleShape)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(roll) { if (up) it else -it } + fadeIn(effects))
                        .togetherWith(slideOutVertically(roll) { if (up) -it else it } + fadeOut(effects))
                },
                label = "count",
            ) { shown ->
                Text(
                    text ?: compactCount(shown),
                    color = if (muted) colors.onSurfaceVariant else colors.onTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun OnlineDot(visible: Boolean, ring: Color, modifier: Modifier = Modifier) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    AnimatedVisibility(visible, modifier = modifier, enter = scaleIn(spatial), exit = scaleOut(spatial)) {
        Box(Modifier.background(ring, CircleShape).padding(2.dp)) {
            Box(Modifier.defaultMinSize(10.dp, 10.dp).background(legibleOn(ring, closeFriendsGreens), CircleShape))
        }
    }
}
