package cn.ellerotta.core.ui.components.chats.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.PreviewLine
import cn.ellerotta.core.ui.components.chats.TypingText
import cn.ellerotta.core.ui.components.chats.compactCount

@Composable
fun ChatPrimaryInfo(
    name: String,
    message: AnnotatedString,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    typing: String? = null,
    seed: Int = 0,
    messageModifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = name,
                style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isMuted) {
                Icon(
                    painterResource(R.drawable.ic_volume_off),
                    contentDescription = stringResource(R.string.chats_muted),
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
            badge?.invoke()
        }
        val fade = motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(typing, transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }, label = "preview") { shown ->
            if (shown != null) {
                TypingText(shown)
            } else {
                PreviewLine(
                    message,
                    messageModifier,
                    seed = seed,
                    maxLines = 2,
                    style = typography.bodyMedium.copy(lineHeight = typography.bodyMedium.fontSize * 1.15),
                )
            }
        }
    }
}

@Composable
fun ChatSecondaryInfo(
    time: String,
    isPinned: Boolean,
    unreadCount: Int,
    unreadMentionCount: Int,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    unreadMark: Boolean = false,
    status: @Composable (() -> Unit)? = null,
    unreadModifier: Modifier = Modifier,
) {
    val isUnread = unreadCount > 0 || unreadMentionCount > 0 || unreadMark
    val enterTransition = scaleIn(motionScheme.fastSpatialSpec()) + fadeIn(motionScheme.fastEffectsSpec())
    val exitTransition = scaleOut(motionScheme.fastSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec())

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
        ) {
            if (isPinned) {
                Icon(
                    painterResource(R.drawable.ic_push_pin),
                    contentDescription = stringResource(R.string.chats_pinned),
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            status?.invoke()
            Text(text = time, style = typography.bodySmall, color = if (isUnread && !isMuted) colorScheme.primary else colorScheme.onSurfaceVariant)
        }

        if (isUnread) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                AnimatedVisibility(visible = unreadMentionCount > 0, enter = enterTransition, exit = exitTransition) {
                    Text(text = "@", style = typography.labelSmall, color = colorScheme.tertiary)
                }
                AnimatedVisibility(visible = unreadCount > 0 || unreadMark, enter = enterTransition, exit = exitTransition, modifier = unreadModifier) {
                    Badge(
                        containerColor = if (isMuted) colorScheme.surfaceContainerHighest else colorScheme.tertiary,
                        contentColor = if (isMuted) colorScheme.onSurfaceVariant else colorScheme.onTertiary,
                    ) {
                        Text(if (unreadCount > 0) compactCount(unreadCount) else "")
                    }
                }
            }
        }
    }
}
