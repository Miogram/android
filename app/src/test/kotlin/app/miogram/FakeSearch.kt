package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.SearchGlobal
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerChat
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerChannel
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.TypeMessagesFilter
import cn.ellerotta.ktproto.api.types.TypePeer
import cn.ellerotta.ktproto.api.types.messages.MessagesSlice
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeSearch(private val fake: FakeTelegram) {
    val calls: MutableList<SearchGlobal> = CopyOnWriteArrayList()

    val messages: MutableMap<TypeMessagesFilter, List<Message>> = ConcurrentHashMap()

    private fun keyOf(peer: TypePeer) = when (peer) {
        is PeerUser -> "u${peer.userId}"
        is PeerChat -> "c${peer.chatId}"
        is PeerChannel -> "ch${peer.channelId}"
        else -> ""
    }

    private fun keyOf(peer: TypeInputPeer) = when (peer) {
        is InputPeerUser -> "u${peer.userId}"
        is InputPeerChat -> "c${peer.chatId}"
        is InputPeerChannel -> "ch${peer.channelId}"
        else -> ""
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(SearchGlobal.CONSTRUCTOR_ID) { request ->
            val call = SearchGlobal.decode(request)
            calls += call
            val found = messages[call.filter].orEmpty()
                .filter { call.q.isBlank() || it.message.contains(call.q, ignoreCase = true) }
                .sortedByDescending { it.date }
            val after = if (call.offsetPeer is InputPeerEmpty) -1 else found.indexOfFirst { it.id == call.offsetId && keyOf(it.peerId) == keyOf(call.offsetPeer) }
            val rest = found.drop(after + 1)
            val page = rest.take(call.limit)
            val more = rest.size > page.size
            FakeAnswer.Result(
                MessagesSlice(
                    count = found.size,
                    nextRate = if (more) page.last().date else null,
                    messages = page,
                    topics = emptyList(),
                    chats = listOf(fake.groups.devs()),
                    users = listOf(fake.alice, fake.bob),
                ),
            )
        }
    }

    fun message(id: Int, peer: TypePeer, text: String, age: Int = 0, from: Long? = null) =
        Message(id = id, peerId = peer, fromId = from?.let(::PeerUser) ?: (peer as? PeerUser), date = NOW - age, message = text)
}
