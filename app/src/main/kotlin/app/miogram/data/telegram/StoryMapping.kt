package app.miogram.data.telegram

import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.PeerStories
import cn.ellerotta.core.model.StoryItem
import cn.ellerotta.core.model.StoryAudience
import cn.ellerotta.core.model.StoryViews
import cn.ellerotta.ktproto.api.functions.stories.GetStoriesByID
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowAll
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowCloseFriends
import cn.ellerotta.ktproto.api.types.PrivacyValueAllowContacts
import cn.ellerotta.ktproto.api.types.ReactionEmpty
import cn.ellerotta.ktproto.api.types.StoryItemDeleted
import cn.ellerotta.ktproto.api.types.StoryItemSkipped
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeMessageMedia
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TypePrivacyRule
import cn.ellerotta.ktproto.api.types.TypeReaction
import cn.ellerotta.ktproto.api.types.TypeStoryItem
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.PeerStories as TlPeerStories
import cn.ellerotta.ktproto.api.types.StoryItem as TlStoryItem
import cn.ellerotta.ktproto.api.types.StoryViews as TlStoryViews

internal fun PeerRegistry.storyOf(peerKey: String, story: TypeStoryItem): StoryItem? = when (story) {
    is TlStoryItem -> {
        val caption = story.caption.orEmpty()
        StoryItem(
            id = story.id,
            date = story.date,
            expireDate = story.expireDate,
            media = storyMediaOf(story.media, StoryOrigin(peerKey, story.id)),
            caption = caption,
            entities = entitiesOf(caption, story.entities.orEmpty()),
            closeFriends = story.closeFriends,
            out = story.out,
            views = story.views?.let { storyViewsOf(it) },
            reaction = story.sentReaction?.let(::reactionOf),
            pinned = story.pinned,
            audience = if (story.out) audienceOf(story) else null,
        )
    }
    is StoryItemSkipped -> StoryItem(id = story.id, date = story.date, expireDate = story.expireDate, closeFriends = story.closeFriends)
    is StoryItemDeleted -> null
}

private fun PeerRegistry.storyMediaOf(media: TypeMessageMedia, origin: StoryOrigin): MediaItem? = when (media) {
    is MessageMediaPhoto -> (media.photo as? Photo)?.let { photoItem(it, origin) }
    is MessageMediaDocument -> (media.document as? Document)?.let { documentOf(media, it, origin) }
    else -> null
}

private fun audienceOf(story: TlStoryItem): StoryAudience? {
    val rules = story.privacy
    return when {
        rules != null && rules.any { it is PrivacyValueAllowCloseFriends } -> StoryAudience.CloseFriends
        rules != null && rules.any { it is PrivacyValueAllowAll } -> StoryAudience.Everybody
        rules != null && rules.any { it is PrivacyValueAllowContacts } -> StoryAudience.Contacts
        rules != null -> null
        story.closeFriends -> StoryAudience.CloseFriends
        story.contacts -> StoryAudience.Contacts
        story.public -> StoryAudience.Everybody
        else -> null
    }
}

private fun PeerRegistry.storyViewsOf(views: TlStoryViews): StoryViews = StoryViews(
    views = views.viewsCount,
    reactions = views.reactionsCount ?: 0,
    recent = views.recentViewers.orEmpty().mapNotNull { peerAvatars["u$it"] },
    hasViewers = views.hasViewers,
)

internal fun PeerRegistry.peerStoriesOf(stories: TlPeerStories, hidden: Boolean, selfId: Long?): PeerStories {
    val key = peerKey(stories.peer)
    return PeerStories(
        key = key,
        name = peerNames[key].orEmpty(),
        avatarKey = peerAvatars[key],
        peerId = peerIdOf(stories.peer),
        stories = stories.stories.mapNotNull { storyOf(key, it) }.sortedBy { it.id },
        maxReadId = stories.maxReadId ?: 0,
        hidden = hidden,
        self = selfId != null && key == "u$selfId",
    )
}

