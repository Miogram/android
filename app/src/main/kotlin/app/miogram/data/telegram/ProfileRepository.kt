package app.miogram.data.telegram

import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.ChannelPost
import cn.ellerotta.core.model.Membership
import cn.ellerotta.core.model.OwnUsername
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.ktproto.api.functions.account.SaveMusic
import cn.ellerotta.ktproto.api.functions.channels.GetFullChannel
import cn.ellerotta.ktproto.api.functions.channels.GetMessages as ChannelsGetMessages
import cn.ellerotta.ktproto.api.functions.messages.GetFullChat
import cn.ellerotta.ktproto.api.functions.photos.GetUserPhotos
import cn.ellerotta.ktproto.api.functions.users.GetFullUser
import cn.ellerotta.ktproto.api.functions.users.GetSavedMusic
import cn.ellerotta.ktproto.api.functions.users.GetUsers
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChannelFull
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.ChatPhoto
import cn.ellerotta.ktproto.api.types.ChatFull
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.EmojiStatus
import cn.ellerotta.ktproto.api.types.EmojiStatusCollectible
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.InputMessageID
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerColor
import cn.ellerotta.ktproto.api.types.PeerColorCollectible
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.TypeEmojiStatus
import cn.ellerotta.ktproto.api.types.TypePeerColor
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.UserFull
import cn.ellerotta.ktproto.api.types.UserProfilePhoto
import cn.ellerotta.ktproto.api.types.Username
import cn.ellerotta.ktproto.api.types.photos.Photos
import cn.ellerotta.ktproto.api.types.photos.PhotosSlice
import cn.ellerotta.ktproto.api.types.users.SavedMusic
import cn.ellerotta.ktproto.api.types.users.SavedMusicNotModified
import cn.ellerotta.ktproto.session.RpcException
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.TypePhoto
import cn.ellerotta.ktproto.api.types.messages.ChatFull as MessagesChatFull
import cn.ellerotta.ktproto.api.types.photos.TypePhotos
import cn.ellerotta.ktproto.api.types.users.UserFull as UsersUserFull
import cn.ellerotta.ktproto.tl.TlObject
import cn.ellerotta.ktproto.tl.decode
import cn.ellerotta.ktproto.tl.toByteArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ProfileRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    private val selfState = MutableStateFlow<ProfileInfo?>(null)

    val self: StateFlow<ProfileInfo?> = selfState.asStateFlow()

    suspend fun me(): ProfileInfo = registry.profileOf(telegram.client().invoke(GetUsers(listOf(InputUserSelf))).first()).also { selfState.value = it }

    private val ownState = MutableStateFlow<ProfileDetails?>(null)

    val own: StateFlow<ProfileDetails?> = ownState.asStateFlow()

    fun forget() {
        selfState.value = null
        ownState.value = null
    }

    suspend fun profile(dialogKey: String?): ProfileDetails {
        val key = dialogKey ?: SELF_KEY
        val full: TlObject = when (val peer = peerOfKey(key)) {
            is PeerChannel -> telegram.client().invoke(GetFullChannel(registry.channel(key)))
            is PeerChat -> telegram.client().invoke(GetFullChat(peer.chatId))
            else -> if (isSelf(key)) telegram.client().invoke(GetFullUser(InputUserSelf)) else if (peer is PeerUser) telegram.client().invoke(GetFullUser(registry.user(key))) else error("no profile for $dialogKey")
        }
        val (post, subscribers) = (full as? UsersUserFull)?.let { personalPost(it.fullUser, it.chats) } ?: (null to null)
        if (full is UsersUserFull) registry.blocked["u${full.fullUser.id}"] = full.fullUser.blocked
        telegram.cache?.changeProfile(key) { it.copy(full = full.toByteArray(), post = post?.toByteArray(), subscribers = subscribers) }
        telegram.loadDefaultMutes()
        return detailsOf(key, full, post, subscribers).also {
            telegram.memory["profile:$key"] = it
            if (it.isSelf) shown(it)
        }
    }

    suspend fun stored(dialogKey: String?): ProfileDetails? {
        val key = dialogKey ?: SELF_KEY
        val remembered: ProfileDetails? = telegram.memory["profile:$key"]
        val row = telegram.cache?.profile(key)
        val bytes = row?.full ?: return remembered?.also { if (it.isSelf && selfState.value == null) shown(it) }
        return runCatching {
            val full: TlObject = if (peerOfKey(key) is PeerUser || key == SELF_KEY) UsersUserFull.decode(bytes) else MessagesChatFull.decode(bytes)
            detailsOf(key, full, row.post?.let { TypeMessage.decode(it) as? Message }, row.subscribers)
        }.onFailure { telegram.log("profile: cannot read the stored $key: ${it.message}") }.getOrNull()?.also { if (it.isSelf && selfState.value == null) shown(it) }
    }

    suspend fun storedSelf(): ProfileInfo? = selfState.value ?: stored(null)?.info()

    private fun shown(details: ProfileDetails) {
        selfState.value = details.info()
        ownState.value = details
    }

    private fun ProfileDetails.info() = ProfileInfo(id, name, phone, usernames.firstOrNull(), avatarKey)

    fun isSelf(key: String): Boolean = key == SELF_KEY || telegram.userId?.let { (peerOfKey(key) as? PeerUser)?.userId == it } == true

    private fun detailsOf(key: String, full: TlObject, post: Message?, subscribers: Int?): ProfileDetails {
        val now = System.currentTimeMillis() / 1000
        val self = telegram.requireClient().userId
        val own = isSelf(key)
        return when (full) {
            is UsersUserFull -> {
                val user = full.users.filterIsInstance<User>().first { it.id == full.fullUser.id }
                registry.rememberMute("u${user.id}", full.fullUser.notifySettings)
                ProfileDetails(
                    key = "u${user.id}",
                    id = user.id,
                    name = nameOf(user),
                    kind = when {
                        user.self -> PeerKind.Saved
                        user.bot -> PeerKind.Bot
                        else -> PeerKind.User
                    },
                    presence = if (user.bot) Presence.Bot else if (user.self) Presence.Online else presenceOf(user.status, now),
                    avatarKey = registry.avatarKey(avatarOf(if (own) SELF_KEY else "u${user.id}", user)),
                    phone = user.phone,
                    usernames = activeUsernames(user.username, user.usernames),
                    firstName = user.firstName.orEmpty(),
                    lastName = user.lastName.orEmpty(),
                    usernameList = ownUsernames(user.username, user.usernames),
                    about = full.fullUser.about?.ifBlank { null },
                    birthday = full.fullUser.birthday?.let { Birthday(it.day, it.month, it.year) },
                    verified = user.verified,
                    premium = user.premium,
                    colorIndex = colorIndex((user.profileColor as? PeerColor)?.color ?: (user.color as? PeerColor)?.color, user.id),
                    muted = registry.muted(full.fullUser.notifySettings.muteUntil, PeerKind.User),
                    isSelf = user.self || user.id == self,
                    emojiStatusId = emojiStatusId(user.emojiStatus, now),
                    patternEmojiId = patternEmojiId(user.profileColor, user.color),
                    collectibleGiftId = collectibleOf(user.emojiStatus, now)?.collectibleId,
                    music = (full.fullUser.savedMusic as? Document)?.let { registry.documentOf(null, it, ProfileMusicOrigin("u${user.id}")) },
                    channel = personalChannel(full.fullUser, full.chats, post, subscribers),
                    dcId = (user.photo as? UserProfilePhoto)?.dcId,
                    contact = user.contact,
                    hasMainApp = user.bot && user.botHasMainApp,
                    blocked = registry.blocked["u${user.id}"] ?: full.fullUser.blocked,
                )
            }
            is MessagesChatFull -> when (val info = full.fullChat) {
                is ChannelFull -> {
                    val channel = full.chats.filterIsInstance<Channel>().first { it.id == info.id }
                    registry.rememberMute("ch${channel.id}", info.notifySettings)
                    ProfileDetails(
                        key = "ch${channel.id}",
                        id = channel.id,
                        name = channel.title,
                        kind = if (channel.broadcast) PeerKind.Channel else PeerKind.Group,
                        presence = Presence.Members(info.participantsCount ?: 0, info.onlineCount, channel.broadcast),
                        avatarKey = registry.avatarKey(photoRef("ch${channel.id}", channel.photo)),
                        usernames = activeUsernames(channel.username, channel.usernames),
                        about = info.about.ifBlank { null },
                        verified = channel.verified,
                        muted = registry.muted(info.notifySettings.muteUntil, if (channel.broadcast) PeerKind.Channel else PeerKind.Group),
                        member = !channel.left,
                        canInvite = !channel.left && infoOf(key, channel).canInvite,
                        canEdit = !channel.left && infoOf(key, channel).canChangeInfo,
                        requestsPending = info.requestsPending ?: 0,
                        canViewMembers = !channel.left && info.canViewParticipants,
                        colorIndex = colorIndex((channel.color as? PeerColor)?.color, channel.id),
                        emojiStatusId = emojiStatusId(channel.emojiStatus, now),
                        patternEmojiId = patternEmojiId(channel.profileColor, channel.color),
                        collectibleGiftId = collectibleOf(channel.emojiStatus, now)?.collectibleId,
                        dcId = (channel.photo as? ChatPhoto)?.dcId,
                    )
                }
                is ChatFull -> {
                    val chat = full.chats.filterIsInstance<Chat>().first { it.id == info.id }
                    registry.rememberMute("c${chat.id}", info.notifySettings)
                    ProfileDetails(
                        key = "c${chat.id}",
                        id = chat.id,
                        name = chat.title,
                        kind = PeerKind.Group,
                        presence = Presence.Members(chat.participantsCount, null, false),
                        avatarKey = registry.avatarKey(photoRef("c${chat.id}", chat.photo)),
                        about = info.about.ifBlank { null },
                        colorIndex = colorIndex(null, chat.id),
                        muted = registry.muted(info.notifySettings.muteUntil, PeerKind.Group),
                        member = !chat.left && !chat.deactivated,
                        canInvite = infoOf(key, chat).let { it.membership == Membership.Member && it.canInvite },
                        canEdit = infoOf(key, chat).let { it.membership == Membership.Member && it.canChangeInfo },
                        requestsPending = info.requestsPending ?: 0,
                        canViewMembers = !chat.left && !chat.deactivated,
                        dcId = (chat.photo as? ChatPhoto)?.dcId,
                    )
                }
                else -> error("no profile in ${info::class.simpleName}")
            }
            else -> error("no profile in ${full::class.simpleName}")
        }
    }

    private suspend fun personalPost(user: UserFull, chats: List<TypeChat>): Pair<Message?, Int?> {
        val id = user.personalChannelId ?: return null to null
        val channel = chats.filterIsInstance<Channel>().firstOrNull { it.id == id } ?: return null to null
        val input = registry.inputChannel(id)
        val post = user.personalChannelMessage?.let { messageId ->
            input ?: return@let null
            try {
                messagesOf(telegram.client().invoke(ChannelsGetMessages(input, listOf(InputMessageID(messageId))))).filterIsInstance<Message>().firstOrNull { it.id == messageId }
            } catch (e: RpcException) {
                null
            }
        }
        val subscribers = if (channel.participantsCount != null) null else input?.let {
            try {
                (telegram.client().invoke(GetFullChannel(it)).fullChat as? ChannelFull)?.participantsCount
            } catch (e: RpcException) {
                null
            }
        }
        return post to subscribers
    }

    private fun personalChannel(user: UserFull, chats: List<TypeChat>, post: Message?, subscribers: Int?): PersonalChannel? {
        val id = user.personalChannelId ?: return null
        val channel = chats.filterIsInstance<Channel>().firstOrNull { it.id == id } ?: return null
        val key = "ch$id"
        val shown = post?.takeIf { it.id == user.personalChannelMessage }?.let { ChannelPost(it.id, it.date, it.message, entitiesOf(it.message, it.entities.orEmpty()), mediaKindOf(it.media)) }
        return PersonalChannel(key, channel.title, registry.avatarKey(photoRef(key, channel.photo)), channel.verified, channel.participantsCount ?: subscribers, shown)
    }

    suspend fun photos(dialogKey: String): List<MediaItem> {
        val photos = telegram.profilePhotoList(dialogKey)
        telegram.cache?.changeProfile(dialogKey) { it.copy(photos = Photos(photos, emptyList()).toByteArray()) }
        return telegram.profilePhotoItems(dialogKey, photos)
    }

    suspend fun storedPhotos(dialogKey: String): List<MediaItem>? {
        val bytes = telegram.cache?.profile(dialogKey)?.photos ?: return null
        val photos = runCatching { TypePhotos.decode(bytes) }.getOrNull() ?: return null
        return telegram.profilePhotoItems(dialogKey, when (photos) {
            is Photos -> photos.photos
            is PhotosSlice -> photos.photos
        })
    }

    suspend fun music(dialogKey: String): List<MediaItem> = telegram.profileMusic(dialogKey)

    suspend fun moveMusic(mediaKey: String, afterKey: String?) {
        fun input(key: String) = registry.inputDocuments[key] ?: error("unknown song $key")
        telegram.client().invoke(SaveMusic(id = input(mediaKey), afterId = afterKey?.let(::input)))
    }

    suspend fun saveMusic(mediaKey: String) {
        telegram.client().invoke(SaveMusic(id = registry.inputDocuments[mediaKey] ?: error("unknown song $mediaKey")))
    }
}

