package cn.ellerotta.core.ui.components.chats.list

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun ChatComponent(
    name: String,
    message: AnnotatedString,
    time: String,
    unreadCount: Int,
    unreadMentionCount: Int,
    avatar: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    isMuted: Boolean = false,
    unreadMark: Boolean = false,
    isSelected: Boolean = false,
    typing: String? = null,
    seed: Int = 0,
    messageModifier: Modifier = Modifier,
    unreadModifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null,
    status: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animationState = rememberChatComponentAnimationState(isPressed = isPressed, isSelected = isSelected)
    val shape = RoundedCornerShape(animationState.cornerRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = animationState.scale }
            .clip(shape)
            .background(animationState.backgroundColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(12.dp))
        ChatPrimaryInfo(
            name = name,
            message = message,
            isMuted = isMuted,
            typing = typing,
            seed = seed,
            messageModifier = messageModifier,
            modifier = Modifier.weight(1f),
            badge = badge,
        )
        Spacer(Modifier.width(8.dp))
        ChatSecondaryInfo(
            time = time,
            isPinned = isPinned,
            unreadCount = unreadCount,
            unreadMentionCount = unreadMentionCount,
            isMuted = isMuted,
            unreadMark = unreadMark,
            status = status,
            unreadModifier = unreadModifier,
        )
    }
}
