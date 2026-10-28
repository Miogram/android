package cn.ellerotta.core.ui.components.appbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.chats.ChatsPreviewData
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

@MioPreviews
@Composable
internal fun CollapsibleTopBarCollapsedPreview() {
    PreviewTheme {
        CollapsibleTopBar(
            collapseProgress = 1f,
            title = stringResource(R.string.chats_title),
            onSearchClick = {},
            stories = ChatsPreviewData.topBarStories(),
        )
    }
}

@MioPreviews
@Composable
internal fun CollapsibleTopBarExpandedPreview() {
    var query by remember { mutableStateOf("") }
    PreviewTheme {
        CollapsibleTopBar(
            collapseProgress = 0f,
            title = stringResource(R.string.chats_title),
            onSearchClick = {},
            searchBar = { SearchBar(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.chats_search)) },
            stories = ChatsPreviewData.topBarStories(),
        )
    }
}

@MioPreviews
@Composable
internal fun CollapsibleTopBarNoStoriesPreview() {
    PreviewTheme {
        CollapsibleTopBar(
            collapseProgress = 0f,
            title = stringResource(R.string.chats_title),
            onSearchClick = {},
            searchBar = { SearchBar(value = "", onValueChange = {}, placeholder = stringResource(R.string.chats_search)) },
            stories = ChatsPreviewData.topBarStories(self = null, peers = emptyList(), canPost = false),
        )
    }
}

@MioPreviews
@Composable
internal fun SearchBarPreview() {
    PreviewTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchBar(value = "", onValueChange = {}, placeholder = stringResource(R.string.chats_search))
            SearchBar(value = "Alice", onValueChange = {}, placeholder = stringResource(R.string.chats_search))
        }
    }
}

@MioPreviews
@Composable
internal fun StoryAvatarPreview() {
    PreviewTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatsPreviewData.storyPeers.take(4).forEach { peer ->
                StoryAvatar(peer, ChatsPreviewData.avatars, size = 48)
            }
            OverflowCircle(count = 2, size = 48)
        }
    }
}

@MioPreviews
@Composable
internal fun ExpandedStoryCardPreview() {
    PreviewTheme {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChatsPreviewData.storyPeers.take(4).forEach { peer ->
                ExpandedStoryCard(
                    peer = peer,
                    avatars = ChatsPreviewData.avatars,
                    onClick = {},
                    menuLabel = stringResource(R.string.stories_bar_hide),
                    menuIcon = R.drawable.ic_archive,
                    onMenu = {},
                )
            }
        }
    }
}

@MioPreviews
@Composable
internal fun AddStoryCardPreview() {
    PreviewTheme {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AddStoryCard(self = ChatsPreviewData.myStories, avatars = ChatsPreviewData.avatars, onAddStory = {}, onOpenStories = {})
            AddStoryCard(self = ChatsPreviewData.myStoriesEmpty, avatars = ChatsPreviewData.avatars, onAddStory = {}, onOpenStories = {})
            AddStoryCard(self = ChatsPreviewData.myStories, avatars = ChatsPreviewData.avatars, onAddStory = null, onOpenStories = {})
        }
    }
}

@MioPreviews
@Composable
internal fun HiddenStoriesRowPreview() {
    PreviewTheme {
        LazyColumn {
            hiddenStoriesRow(ChatsPreviewData.hiddenStories, ChatsPreviewData.avatars, onOpenStories = {}, onUnhideStories = {})
        }
    }
}
