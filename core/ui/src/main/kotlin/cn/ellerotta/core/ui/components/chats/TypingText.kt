package cn.ellerotta.core.ui.components.chats

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R

@Composable
fun TypingText(who: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val wave by rememberInfiniteTransition(label = "typing").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (who.isEmpty()) stringResource(R.string.chats_typing) else stringResource(R.string.chats_typing_named, who),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        repeat(3) { dot ->
            val phase = ((wave - dot).mod(3f)).let { if (it < 1f) it else 0f }
            Text(
                ".",
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = if (dot == 0) 1.dp else 0.dp).graphicsLayer { translationY = -kotlin.math.sin(phase * Math.PI).toFloat() * 3.dp.toPx() },
            )
        }
    }
}
