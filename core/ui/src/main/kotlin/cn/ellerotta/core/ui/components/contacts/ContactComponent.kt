package cn.ellerotta.core.ui.components.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.OnlineDot
import cn.ellerotta.core.ui.components.chats.list.rememberChatComponentAnimationState
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.profile.presenceText

@Composable
fun ContactComponent(
    contact: ContactItem,
    avatars: Map<String, ByteArray>,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("contact_${contact.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(contact.name, contact.avatarKey?.let { avatars[it] }, seed = contact.userId)
            OnlineDot(contact.presence == Presence.Online, ring = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(12.dp))
        ContactPrimaryInfo(name = contact.name, lastSeen = presenceText(contact.presence, now), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        ContactSecondaryInfo(isMutual = contact.mutual)
    }
}

@Composable
private fun ContactPrimaryInfo(name: String, lastSeen: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lastSeen.isNotEmpty()) {
            Text(
                text = lastSeen,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContactSecondaryInfo(isMutual: Boolean, modifier: Modifier = Modifier) {
    if (isMutual) {
        Icon(
            painter = painterResource(R.drawable.ic_person_check),
            contentDescription = stringResource(R.string.contacts_mutual),
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(24.dp),
        )
    }
}
