package app.miogram.data.telegram

import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.Mutes
import cn.ellerotta.core.model.NotifyScope
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.ProfileInfo
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.ChatPhoto
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputStickerSetID
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeChatPhoto
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeInputChannel
import cn.ellerotta.ktproto.api.types.TypeInputFileLocation
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeInputSavedStarGift
import cn.ellerotta.ktproto.api.types.TypeInputStickerSet
import cn.ellerotta.ktproto.api.types.TypeWallPaper
import cn.ellerotta.ktproto.api.types.TypeInputUser
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.UserProfilePhoto
import cn.ellerotta.ktproto.client.PeerCache
import cn.ellerotta.core.database.DialogRow
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import java.util.concurrent.ConcurrentHashMap

const val SELF_KEY = "self"

internal sealed interface FileOrigin

internal data class MessageOrigin(val dialogKey: String, val id: Int) : FileOrigin

internal data class RichMessageOrigin(val dialogKey: String, val id: Int) : FileOrigin

internal data class StickerSetOrigin(val set: TypeInputStickerSet) : FileOrigin

internal data object RecentStickersOrigin : FileOrigin

internal data object FavedStickersOrigin : FileOrigin

internal data class EmojiStickersOrigin(val emoji: String) : FileOrigin

internal data class CustomEmojiOrigin(val documentId: Long) : FileOrigin

internal data class GiftsOrigin(val dialogKey: String?) : FileOrigin

internal data class ProfilePhotosOrigin(val dialogKey: String) : FileOrigin

internal data class ProfileMusicOrigin(val dialogKey: String) : FileOrigin

internal data object AvailableReactionsOrigin : FileOrigin

internal data object SavedGifsOrigin : FileOrigin

internal data class GifSearchOrigin(val query: String, val offset: String) : FileOrigin

internal data class StickerSearchOrigin(val query: String, val languages: List<String>, val emoticon: String = "", val emojis: Boolean = false) : FileOrigin

internal data class StoryOrigin(val peerKey: String, val storyId: Int) : FileOrigin

internal data class WebFile(val url: String, val accessHash: Long?)

internal data class AvatarRef(val peerKey: String, val photoId: Long, val dcId: Int) {
    val cacheKey: String get() = "$photoId"
}

class UnknownPeerException(key: String) : IllegalStateException("unknown dialog $key")