internal fun peerIdOf(peer: TypePeer): Long = when (peer) {
    is PeerUser -> peer.userId
    is PeerChat -> peer.chatId
    is PeerChannel -> peer.channelId
}

internal fun storyIdOf(story: TypeStoryItem): Int = when (story) {
    is TlStoryItem -> story.id
    is StoryItemSkipped -> story.id
    is StoryItemDeleted -> story.id
}

internal fun TlPeerStories.merged(items: List<TypeStoryItem>): TlPeerStories {
    val byId = LinkedHashMap<Int, TypeStoryItem>()
    stories.forEach { byId[storyIdOf(it)] = it }
    items.forEach { item ->
        when (item) {
            is StoryItemDeleted -> byId.remove(item.id)
            is StoryItemSkipped -> byId.putIfAbsent(item.id, item)
            is TlStoryItem -> {
                val old = byId[item.id] as? TlStoryItem
                byId[item.id] = if (item.min && old != null) item.copied(views = old.views, sentReaction = old.sentReaction, privacy = old.privacy) else item
            }
        }
    }
    return TlPeerStories(peer = peer, maxReadId = maxReadId, stories = byId.values.sortedBy(::storyIdOf))
}

internal fun TlPeerStories.read(maxId: Int): TlPeerStories =
    if (maxId <= (maxReadId ?: 0)) this else TlPeerStories(peer = peer, maxReadId = maxId, stories = stories)

internal fun TlPeerStories.reacted(id: Int, reaction: TypeReaction?): TlPeerStories = TlPeerStories(
    peer = peer,
    maxReadId = maxReadId,
    stories = stories.map { if (it is TlStoryItem && it.id == id) it.copied(sentReaction = reaction?.takeIf { sent -> sent != ReactionEmpty }) else it },
)

internal fun TlPeerStories.without(ids: Set<Int>): TlPeerStories =
    TlPeerStories(peer = peer, maxReadId = maxReadId, stories = stories.filter { storyIdOf(it) !in ids })

internal fun TlPeerStories.pinned(ids: Set<Int>, pinned: Boolean): TlPeerStories = TlPeerStories(
    peer = peer,
    maxReadId = maxReadId,
    stories = stories.map { if (it is TlStoryItem && it.id in ids) it.copied(pinned = pinned) else it },
)

private fun TlStoryItem.copied(
    views: TlStoryViews? = this.views,
    sentReaction: TypeReaction? = this.sentReaction,
    privacy: List<TypePrivacyRule>? = this.privacy,
    pinned: Boolean = this.pinned,
) = TlStoryItem(
    pinned = pinned,
    public = this.public,
    closeFriends = closeFriends,
    min = min,
    noforwards = noforwards,
    edited = edited,
    contacts = contacts,
    selectedContacts = selectedContacts,
    out = out,
    id = id,
    date = date,
    fromId = fromId,
    fwdFrom = fwdFrom,
    expireDate = expireDate,
    caption = caption,
    entities = entities,
    media = media,
    mediaAreas = mediaAreas,
    privacy = privacy,
    views = views,
    sentReaction = sentReaction,
    albums = albums,
    music = music,
)

internal fun storiesHiddenIn(key: String, users: List<TypeUser>, chats: List<TypeChat>): Boolean =
    users.any { it is User && !it.min && "u${it.id}" == key && it.storiesHidden } ||
        chats.any { it is Channel && !it.min && "ch${it.id}" == key && it.storiesHidden }

internal fun TelegramClientHolder.storyPeer(key: String): TypeInputPeer = if (userId?.let { key == "u$it" } == true) InputPeerSelf else peers.peer(key)

internal suspend fun TelegramClientHolder.storiesById(peerKey: String, ids: List<Int>): List<TypeStoryItem> {
    val answer = client().invoke(GetStoriesByID(storyPeer(peerKey), ids))
    peers.rememberUsers(answer.users)
    peers.rememberChats(answer.chats)
    answer.stories.forEach { peers.storyOf(peerKey, it) }
    cache?.changeStories(peerKey) { it.merged(answer.stories) }
    return answer.stories
}
