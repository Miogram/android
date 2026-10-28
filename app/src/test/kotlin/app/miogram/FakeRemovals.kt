package app.miogram

import app.miogram.FakeTelegram.Companion.BOOL_TRUE
import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.channels.DeleteChannel
import cn.ellerotta.ktproto.api.functions.channels.DeleteHistory as ChannelsDeleteHistory
import cn.ellerotta.ktproto.api.functions.messages.DeleteChat
import cn.ellerotta.ktproto.api.functions.messages.DeleteHistory
import cn.ellerotta.ktproto.api.types.InputChannel
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerChat
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.TypeInputPeer
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.messages.AffectedHistory
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeRemovals {
    val historyCalls: MutableList<DeleteHistory> = CopyOnWriteArrayList()

    val channelHistoryCalls: MutableList<ChannelsDeleteHistory> = CopyOnWriteArrayList()

    val deletedChannels: MutableList<Long> = CopyOnWriteArrayList()

    val deletedChats: MutableList<Long> = CopyOnWriteArrayList()

    val gone: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile var moreRounds = 0

    @Volatile var failing: Boolean = false

    private fun keyOf(peer: TypeInputPeer): String = when (peer) {
        is InputPeerChat -> "c${peer.chatId}"
        is InputPeerChannel -> "ch${peer.channelId}"
        is InputPeerUser -> "u${peer.userId}"
        else -> ""
    }

    fun register(server: FakeTelegramServer, fake: FakeTelegram) {
        server.rpc.on(DeleteHistory.CONSTRUCTOR_ID) { request ->
            val call = DeleteHistory.decode(request)
            historyCalls += call
            if (failing) return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
            val more = synchronized(this) { if (moreRounds > 0) { moreRounds--; true } else false }
            if (!more && !call.justClear) gone += keyOf(call.peer)
            FakeAnswer.Result(AffectedHistory(pts = fake.cluster.updates.pts, ptsCount = 0, offset = if (more) 1 else 0))
        }
        server.rpc.on(ChannelsDeleteHistory.CONSTRUCTOR_ID) { request ->
            channelHistoryCalls += ChannelsDeleteHistory.decode(request)
            if (failing) return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
        }
        server.rpc.on(DeleteChannel.CONSTRUCTOR_ID) { request ->
            val id = (DeleteChannel.decode(request).channel as? InputChannel)?.channelId ?: return@on FakeAnswer.Error(400, "CHANNEL_INVALID")
            if (failing) return@on FakeAnswer.Error(400, "CHANNEL_PRIVATE")
            deletedChannels += id
            gone += "ch$id"
            FakeAnswer.Result(Updates(emptyList(), emptyList(), emptyList(), NOW, 0))
        }
        server.rpc.on(DeleteChat.CONSTRUCTOR_ID) { request ->
            val id = DeleteChat.decode(request).chatId
            if (failing) return@on FakeAnswer.Error(400, "CHAT_ID_INVALID")
            deletedChats += id
            gone += "c$id"
            BOOL_TRUE
        }
    }
}
