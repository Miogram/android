package cn.ellerotta.core.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.theme.legibleOn

internal val closeFriendsGreens = listOf(Color(0xFF2E7D32), Color(0xFF81C784))

private const val LARGE_RING_FROM = 80

@Composable
fun storyRingColor(ring: StoryRing, background: Color): Color? = when (ring) {
    StoryRing.None -> null
    StoryRing.Unread -> MaterialTheme.colorScheme.primary
    StoryRing.CloseFriends -> legibleOn(background, closeFriendsGreens)
    StoryRing.Read -> MaterialTheme.colorScheme.outline
}

@Composable
fun storyRingLabel(ring: StoryRing): String? = when (ring) {
    StoryRing.None -> null
    StoryRing.Unread -> stringResource(R.string.stories_ring_unread)
    StoryRing.CloseFriends -> stringResource(R.string.stories_ring_close_friends)
    StoryRing.Read -> stringResource(R.string.stories_ring_read)
}

fun storyRingInset(size: Int): Dp = if (size >= LARGE_RING_FROM) 6.dp else 4.dp

@Composable
fun StoryRingBox(
    ring: StoryRing,
    size: Int,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable (photoSize: Int) -> Unit,
) {
    val color = storyRingColor(ring, background)
    if (color == null) {
        Box(modifier.size(size.dp), contentAlignment = Alignment.Center) { content(size) }
        return
    }
    val label = storyRingLabel(ring).orEmpty()
    val openLabel = stringResource(R.string.stories_ring_open)
    val inset = storyRingInset(size)
    val width = when {
        ring == StoryRing.Read -> 1.dp
        size >= LARGE_RING_FROM -> 3.dp
        else -> 2.dp
    }
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = openLabel, onClick = onClick) else Modifier)
            .semantics { stateDescription = label }
            .drawBehind {
                val stroke = width.toPx()
                drawCircle(color, radius = this.size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            },
        contentAlignment = Alignment.Center,
    ) {
        content((size.dp - inset * 2).value.toInt())
    }
}
