package cn.ellerotta.core.model

sealed interface Presence {
    data object Online : Presence

    data class Offline(val wasOnline: Int) : Presence

    data object Recently : Presence
    data object LastWeek : Presence
    data object LastMonth : Presence
    data object LongAgo : Presence
    data object Bot : Presence

    data class Members(val count: Int, val online: Int?, val channel: Boolean) : Presence

    data object Unknown : Presence
}

data class Birthday(val day: Int, val month: Int, val year: Int?)

fun bigPhotoKey(avatarKey: String): String = "$avatarKey:big"

enum class MuteDuration(val seconds: Int?) {
    Hour(3_600),
    EightHours(8 * 3_600),
    Day(86_400),
    Week(7 * 86_400),
    Forever(null),
}

data class ProfileDetails(
    val key: String,
    val id: Long,
    val name: String,
    val kind: PeerKind,
    val presence: Presence = Presence.Unknown,
    val avatarKey: String? = null,
    val phone: String? = null,
    val usernames: List<String> = emptyList(),
    val firstName: String = "",
    val lastName: String = "",
    val usernameList: List<OwnUsername> = emptyList(),
    val about: String? = null,
    val birthday: Birthday? = null,
    val verified: Boolean = false,
    val premium: Boolean = false,
    val colorIndex: Int = 0,
    val muted: Boolean = false,
    val isSelf: Boolean = false,
    val member: Boolean = false,
    val canInvite: Boolean = false,
    val requestsPending: Int = 0,
    val canEdit: Boolean = false,
    val canViewMembers: Boolean = false,
    val emojiStatusId: Long? = null,
    val patternEmojiId: Long? = null,
    val collectibleGiftId: Long? = null,
    val music: MediaItem? = null,
    val channel: PersonalChannel? = null,
    val dcId: Int? = null,
    val contact: Boolean = false,
    val hasMainApp: Boolean = false,
    val blocked: Boolean = false,
)

data class PersonalChannel(
    val key: String,
    val title: String,
    val avatarKey: String? = null,
    val verified: Boolean = false,
    val subscribers: Int? = null,
    val post: ChannelPost? = null,
)

data class ChannelPost(val id: Int, val date: Int, val text: String, val entities: List<TextEntity> = emptyList(), val media: MediaKind? = null)

data class SessionInfo(
    val hash: Long,
    val device: String,
    val platform: String,
    val system: String,
    val app: String,
    val appVersion: String,
    val location: String,
    val ip: String,
    val dateActive: Int,
    val current: Boolean,
    val official: Boolean,
)

enum class ThemeMode { System, Light, Dark }

data class Appearance(val mode: ThemeMode = ThemeMode.System, val dynamicColor: Boolean = true)
