package cn.ellerotta.core.ui.components.appbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R

internal const val TOOLBAR_HEIGHT = 64

private const val COLLAPSED_STORIES = 3
private const val COLLAPSED_STORY_SIZE = 32

private fun storySharedKey(peerKey: String) = "story-avatar-$peerKey"

@Composable
internal fun CollapsedLayout(
    title: String,
    onTitleLongPress: () -> Unit,
    titleLongPressLabel: String?,
    spatialSpec: FiniteAnimationSpec<Rect>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    stories: TopBarStories? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        TitleRow(title, onTitleLongPress, titleLongPressLabel, spatialSpec, sharedTransitionScope, animatedContentScope, "title_collapsed") {
            if (stories != null && stories.peers.isNotEmpty()) {
                CollapsedStories(stories, spatialSpec, sharedTransitionScope, animatedContentScope)
            }
        }
    }
}

@Composable
private fun CollapsedStories(
    stories: TopBarStories,
    spatialSpec: FiniteAnimationSpec<Rect>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
) {
    val peers = stories.peers
    val label = stringResource(R.string.stories_bar_title)
    with(sharedTransitionScope) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.stories_bar_show), onClick = stories.onExpand)
                .semantics { contentDescription = label }
                .padding(end = 8.dp)
                .testTag("storiesCollapsed"),
            horizontalArrangement = Arrangement.spacedBy((-6).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            peers.take(COLLAPSED_STORIES).forEach { peer ->
                StoryAvatar(
                    peer = peer,
                    avatars = stories.avatars,
                    size = COLLAPSED_STORY_SIZE,
                    modifier = Modifier.sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = storySharedKey(peer.key)),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = { _, _ -> spatialSpec },
                    ),
                )
            }
            if (peers.size > COLLAPSED_STORIES) {
                val more = peers.size - COLLAPSED_STORIES
                val moreLabel = pluralStringResource(R.plurals.stories_bar_more, more, more)
                OverflowCircle(count = more, size = COLLAPSED_STORY_SIZE, modifier = Modifier.semantics { contentDescription = moreLabel })
            }
        }
    }
}

@Composable
internal fun ExpandedLayout(
    title: String,
    searchBar: (@Composable () -> Unit)?,
    onTitleLongPress: () -> Unit,
    titleLongPressLabel: String?,
    spatialSpec: FiniteAnimationSpec<Rect>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    stories: TopBarStories? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        TitleRow(title, onTitleLongPress, titleLongPressLabel, spatialSpec, sharedTransitionScope, animatedContentScope, "title_expanded")
        if (stories != null && stories.shown) {
            ExpandedStories(stories, spatialSpec, sharedTransitionScope, animatedContentScope)
        }
        if (searchBar != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            ) {
                searchBar()
            }
        }
    }
}

@Composable
private fun ExpandedStories(
    stories: TopBarStories,
    spatialSpec: FiniteAnimationSpec<Rect>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
) {
    val self = stories.self
    val showAdd = stories.onAddStory != null || (self != null && self.stories.isNotEmpty())
    val hideLabel = stringResource(R.string.stories_bar_hide)
    with(sharedTransitionScope) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(STORY_ROW_HEIGHT.dp)
                .testTag("storiesExpanded"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (showAdd) {
                item(key = "add-story") {
                    AddStoryCard(self = self, avatars = stories.avatars, onAddStory = stories.onAddStory, onOpenStories = stories.onOpenStories)
                }
            }
            items(stories.peers, key = { it.key }) { peer ->
                ExpandedStoryCard(
                    peer = peer,
                    avatars = stories.avatars,
                    onClick = { stories.onOpenStories(peer.key) },
                    menuLabel = hideLabel,
                    menuIcon = R.drawable.ic_archive,
                    onMenu = { stories.onHideStories(peer.key) },
                    cardModifier = Modifier.sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = storySharedKey(peer.key)),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = { _, _ -> spatialSpec },
                    ),
                )
            }
        }
    }
}

@Composable
private fun TitleRow(
    title: String,
    onTitleLongPress: () -> Unit,
    titleLongPressLabel: String?,
    spatialSpec: FiniteAnimationSpec<Rect>,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedVisibilityScope,
    label: String,
    leading: @Composable () -> Unit = {},
) {
    val motion = MaterialTheme.motionScheme
    with(sharedTransitionScope) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOOLBAR_HEIGHT.dp)
                .padding(start = 16.dp, end = 112.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutVertically(motion.defaultSpatialSpec()) { it } + fadeOut(motion.defaultEffectsSpec()))
                },
                label = label,
                modifier = Modifier
                    .sharedElement(
                        sharedTransitionScope.rememberSharedContentState(key = "title"),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = { _, _ -> spatialSpec },
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClickLabel = titleLongPressLabel,
                        onLongClick = onTitleLongPress,
                    ),
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
    }
}
