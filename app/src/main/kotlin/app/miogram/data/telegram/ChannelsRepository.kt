package app.miogram.data.telegram

import cn.ellerotta.core.model.ChatInfo
import cn.ellerotta.core.model.ChatMember
import cn.ellerotta.core.model.MemberRole
import cn.ellerotta.core.model.MemberCounts
import cn.ellerotta.core.model.MembersPage
import cn.ellerotta.core.model.Membership
import cn.ellerotta.core.model.Presence
import cn.ellerotta.ktproto.api.functions.channels.EditBanned
import cn.ellerotta.ktproto.api.functions.channels.GetChannels
import cn.ellerotta.ktproto.api.functions.channels.GetFullChannel
import cn.ellerotta.ktproto.api.functions.channels.GetParticipants
import cn.ellerotta.ktproto.api.functions.channels.JoinChannel
import cn.ellerotta.ktproto.api.functions.channels.LeaveChannel
import cn.ellerotta.ktproto.api.functions.messages.DeleteChatUser
import cn.ellerotta.ktproto.api.functions.messages.GetChats
import cn.ellerotta.ktproto.api.functions.messages.GetFullChat
import cn.ellerotta.ktproto.api.types.ChannelFull
import cn.ellerotta.ktproto.api.types.ChannelParticipant
import cn.ellerotta.ktproto.api.types.ChannelParticipantAdmin
import cn.ellerotta.ktproto.api.types.ChannelParticipantCreator
import cn.ellerotta.ktproto.api.types.ChannelParticipantSelf
import cn.ellerotta.ktproto.api.types.ChannelParticipantsRecent
import cn.ellerotta.ktproto.api.types.ChannelParticipantsSearch
import cn.ellerotta.ktproto.api.types.ChatBannedRights
import cn.ellerotta.ktproto.api.types.ChatFull
import cn.ellerotta.ktproto.api.types.ChatParticipant
import cn.ellerotta.ktproto.api.types.ChatParticipantAdmin
import cn.ellerotta.ktproto.api.types.ChatParticipantCreator
import cn.ellerotta.ktproto.api.types.ChatParticipants
import cn.ellerotta.ktproto.api.types.ChatParticipantsForbidden
import cn.ellerotta.ktproto.api.types.TypeChannelParticipant
import cn.ellerotta.ktproto.api.types.TypeChatParticipant
import cn.ellerotta.ktproto.api.types.TypeUser
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.channels.ChannelParticipants as ChannelMembers
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.Chat
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.messages.Chats
import cn.ellerotta.ktproto.api.types.messages.ChatsSlice
import cn.ellerotta.ktproto.api.types.messages.TypeChats
import cn.ellerotta.ktproto.session.RpcException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelsRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun info(dialogKey: String): ChatInfo? = fetchInfo(dialogKey).also { if (it != null) telegram.memory["info:$dialogKey"] = it }

    fun storedInfo(dialogKey: String): ChatInfo? = telegram.memory["info:$dialogKey"]

    fun storedMembers(dialogKey: String): MembersPage? = telegram.memory["members:$dialogKey"]

    private suspend fun fetchInfo(dialogKey: String): ChatInfo? = when (val peer = peerOfKey(dialogKey)) {
        is PeerChannel -> {
            val channel = chatsOf(telegram.client().invoke(GetChannels(listOf(registry.channel(dialogKey))))).filterIsInstance<Channel>().firstOrNull { it.id == peer.channelId }
            channel?.let { infoOf(dialogKey, it) } ?: ChatInfo(dialogKey, Membership.Removed)
        }
        is PeerChat -> {
            val chat = chatsOf(telegram.client().invoke(GetChats(listOf(peer.chatId)))).filterIsInstance<Chat>().firstOrNull { it.id == peer.chatId }
            chat?.let { infoOf(dialogKey, it) } ?: ChatInfo(dialogKey, Membership.Removed, basicGroup = true)
        }
        else -> null
    }

    suspend fun memberCounts(dialogKey: String): MemberCounts? = when (val peer = peerOfKey(dialogKey)) {
        is PeerChannel -> (telegram.client().invoke(GetFullChannel(registry.channel(dialogKey))).fullChat as? ChannelFull)?.let { MemberCounts(it.participantsCount, it.onlineCount) }
        is PeerChat -> {
            val full = telegram.client().invoke(GetFullChat(peer.chatId))
            registry.rememberUsers(full.users)
            val now = System.currentTimeMillis() / 1000
            val members = ((full.fullChat as? ChatFull)?.participants as? ChatParticipants)?.participants.orEmpty().map { participant ->
                when (participant) {
                    is ChatParticipant -> participant.userId
                    is ChatParticipantCreator -> participant.userId
                    is ChatParticipantAdmin -> participant.userId
                }
            }.toSet()
            MemberCounts(members.size.takeIf { it > 0 }, full.users.filterIsInstance<User>().count { it.id in members && (it.self || isOnline(it, now)) })
        }
        else -> null
    }

    suspend fun join(dialogKey: String): Membership = try {
        joinedUpdates(telegram.client().invoke(JoinChannel(registry.channel(dialogKey))))
        Membership.Member
    } catch (e: RpcException) {
        if (e.errorMessage != "INVITE_REQUEST_SENT") throw e
        Membership.RequestSent
    }

    suspend fun leave(dialogKey: String) {
        when (val peer = peerOfKey(dialogKey)) {
            is PeerChannel -> telegram.client().invoke(LeaveChannel(registry.channel(dialogKey)))
            is PeerChat -> telegram.client().invoke(DeleteChatUser(chatId = peer.chatId, userId = InputUserSelf))
            else -> error("not a group or channel: $dialogKey")
        }
    }

    suspend fun members(dialogKey: String, query: String = "", offset: Int = 0, limit: Int = 50): MembersPage {
        val self = telegram.requireClient().userId
        val now = System.currentTimeMillis() / 1000
        val q = query.trim()
        val page = when (val peer = peerOfKey(dialogKey)) {
            is PeerChannel -> {
                val filter = if (q.isEmpty()) ChannelParticipantsRecent else ChannelParticipantsSearch(q)
                val result = telegram.client().invoke(GetParticipants(registry.channel(dialogKey), filter, offset, limit, 0)) as? ChannelMembers
                    ?: return MembersPage(emptyList(), 0)
                registry.rememberUsers(result.users)
                val users = usersById(result.users)
                val members = result.participants.mapNotNull { memberOf(it, users, self, now) }
                MembersPage(members, maxOf(result.count, offset + members.size))
            }
            is PeerChat -> {
                val full = telegram.client().invoke(GetFullChat(peer.chatId))
                registry.rememberUsers(full.users)
                val users = usersById(full.users)
                val participants = when (val all = (full.fullChat as ChatFull).participants) {
                    is ChatParticipants -> all.participants
                    is ChatParticipantsForbidden -> listOfNotNull(all.selfParticipant)
                }
                val matching = participants.map { memberOf(it, users, self, now) }.filter { member ->
                    q.isEmpty() || member.name.contains(q, ignoreCase = true) ||
                        users[member.userKey.drop(1).toLong()]?.username?.contains(q, ignoreCase = true) == true
                }
                MembersPage(matching.drop(offset).take(limit), matching.size)
            }
            else -> error("not a group or channel: $dialogKey")
        }
        if (q.isEmpty() && offset == 0) telegram.memory["members:$dialogKey"] = page
        return page
    }

    suspend fun removeMember(dialogKey: String, userKey: String, ban: Boolean) {
        when (val peer = peerOfKey(dialogKey)) {
            is PeerChannel -> {
                val channel = registry.channel(dialogKey)
                val member = registry.peer(userKey)
                telegram.client().invoke(EditBanned(channel, member, ChatBannedRights(viewMessages = true, untilDate = 0)))
                if (!ban) telegram.client().invoke(EditBanned(channel, member, ChatBannedRights(untilDate = 0)))
            }
            is PeerChat -> telegram.client().invoke(DeleteChatUser(chatId = peer.chatId, userId = registry.user(userKey)))
            else -> error("not a group or channel: $dialogKey")
        }
    }

    private fun memberOf(participant: TypeChannelParticipant, users: Map<Long, User>, self: Long?, now: Long): ChatMember? = when (participant) {
        is ChannelParticipant -> memberOf(users[participant.userId], participant.userId, MemberRole.Member, participant.rank, self, now)
        is ChannelParticipantSelf -> memberOf(users[participant.userId], participant.userId, MemberRole.Member, participant.rank, self, now)
        is ChannelParticipantCreator -> memberOf(users[participant.userId], participant.userId, MemberRole.Owner, participant.rank, self, now)
        is ChannelParticipantAdmin -> memberOf(users[participant.userId], participant.userId, MemberRole.Admin, participant.rank, self, now)
        else -> null
    }

    private fun memberOf(participant: TypeChatParticipant, users: Map<Long, User>, self: Long?, now: Long): ChatMember = when (participant) {
        is ChatParticipant -> memberOf(users[participant.userId], participant.userId, MemberRole.Member, participant.rank, self, now)
        is ChatParticipantCreator -> memberOf(users[participant.userId], participant.userId, MemberRole.Owner, participant.rank, self, now)
        is ChatParticipantAdmin -> memberOf(users[participant.userId], participant.userId, MemberRole.Admin, participant.rank, self, now)
    }

    private fun memberOf(user: User?, id: Long, role: MemberRole, rank: String?, self: Long?, now: Long): ChatMember {
        val key = "u$id"
        return ChatMember(
            userKey = key,
            name = user?.let(::nameOf) ?: registry.peerNames[key].orEmpty(),
            avatarKey = user?.let { registry.avatarKey(avatarOf(key, it)) },
            role = role,
            rank = rank?.ifBlank { null },
            presence = when {
                user == null -> Presence.Unknown
                user.bot -> Presence.Bot
                user.self || id == self -> Presence.Online
                else -> presenceOf(user.status, now)
            },
            bot = user?.bot == true,
            self = id == self,
            username = if (user != null) usernameOf(user) else registry.usernames[id],
            firstName = if (user != null) user.firstName?.ifBlank { null } else registry.userNames[id],
        )
    }

    fun knownUser(userKey: String, bot: Boolean = false): ChatMember? {
        val id = (peerOfKey(userKey) as? PeerUser)?.userId ?: return null
        val name = registry.peerNames[userKey] ?: return null
        return ChatMember(
            userKey = userKey,
            name = name,
            avatarKey = registry.peerAvatars[userKey],
            presence = if (bot) Presence.Bot else Presence.Unknown,
            bot = bot,
            self = id == registry.selfId(),
            username = registry.usernames[id],
            firstName = registry.userNames[id],
        )
    }
}

