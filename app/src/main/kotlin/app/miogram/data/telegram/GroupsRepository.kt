package app.miogram.data.telegram

import cn.ellerotta.core.model.CHAT_ABOUT_MAX
import cn.ellerotta.core.model.CreatedChat
import cn.ellerotta.core.model.MissingInvitee
import cn.ellerotta.core.model.UsernameAvailability
import cn.ellerotta.core.model.chatTitleOf
import cn.ellerotta.ktproto.api.functions.channels.CheckUsername
import cn.ellerotta.ktproto.api.functions.channels.CreateChannel
import cn.ellerotta.ktproto.api.functions.channels.EditPhoto
import cn.ellerotta.ktproto.api.functions.channels.EditTitle
import cn.ellerotta.ktproto.api.functions.channels.InviteToChannel
import cn.ellerotta.ktproto.api.functions.channels.UpdateUsername
import cn.ellerotta.ktproto.api.functions.messages.AddChatUser
import cn.ellerotta.ktproto.api.functions.messages.EditChatAbout
import cn.ellerotta.ktproto.api.functions.messages.EditChatPhoto
import cn.ellerotta.ktproto.api.functions.messages.EditChatTitle
import cn.ellerotta.ktproto.api.functions.messages.SendMessage
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.InputChatPhotoEmpty
import cn.ellerotta.ktproto.api.types.InputChatUploadedPhoto
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeInputFile
import cn.ellerotta.ktproto.api.types.TypeUpdates
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.UpdatesCombined
import cn.ellerotta.ktproto.api.types.messages.InvitedUsers
import cn.ellerotta.ktproto.client.Upload
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.uploadFile
import cn.ellerotta.ktproto.session.RpcException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class GroupsRepository @Inject constructor(
    private val telegram: TelegramClientHolder,
    private val invites: InvitesRepository,
) {
    private val registry get() = telegram.peers

    suspend fun createGroup(title: String): CreatedChat = create(title, "", broadcast = false)

    suspend fun createChannel(title: String, about: String): CreatedChat = create(title, about, broadcast = true)

    private suspend fun create(title: String, about: String, broadcast: Boolean): CreatedChat {
        val name = chatTitleOf(title)
        val updates = telegram.client().invoke(CreateChannel(broadcast = broadcast, megagroup = !broadcast, title = name, about = about.trim().take(CHAT_ABOUT_MAX)))
        val channel = chatsOf(updates).filterIsInstance<Channel>().firstOrNull() ?: error("telegram made no channel")
        registry.remember(listOf(channel))
        registry.rememberChats(listOf(channel))
        telegram.updates.value?.rememberChannel(channel.id, channel.accessHash, null)
        return CreatedChat("ch${channel.id}", channel.title)
    }

    suspend fun invite(dialogKey: String, userKeys: List<String>): List<MissingInvitee> {
        if (userKeys.isEmpty()) return emptyList()
        val missing = mutableListOf<MissingInvitee>()
        when (val peer = peerOfKey(dialogKey)) {
            is PeerChannel -> userKeys.chunked(INVITE_BATCH).forEach { batch -> missing += inviteBatch(dialogKey, batch) }
            is PeerChat -> userKeys.forEach { key ->
                try {
                    missing += missingOf(telegram.client().invoke(AddChatUser(chatId = peer.chatId, userId = registry.user(key), fwdLimit = FORWARD_LIMIT)))
                } catch (e: RpcException) {
                    when (e.errorMessage) {
                        "USER_ALREADY_PARTICIPANT" -> Unit
                        "USER_PRIVACY_RESTRICTED" -> missing += MissingInvitee(key, peerName(key))
                        else -> throw e
                    }
                }
            }
            else -> error("not a group or channel: $dialogKey")
        }
        return missing
    }

    private suspend fun inviteBatch(dialogKey: String, keys: List<String>): List<MissingInvitee> {
        val channel = registry.channel(dialogKey)
        return try {
            missingOf(telegram.client().invoke(InviteToChannel(channel, keys.map { registry.user(it) })))
        } catch (e: RpcException) {
            if (e.errorMessage != "USER_ALREADY_PARTICIPANT" || keys.size == 1) throw e
            keys.flatMap { key ->
                try {
                    missingOf(telegram.client().invoke(InviteToChannel(channel, listOf(registry.user(key)))))
                } catch (single: RpcException) {
                    if (single.errorMessage != "USER_ALREADY_PARTICIPANT") throw single
                    emptyList()
                }
            }
        }
    }

    private fun missingOf(result: InvitedUsers): List<MissingInvitee> = result.missingInvitees.map { missing ->
        MissingInvitee("u${missing.userId}", peerName("u${missing.userId}"), missing.premiumWouldAllowInvite)
    }

    private fun peerName(key: String): String = registry.peerNames[key].orEmpty()

    suspend fun sendInviteLink(dialogKey: String, userKeys: List<String>) {
        val link = invites.links(dialogKey).primary?.link ?: error("no invite link for $dialogKey")
        userKeys.forEach { key ->
            telegram.client().invoke(SendMessage(peer = registry.peer(key), message = link, randomId = Random.nextLong()))
        }
    }

    suspend fun checkUsername(dialogKey: String, username: String): UsernameAvailability = try {
        if (telegram.client().invoke(CheckUsername(registry.channel(dialogKey), username))) UsernameAvailability.Available else UsernameAvailability.Taken
    } catch (e: RpcException) {
        when (e.errorMessage) {
            "USERNAME_INVALID" -> UsernameAvailability.Invalid
            "USERNAME_OCCUPIED", "USERNAME_PURCHASE_AVAILABLE" -> UsernameAvailability.Taken
            "CHANNELS_ADMIN_PUBLIC_TOO_MUCH" -> UsernameAvailability.TooManyPublic
            else -> throw e
        }
    }

    suspend fun setUsername(dialogKey: String, username: String) {
        telegram.client().invoke(UpdateUsername(registry.channel(dialogKey), username.trim()))
    }

    suspend fun setTitle(dialogKey: String, title: String) {
        val name = chatTitleOf(title)
        when (val peer = peerOfKey(dialogKey)) {
            is PeerChannel -> telegram.client().invoke(EditTitle(registry.channel(dialogKey), name))
            is PeerChat -> telegram.client().invoke(EditChatTitle(peer.chatId, name))
            else -> error("not a group or channel: $dialogKey")
        }
    }

    suspend fun setAbout(dialogKey: String, about: String) {
        telegram.client().invoke(EditChatAbout(registry.peer(dialogKey), about.trim().take(CHAT_ABOUT_MAX)))
    }

    suspend fun setPhoto(dialogKey: String, photo: ByteArray?) {
        val input = if (photo == null) InputChatPhotoEmpty else InputChatUploadedPhoto(file = upload(photo))
        when (val peer = peerOfKey(dialogKey)) {
            is PeerChannel -> telegram.client().invoke(EditPhoto(registry.channel(dialogKey), input))
            is PeerChat -> telegram.client().invoke(EditChatPhoto(peer.chatId, input))
            else -> error("not a group or channel: $dialogKey")
        }
    }

    private suspend fun upload(photo: ByteArray): TypeInputFile {
        var uploaded: TypeInputFile? = null
        telegram.client().uploadFile("photo.jpg", UploadSource.of(photo)).collect { step -> if (step is Upload.Done) uploaded = step.file }
        return uploaded ?: error("the upload ended without a file")
    }

    private companion object {
        const val INVITE_BATCH = 100

        const val FORWARD_LIMIT = 100
    }
}

private fun chatsOf(updates: TypeUpdates): List<TypeChat> = when (updates) {
    is Updates -> updates.chats
    is UpdatesCombined -> updates.chats
    else -> emptyList()
}