internal suspend fun TelegramClientHolder.profileMusic(dialogKey: String): List<MediaItem> {
    val peer = peerOfKey(dialogKey) as? PeerUser ?: return emptyList()
    val input = if (peer.userId == requireClient().userId) InputUserSelf else peers.user(dialogKey)
    val documents = when (val result = client().invoke(GetSavedMusic(input, offset = 0, limit = PROFILE_MUSIC, hash = 0))) {
        is SavedMusic -> result.documents
        is SavedMusicNotModified -> emptyList()
    }
    val origin = ProfileMusicOrigin(dialogKey)
    return documents.filterIsInstance<Document>().map { peers.documentOf(null, it, origin) }
}

private const val PROFILE_MUSIC = 100

internal suspend fun TelegramClientHolder.profilePhotos(dialogKey: String): List<MediaItem> = profilePhotoItems(dialogKey, profilePhotoList(dialogKey))

internal suspend fun TelegramClientHolder.profilePhotoList(dialogKey: String): List<TypePhoto> = when (val peer = peerOfKey(dialogKey)) {
    is PeerUser -> {
        val input = if (peer.userId == requireClient().userId) InputUserSelf else peers.user(dialogKey)
        when (val result = client().invoke(GetUserPhotos(input, offset = 0, maxId = 0, limit = PROFILE_PHOTOS))) {
            is Photos -> result.photos
            is PhotosSlice -> result.photos
        }
    }
    is PeerChannel -> listOfNotNull((client().invoke(GetFullChannel(peers.channel(dialogKey))).fullChat as ChannelFull).chatPhoto)
    is PeerChat -> listOfNotNull((client().invoke(GetFullChat(peer.chatId)).fullChat as ChatFull).chatPhoto)
    else -> emptyList()
}

