package app.miogram.data.telegram

import cn.ellerotta.core.model.AttachPeerType
import cn.ellerotta.core.model.ChatInvitePreview
import cn.ellerotta.core.model.DialogItem
import cn.ellerotta.core.model.ForumTopic
import cn.ellerotta.core.model.LinkTarget
import cn.ellerotta.core.model.WebAppMode
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.ktproto.api.functions.channels.GetChannels
import cn.ellerotta.ktproto.api.functions.channels.GetMessages as ChannelsGetMessages
import cn.ellerotta.ktproto.api.functions.messages.GetForumTopicsByID
import cn.ellerotta.ktproto.api.types.InputMessageID
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageActionTopicCreate
import cn.ellerotta.ktproto.api.types.MessageService
import cn.ellerotta.ktproto.api.types.ForumTopic as TlForumTopic
import cn.ellerotta.ktproto.api.types.messages.ChannelMessages
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import cn.ellerotta.ktproto.api.types.messages.TypeMessages
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.functions.contacts.ResolvePhone
import cn.ellerotta.ktproto.api.functions.contacts.ResolveUsername
import cn.ellerotta.ktproto.api.functions.messages.CheckChatInvite
import cn.ellerotta.ktproto.api.functions.messages.ImportChatInvite
import cn.ellerotta.ktproto.api.functions.messages.StartBot
import cn.ellerotta.ktproto.api.functions.users.GetFullUser
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.ChatInvite
import cn.ellerotta.ktproto.api.types.ChatInviteAlready
import cn.ellerotta.ktproto.api.types.ChatInvitePeek
import cn.ellerotta.ktproto.api.types.InputPhotoFileLocation
import cn.ellerotta.ktproto.api.types.InputStickerSetShortName
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.TypeUpdates
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.UpdatesCombined
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.contacts.ResolvedPeer
import cn.ellerotta.ktproto.api.types.messages.Chats
import cn.ellerotta.ktproto.api.types.messages.ChatsSlice
import cn.ellerotta.ktproto.client.TelegramLink
import cn.ellerotta.ktproto.client.downloadFile
import cn.ellerotta.ktproto.client.parseTelegramLink
import cn.ellerotta.ktproto.session.RpcException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class LinksRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun open(url: String): LinkTarget {
        USER_LINK.matchEntire(url.trim())?.let { return user(it.groupValues[1].toLong()) }
        return parsed(url)
    }

    private suspend fun parsed(url: String): LinkTarget = when (val link = parseTelegramLink(url)) {
        null -> if (url.trim().startsWith("tg:", ignoreCase = true)) LinkTarget.Unsupported(url.trim()) else LinkTarget.External(browsable(url))
        is TelegramLink.Username -> {
            val target = chat(telegram.client().invoke(ResolveUsername(link.username)), link.profile, draftOf(link.text))
            target.copy(startParameter = link.start.takeIf { target.dialog.kind == PeerKind.Bot })
        }
        is TelegramLink.Referral -> chat(telegram.client().invoke(ResolveUsername(link.bot, referer = link.referrer)))
        is TelegramLink.PhoneNumber -> chat(telegram.client().invoke(ResolvePhone(link.phone)), link.profile, draftOf(link.text))
        is TelegramLink.Message -> message(link)
        is TelegramLink.ChatInvite -> invite(link.hash)
        is TelegramLink.StickerSet -> LinkTarget.StickerSet(telegram.stickerSet(InputStickerSetShortName(link.slug)) ?: error("sticker set ${link.slug} not found"))
        is TelegramLink.Share -> LinkTarget.Share(listOfNotNull(link.url.trim(), link.text).joinToString("\n"))
        is TelegramLink.LoginCode -> LinkTarget.LoginCode(link.code)
        is TelegramLink.WebApp -> {
            val target = chat(telegram.client().invoke(ResolveUsername(link.bot)))
            if (target.dialog.kind != PeerKind.Bot) target else LinkTarget.BotApp(target.dialog, link.appName, link.startParameter, WebAppMode.of(link.mode))
        }
        is TelegramLink.MainWebApp -> {
            val resolved = telegram.client().invoke(ResolveUsername(link.bot))
            val target = chat(resolved)
            val bot = resolved.users.filterIsInstance<User>().firstOrNull { "u${it.id}" == target.dialog.key }
            if (bot?.bot == true && bot.botHasMainApp) LinkTarget.MainApp(target.dialog, link.startParameter, WebAppMode.of(link.mode)) else target
        }
        is TelegramLink.AttachMenu -> {
            val target = chat(telegram.client().invoke(ResolveUsername(link.bot)))
            if (target.dialog.kind != PeerKind.Bot) {
                target
            } else {
                val chat = link.chatUsername?.let { chat(telegram.client().invoke(ResolveUsername(it))).dialog }
                    ?: link.chatPhone?.let { chat(telegram.client().invoke(ResolvePhone(it))).dialog }
                LinkTarget.AttachApp(target.dialog, link.startParameter, chat, link.choose.mapNotNullTo(HashSet(), ::attachPeerTypeOf))
            }
        }
        else -> LinkTarget.Unsupported(url.trim())
    }

    private fun attachPeerTypeOf(word: String): AttachPeerType? = when (word) {
        "users" -> AttachPeerType.Users
        "bots" -> AttachPeerType.Bots
        "groups" -> AttachPeerType.Groups
        "channels" -> AttachPeerType.Channels
        else -> null
    }

    private suspend fun user(id: Long): LinkTarget {
        val full = telegram.client().invoke(GetFullUser(registry.user("u$id")))
        val users = full.users.filterIsInstance<User>().associateBy { it.id }
        return LinkTarget.Chat(registry.dialogOf(PeerUser(id), users, emptyMap(), System.currentTimeMillis() / 1000) ?: error("unknown user $id"))
    }

    suspend fun joinInvite(hash: String): DialogItem {
        val updates = joinedUpdates(telegram.client().invoke(ImportChatInvite(hash)))
        val chat = chatsOf(updates).firstOrNull() ?: error("no chat in the answer")
        return chatItem(chat)
    }

    suspend fun startBot(dialogKey: String, parameter: String) {
        telegram.client().invoke(StartBot(registry.user(dialogKey), registry.peer(dialogKey), Random.nextLong(), parameter))
    }

    private suspend fun message(link: TelegramLink.Message): LinkTarget {
        val target = chatOf(link)
        return if (target is LinkTarget.Chat && target.dialog.forum) inTopic(target, link) else target
    }

    private suspend fun inTopic(target: LinkTarget.Chat, link: TelegramLink.Message): LinkTarget.Chat {
        val key = target.dialog.key
        val topic = link.thread ?: run {
            val result = telegram.client().invoke(ChannelsGetMessages(registry.channel(key), listOf(InputMessageID(link.id))))
            val message = messagesOf(result).firstOrNull { idOf(it) == link.id }
            when {
                message is MessageService && message.action is MessageActionTopicCreate -> message.id
                message is Message -> topicOf(message.replyTo)
                message is MessageService -> topicOf(message.replyTo)
                else -> null
            } ?: ForumTopic.GENERAL_TOPIC
        }
        val title = try {
            telegram.client().invoke(GetForumTopicsByID(registry.peer(key), listOf(topic))).topics.filterIsInstance<TlForumTopic>().firstOrNull()?.title
        } catch (e: RpcException) {
            null
        }
        return target.copy(topicId = topic, topicTitle = title, messageId = link.id.takeIf { it != topic })
    }

    private suspend fun chatOf(link: TelegramLink.Message): LinkTarget = when (val chat = link.chat) {
        is TelegramLink.ChatRef.Public -> chat(telegram.client().invoke(ResolveUsername(chat.username))).copy(messageId = link.id)
        is TelegramLink.ChatRef.Private -> {
            val input = registry.inputChannel(chat.channelId)
            val channel = input?.let {
                try {
                    channelsOf(telegram.client().invoke(GetChannels(listOf(it)))).firstOrNull()
                } catch (e: RpcException) {
                    if (e.code != 400 && e.code != 406) throw e
                    null
                }
            }
            if (channel == null || channel.left) LinkTarget.PrivateChat else LinkTarget.Chat(chatItem(channel), messageId = link.id)
        }
    }

    private suspend fun invite(hash: String): LinkTarget = when (val invite = telegram.client().invoke(CheckChatInvite(hash))) {
        is ChatInviteAlready -> LinkTarget.Chat(chatItem(invite.chat))
        is ChatInvitePeek -> LinkTarget.Invite(ChatInvitePreview(hash, titleOf(invite.chat), participants = participantsOf(invite.chat), channel = (invite.chat as? Channel)?.broadcast == true))
        is ChatInvite -> LinkTarget.Invite(
            ChatInvitePreview(
                hash = hash,
                title = invite.title,
                about = invite.about?.ifBlank { null },
                participants = invite.participantsCount,
                channel = invite.broadcast,
                requestNeeded = invite.requestNeeded,
                photo = (invite.photo as? Photo)?.let { runCatching { smallPhoto(it) }.getOrNull() },
            ),
        )
    }

    private fun chat(resolved: ResolvedPeer, profile: Boolean = false, draft: String? = null): LinkTarget.Chat {
        val users = resolved.users.filterIsInstance<User>().associateBy { it.id }
        val chats = resolved.chats.associateBy { chatId(it) }
        val item = registry.dialogOf(resolved.peer, users, chats, System.currentTimeMillis() / 1000) ?: error("unknown peer in the answer")
        rememberChannel(resolved.peer, chats)
        return LinkTarget.Chat(item, profile = profile, draft = draft)
    }

    private fun chatItem(chat: TypeChat): DialogItem {
        val peer: TypePeer = if (chat is Channel) PeerChannel(chat.id) else PeerChat(chatId(chat))
        val chats = mapOf(chatId(chat) to chat)
        registry.remember(listOf(chat))
        rememberChannel(peer, chats)
        return registry.dialogOf(peer, emptyMap(), chats, System.currentTimeMillis() / 1000) ?: error("unknown chat ${chatId(chat)}")
    }

    private fun rememberChannel(peer: TypePeer, chats: Map<Long, TypeChat>) {
        val channel = (peer as? PeerChannel)?.let { chats[it.channelId] } as? Channel ?: return
        telegram.updates.value?.rememberChannel(channel.id, channel.accessHash, null)
    }

    private suspend fun smallPhoto(photo: Photo): ByteArray? {
        val size = photo.sizes.filterIsInstance<PhotoSize>().minByOrNull { it.w * it.h } ?: return null
        val out = ByteArrayOutputStream()
        val location = InputPhotoFileLocation(photo.id, photo.accessHash, photo.fileReference, size.type)
        telegram.client().downloadFile(location, photo.dcId, size.size.toLong()).collect { out.write(it.bytes) }
        return out.toByteArray()
    }
}

