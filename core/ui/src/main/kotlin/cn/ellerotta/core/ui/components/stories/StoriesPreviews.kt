package cn.ellerotta.core.ui.components.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.ui.components.common.MioPreviews
import cn.ellerotta.core.ui.components.common.PreviewTheme

private val PhotoBackdrop = Brush.linearGradient(listOf(Color(0xFFF4B04F), Color(0xFFE86A9A), Color(0xFF5B8DEF)))

@Composable
private fun BoxScope.PreviewStoryFooter(peer: PeerStories, story: StoryItem, pause: (Boolean) -> Unit) {
    if (peer.self) {
        StoryViewsBar(story = story, avatars = StoriesPreviewData.avatars, onOpen = {}, modifier = Modifier.align(Alignment.Center).padding(16.dp))
        StoryOwnMenu(story = story, onEdit = {}, onPin = {}, onDelete = {}, pause = pause, modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 8.dp))
    } else {
        StoryReplyBar(story = story, peerName = peer.name, onReply = {}, onReact = {}, pause = pause, modifier = Modifier.fillMaxWidth())
    }
}

@MioPreviews
@Composable
internal fun StoryViewerPreview() {
    PreviewTheme {
        StoryViewer(
            peers = listOf(StoriesPreviewData.others, StoriesPreviewData.own),
            initialPeer = 0,
            avatars = StoriesPreviewData.avatars,
            downloads = StoriesPreviewData.downloads,
            onShown = { _, _ -> },
            onClose = {},
            video = { _, _, _, modifier -> Box(modifier.background(Color.Black)) },
            footer = { peer, story, pause -> PreviewStoryFooter(peer, story, pause) },
        )
    }
}

@MioPreviews
@Composable
internal fun StoryViewerOwnPreview() {
    PreviewTheme {
        StoryViewer(
            peers = listOf(StoriesPreviewData.own),
            initialPeer = 0,
            avatars = StoriesPreviewData.avatars,
            downloads = StoriesPreviewData.downloads,
            onShown = { _, _ -> },
            onClose = {},
            video = { _, _, _, modifier -> Box(modifier.background(Color.Black)) },
            footer = { peer, story, pause -> PreviewStoryFooter(peer, story, pause) },
        )
    }
}

@MioPreviews
@Composable
internal fun StoryViewerDownloadingPreview() {
    PreviewTheme {
        StoryViewer(
            peers = listOf(StoriesPreviewData.others),
            initialPeer = 0,
            avatars = StoriesPreviewData.avatars,
            downloads = StoriesPreviewData.downloading,
            onShown = { _, _ -> },
            onClose = {},
            video = { _, _, _, modifier -> Box(modifier.background(Color.Black)) },
            footer = { peer, story, pause -> PreviewStoryFooter(peer, story, pause) },
        )
    }
}

@MioPreviews
@Composable
internal fun StoryReplyBarPreview() {
    PreviewTheme {
        Box(Modifier.background(PhotoBackdrop)) {
            StoryReplyBar(story = StoriesPreviewData.others.stories.first(), peerName = StoriesPreviewData.others.name, onReply = {}, onReact = {}, pause = {})
        }
    }
}

@MioPreviews
@Composable
internal fun StoryReplyBarFocusedPreview() {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    PreviewTheme {
        Box(Modifier.background(PhotoBackdrop)) {
            StoryReplyBar(
                story = StoriesPreviewData.liked,
                peerName = StoriesPreviewData.others.name,
                onReply = {},
                onReact = {},
                pause = {},
                modifier = Modifier.focusRequester(focus),
            )
        }
    }
}

@MioPreviews
@Composable
internal fun StoryViewsBarPreview() {
    PreviewTheme {
        Box(Modifier.background(PhotoBackdrop).padding(16.dp)) {
            StoryViewsBar(story = StoriesPreviewData.own.stories.first(), avatars = StoriesPreviewData.avatars, onOpen = {})
        }
    }
}

@MioPreviews
@Composable
internal fun StoryViewersContentPreview() {
    PreviewTheme {
        StoryViewersContent(
            page = StoriesPreviewData.viewers,
            avatars = StoriesPreviewData.avatars,
            loading = false,
            onMore = {},
            onOpenPeer = {},
        )
    }
}

@MioPreviews
@Composable
internal fun StoryViewersContentEmptyPreview() {
    PreviewTheme {
        StoryViewersContent(
            page = StoriesPreviewData.noViewers,
            avatars = emptyMap(),
            loading = false,
            onMore = {},
            onOpenPeer = {},
        )
    }
}

@MioPreviews
@Composable
internal fun StoryComposerEmptyPreview() {
    PreviewTheme {
        StoryComposer(preview = null, isVideo = false, sending = false, progress = null, onPick = {}, onSend = { _, _ -> }, onClose = {})
    }
}

@MioPreviews
@Composable
internal fun StoryComposerPhotoPreview() {
    PreviewTheme {
        StoryComposer(preview = StoriesPreviewData.composerPhoto, isVideo = false, sending = false, progress = null, onPick = {}, onSend = { _, _ -> }, onClose = {})
    }
}

@MioPreviews
@Composable
internal fun StoryComposerVideoPreview() {
    PreviewTheme {
        StoryComposer(preview = StoriesPreviewData.composerPhoto, isVideo = true, sending = false, progress = null, onPick = {}, onSend = { _, _ -> }, onClose = {})
    }
}

@MioPreviews
@Composable
internal fun StoryComposerSendingPreview() {
    PreviewTheme {
        StoryComposer(
            preview = StoriesPreviewData.composerPhoto,
            isVideo = false,
            sending = true,
            progress = StoriesPreviewData.UPLOAD_PROGRESS,
            onPick = {},
            onSend = { _, _ -> },
            onClose = {},
        )
    }
}

@MioPreviews
@Composable
internal fun StoryComposerEditPreview() {
    val story = StoriesPreviewData.own.stories.first()
    PreviewTheme {
        StoryComposer(
            preview = null,
            isVideo = false,
            sending = false,
            progress = null,
            onPick = {},
            onSend = { _, _ -> },
            onClose = {},
            editing = story,
            editingState = story.media?.let { StoriesPreviewData.downloads[it.key] },
        )
    }
}
