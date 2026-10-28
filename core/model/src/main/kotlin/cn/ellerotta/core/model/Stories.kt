package cn.ellerotta.core.model

const val PHOTO_STORY_MILLIS = 6_000L

enum class StoryRing { None, Unread, CloseFriends, Read }

data class StoryViews(val views: Int = 0, val reactions: Int = 0, val recent: List<String> = emptyList(), val hasViewers: Boolean = false)

data class StoryItem(
    val id: Int,
    val date: Int,
    val expireDate: Int,
    val media: MediaItem? = null,
    val caption: String = "",
    val entities: List<TextEntity> = emptyList(),
    val closeFriends: Boolean = false,
    val out: Boolean = false,
    val views: StoryViews? = null,
    val reaction: Reaction? = null,
    val pinned: Boolean = false,
    val audience: StoryAudience? = null,
) {
    val isVideo: Boolean get() = media?.kind == MediaKind.Video || media?.kind == MediaKind.Gif

    val durationMillis: Long
        get() = media?.takeIf { isVideo && it.duration > 0 }?.let { it.duration * 1_000L } ?: PHOTO_STORY_MILLIS
}

data class PeerStories(
    val key: String,
    val name: String,
    val avatarKey: String? = null,
    val peerId: Long = 0,
    val stories: List<StoryItem> = emptyList(),
    val maxReadId: Int = 0,
    val hidden: Boolean = false,
    val self: Boolean = false,
) {
    val unread: Boolean get() = stories.any { it.id > maxReadId }

    val ring: StoryRing
        get() = when {
            stories.isEmpty() -> StoryRing.None
            stories.any { it.id > maxReadId && it.closeFriends } -> StoryRing.CloseFriends
            unread -> StoryRing.Unread
            else -> StoryRing.Read
        }

    val startIndex: Int get() = stories.indexOfFirst { it.id > maxReadId }.takeIf { it >= 0 } ?: 0
}

fun List<PeerStories>.ordered(): List<PeerStories> =
    sortedWith(compareByDescending<PeerStories> { it.self }.thenByDescending { it.unread })

data class StoryViewer(val key: String, val name: String, val avatarKey: String? = null, val peerId: Long = 0, val date: Int = 0, val reaction: Reaction? = null)

data class StoryViewersPage(val viewers: List<StoryViewer>, val views: Int, val reactions: Int, val nextOffset: String?)

enum class StoryAudience { Everybody, Contacts, CloseFriends }
