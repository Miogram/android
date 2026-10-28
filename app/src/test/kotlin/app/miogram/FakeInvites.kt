package app.miogram

import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.DeleteExportedChatInvite
import cn.ellerotta.ktproto.api.functions.messages.EditExportedChatInvite
import cn.ellerotta.ktproto.api.functions.messages.ExportChatInvite
import cn.ellerotta.ktproto.api.functions.messages.GetChatInviteImporters
import cn.ellerotta.ktproto.api.functions.messages.GetExportedChatInvites
import cn.ellerotta.ktproto.api.functions.messages.HideChatJoinRequest
import cn.ellerotta.ktproto.api.types.ChatInviteExported
import cn.ellerotta.ktproto.api.types.ChatInviteImporter
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.InputUserSelf
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.messages.ChatInviteImporters
import cn.ellerotta.ktproto.api.types.messages.ExportedChatInvite
import cn.ellerotta.ktproto.api.types.messages.ExportedChatInviteReplaced
import cn.ellerotta.ktproto.api.types.messages.ExportedChatInvites
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.CopyOnWriteArrayList

class FakeInvites(private val fake: FakeTelegram) {
    data class Link(
        val link: String,
        val permanent: Boolean = false,
        val revoked: Boolean = false,
        val requestNeeded: Boolean = false,
        val title: String? = null,
        val expireDate: Int? = null,
        val usageLimit: Int? = null,
        val usage: Int = 0,
        val date: Int = NOW,
    )

    val links: MutableList<Link> = CopyOnWriteArrayList(
        listOf(
            Link("https://t.me/+DevsPrimary", permanent = true, usage = 12, date = NOW - 86_400),
            Link("https://t.me/+DevsMeetup", title = "meetup", usageLimit = 50, usage = 3),
        ),
    )

    @Volatile var lastExport: ExportChatInvite? = null

    val editCalls: MutableList<EditExportedChatInvite> = CopyOnWriteArrayList()

    val importerCalls: MutableList<GetChatInviteImporters> = CopyOnWriteArrayList()

    val hideCalls: MutableList<HideChatJoinRequest> = CopyOnWriteArrayList()

    private var created = 0

    val requesters: MutableList<User> = CopyOnWriteArrayList(listOf(fake.carol) + (1..34).map { User(id = 700L + it, accessHash = 800L + it, firstName = "Requester", lastName = "$it") })

    private fun dateOf(user: User) = if (user.id == fake.carol.id) NOW else NOW - (user.id - 700L).toInt() * 60

    private fun exported(link: Link) = ChatInviteExported(
        revoked = link.revoked, permanent = link.permanent, requestNeeded = link.requestNeeded, link = link.link, adminId = fake.selfId, date = link.date,
        expireDate = link.expireDate, usageLimit = link.usageLimit, usage = link.usage, title = link.title,
    )

    private fun isDevs(peer: TypeInputPeer) = peer is InputPeerChannel && (peer.channelId == DEVS_ID || fake.groups.newChats.isCreated(peer.channelId))

    private val me get() = User(self = true, id = fake.selfId, accessHash = 1, firstName = "Test", lastName = "User")

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetExportedChatInvites.CONSTRUCTOR_ID) { request ->
            val call = GetExportedChatInvites.decode(request)
            if (!isDevs(call.peer) || call.adminId != InputUserSelf) return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
            val found = links.filter { it.revoked == call.revoked }.take(call.limit)
            FakeAnswer.Result(ExportedChatInvites(found.size, found.map(::exported), listOf(me)))
        }
        server.rpc.on(ExportChatInvite.CONSTRUCTOR_ID) { request ->
            val call = ExportChatInvite.decode(request)
            if (!isDevs(call.peer)) return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
            if (call.requestNeeded && call.usageLimit != null) return@on FakeAnswer.Error(400, "USAGE_LIMIT_INVALID")
            lastExport = call
            val link = Link("https://t.me/+DevsNew${++created}", requestNeeded = call.requestNeeded, title = call.title, expireDate = call.expireDate, usageLimit = call.usageLimit)
            links += link
            FakeAnswer.Result(exported(link))
        }
        server.rpc.on(EditExportedChatInvite.CONSTRUCTOR_ID) { request ->
            val call = EditExportedChatInvite.decode(request)
            editCalls += call
            val index = links.indexOfFirst { it.link == call.link }
            if (!isDevs(call.peer) || index < 0) return@on FakeAnswer.Error(400, "INVITE_HASH_EXPIRED")
            val old = links[index]
            val revoked = old.copy(revoked = call.revoked || old.revoked, permanent = old.permanent && !call.revoked)
            links[index] = revoked
            if (call.revoked && old.permanent) {
                val replacement = Link("https://t.me/+DevsPrimary${++created}", permanent = true)
                links.add(0, replacement)
                FakeAnswer.Result(ExportedChatInviteReplaced(exported(revoked), exported(replacement), listOf(me)))
            } else {
                FakeAnswer.Result(ExportedChatInvite(exported(revoked), listOf(me)))
            }
        }
        server.rpc.on(DeleteExportedChatInvite.CONSTRUCTOR_ID) { request ->
            val call = DeleteExportedChatInvite.decode(request)
            if (!links.removeIf { it.link == call.link && it.revoked }) return@on FakeAnswer.Error(400, "INVITE_REVOKED_MISSING")
            BOOL_TRUE
        }
        server.rpc.on(GetChatInviteImporters.CONSTRUCTOR_ID) { request ->
            val call = GetChatInviteImporters.decode(request)
            importerCalls += call
            if (!isDevs(call.peer) || !call.requested) return@on FakeAnswer.Result(ChatInviteImporters(0, emptyList(), emptyList()))
            val after = (call.offsetUser as? InputUser)?.userId
            val start = if (after == null) 0 else requesters.indexOfFirst { it.id == after } + 1
            val page = requesters.drop(start).take(call.limit)
            val importers = page.map { ChatInviteImporter(requested = true, userId = it.id, date = dateOf(it), about = if (it.id == fake.carol.id) "i write about kotlin" else null) }
            FakeAnswer.Result(ChatInviteImporters(requesters.size, importers, page))
        }
        server.rpc.on(HideChatJoinRequest.CONSTRUCTOR_ID) { request ->
            val call = HideChatJoinRequest.decode(request)
            val id = (call.userId as? InputUser)?.userId
            if (!isDevs(call.peer) || !requesters.removeIf { it.id == id }) return@on FakeAnswer.Error(400, "HIDE_REQUESTER_MISSING")
            hideCalls += call
            FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
        }
    }

    private companion object {
        val BOOL_TRUE = FakeAnswer.Raw(byteArrayOf(0xb5.toByte(), 0x75, 0x72, 0x99.toByte()))
    }
}