internal class PeerRegistry(
    private val mutes: ChatMutes? = null,
    val selfId: () -> Long? = { null },
    private val cache: () -> PeerCache?,
) {
    val topMessages = ConcurrentHashMap<String, Int>()
    val readOutbox = ConcurrentHashMap<String, Int>()
    val userNames = ConcurrentHashMap<Long, String>()

    val userEmojiStatuses = ConcurrentHashMap<Long, Long>()

    val peerNames = ConcurrentHashMap<String, String>()
    val peerAvatars = ConcurrentHashMap<String, String>()

    val muteUntil = ConcurrentHashMap<String, Int>()

    fun rememberMute(key: String, settings: PeerNotifySettings) {
        rememberMute(key, settings.muteUntil ?: DialogRow.FOLLOWS_DEFAULT)
    }

    fun rememberMute(key: String, until: Int) {
        muteUntil[key] = until
        mutes?.set(key, until)
    }

    private val defaultMutes = ConcurrentHashMap<NotifyScope, Int>()

    fun defaultMute(scope: NotifyScope): Int? = defaultMutes[scope] ?: mutes?.default(scope)?.also { defaultMutes[scope] = it }

    fun rememberDefaultMute(scope: NotifyScope, until: Int) {
        defaultMutes[scope] = until
        mutes?.setDefault(scope, until)
    }

    fun ownMute(key: String): Int? = muteUntil[key]?.takeIf { it != DialogRow.FOLLOWS_DEFAULT }

    fun muted(own: Int?, kind: PeerKind): Boolean = Mutes.muted(own, defaultMute(NotifyScope.of(kind)), System.currentTimeMillis() / 1000)

    fun forgetMutes() {
        muteUntil.clear()
        defaultMutes.clear()
        mutes?.clear()
    }

    val blocked = ConcurrentHashMap<String, Boolean>()

    val channelUsernames = ConcurrentHashMap<String, String>()

    val usernames = ConcurrentHashMap<Long, String>()

    val botIds = ConcurrentHashMap<String, Long>()

    val webFiles = ConcurrentHashMap<String, WebFile>()
    val avatarRefs = ConcurrentHashMap<String, AvatarRef>()
    val locations = ConcurrentHashMap<String, TypeInputFileLocation>()

    val inputPhotos = ConcurrentHashMap<String, InputPhoto>()
    val origins = ConcurrentHashMap<String, FileOrigin>()

    val fileDcs = ConcurrentHashMap<String, Int>()

    val inputDocuments = ConcurrentHashMap<String, InputDocument>()
    val stickerSetOf = ConcurrentHashMap<String, TypeInputStickerSet>()

    val wallpapers = ConcurrentHashMap<String, TypeWallPaper>()

    val chatWallpapers = ConcurrentHashMap<String, TypeWallPaper>()

    val wallpaperOverridden: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun rememberChatWallpaper(key: String, paper: TypeWallPaper?, overridden: Boolean) {
        if (paper == null) chatWallpapers -= key else chatWallpapers[key] = paper
        if (overridden) wallpaperOverridden += key else wallpaperOverridden -= key
    }

    val savedGifts = ConcurrentHashMap<String, TypeInputSavedStarGift>()

    val customEmoji = ConcurrentHashMap<Long, MediaItem>()

    val stickerSets = ConcurrentHashMap<String, InputStickerSetID>()

    val storiesHidden: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun inputPeer(dialogKey: String): TypeInputPeer? {
        if (dialogKey == SELF_KEY) return InputPeerSelf
        return peerOfKey(dialogKey)?.let { cache()?.inputPeer(it) }
    }

    fun peer(dialogKey: String): TypeInputPeer = inputPeer(dialogKey) ?: throw UnknownPeerException(dialogKey)

    fun inputUser(userId: Long): TypeInputUser? = cache()?.inputUser(userId)

    fun user(dialogKey: String): TypeInputUser =
        (peerOfKey(dialogKey) as? PeerUser)?.let { inputUser(it.userId) } ?: throw UnknownPeerException(dialogKey)

    fun inputChannel(channelId: Long): TypeInputChannel? = cache()?.inputChannel(channelId)

    fun channel(dialogKey: String): TypeInputChannel =
        (peerOfKey(dialogKey) as? PeerChannel)?.let { inputChannel(it.channelId) } ?: throw UnknownPeerException(dialogKey)

    fun rememberUsers(users: List<TypeUser>) {
        val now = System.currentTimeMillis() / 1000
        users.filterIsInstance<User>().forEach { user ->
            userNames[user.id] = user.firstName?.ifBlank { null } ?: nameOf(user)
            val key = "u${user.id}"
            peerNames[key] = nameOf(user)
            user.username?.let { usernames[user.id] = it }
            avatarKey(avatarOf(key, user))?.let { peerAvatars[key] = it }
            if (!user.min) {
                rememberStoriesHidden(key, user.storiesHidden)
                val status = emojiStatusId(user.emojiStatus, now)
                if (status == null) userEmojiStatuses -= user.id else userEmojiStatuses[user.id] = status
            }
        }
    }

    fun rememberChats(chats: List<TypeChat>) {
        chats.forEach { chat ->
            val key = when (chat) {
                is Channel -> "ch${chat.id}"
                is Chat -> "c${chat.id}"
                else -> return@forEach
            }
            peerNames[key] = titleOf(chat)
            (chat as? Channel)?.username?.let { channelUsernames[key] = it }
            (chat as? Channel)?.takeIf { !it.min }?.let { rememberStoriesHidden(key, it.storiesHidden) }
            val photo = when (chat) {
                is Channel -> photoRef(key, chat.photo)
                is Chat -> photoRef(key, chat.photo)
                else -> null
            }
            avatarKey(photo)?.let { peerAvatars[key] = it }
        }
    }

    private fun rememberStoriesHidden(key: String, hidden: Boolean) {
        if (hidden) storiesHidden += key else storiesHidden -= key
    }

    fun remember(chats: List<TypeChat>) {
        cache()?.remember(emptyList(), chats)
    }

    fun register(item: DialogItem, avatar: AvatarRef?): DialogItem = item.copy(avatarKey = avatarKey(avatar))

    fun avatarKey(avatar: AvatarRef?): String? = avatar?.also { avatarRefs[it.cacheKey] = it }?.cacheKey

    fun profileOf(user: TypeUser): ProfileInfo {
        val u = user as? User ?: return ProfileInfo(0, "unknown", null, null, null)
        return ProfileInfo(u.id, nameOf(u), u.phone, u.username, avatarKey(avatarOf(SELF_KEY, u)))
    }
}

internal fun avatarOf(peerKey: String, user: User): AvatarRef? =
    (user.photo as? UserProfilePhoto)?.let { AvatarRef(peerKey, it.photoId, it.dcId) }

internal fun photoRef(peerKey: String, photo: TypeChatPhoto): AvatarRef? =
    (photo as? ChatPhoto)?.let { AvatarRef(peerKey, it.photoId, it.dcId) }
