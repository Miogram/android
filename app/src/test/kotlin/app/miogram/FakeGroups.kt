package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.channels.LeaveChannel
import cn.ellerotta.ktproto.api.functions.messages.DeleteChatUser
import cn.ellerotta.ktproto.api.functions.messages.GetChats
import cn.ellerotta.ktproto.api.functions.messages.GetFullChat
import cn.ellerotta.ktproto.api.types.BotInfo
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChannelFull
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.ChatAdminRights
import cn.ellerotta.ktproto.api.types.ChatFull
import cn.ellerotta.ktproto.api.types.ChatParticipant
import cn.ellerotta.ktproto.api.types.ChatParticipantCreator
import cn.ellerotta.ktproto.api.types.ChatParticipants
import cn.ellerotta.ktproto.api.types.ChatPhotoEmpty
import cn.ellerotta.ktproto.api.types.Dialog
import cn.ellerotta.ktproto.api.types.InputChannel
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerChat
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerNotifySettings
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.PhotoEmpty
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.ChatParticipantAdmin
import cn.ellerotta.ktproto.api.types.TypeChatParticipant
import cn.ellerotta.ktproto.api.types.TypeMessage
import cn.ellerotta.ktproto.api.types.messages.ChatFull as MessagesChatFull
import cn.ellerotta.ktproto.api.types.messages.ChatInviteJoinResultOk
import cn.ellerotta.ktproto.api.types.messages.Chats
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeGroups(private val fake: FakeTelegram) {
    @Volatile var inDialogs = false

    val left: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    val leaveCalls: MutableList<Long> = CopyOnWriteArrayList()

    val deleteChatUserCalls: MutableList<DeleteChatUser> = CopyOnWriteArrayList()

    val joinRequests: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    val invites = FakeInvites(fake)

    val members = FakeMembers(fake)

    val newChats = FakeNewChats(fake)

    val forum = FakeForum(fake)

    val discussion = FakeDiscussion(fake)

    val removedFromFamily: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun devs() = Channel(
        megagroup = true, creator = true, left = DEVS_ID in left, id = DEVS_ID, accessHash = DEVS_HASH, title = "Kotlin devs", username = "kotlindevs",
        photo = ChatPhotoEmpty, date = NOW, participantsCount = 65, adminRights = ChatAdminRights(changeInfo = true, deleteMessages = true, banUsers = true, inviteUsers = true, pinMessages = true, addAdmins = true),
    )

    fun family() = Chat(left = FAMILY_ID in left, id = FAMILY_ID, title = "Family", photo = ChatPhotoEmpty, participantsCount = 3, date = NOW, version = 1)

    fun jobs() = Channel(megagroup = true, joinRequest = true, left = true, id = JOBS_ID, accessHash = JOBS_HASH, title = "Kotlin jobs", username = "kotlinjobs", photo = ChatPhotoEmpty, date = NOW, participantsCount = 900)

    fun channels(): List<Channel> = listOf(devs(), jobs(), forum.forum(), discussion.blog(), discussion.chat()) + newChats.created()

    private val devsMessages get() = listOf(Message(id = 5, fromId = PeerUser(fake.alice.id), peerId = PeerChannel(DEVS_ID), date = NOW, message = "welcome to kotlin devs"))
    private val familyMessages get() = listOf(Message(id = 7, fromId = PeerUser(fake.alice.id), peerId = PeerChat(FAMILY_ID), date = NOW, message = "dinner at seven"))

    fun dialogs(archive: Boolean): Triple<List<Dialog>, List<TypeMessage>, List<TypeChat>> {
        val (blogDialogs, blogMessages, blogChats) = if (archive) Triple(emptyList<Dialog>(), emptyList<Message>(), emptyList<Channel>()) else discussion.dialogs()
        if (!inDialogs) return Triple(blogDialogs, blogMessages, blogChats)
        val (forumDialog, forumTop) = forum.dialog()
        val dialogs = listOfNotNull(
            dialog(PeerChannel(DEVS_ID), 5, pts = fake.channelPts(DEVS_ID)).takeIf { DEVS_ID !in left },
            dialog(PeerChat(FAMILY_ID), 7).takeIf { FAMILY_ID !in left },
            forumDialog,
        ).filter { (idOf(it.peer) in fake.archived) == archive }
        return Triple(dialogs + blogDialogs, devsMessages + familyMessages + forumTop + blogMessages, listOf(devs(), family(), forum.forum()) + blogChats)
    }

    private fun dialog(peer: TypePeer, top: Int, pts: Int? = null) = Dialog(
        peer = peer, topMessage = top, readInboxMaxId = top, readOutboxMaxId = 0, unreadCount = 0, unreadMentionsCount = 0, unreadReactionsCount = 0, unreadPollVotesCount = 0,
        notifySettings = PeerNotifySettings(muteUntil = fake.muteUntilOf(idOf(peer))), pts = pts,
        folderId = if (idOf(peer) in fake.archived) 1 else null,
    )

    private fun idOf(peer: TypePeer) = when (peer) {
        is PeerChannel -> peer.channelId
        is PeerChat -> peer.chatId
        else -> 0L
    }

    fun join(channelId: Long): FakeAnswer? = when (channelId) {
        JOBS_ID -> {
            joinRequests += JOBS_ID
            FakeAnswer.Error(400, "INVITE_REQUEST_SENT")
        }
        DEVS_ID -> {
            left -= DEVS_ID
            FakeAnswer.Result(ChatInviteJoinResultOk(Updates(emptyList(), emptyList(), listOf(devs()), NOW, 0)))
        }
        else -> null
    }

    fun history(peer: TypeInputPeer): Pair<List<Message>, List<TypeChat>>? = when {
        peer is InputPeerChannel && peer.channelId == DEVS_ID -> devsMessages to listOf(devs())
        peer is InputPeerChannel && peer.channelId == JOBS_ID -> emptyList<Message>() to listOf(jobs())
        peer is InputPeerChannel && newChats.isCreated(peer.channelId) -> emptyList<Message>() to channels().filter { it.id == peer.channelId }
        peer is InputPeerChat && peer.chatId == FAMILY_ID -> familyMessages to listOf(family())
        else -> forum.history(peer) ?: discussion.history(peer)
    }

    fun resolve(username: String): Channel? = channels().firstOrNull { it.username == username }

    fun fullChannel(channelId: Long): MessagesChatFull? {
        val channel = channels().firstOrNull { it.id == channelId } ?: return null
        val full = ChannelFull(
            canViewParticipants = channel.id == DEVS_ID, id = channel.id, about = "all about ${channel.title.lowercase()}", readInboxMaxId = 0, readOutboxMaxId = 0, unreadCount = 0,
            chatPhoto = PhotoEmpty(0), notifySettings = PeerNotifySettings(muteUntil = fake.muteUntilOf(channel.id)), pts = fake.channelPts(channel.id),
            botInfo = if (channel.id == DEVS_ID) listOf(BotInfo(userId = fake.helperBot.id, commands = fake.bots.helperCommands)) else emptyList(),
            participantsCount = channel.participantsCount,
            onlineCount = if (channel.id == DEVS_ID) DEVS_ONLINE else null,
            requestsPending = if (channel.id == DEVS_ID) invites.requesters.size else null,
            recentRequesters = if (channel.id == DEVS_ID) invites.requesters.take(3).map { it.id } else null,
        )
        return MessagesChatFull(full, listOf(channel), listOf(fake.alice, fake.bob, fake.helperBot))
    }

    fun register(server: FakeTelegramServer) {
        invites.register(server)
        members.register(server)
        newChats.register(server)
        forum.register(server)
        discussion.register(server)
        server.rpc.on(LeaveChannel.CONSTRUCTOR_ID) { request ->
            val id = (LeaveChannel.decode(request).channel as? InputChannel)?.channelId ?: return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            leaveCalls += id
            left += id
            fake.joinedChannels -= id
            FakeAnswer.Result(Updates(emptyList(), emptyList(), channels().filter { it.id == id }, NOW, 0))
        }
        server.rpc.on(DeleteChatUser.CONSTRUCTOR_ID) { request ->
            val call = DeleteChatUser.decode(request)
            if (call.chatId != FAMILY_ID) return@on FakeAnswer.Error(400, "CHAT_ID_INVALID")
            deleteChatUserCalls += call
            when (val user = call.userId) {
                InputUserSelf -> left += FAMILY_ID
                is InputUser -> removedFromFamily += user.userId
                else -> Unit
            }
            FakeAnswer.Result(Updates(emptyList(), emptyList(), listOf(family()), NOW, 0))
        }
        server.rpc.on(GetChats.CONSTRUCTOR_ID) { request ->
            val ids = GetChats.decode(request).id
            FakeAnswer.Result(Chats(listOf(family()).filter { it.id in ids }))
        }
        server.rpc.on(GetFullChat.CONSTRUCTOR_ID) { request ->
            if (GetFullChat.decode(request).chatId != FAMILY_ID) return@on FakeAnswer.Error(400, "CHAT_ID_INVALID")
            val members = listOf(ChatParticipantCreator(fake.alice.id), ChatParticipant(fake.selfId, fake.alice.id, NOW), ChatParticipant(fake.bob.id, fake.alice.id, NOW))
            val full = ChatFull(
                id = FAMILY_ID, about = "",
                participants = ChatParticipants(FAMILY_ID, members.filter { FAMILY_ID !in left || (it as? ChatParticipant)?.userId != fake.selfId }.filter { userOf(it) !in removedFromFamily }, 1),
                notifySettings = PeerNotifySettings(muteUntil = fake.muteUntilOf(FAMILY_ID)),
            )
            val me = User(self = true, id = fake.selfId, accessHash = 1, firstName = "Test", lastName = "User")
            FakeAnswer.Result(MessagesChatFull(full, listOf(family()), listOf(me, fake.alice, fake.bob)))
        }
    }

    private fun userOf(participant: TypeChatParticipant): Long = when (participant) {
        is ChatParticipant -> participant.userId
        is ChatParticipantCreator -> participant.userId
        is ChatParticipantAdmin -> participant.userId
    }

    companion object {
        const val DEVS_ID = 9_003L
        const val DEVS_HASH = 13L

        const val DEVS_ONLINE = 12
        const val FAMILY_ID = 9_004L
        const val JOBS_ID = 9_006L
        const val JOBS_HASH = 16L
    }
}