private fun participantsOf(chat: TypeChat): Int = when (chat) {
    is Channel -> chat.participantsCount ?: 0
    is Chat -> chat.participantsCount
    else -> 0
}

private fun chatsOf(updates: TypeUpdates): List<TypeChat> = when (updates) {
    is Updates -> updates.chats
    is UpdatesCombined -> updates.chats
    else -> emptyList()
}

private fun channelsOf(result: cn.ellerotta.ktproto.api.types.messages.TypeChats): List<Channel> = when (result) {
    is Chats -> result.chats
    is ChatsSlice -> result.chats
}.filterIsInstance<Channel>()

internal fun draftOf(text: String?): String? {
    val draft = text?.takeIf { it.isNotBlank() } ?: return null
    val spaced = if (draft.startsWith("@")) " $draft" else draft
    val count = spaced.codePointCount(0, spaced.length)
    return if (count <= MAX_DRAFT) spaced else spaced.substring(0, spaced.offsetByCodePoints(0, MAX_DRAFT))
}

internal fun browsable(url: String): String {
    val trimmed = url.trim()
    val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed) || trimmed.startsWith("mailto:") || trimmed.startsWith("tel:")
    return if (scheme) trimmed else "https://$trimmed"
}

private const val MAX_DRAFT = 4096

private val USER_LINK = Regex("tg://user\\?id=(\\d+)", RegexOption.IGNORE_CASE)

internal fun messagesOf(result: TypeMessages): List<TypeMessage> = when (result) {
    is Messages -> result.messages
    is MessagesSlice -> result.messages
    is ChannelMessages -> result.messages
    else -> emptyList()
}
