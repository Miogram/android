package cn.ellerotta.core.ui.components.appbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R

@Composable
fun CollapsibleTopBar(
    collapseProgress: Float,
    title: String,
    modifier: Modifier = Modifier,
    onSearchClick: (() -> Unit)? = null,
    searchBar: (@Composable () -> Unit)? = null,
    onTitleLongPress: () -> Unit = {},
    titleLongPressLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    stories: TopBarStories? = null,
) {
    val isExpanded = collapseProgress < 0.5f
    val fadeSpec: FiniteAnimationSpec<Float> = motionScheme.defaultEffectsSpec()
    val spatialSpec: FiniteAnimationSpec<Rect> = motionScheme.defaultSpatialSpec()

    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Box(Modifier.fillMaxWidth()) {
                SharedTransitionLayout(Modifier.fillMaxWidth()) {
                    AnimatedContent(
                        targetState = isExpanded,
                        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
                        label = "search_collapse",
                    ) { expanded ->
                        if (expanded) {
                            ExpandedLayout(
                                title = title,
                                searchBar = searchBar,
                                onTitleLongPress = onTitleLongPress,
                                titleLongPressLabel = titleLongPressLabel,
                                spatialSpec = spatialSpec,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedContentScope = this@AnimatedContent,
                                stories = stories,
                            )
                        } else {
                            CollapsedLayout(
                                title = title,
                                onTitleLongPress = onTitleLongPress,
                                titleLongPressLabel = titleLongPressLabel,
                                spatialSpec = spatialSpec,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedContentScope = this@AnimatedContent,
                                stories = stories,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TOOLBAR_HEIGHT.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    AnimatedVisibility(
                        visible = !isExpanded && onSearchClick != null,
                        enter = fadeIn(fadeSpec),
                        exit = fadeOut(fadeSpec),
                    ) {
                        IconButton(onClick = { onSearchClick?.invoke() }, modifier = Modifier.testTag("openSearch"), shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_search), stringResource(R.string.chats_search))
                        }
                    }
                    actions()
                }
            }
        }
    }
}
