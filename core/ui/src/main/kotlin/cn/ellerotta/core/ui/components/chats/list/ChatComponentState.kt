package cn.ellerotta.core.ui.components.chats.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
data class ChatComponentAnimationState(
    val cornerRadius: Dp,
    val backgroundColor: Color,
    val scale: Float,
)

@Composable
fun rememberChatComponentAnimationState(
    isPressed: Boolean,
    isSelected: Boolean,
    baseCornerRadius: Dp = 16.dp,
): ChatComponentAnimationState {
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) baseCornerRadius + 16.dp else baseCornerRadius,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "corner",
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isPressed -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> Color.Transparent
        },
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "background",
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.99f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "scale",
    )
    return ChatComponentAnimationState(cornerRadius = cornerRadius, backgroundColor = backgroundColor, scale = scale)
}
