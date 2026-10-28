package cn.ellerotta.core.ui.components.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import cn.ellerotta.core.model.ChatRemoval
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MuteDuration
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.appbar.hiddenStoriesRow
import cn.ellerotta.core.ui.components.common.EmptyState

@Composable
fun ArchivedChatsScreen(
    dialogs: List<DialogItem>,
    avatars: Map<String, ByteArray>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onOpenChat: (DialogItem) -> Unit,
    onAction: (DialogItem, ChatAction) -> Unit,
    modifier: Modifier = Modifier,
    onOpenStories: ((String) -> Unit)? = null,
    hiddenStories: List<PeerStories> = emptyList(),
    onUnhideStories: (String) -> Unit = {},
    onLoadMore: (() -> Unit)? = null,
    onRemove: ((DialogItem, ChatRemoval, Boolean) -> Unit)? = null,
    onMute: ((DialogItem, MuteDuration) -> Unit)? = null,
    now: Long = rememberNow(),
) {
    var menuFor by remember { mutableStateOf<DialogItem?>(null) }
    val list = rememberLazyListState()
    val effectsSpec = motionScheme.defaultEffectsSpec<Float>()
    val sizeSpatialSpec = motionScheme.defaultSpatialSpec<IntSize>()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chats_archive), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back"), shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            if (dialogs.isEmpty() && hiddenStories.isEmpty() && !refreshing) {
                EmptyState(R.drawable.ic_archive, stringResource(R.string.chats_archive_empty), "emptyChats", Modifier.align(Alignment.Center))
            } else {
                Column {
                    AnimatedVisibility(
                        visible = refreshing && dialogs.isNotEmpty(),
                        enter = expandVertically(animationSpec = sizeSpatialSpec) + fadeIn(animationSpec = effectsSpec),
                        exit = shrinkVertically(animationSpec = sizeSpatialSpec) + fadeOut(animationSpec = effectsSpec),
                    ) {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    ChatListContent(
                        dialogs = dialogs,
                        avatars = avatars,
                        now = now,
                        listState = list,
                        contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                        selectedKey = menuFor?.key,
                        onOpenChat = onOpenChat,
                        onMenu = { menuFor = it },
                        empty = {},
                        header = { hiddenStoriesRow(hiddenStories, avatars, onOpenStories ?: {}, onUnhideStories) },
                        onOpenStories = onOpenStories,
                        onEnd = onLoadMore,
                    )
                }
            }
        }
    }

    menuFor?.let { dialog ->
        ChatActionsSheet(
            dialog,
            avatars,
            onAction = { action ->
                menuFor = null
                onAction(dialog, action)
            },
            onDismiss = { menuFor = null },
            onRemove = onRemove?.let { remove -> { removal, revoke -> remove(dialog, removal, revoke) } },
            onMute = onMute?.let { mute -> { duration -> mute(dialog, duration) } },
        )
    }
}
