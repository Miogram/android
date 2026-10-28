package app.miogram

import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.channels.EditBanned
import cn.ellerotta.ktproto.api.functions.channels.GetParticipants
import cn.ellerotta.ktproto.api.types.ChannelParticipant
import cn.ellerotta.ktproto.api.types.ChannelParticipantAdmin
import cn.ellerotta.ktproto.api.types.ChannelParticipantCreator
import cn.ellerotta.ktproto.api.types.ChannelParticipantsRecent
import cn.ellerotta.ktproto.api.types.ChannelParticipantsSearch
import cn.ellerotta.ktproto.api.types.ChatAdminRights
import cn.ellerotta.ktproto.api.types.InputChannel
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.TypeChannelParticipant
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.channels.ChannelParticipants
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeMembers(private val fake: FakeTelegram) {
    val participantCalls: MutableList<GetParticipants> = CopyOnWriteArrayList()

    val bannedCalls: MutableList<EditBanned> = CopyOnWriteArrayList()

    val banned: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    val removed: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    val others: List<User> = (1..60).map { User(id = 900L + it, accessHash = 1_000L + it, firstName = "Member", lastName = "$it") }

    private val me get() = User(self = true, id = fake.selfId, accessHash = 1, firstName = "Test", lastName = "User")

    private fun all(): List<Pair<User, TypeChannelParticipant>> {
        val rights = ChatAdminRights(changeInfo = true, deleteMessages = true, banUsers = true, inviteUsers = true, pinMessages = true)
        return listOf(
            me to ChannelParticipantCreator(userId = fake.selfId, adminRights = rights),
            fake.alice to ChannelParticipantAdmin(userId = fake.alice.id, promotedBy = fake.selfId, date = NOW, adminRights = rights, rank = "maintainer"),
            fake.helperBot to ChannelParticipant(userId = fake.helperBot.id, date = NOW),
            fake.bob to ChannelParticipant(userId = fake.bob.id, date = NOW),
            fake.carol to ChannelParticipant(userId = fake.carol.id, date = NOW),
        ) + others.map { it to ChannelParticipant(userId = it.id, date = NOW - it.id.toInt()) }
    }

    private fun matches(user: User, q: String): Boolean =
        (listOfNotNull(user.firstName, user.lastName, user.username)).any { word -> word.split(' ').any { it.startsWith(q, ignoreCase = true) } }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetParticipants.CONSTRUCTOR_ID) { request ->
            fake.answerLate("members")
            val call = GetParticipants.decode(request)
            if ((call.channel as? InputChannel)?.channelId != DEVS_ID) return@on FakeAnswer.Error(400, "CHAT_ADMIN_REQUIRED")
            participantCalls += call
            val present = all().filter { it.first.id !in removed }
            val found = when (val filter = call.filter) {
                ChannelParticipantsRecent -> present
                is ChannelParticipantsSearch -> present.filter { matches(it.first, filter.q) }
                else -> return@on FakeAnswer.Error(400, "FILTER_NOT_SUPPORTED")
            }
            val page = found.drop(call.offset).take(call.limit)
            FakeAnswer.Result(ChannelParticipants(found.size, page.map { it.second }, emptyList(), page.map { it.first }))
        }
        server.rpc.on(EditBanned.CONSTRUCTOR_ID) { request ->
            val call = EditBanned.decode(request)
            if ((call.channel as? InputChannel)?.channelId != DEVS_ID) return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            val user = (call.participant as? InputPeerUser)?.userId ?: return@on FakeAnswer.Error(400, "PARTICIPANT_ID_INVALID")
            bannedCalls += call
            if (call.bannedRights.viewMessages) {
                banned += user
                removed += user
            } else {
                banned -= user
            }
            FakeAnswer.Result(Updates(emptyList(), emptyList(), listOf(fake.groups.devs()), NOW, 0))
        }
    }
}
