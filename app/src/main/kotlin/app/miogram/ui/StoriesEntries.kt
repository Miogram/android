package app.miogram.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import app.miogram.feature.stories.StoriesViewModel
import app.miogram.feature.stories.StoryComposerViewModel
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.ui.components.safety.ReportActions
import cn.ellerotta.core.ui.components.safety.ReportSheet
import cn.ellerotta.core.ui.components.stories.StoryComposer
import cn.ellerotta.core.ui.components.stories.StoryOwnMenu
import cn.ellerotta.core.ui.components.stories.StoryReplyBar
import cn.ellerotta.core.ui.components.stories.StoryViewer
import cn.ellerotta.core.ui.components.stories.StoryViewersSheet
import cn.ellerotta.core.ui.components.stories.StoryViewsBar

internal fun EntryProviderScope<Route>.storiesEntries(navigator: Navigator, back: () -> Unit, onOpenLink: (String) -> Unit) {
    entry<Route.Stories> { route ->
        val model = hiltViewModel<StoriesViewModel, StoriesViewModel.Factory>(key = route.peerKey) { it.create(route.peerKey) }
        val peers by model.peers.collectAsStateWithLifecycle()
        val avatars by model.avatars.collectAsStateWithLifecycle()
        val downloads by model.downloads.collectAsStateWithLifecycle()
        val viewers by model.viewers.collectAsStateWithLifecycle()
        val viewersLoading by model.viewersLoading.collectAsStateWithLifecycle()
        val customEmoji by model.customEmojiDocuments.collectAsStateWithLifecycle()
        val emojiFiles by model.stickers.collectAsStateWithLifecycle()
        val report by model.report.collectAsStateWithLifecycle()
        val reportActions = remember(model) { ReportActions(model::reportChoose, model::reportComment, model::closeReport) }
        val shown = peers
        LaunchedEffect(shown) { if (shown != null && shown.all { it.stories.isEmpty() }) back() }
        Work(model) {
            if (shown == null || shown.all { it.stories.isEmpty() }) {
                Box(Modifier.fillMaxSize().background(Color.Black))
                return@Work
            }
            report?.let { ReportSheet(it, reportActions) }
            val pages = shown.filter { it.stories.isNotEmpty() }
            val initial = remember { model.initialPeer(pages) }
            fun openPeer(key: String, name: String?) = navigator.go(Route.PeerProfile(key, name.orEmpty()))
            StoryViewer(
                peers = pages,
                initialPeer = initial,
                avatars = avatars,
                downloads = downloads,
                onShown = model::onShown,
                onClose = back,
                video = { media, playing, scale, modifier ->
                    StoryVideo({ model.stream(media) }, playing, looping = media.kind == MediaKind.Gif, contentScale = scale, modifier = modifier)
                },
                onOpenPeer = { key -> openPeer(key, pages.firstOrNull { it.key == key }?.name) },
                onOpenLink = onOpenLink,
                customEmoji = customEmoji,
                emojiFiles = emojiFiles,
                onReport = { peer, story -> model.reportStory(peer.key, story.id) },
                hold = report != null,
                footer = { peer, story, pause ->
                    if (peer.self) {
                        var listing by remember(story.id) { mutableStateOf(false) }
                        StoryViewsBar(
                            story = story,
                            avatars = avatars,
                            onOpen = {
                                listing = true
                                pause(true)
                                model.openViewers(story.id)
                            },
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                        StoryOwnMenu(
                            story = story,
                            onEdit = { navigator.go(Route.StoryComposer(story.id)) },
                            onPin = { model.pin(story, it) },
                            onDelete = { model.delete(story) },
                            pause = pause,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 8.dp),
                        )
                        if (listing) {
                            StoryViewersSheet(
                                page = viewers,
                                avatars = avatars,
                                loading = viewersLoading,
                                onMore = model::moreViewers,
                                onOpenPeer = { key -> openPeer(key, viewers?.viewers?.firstOrNull { it.key == key }?.name) },
                                onDismiss = {
                                    listing = false
                                    pause(false)
                                },
                                customEmoji = customEmoji,
                                emojiFiles = emojiFiles,
                            )
                        }
                    } else {
                        StoryReplyBar(
                            story = story,
                            peerName = peer.name,
                            onReply = { model.reply(peer, story, it) },
                            onReact = { model.react(peer, story, it) },
                            pause = pause,
                            modifier = Modifier.fillMaxWidth(),
                            customEmoji = customEmoji,
                            emojiFiles = emojiFiles,
                        )
                    }
                },
            )
        }
    }
    entry<Route.StoryComposer> { route ->
        val model = hiltViewModel<StoryComposerViewModel, StoryComposerViewModel.Factory>(key = "storyComposer${route.editId}") { it.create(route.editId) }
        val picked by model.picked.collectAsStateWithLifecycle()
        val sending by model.sending.collectAsStateWithLifecycle()
        val progress by model.progress.collectAsStateWithLifecycle()
        val editing by model.editing.collectAsStateWithLifecycle()
        val downloads by model.downloads.collectAsStateWithLifecycle()
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::pick) }
        LaunchedEffect(model) { model.posted.collect { back() } }
        Work(model) {
            StoryComposer(
                preview = picked?.preview,
                isVideo = picked?.isVideo == true,
                sending = sending,
                progress = progress,
                onPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onSend = model::send,
                onClose = back,
                editing = editing,
                editingState = editing?.media?.let { downloads[it.key] },
            )
        }
    }
}
