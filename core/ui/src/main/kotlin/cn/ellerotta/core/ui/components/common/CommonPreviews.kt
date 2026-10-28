package cn.ellerotta.core.ui.components.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.ui.R

@MioPreviews
@Composable
internal fun AvatarPreview() {
    PreviewTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(CommonPreviewData.NAME, null, size = 32, seed = CommonPreviewData.SEED)
            Avatar(CommonPreviewData.NAME, null, seed = CommonPreviewData.SEED)
            Avatar("Design team", null, size = 64, seed = 202)
            Avatar(CommonPreviewData.NAME, CommonPreviewData.photo)
            Avatar(CommonPreviewData.NAME, CommonPreviewData.photo, size = 64, shape = RoundedCornerShape(16.dp))
        }
    }
}

@MioPreviews
@Composable
internal fun AvatarStoryRingPreview() {
    PreviewTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(null, CommonPreviewData.photo).forEach { bytes ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StoryRing.entries.forEach { ring ->
                        StoryRingBox(ring, 56, onClick = if (ring == StoryRing.None) null else ({})) { photo ->
                            Avatar(CommonPreviewData.NAME, bytes, size = photo, seed = CommonPreviewData.SEED)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StoryRingBox(StoryRing.Unread, 96) { photo -> Avatar(CommonPreviewData.NAME, CommonPreviewData.photo, size = photo) }
                StoryRingBox(StoryRing.CloseFriends, 96) { photo -> Avatar(CommonPreviewData.NAME, null, size = photo, seed = CommonPreviewData.SEED) }
            }
        }
    }
}

@MioPreviews
@Composable
internal fun PagePreview() {
    val list = rememberLazyListState()
    val title = stringResource(R.string.contacts_title)
    PreviewTheme {
        Page(
            title = title,
            state = rememberTopBarState(list),
            onBack = {},
            actions = { TopBarPill(R.drawable.ic_search, stringResource(R.string.chats_search), {}) },
        ) { padding ->
            LazyColumn(state = list, contentPadding = padding) {
                largeTitle(title)
                items(CommonPreviewData.names) { name ->
                    ListItem(headlineContent = { Text(name) }, leadingContent = { Avatar(name, null, size = 40) })
                }
            }
        }
    }
}

@MioPreviews
@Composable
internal fun TopBarPreview() {
    PreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TopBar(
                title = stringResource(R.string.contacts_title),
                state = TopBarState.Shown,
                onBack = {},
                actions = { TopBarPill(R.drawable.ic_edit, stringResource(R.string.chat_edit), {}) },
            )
            TopBar(
                title = stringResource(R.string.contacts_title),
                state = TopBarState(largeTitle = true, scrim = { 0f }, title = { 0f }),
                onBack = {},
            )
            TopBar(title = stringResource(R.string.log_title), state = TopBarState.Shown, onBack = null)
        }
    }
}

@MioPreviews
@Composable
internal fun TopBarMediaPreview() {
    val photo = remember { DecodedPhotos.get(CommonPreviewData.mediaPhoto) }
    PreviewTheme {
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            if (photo != null) Image(photo, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            TopBar(
                title = CommonPreviewData.NAME,
                state = TopBarState.Shown,
                onBack = {},
                colors = TopBarColors.Media,
                actions = {
                    TopBarPill(R.drawable.ic_download, stringResource(R.string.chat_save_downloads), {}, colors = TopBarColors.Media)
                },
            )
        }
    }
}

@MioPreviews
@Composable
internal fun TopBarPillPreview() {
    val photo = remember { DecodedPhotos.get(CommonPreviewData.mediaPhoto) }
    PreviewTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(TopBarTokens.PillGap)) {
                BackPill({})
                TopBarPill(R.drawable.ic_search, stringResource(R.string.chats_search), {})
                TopBarPill(R.drawable.ic_edit, stringResource(R.string.chat_edit), {}, enabled = false)
            }
            Box {
                if (photo != null) Image(photo, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(TopBarTokens.PillGap)) {
                    BackPill({}, colors = TopBarColors.Media)
                    TopBarPill(R.drawable.ic_download, stringResource(R.string.chat_save_downloads), {}, colors = TopBarColors.Media)
                }
            }
        }
    }
}

@MioPreviews
@Composable
internal fun SearchTopBarPreview() {
    var query by remember { mutableStateOf("") }
    PreviewTheme {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SearchTopBar(query, { query = it }, onClose = {}, placeholder = stringResource(R.string.chats_search), closeLabel = stringResource(R.string.common_back))
            SearchTopBar("Alice", {}, onClose = {}, placeholder = stringResource(R.string.chats_search), closeLabel = stringResource(R.string.common_back), fieldTag = "searchField2")
        }
    }
}

@MioPreviews
@Composable
internal fun ErrorSnackbarPreview() {
    PreviewTheme {
        Column {
            CommonPreviewData.errors.forEach { error -> ErrorSnackbar(error, onDismiss = {}) }
        }
    }
}

@MioPreviews
@Composable
internal fun SavedSnackbarPreview() {
    PreviewTheme {
        SavedSnackbar(key = Unit, onOpen = {}, onDismiss = {}, visibleMs = Long.MAX_VALUE)
    }
}

@MioPreviews
@Composable
internal fun ConfirmDialogPreview() {
    PreviewTheme {
        ConfirmDialog(
            title = stringResource(R.string.contacts_delete),
            text = stringResource(R.string.contacts_delete_text, CommonPreviewData.NAME),
            confirm = stringResource(R.string.chat_delete),
            tag = "confirm",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@MioPreviews
@Composable
internal fun LockScreenPreview() {
    PreviewTheme {
        LockScreen(onUnlock = {})
    }
}

@MioPreviews
@Composable
internal fun MorphingShapePreview() {
    PreviewTheme {
        Box(Modifier.padding(24.dp)) {
            MorphingShape(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, size = 112.dp) {
                Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
