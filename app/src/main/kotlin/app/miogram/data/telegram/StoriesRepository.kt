package app.miogram.data.telegram

import app.miogram.data.MediaFileInfo
import app.miogram.di.ApplicationScope
import cn.ellerotta.core.model.DownloadState
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.Reaction
import cn.ellerotta.core.model.StoryAudience
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.model.StoryRing
import cn.ellerotta.core.model.StoryViewer
import cn.ellerotta.core.model.StoryViewersPage
import cn.ellerotta.core.model.ordered
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.functions.stories.CanSendStory
import cn.ellerotta.ktproto.api.functions.stories.DeleteStories
import cn.ellerotta.ktproto.api.functions.stories.EditStory
import cn.ellerotta.ktproto.api.functions.stories.GetAllStories
import cn.ellerotta.ktproto.api.functions.stories.GetPeerStories
import cn.ellerotta.ktproto.api.functions.stories.GetStoryViewsList
import cn.ellerotta.ktproto.api.functions.stories.IncrementStoryViews
import cn.ellerotta.ktproto.api.functions.stories.ReadStories
import cn.ellerotta.ktproto.api.functions.stories.SendReaction
import cn.ellerotta.ktproto.api.functions.stories.SendStory
import cn.ellerotta.ktproto.api.functions.stories.TogglePeerStoriesHidden
import cn.ellerotta.ktproto.api.functions.stories.TogglePinned
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowAll
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowCloseFriends
import cn.ellerotta.ktproto.api.types.InputPrivacyValueAllowContacts
import cn.ellerotta.ktproto.api.types.InputReplyToStory
import cn.ellerotta.ktproto.api.types.ReactionEmpty
import cn.ellerotta.ktproto.api.types.StoryItemDeleted
import cn.ellerotta.ktproto.api.types.StoryView
import cn.ellerotta.ktproto.api.types.TypeInputFile
import cn.ellerotta.ktproto.api.types.TypeInputMedia
import cn.ellerotta.ktproto.api.types.TypeInputPrivacyRule
import cn.ellerotta.ktproto.api.types.TypeUpdates
import cn.ellerotta.ktproto.api.types.UpdateStory
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.UpdatesCombined
import cn.ellerotta.ktproto.api.types.stories.AllStories
import cn.ellerotta.ktproto.api.types.stories.AllStoriesNotModified
import cn.ellerotta.ktproto.client.Upload
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.uploadFile
import cn.ellerotta.ktproto.session.RpcException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class StoriesRepository @Inject constructor(
    private val telegram: TelegramClientHolder,
    private val updates: UpdatesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val registry get() = telegram.peers
    private val loads = Mutex()
    private val lock = Any()

    private var peers: Map<String, PeerStories> = emptyMap()

    private val outsideKeys = HashSet<String>()

    private val states = ConcurrentHashMap<Boolean, String>()

    @Volatile private var restored = false

    private val storiesFlow = MutableStateFlow<List<PeerStories>>(emptyList())
    private val hiddenFlow = MutableStateFlow<List<PeerStories>>(emptyList())
    private val ringsFlow = MutableStateFlow<Map<String, StoryRing>>(emptyMap())
    private val selfFlow = MutableStateFlow<PeerStories?>(null)
    private val outsideFlow = MutableStateFlow<Map<String, PeerStories>>(emptyMap())

    val stories: StateFlow<List<PeerStories>> = storiesFlow.asStateFlow()

    val hidden: StateFlow<List<PeerStories>> = hiddenFlow.asStateFlow()

    val outside: StateFlow<Map<String, PeerStories>> = outsideFlow.asStateFlow()

    val rings: StateFlow<Map<String, StoryRing>> = ringsFlow.asStateFlow()

    val self: StateFlow<PeerStories?> = selfFlow.asStateFlow()

    init {
        scope.launch { updates.events().collect { onEvent(it) } }
    }

    suspend fun stored() = loads.withLock { restore() }

    suspend fun load(hidden: Boolean = false) = loads.withLock {
        restore()
        val client = telegram.client()
        val first = client.invoke(GetAllStories(hidden = hidden, state = states[hidden] ?: telegram.cache?.storyState(hidden)))
        if (first is AllStoriesNotModified) {
            keepState(hidden, first.state)
            return@withLock
        }
        val pages = mutableListOf(first as AllStories)
        while (pages.last().hasMore) {
            pages += client.invoke(GetAllStories(next = true, hidden = hidden, state = pages.last().state)) as? AllStories ?: break
        }
        val stories = pages.flatMap { it.peerStories }
        val users = pages.flatMap { it.users }
        val chats = pages.flatMap { it.chats }
        registry.rememberUsers(users)
        registry.rememberChats(chats)
        telegram.cache?.putStories(hidden, stories, users, chats, replace = true)
        keepState(hidden, pages.last().state)
        val selfId = telegram.userId
        val listed = stories.map { registry.peerStoriesOf(it, hidden, selfId) }.filter { it.stories.isNotEmpty() }
        change { current ->
            outsideKeys -= listed.mapTo(HashSet()) { it.key }
            current.filterValues { it.hidden != hidden || it.key in outsideKeys } + listed.associateBy { it.key }
        }
    }

    suspend fun peer(key: String): PeerStories? {
        val answer = telegram.client().invoke(GetPeerStories(telegram.storyPeer(key)))
        registry.rememberUsers(answer.users)
        registry.rememberChats(answer.chats)
        val hidden = synchronized(lock) { peers[key]?.hidden } ?: (key in registry.storiesHidden)
        telegram.cache?.putPeerStories(answer.stories, hidden, answer.users, answer.chats)
        val item = registry.peerStoriesOf(answer.stories, hidden, telegram.userId)
        change { current ->
            if (item.key !in current) outsideKeys += item.key
            if (item.stories.isEmpty()) current - item.key else current + (item.key to item)
        }
        return item.takeIf { it.stories.isNotEmpty() }
    }

    suspend fun fill(key: String, ids: List<Int>) {
        if (ids.isEmpty()) return
        val items = telegram.storiesById(key, ids)
        val deleted = items.filterIsInstance<StoryItemDeleted>().mapTo(HashSet()) { it.id }
        val full = items.mapNotNull { registry.storyOf(key, it) }
        change { current ->
            val peer = current[key] ?: return@change current
            val left = full.fold(peer.stories.filter { it.id !in deleted }) { stories, story -> stories.merged(story, min = false) }
            if (left.isEmpty()) current - key else current + (key to peer.copy(stories = left))
        }
    }

    suspend fun read(key: String, maxId: Int) {
        change { current -> current[key]?.let { current + (key to it.copy(maxReadId = maxOf(it.maxReadId, maxId))) } ?: current }
        telegram.cache?.readStories(key, maxId)
        telegram.client().invoke(ReadStories(telegram.storyPeer(key), maxId))
    }

    suspend fun view(key: String, ids: List<Int>) {
        val peer = telegram.storyPeer(key)
        ids.distinct().chunked(MAX_VIEWS).forEach { chunk -> telegram.client().invoke(IncrementStoryViews(peer, chunk)) }
    }

    suspend fun setHidden(key: String, hidden: Boolean) {
        telegram.client().invoke(TogglePeerStoriesHidden(registry.peer(key), hidden))
        if (hidden) registry.storiesHidden += key else registry.storiesHidden -= key
        change { current -> current[key]?.let { current + (key to it.copy(hidden = hidden)) } ?: current }
        telegram.cache?.hideStories(key, hidden)
    }

    suspend fun react(key: String, storyId: Int, reaction: Reaction?) {
        val sent = reaction?.let(::inputReactionOf) ?: ReactionEmpty
        telegram.client().invoke(SendReaction(addToRecent = reaction != null, peer = telegram.storyPeer(key), storyId = storyId, reaction = sent))
        reacted(key, storyId, reaction)
        telegram.cache?.changeStories(key) { it.reacted(storyId, sent) }
    }

    suspend fun reply(key: String, storyId: Int, text: String) {
        val peer = registry.peer(key)
        telegram.client().invoke(SendMessage(peer = peer, replyTo = InputReplyToStory(peer, storyId), message = text, randomId = Random.nextLong()))
    }

    suspend fun viewers(storyId: Int, offset: String = "", limit: Int = 50): StoryViewersPage {
        val answer = telegram.client().invoke(GetStoryViewsList(peer = InputPeerSelf, id = storyId, offset = offset, limit = limit))
        registry.rememberUsers(answer.users)
        registry.rememberChats(answer.chats)
        val viewers = answer.views.filterIsInstance<StoryView>().map { view ->
            val key = "u${view.userId}"
            StoryViewer(key, registry.peerNames[key].orEmpty(), registry.peerAvatars[key], view.userId, view.date, view.reaction?.let(::reactionOf))
        }
        return StoryViewersPage(viewers, answer.viewsCount, answer.reactionsCount, answer.nextOffset?.takeIf { it.isNotEmpty() })
            .also { if (offset.isEmpty()) telegram.memory["viewers:$storyId"] = it }
    }

    fun storedViewers(storyId: Int): StoryViewersPage? = telegram.memory["viewers:$storyId"]

    private val canPostState = MutableStateFlow(false)

    val canPost: StateFlow<Boolean> = canPostState.asStateFlow()

    suspend fun refreshCanPost(): Boolean {
        val can = try {
            telegram.client().invoke(CanSendStory(InputPeerSelf)).countRemains > 0
        } catch (e: RpcException) {
            if (e.errorMessage == "PREMIUM_ACCOUNT_REQUIRED" || e.errorMessage == "STORIES_TOO_MUCH") false else throw e
        }
        canPostState.value = can
        return can
    }

    private suspend fun refreshCanPostQuietly() {
        try {
            refreshCanPost()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    fun post(media: StoryUpload, caption: String, audience: StoryAudience, period: Int = DEFAULT_PERIOD): Flow<DownloadState.Progress> = flow {
        val answer = telegram.client().invoke(
            SendStory(
                peer = InputPeerSelf,
                media = uploaded(media),
                caption = caption.ifEmpty { null },
                privacyRules = listOf(ruleOf(audience)),
                randomId = Random.nextLong(),
                period = period.takeIf { it != DEFAULT_PERIOD },
            ),
        )
        applied(answer)
        refreshCanPostQuietly()
    }

    fun edit(storyId: Int, caption: String? = null, audience: StoryAudience? = null, media: StoryUpload? = null): Flow<DownloadState.Progress> = flow {
        val answer = telegram.client().invoke(
            EditStory(
                peer = InputPeerSelf,
                id = storyId,
                media = media?.let { uploaded(it) },
                caption = caption,
                entities = caption?.let { emptyList() },
                privacyRules = audience?.let { listOf(ruleOf(it)) },
            ),
        )
        applied(answer)
    }

    suspend fun delete(ids: List<Int>) {
        val deleted = telegram.client().invoke(DeleteStories(InputPeerSelf, ids)).toSet()
        val key = selfKey() ?: return
        change { current ->
            val peer = current[key] ?: return@change current
            val left = peer.stories.filter { it.id !in deleted }
            if (left.isEmpty()) current - key else current + (key to peer.copy(stories = left))
        }
        telegram.cache?.changeStories(key) { it.without(deleted) }
        refreshCanPostQuietly()
    }

    suspend fun pin(ids: List<Int>, pinned: Boolean) {
        val changed = telegram.client().invoke(TogglePinned(InputPeerSelf, ids, pinned)).toSet()
        val key = selfKey() ?: return
        change { current ->
            val peer = current[key] ?: return@change current
            current + (key to peer.copy(stories = peer.stories.map { if (it.id in changed) it.copy(pinned = pinned) else it }))
        }
        telegram.cache?.changeStories(key) { it.pinned(changed, pinned) }
    }

    private suspend fun FlowCollector<DownloadState.Progress>.uploaded(media: StoryUpload): TypeInputMedia {
        var uploaded: TypeInputFile? = null
        telegram.client().uploadFile(media.name, media.source).collect { step ->
            when (step) {
                is Upload.Progress -> emit(DownloadState.Progress(step.uploaded, step.total))
                is Upload.Done -> uploaded = step.file
            }
        }
        val file = uploaded ?: error("the upload ended without a file")
        val info = media.info
        return if (media.mimeType.startsWith("video/")) {
            InputMediaUploadedDocument(
                file = file,
                mimeType = "video/mp4",
                attributes = listOf(DocumentAttributeVideo(supportsStreaming = true, duration = info.duration, w = info.width, h = info.height)),
            )
        } else {
            InputMediaUploadedPhoto(file = file)
        }
    }

    private fun selfKey(): String? = telegram.userId?.let { "u$it" }

    fun clear() = synchronized(lock) {
        peers = emptyMap()
        states.clear()
        restored = false
        outsideKeys.clear()
        canPostState.value = false
        publish(peers)
    }

    private suspend fun restore() {
        if (restored) return
        restored = true
        val cached = telegram.cache?.stories() ?: return
        registry.rememberUsers(cached.users)
        registry.rememberChats(cached.chats)
        val selfId = telegram.userId
        val stored = cached.peers.map { registry.peerStoriesOf(it.stories, it.hidden, selfId) }.filter { it.stories.isNotEmpty() }
        change { current -> current.ifEmpty { stored.associateBy { it.key } } }
    }

    private suspend fun keepState(hidden: Boolean, state: String) {
        states[hidden] = state
        telegram.cache?.putStoryState(hidden, state)
    }

    private fun applied(answer: TypeUpdates) {
        val (list, users, chats) = when (answer) {
            is Updates -> Triple(answer.updates, answer.users, answer.chats)
            is UpdatesCombined -> Triple(answer.updates, answer.users, answer.chats)
            else -> return
        }
        registry.rememberUsers(users)
        registry.rememberChats(chats)
        list.filterIsInstance<UpdateStory>().forEach { update ->
            val key = peerKey(update.peer)
            registry.storyOf(key, update.story)?.let { onEvent(ChatEvent.Story(key, it)) }
        }
    }

    private fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.Story -> change { current ->
                val peer = current[event.key] ?: newPeer(event.key, event.hidden)
                current + (event.key to peer.copy(stories = peer.stories.merged(event.story, event.min)))
            }
            is ChatEvent.StoryDeleted -> change { current ->
                val peer = current[event.key] ?: return@change current
                val left = peer.stories.filter { it.id != event.id }
                if (left.isEmpty()) current - event.key else current + (event.key to peer.copy(stories = left))
            }
            is ChatEvent.StoriesRead -> change { current ->
                current[event.key]?.let { current + (event.key to it.copy(maxReadId = maxOf(it.maxReadId, event.maxId))) } ?: current
            }
            is ChatEvent.StoryReaction -> reacted(event.key, event.storyId, event.reaction)
            else -> Unit
        }
    }

    private fun reacted(key: String, storyId: Int, reaction: Reaction?) = change { current ->
        val peer = current[key] ?: return@change current
        current + (key to peer.copy(stories = peer.stories.map { if (it.id == storyId) it.copy(reaction = reaction) else it }))
    }

    private fun newPeer(key: String, hidden: Boolean) = PeerStories(
        key = key,
        name = registry.peerNames[key].orEmpty(),
        avatarKey = registry.peerAvatars[key],
        peerId = peerOfKey(key)?.let(::peerIdOf) ?: 0L,
        hidden = hidden,
        self = telegram.userId?.let { key == "u$it" } == true,
    )

    private fun change(block: (Map<String, PeerStories>) -> Map<String, PeerStories>) = synchronized(lock) {
        peers = block(peers)
        outsideKeys.retainAll(peers.keys)
        publish(peers)
    }

    private fun publish(all: Map<String, PeerStories>) {
        val active = all.values.filter { it.stories.isNotEmpty() }.sortedByDescending { peer -> peer.stories.maxOf { it.date } }
        val (outsiders, listed) = active.partition { it.key in outsideKeys }
        storiesFlow.value = listed.filter { !it.hidden }.ordered()
        hiddenFlow.value = listed.filter { it.hidden }.ordered()
        outsideFlow.value = outsiders.associateBy { it.key }
        ringsFlow.value = active.associate { it.key to it.ring }
        selfFlow.value = active.firstOrNull { it.self }
    }

    private fun ruleOf(audience: StoryAudience): TypeInputPrivacyRule = when (audience) {
        StoryAudience.Everybody -> InputPrivacyValueAllowAll
        StoryAudience.Contacts -> InputPrivacyValueAllowContacts
        StoryAudience.CloseFriends -> InputPrivacyValueAllowCloseFriends
    }

    private companion object {
        const val MAX_VIEWS = 200

        const val DEFAULT_PERIOD = 86_400
    }
}

class StoryUpload(val name: String, val mimeType: String, val source: UploadSource, val info: MediaFileInfo = MediaFileInfo())

private fun List<StoryItem>.merged(story: StoryItem, min: Boolean): List<StoryItem> {
    val old = firstOrNull { it.id == story.id }
    val kept = when {
        old == null -> story
        story.media == null && old.media != null -> old
        min -> story.copy(views = old.views, reaction = old.reaction)
        else -> story
    }
    return (filter { it.id != story.id } + kept).sortedBy { it.id }
}
