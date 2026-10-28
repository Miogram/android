package app.miogram

import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.FakeGroups.Companion.FAMILY_ID
import app.miogram.FakeTelegram.Companion.NOW
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
import cn.ellerotta.ktproto.api.types.Channel
import cn.ellerotta.ktproto.api.types.ChatAdminRights
import cn.ellerotta.ktproto.api.types.ChatPhotoEmpty
import cn.ellerotta.ktproto.api.types.InputChannel
import cn.ellerotta.ktproto.api.types.InputChatPhotoEmpty
import cn.ellerotta.ktproto.api.types.InputChatUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerChat
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.MissingInvitee
import cn.ellerotta.ktproto.api.types.TypeChat
import cn.ellerotta.ktproto.api.types.TypeInputUser
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.messages.InvitedUsers
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeNewChats(private val fake: FakeTelegram) {
    val createCalls: MutableList<CreateChannel> = CopyOnWriteArrayList()

    val inviteCalls: MutableList<InviteToChannel> = CopyOnWriteArrayList()

    val addChatUserCalls: MutableList<AddChatUser> = CopyOnWriteArrayList()

    val checked: MutableList<String> = CopyOnWriteArrayList()

    val restricted: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    val members: MutableMap<Long, MutableSet<Long>> = ConcurrentHashMap()

    val takenUsernames: Set<String> = setOf("kotlin", "news")

    val titles: MutableMap<Long, String> = ConcurrentHashMap()

    val abouts: MutableMap<Long, String> = ConcurrentHashMap()

    val photos: MutableMap<Long, Long> = ConcurrentHashMap()

    val usernames: MutableMap<Long, String> = ConcurrentHashMap()

    val editChatTitles: MutableList<EditChatTitle> = CopyOnWriteArrayList()

    private val made: MutableList<Channel> = CopyOnWriteArrayList()

    fun created(): List<Channel> = made.map { current(it) }

    fun isCreated(id: Long) = made.any { it.id == id }

    private fun current(channel: Channel) = Channel(
        creator = true, broadcast = channel.broadcast, megagroup = channel.megagroup, id = channel.id, accessHash = channel.accessHash,
        title = titles[channel.id] ?: channel.title, username = usernames[channel.id], photo = ChatPhotoEmpty, date = NOW, participantsCount = 1,
        adminRights = ChatAdminRights(changeInfo = true, inviteUsers = true),
    )

    private fun changed(id: Long): FakeAnswer {
        val chats: List<TypeChat> = listOf(fake.groups.channels().firstOrNull { it.id == id } ?: fake.groups.family())
        return FakeAnswer.Result(Updates(emptyList(), emptyList(), chats, NOW, 0))
    }

    private fun known(channel: cn.ellerotta.ktproto.api.types.TypeInputChannel): Long? =
        (channel as? InputChannel)?.channelId?.takeIf { it == DEVS_ID || isCreated(it) }

    private fun usersOf(users: List<TypeInputUser>): List<Long> = users.mapNotNull { (it as? InputUser)?.userId }

    private fun invited(id: Long, chats: List<TypeChat>, users: List<Long>): FakeAnswer {
        val inside = members.getOrPut(id) { ConcurrentHashMap.newKeySet() }
        if (users.any { it in inside } && users.size > 1) return FakeAnswer.Error(400, "USER_ALREADY_PARTICIPANT")
        if (users.singleOrNull()?.let { it in inside } == true) return FakeAnswer.Error(400, "USER_ALREADY_PARTICIPANT")
        val missing = users.filter { it in restricted }
        inside += users - missing.toSet()
        return FakeAnswer.Result(InvitedUsers(Updates(emptyList(), emptyList(), chats, NOW, 0), missing.map { MissingInvitee(userId = it) }))
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(CreateChannel.CONSTRUCTOR_ID) { request ->
            val call = CreateChannel.decode(request)
            createCalls += call
            if (call.title.isBlank()) return@on FakeAnswer.Error(400, "CHAT_TITLE_EMPTY")
            val channel = Channel(creator = true, broadcast = call.broadcast, megagroup = call.megagroup, id = NEW_ID + made.size, accessHash = NEW_HASH + made.size, title = call.title, photo = ChatPhotoEmpty, date = NOW, participantsCount = 1)
            made += channel
            abouts[channel.id] = call.about
            FakeAnswer.Result(Updates(emptyList(), emptyList(), listOf(current(channel)), NOW, 0))
        }
        server.rpc.on(InviteToChannel.CONSTRUCTOR_ID) { request ->
            val call = InviteToChannel.decode(request)
            inviteCalls += call
            val id = known(call.channel) ?: return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            invited(id, listOf(fake.groups.channels().first { it.id == id }), usersOf(call.users))
        }
        server.rpc.on(AddChatUser.CONSTRUCTOR_ID) { request ->
            val call = AddChatUser.decode(request)
            addChatUserCalls += call
            if (call.chatId != FAMILY_ID) return@on FakeAnswer.Error(400, "CHAT_ID_INVALID")
            val user = (call.userId as? InputUser)?.userId ?: return@on FakeAnswer.Error(400, "USER_ID_INVALID")
            val inside = members.getOrPut(FAMILY_ID) { ConcurrentHashMap.newKeySet<Long>().apply { add(fake.alice.id); add(fake.selfId); add(fake.bob.id) } }
            if (user in inside) return@on FakeAnswer.Error(400, "USER_ALREADY_PARTICIPANT")
            val missing = user in restricted
            if (!missing) inside += user
            FakeAnswer.Result(InvitedUsers(Updates(emptyList(), emptyList(), listOf(fake.groups.family()), NOW, 0), if (missing) listOf(MissingInvitee(userId = user)) else emptyList()))
        }
        server.rpc.on(CheckUsername.CONSTRUCTOR_ID) { request ->
            val call = CheckUsername.decode(request)
            known(call.channel) ?: return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            checked += call.username
            when {
                call.username == "admin" -> FakeAnswer.Error(400, "USERNAME_INVALID")
                call.username == "toomany" -> FakeAnswer.Error(400, "CHANNELS_ADMIN_PUBLIC_TOO_MUCH")
                call.username in takenUsernames -> FakeAnswer.Error(400, "USERNAME_OCCUPIED")
                else -> TRUE
            }
        }
        server.rpc.on(UpdateUsername.CONSTRUCTOR_ID) { request ->
            val call = UpdateUsername.decode(request)
            val id = known(call.channel) ?: return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            if (call.username in takenUsernames) return@on FakeAnswer.Error(400, "USERNAME_OCCUPIED")
            usernames[id] = call.username
            TRUE
        }
        server.rpc.on(EditTitle.CONSTRUCTOR_ID) { request ->
            val call = EditTitle.decode(request)
            val id = known(call.channel) ?: return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            titles[id] = call.title
            changed(id)
        }
        server.rpc.on(EditChatTitle.CONSTRUCTOR_ID) { request ->
            val call = EditChatTitle.decode(request)
            editChatTitles += call
            if (call.chatId != FAMILY_ID) return@on FakeAnswer.Error(400, "CHAT_ID_INVALID")
            titles[FAMILY_ID] = call.title
            changed(FAMILY_ID)
        }
        server.rpc.on(EditChatAbout.CONSTRUCTOR_ID) { request ->
            val call = EditChatAbout.decode(request)
            val id = when (val peer = call.peer) {
                is InputPeerChannel -> peer.channelId.takeIf { it == DEVS_ID || isCreated(it) }
                is InputPeerChat -> peer.chatId.takeIf { it == FAMILY_ID }
                else -> null
            } ?: return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
            abouts[id] = call.about
            TRUE
        }
        server.rpc.on(EditPhoto.CONSTRUCTOR_ID) { request ->
            val call = EditPhoto.decode(request)
            val id = known(call.channel) ?: return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            photos[id] = uploadedOf(call.photo)
            changed(id)
        }
        server.rpc.on(EditChatPhoto.CONSTRUCTOR_ID) { request ->
            val call = EditChatPhoto.decode(request)
            if (call.chatId != FAMILY_ID) return@on FakeAnswer.Error(400, "CHAT_ID_INVALID")
            photos[FAMILY_ID] = uploadedOf(call.photo)
            changed(FAMILY_ID)
        }
    }

    private fun uploadedOf(photo: cn.ellerotta.ktproto.api.types.TypeInputChatPhoto): Long = when (photo) {
        is InputChatUploadedPhoto -> UPLOADED
        InputChatPhotoEmpty -> REMOVED
        else -> error("unexpected photo $photo")
    }

    companion object {
        const val NEW_ID = 9_100L
        const val NEW_HASH = 21L
        const val UPLOADED = 1L
        const val REMOVED = 0L

        val TRUE = FakeAnswer.Raw(byteArrayOf(0xb5.toByte(), 0x75, 0x72, 0x99.toByte()))
    }
}
