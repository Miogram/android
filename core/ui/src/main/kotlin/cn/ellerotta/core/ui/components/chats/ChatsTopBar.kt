package cn.ellerotta.core.ui.components.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.appbar.CollapsibleTopBar
import cn.ellerotta.core.ui.components.appbar.SearchBar
import cn.ellerotta.core.ui.components.appbar.TopBarStories
import cn.ellerotta.core.ui.theme.crossfade

@Composable
internal fun ChatsTopBar(
    collapseProgress: Float,
    status: ChatsStatus,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onArchiveClick: () -> Unit,
    searchFocus: FocusRequester,
    modifier: Modifier = Modifier,
    stories: TopBarStories? = null,
    onSearchFocusChange: ((Boolean) -> Unit)? = null,
) {
    CollapsibleTopBar(
        modifier = modifier,
        collapseProgress = collapseProgress,
        title = stringResource(
            when (status) {
                ChatsStatus.WaitingForNetwork -> R.string.chats_waiting_network
                ChatsStatus.Connecting -> R.string.chats_connecting
                ChatsStatus.Updating -> R.string.chats_updating
                ChatsStatus.Ready -> R.string.chats_title
            },
        ),
        onSearchClick = onSearchClick,
        searchBar = {
            SearchBar(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.chats_search),
                focusRequester = searchFocus,
                onFocusChange = onSearchFocusChange,
                leading = {
                    val motion = MaterialTheme.motionScheme
                    AnimatedContent(status != ChatsStatus.Ready, transitionSpec = { motion.crossfade() }, label = "status") { loading ->
                        if (loading) {
                            LoadingIndicator(Modifier.size(24.dp))
                        } else {
                            Icon(painterResource(R.drawable.ic_search), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        },
        onTitleLongPress = onArchiveClick,
        titleLongPressLabel = stringResource(R.string.chats_archive),
        stories = stories,
    )
}