internal fun TelegramClientHolder.profilePhotoItems(dialogKey: String, photos: List<TypePhoto>): List<MediaItem> {
    val origin = ProfilePhotosOrigin(dialogKey)
    return photos.filterIsInstance<Photo>().mapNotNull { peers.photoItem(it, origin) }
}

internal fun activeUsernames(public: String?, all: List<Username>?): List<String> =
    listOfNotNull(public) + all.orEmpty().filter { it.active }.map { it.username }.filter { it != public }

internal fun ownUsernames(public: String?, all: List<Username>?): List<OwnUsername> =
    if (all.isNullOrEmpty()) listOfNotNull(public?.let { OwnUsername(it, active = true, editable = true) }) else all.map { OwnUsername(it.username, it.active, it.editable) }

private const val PROFILE_PHOTOS = 100

internal fun emojiStatusId(status: TypeEmojiStatus?, now: Long): Long? = when (status) {
    is EmojiStatus -> status.documentId.takeIf { (status.until ?: Int.MAX_VALUE) > now }
    is EmojiStatusCollectible -> status.documentId.takeIf { (status.until ?: Int.MAX_VALUE) > now }
    else -> null
}

internal fun patternEmojiId(vararg colors: TypePeerColor?): Long? = colors.firstNotNullOfOrNull { color ->
    when (color) {
        is PeerColor -> color.backgroundEmojiId
        is PeerColorCollectible -> color.backgroundEmojiId
        else -> null
    }
}

private fun collectibleOf(status: TypeEmojiStatus?, now: Long): EmojiStatusCollectible? =
    (status as? EmojiStatusCollectible)?.takeIf { (it.until ?: Int.MAX_VALUE) > now }
