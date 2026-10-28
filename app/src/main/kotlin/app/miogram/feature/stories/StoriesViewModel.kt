package app.miogram.feature.stories

import androidx.lifecycle.viewModelScope
import app.miogram.data.AvatarStore
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.StickersRepository
import app.miogram.data.telegram.SafetyRepository
import app.miogram.data.telegram.StoriesRepository
import app.miogram.feature.common.ReportFlow
import cn.ellerotta.core.model.ReportState
import cn.ellerotta.core.model.ReportTarget
import app.miogram.data.telegram.VideoStream
import app.miogram.feature.chat.ChatCustomEmoji
import app.miogram.feature.chat.ChatMedia
import app.miogram.feature.common.WorkViewModel
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.model.StoryViewersPage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = StoriesViewModel.Factory::class)
class StoriesViewModel @AssistedInject constructor(
    @Assisted private val peerKey: String,
    private val stories: StoriesRepository,
    private val repository: MediaRepository,
    private val avatarStore: AvatarStore,
    stickersRepository: StickersRepository,
    safety: SafetyRepository,
) : WorkViewModel() {
    private val media = ChatMedia(viewModelScope, repository, ::fail)
    private val reporting = ReportFlow(safety) { workWith(it) }

    val report: StateFlow<ReportState?> = reporting.ui

    fun reportStory(peer: String, id: Int) = reporting.open(ReportTarget.Story(peer, id))

    fun reportChoose(option: String) = reporting.choose(option)

    fun reportComment(text: String) = reporting.comment(text)

    fun closeReport() = reporting.close()

    private val customEmoji = ChatCustomEmoji(viewModelScope, stickersRepository) { media.loadSticker(it, preview = false) }
    val avatars: StateFlow<Map<String, ByteArray>> = avatarStore.avatars
    private val single = MutableStateFlow<PeerStories?>(null)
    private val filled = mutableSetOf<String>()
    private val viewed = mutableSetOf<Pair<String, Int>>()
    private val fetched = mutableSetOf<String>()

    private var order: List<String>? = null

    private var inHidden: Boolean? = null

    private val viewersState = MutableStateFlow<StoryViewersPage?>(null)
    private val viewersLoadingState = MutableStateFlow(false)
    private var viewersOf = 0

    val viewers: StateFlow<StoryViewersPage?> = viewersState.asStateFlow()
    val viewersLoading: StateFlow<Boolean> = viewersLoadingState.asStateFlow()

    val peers: StateFlow<List<PeerStories>?> = combine(stories.stories, stories.hidden, stories.outside, single) { main, hidden, outside, fetched ->
        val list = when (inHidden) {
            false -> main
            true -> hidden
            null -> listOf(main, hidden).firstOrNull { list -> list.any { it.key == peerKey } }?.also { inHidden = it === hidden }
        }
        when {
            list != null -> {
                val keys = order ?: list.map { it.key }.also { order = it }
                val byKey = list.associateBy { it.key }
                keys.mapNotNull { byKey[it] }
            }
            fetched != null -> listOf(outside[peerKey] ?: fetched.copy(stories = emptyList()))
            else -> null
        }
    }
        .onEach { peers ->
            peers?.let { list ->
                avatarStore.load(list.map { it.avatarKey })
                list.forEach(::fillSkipped)
                customEmoji.requestIds(list.flatMap { peer -> peer.stories.flatMap(::customEmojiIdsOf) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val downloads: StateFlow<Map<String, DownloadState>> = media.downloads

    val customEmojiDocuments: StateFlow<Map<Long, MediaItem>> = customEmoji.documents
    val stickers: StateFlow<Map<String, ByteArray>> = media.stickers

    init {
        val known = (stories.stories.value + stories.hidden.value).any { it.key == peerKey }
        if (!known) work { single.value = stories.peer(peerKey) ?: PeerStories(peerKey, "") }
    }

    fun initialPeer(peers: List<PeerStories>): Int = peers.indexOfFirst { it.key == peerKey }.coerceAtLeast(0)

    fun onShown(peer: PeerStories, story: StoryItem) {
        story.media?.takeIf { it.kind == MediaKind.Photo }?.let(media::download)
        if (story.id > peer.maxReadId) work { stories.read(peer.key, story.id) }
        if (!peer.self && viewed.add(peer.key to story.id)) work { stories.view(peer.key, listOf(story.id)) }
        nextAfter(peer, story)?.media?.let(::fetchAhead)
    }

    private fun nextAfter(peer: PeerStories, story: StoryItem): StoryItem? {
        val index = peer.stories.indexOfFirst { it.id == story.id }
        peer.stories.getOrNull(index + 1)?.let { return it }
        val list = peers.value ?: return null
        val next = list.getOrNull(list.indexOfFirst { it.key == peer.key } + 1) ?: return null
        return next.stories.getOrNull(next.startIndex)
    }

    private fun fetchAhead(item: MediaItem) {
        if (!fetched.add(item.key)) return
        when (item.kind) {
            MediaKind.Photo -> media.download(item)
            MediaKind.Video, MediaKind.Gif -> viewModelScope.launch {
                try {
                    repository.prefetch(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fetched -= item.key
                }
            }
            else -> Unit
        }
    }

    fun reply(peer: PeerStories, story: StoryItem, text: String) {
        if (text.isNotBlank()) work { stories.reply(peer.key, story.id, text) }
    }

    fun react(peer: PeerStories, story: StoryItem, reaction: Reaction?) = work { stories.react(peer.key, story.id, reaction) }

    fun delete(story: StoryItem) = work { stories.delete(listOf(story.id)) }

    fun pin(story: StoryItem, pinned: Boolean) = work { stories.pin(listOf(story.id), pinned) }

    fun openViewers(storyId: Int) {
        viewersOf = storyId
        val known = stories.storedViewers(storyId)
        viewersState.value = known
        if (known == null) {
            loadViewers(storyId, "")
            return
        }
        avatarStore.load(known.viewers.map { it.avatarKey })
        customEmoji.requestIds(known.viewers.mapNotNull { (it.reaction as? Reaction.CustomEmoji)?.documentId })
        refresh(true) {
            val page = stories.viewers(storyId)
            if (storyId != viewersOf) return@refresh
            avatarStore.load(page.viewers.map { it.avatarKey })
            customEmoji.requestIds(page.viewers.mapNotNull { (it.reaction as? Reaction.CustomEmoji)?.documentId })
            if (viewersState.value == known) viewersState.value = page
        }
    }

    fun moreViewers() {
        val offset = viewersState.value?.nextOffset ?: return
        if (!viewersLoadingState.value) loadViewers(viewersOf, offset)
    }

    private fun loadViewers(storyId: Int, offset: String) {
        viewersLoadingState.value = true
        work {
            try {
                val page = stories.viewers(storyId, offset)
                if (storyId != viewersOf) return@work
                avatarStore.load(page.viewers.map { it.avatarKey })
                customEmoji.requestIds(page.viewers.mapNotNull { (it.reaction as? Reaction.CustomEmoji)?.documentId })
                viewersState.value = viewersState.value?.let { it.copy(viewers = it.viewers + page.viewers, nextOffset = page.nextOffset) } ?: page
            } finally {
                if (storyId == viewersOf) viewersLoadingState.value = false
            }
        }
    }

    fun stream(item: MediaItem): VideoStream = repository.stream(item)

    private fun fillSkipped(peer: PeerStories) {
        val ids = peer.stories.filter { it.media == null }.map { it.id }
        if (ids.isNotEmpty() && filled.add(peer.key)) work { stories.fill(peer.key, ids) }
    }

    @AssistedFactory
    interface Factory {
        fun create(peerKey: String): StoriesViewModel
    }
}

private fun customEmojiIdsOf(story: StoryItem): List<Long> =
    listOfNotNull((story.reaction as? Reaction.CustomEmoji)?.documentId) + story.entities.mapNotNull { (it.type as? EntityType.CustomEmoji)?.documentId }
