package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.messages.Search
import cn.ellerotta.ktproto.api.functions.messages.UnpinAllMessages
import cn.ellerotta.ktproto.api.functions.messages.UpdatePinnedMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.UpdatePinnedMessages
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.Updates
import cn.ellerotta.ktproto.api.types.messages.AffectedHistory
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CopyOnWriteArrayList

class FakePins(private val fake: FakeTelegram) {
    val pinned: MutableSet<Int> = ConcurrentSkipListSet()

    val pinCalls: MutableList<UpdatePinnedMessage> = CopyOnWriteArrayList()
    val unpinAllCalls: MutableList<UnpinAllMessages> = CopyOnWriteArrayList()

    @Volatile var unpinAllRounds = 1

    fun search(call: Search, saved: List<Message>): FakeAnswer {
        val found = saved.filter { it.id in pinned }.sortedByDescending { it.id }.take(call.limit)
        return FakeAnswer.Result(Messages(found, emptyList(), emptyList(), emptyList()))
    }

    suspend fun pushPinned(ids: List<Int>, pinned: Boolean) {
        if (pinned) this.pinned += ids else this.pinned -= ids.toSet()
        fake.cluster.updates.push(UpdateShort(update(ids, pinned), NOW))
    }

    private fun update(ids: List<Int>, pinned: Boolean) = UpdatePinnedMessages(pinned = pinned, peer = PeerUser(fake.selfId), messages = ids, pts = fake.cluster.updates.pts, ptsCount = 0)

    fun register(server: FakeTelegramServer) {
        server.rpc.on(UpdatePinnedMessage.CONSTRUCTOR_ID) { request ->
            val call = UpdatePinnedMessage.decode(request)
            pinCalls += call
            if (call.unpin) pinned -= call.id else pinned += call.id
            FakeAnswer.Result(Updates(listOf(update(listOf(call.id), !call.unpin)), emptyList(), emptyList(), NOW, 0))
        }
        server.rpc.on(UnpinAllMessages.CONSTRUCTOR_ID) { request ->
            unpinAllCalls += UnpinAllMessages.decode(request)
            val left = unpinAllCalls.size < unpinAllRounds
            if (!left) pinned.clear()
            FakeAnswer.Result(AffectedHistory(pts = fake.cluster.updates.pts, ptsCount = 0, offset = if (left) 1 else 0))
        }
    }
}