private fun usersById(users: List<TypeUser>): Map<Long, User> = users.filterIsInstance<User>().associateBy { it.id }

internal fun infoOf(key: String, channel: Channel): ChatInfo {
    val rights = channel.adminRights
    return ChatInfo(
        key = key,
        membership = when {
            !channel.left -> Membership.Member
            channel.joinRequest -> Membership.RequestToJoin
            else -> Membership.CanJoin
        },
        creator = channel.creator,
        admin = channel.creator || rights != null,
        canInvite = channel.creator || rights?.inviteUsers == true,
        canBan = channel.creator || rights?.banUsers == true,
        canManageTopics = channel.forum && (channel.creator || rights?.manageTopics == true),
        canChangeInfo = channel.creator || rights?.changeInfo == true,
        canPin = !channel.left && (
            channel.creator || rights?.pinMessages == true ||
                (channel.megagroup && channel.defaultBannedRights?.pinMessages == false && channel.bannedRights?.pinMessages != true)
            ),
    )
}

internal fun infoOf(key: String, chat: Chat): ChatInfo {
    val rights = chat.adminRights
    return ChatInfo(
        key = key,
        membership = if (chat.left || chat.deactivated) Membership.Removed else Membership.Member,
        basicGroup = true,
        creator = chat.creator,
        admin = chat.creator || rights != null,
        canInvite = chat.creator || rights?.inviteUsers == true,
        canBan = chat.creator || rights?.banUsers == true,
        canChangeInfo = chat.creator || rights?.changeInfo == true,
        canPin = !chat.left && !chat.deactivated && (chat.creator || rights?.pinMessages == true || chat.defaultBannedRights?.pinMessages == false),
    )
}

private fun chatsOf(result: TypeChats): List<TypeChat> = when (result) {
    is Chats -> result.chats
    is ChatsSlice -> result.chats
}
