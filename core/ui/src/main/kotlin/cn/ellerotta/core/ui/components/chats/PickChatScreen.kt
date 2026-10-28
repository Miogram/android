package cn.ellerotta.core.ui.components.chats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.common.Page
import cn.ellerotta.core.ui.components.common.largeTitle
import cn.ellerotta.core.ui.components.common.rememberTopBarState

@Composable
fun PickChatScreen(
    dialogs: List<DialogItem>,
    avatars: Map<String, ByteArray>,
    onPick: (DialogItem) -> Unit,
    onBack: () -> Unit,
    title: String = stringResource(R.string.links_pick_chat),
    header: (@Composable () -> Unit)? = null,
    now: Long = System.currentTimeMillis(),
) {
    val list = rememberLazyListState()
    Page(title, rememberTopBarState(list), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("pickChats"), state = list, contentPadding = padding) {
            largeTitle(title)
            if (header != null) item(key = "header") { header() }
            items(dialogs, key = { it.key }) { dialog ->
                DialogComponent(dialog, avatars, now, onClick = { onPick(dialog) }, onLongClick = {})
            }
        }
    }
}
