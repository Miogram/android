package cn.ellerotta.core.ui.components.appbar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Avatar
import cn.ellerotta.core.ui.components.common.DecodedPhotos
import cn.ellerotta.core.ui.components.common.avatarPalette
import cn.ellerotta.core.ui.components.common.initials
import cn.ellerotta.core.ui.components.common.storyRingColor
import cn.ellerotta.core.ui.components.common.storyRingLabel

internal const val STORY_CARD_WIDTH = 90
internal const val STORY_CARD_HEIGHT = 140

class TopBarStories(
    val peers: List<PeerStories>,
    val self: PeerStories?,
    val avatars: Map<String, ByteArray>,
    val onExpand: () -> Unit,
    val onOpenStories: (String) -> Unit,
    val onAddStory: (() -> Unit)?,
    val onHideStories: (String) -> Unit,
) {
    val shown: Boolean get() = peers.isNotEmpty() || self?.stories?.isNotEmpty() == true
}

internal const val STORY_ROW_HEIGHT = 180

internal fun storyThumb(peer: PeerStories): ByteArray? {
    val stories = peer.stories
    val story = if (peer.self) stories.lastOrNull() else stories.getOrNull(peer.startIndex)
    return story?.media?.thumb
}

@Composable
internal fun ExpandedStoryCard(
    peer: PeerStories,
    avatars: Map<String, ByteArray>,
    onClick: () -> Unit,
    menuLabel: String,
    @DrawableRes menuIcon: Int,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ring = peer.ring
    val unread = ring == StoryRing.Unread || ring == StoryRing.CloseFriends
    val ringColor = storyRingColor(ring, colors.surface)
    val stateLabel = storyRingLabel(ring).orEmpty()
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier.width(STORY_CARD_WIDTH.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize))
                .combinedClickable(
                    onClickLabel = stringResource(R.string.stories_ring_open),
                    onClick = onClick,
                    onLongClickLabel = stringResource(R.string.stories_bar_options),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .semantics { stateDescription = stateLabel }
                .testTag("story_${peer.key}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = cardModifier.size(width = STORY_CARD_WIDTH.dp, height = STORY_CARD_HEIGHT.dp),
                shape = MaterialTheme.shapes.extraLarge,
                border = if (unread && ringColor != null) BorderStroke(3.dp, ringColor) else null,
                color = colors.surfaceContainerHigh,
            ) {
                StoryPicture(peer, avatars, MaterialTheme.shapes.extraLarge, blur = 6.dp)
            }
            Text(
                text = peer.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (unread) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(menuLabel) },
                leadingIcon = { Icon(painterResource(menuIcon), null) },
                onClick = {
                    menu = false
                    onMenu()
                },
                modifier = Modifier.testTag("storyMenuItem"),
            )
        }
    }
}

@Composable
private fun StoryPicture(peer: PeerStories, avatars: Map<String, ByteArray>, shape: Shape, blur: Dp) {
    val thumb = remember(peer) { storyThumb(peer) }
    val thumbBitmap = remember(thumb) { thumb?.let(DecodedPhotos::get) }
    val photo = peer.avatarKey?.let { avatars[it] }
    val photoBitmap = remember(photo) { photo?.let(DecodedPhotos::get) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            thumbBitmap != null -> Image(
                bitmap = thumbBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape).blur(blur),
            )
            photoBitmap != null -> Image(
                bitmap = photoBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
            else -> {
                val palette = avatarPalette(peer.peerId)
                val glyph = with(LocalDensity.current) { 28.dp.toSp() }
                Box(Modifier.fillMaxSize().clip(shape).background(palette.brush))
                Text(
                    text = initials(peer.name),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = glyph, lineHeight = glyph),
                    fontWeight = FontWeight.Bold,
                    color = palette.content,
                )
            }
        }
    }
}

@Composable
internal fun StoryAvatar(
    peer: PeerStories,
    avatars: Map<String, ByteArray>,
    size: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ring = peer.ring
    val ringColor = storyRingColor(ring, colors.surface) ?: colors.outline
    val thumb = remember(peer) { storyThumb(peer) }
    val thumbBitmap = remember(thumb) { thumb?.let(DecodedPhotos::get) }
    val inner = size - 6
    Surface(
        modifier = modifier.size(size.dp).testTag("storyAvatar_${peer.key}"),
        shape = CircleShape,
        border = BorderStroke(if (ring == StoryRing.Read) 1.dp else 2.dp, ringColor),
        color = colors.surface,
    ) {
        Box(Modifier.fillMaxSize().padding(3.dp), contentAlignment = Alignment.Center) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(inner.dp).clip(CircleShape).blur(4.dp),
                )
            } else {
                Avatar(peer.name, peer.avatarKey?.let { avatars[it] }, size = inner, seed = peer.peerId)
            }
        }
    }
}

internal fun LazyListScope.hiddenStoriesRow(
    stories: List<PeerStories>,
    avatars: Map<String, ByteArray>,
    onOpenStories: (String) -> Unit,
    onUnhideStories: (String) -> Unit,
) {
    if (stories.isEmpty()) return
    item(key = "hiddenStories") {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("hiddenStories"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            items(stories, key = { it.key }) { peer ->
                ExpandedStoryCard(
                    peer = peer,
                    avatars = avatars,
                    onClick = { onOpenStories(peer.key) },
                    menuLabel = stringResource(R.string.stories_bar_unhide),
                    menuIcon = R.drawable.ic_visibility,
                    onMenu = { onUnhideStories(peer.key) },
                )
            }
        }
    }
}
