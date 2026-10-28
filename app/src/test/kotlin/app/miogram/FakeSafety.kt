package app.miogram

import app.miogram.FakeTelegram.Companion.NOW
import cn.ellerotta.ktproto.api.functions.account.ReportPeer
import cn.ellerotta.ktproto.api.functions.contacts.Block
import cn.ellerotta.ktproto.api.functions.contacts.GetBlocked
import cn.ellerotta.ktproto.api.functions.contacts.Unblock
import cn.ellerotta.ktproto.api.functions.messages.GetPeerSettings
import cn.ellerotta.ktproto.api.functions.messages.HidePeerSettingsBar
import cn.ellerotta.ktproto.api.functions.messages.Report
import cn.ellerotta.ktproto.api.functions.messages.ReportSpam
import cn.ellerotta.ktproto.api.functions.stories.Report as ReportStory
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.MessageReportOption
import cn.ellerotta.ktproto.api.types.PeerBlocked
import cn.ellerotta.ktproto.api.types.PeerSettings
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.ReportResultAddComment
import cn.ellerotta.ktproto.api.types.ReportResultChooseOption
import cn.ellerotta.ktproto.api.types.ReportResultReported
import cn.ellerotta.ktproto.api.types.TypeReportResult
import cn.ellerotta.ktproto.api.types.UpdatePeerBlocked
import cn.ellerotta.ktproto.api.types.UpdatePeerSettings
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.contacts.BlockedSlice
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class FakeSafety(private val fake: FakeTelegram) {
    private val frank get() = fake.contacts.frank

    val blocked: MutableMap<Long, Int> = ConcurrentHashMap()

    val bars: MutableMap<Long, PeerSettings> = ConcurrentHashMap()

    val getBlockedCalls: MutableList<GetBlocked> = CopyOnWriteArrayList()
    val blocks: MutableList<Block> = CopyOnWriteArrayList()
    val unblocks: MutableList<Unblock> = CopyOnWriteArrayList()
    val reports: MutableList<Report> = CopyOnWriteArrayList()
    val storyReports: MutableList<ReportStory> = CopyOnWriteArrayList()
    val peerReports: MutableList<ReportPeer> = CopyOnWriteArrayList()
    val spamReports: MutableList<ReportSpam> = CopyOnWriteArrayList()
    val hiddenBars: MutableList<HidePeerSettingsBar> = CopyOnWriteArrayList()

    init {
        blocked[507] = NOW - 100
    }

    private fun people(): List<User> = listOf(fake.alice, fake.bob, fake.carol, fake.helperBot, fake.contacts.erin, frank)

    private fun userOf(peer: Any?): Long? = (peer as? InputPeerUser)?.userId

    private fun answer(option: ByteArray, comment: String): TypeReportResult = when {
        option.isEmpty() -> ReportResultChooseOption("What is wrong?", listOf(MessageReportOption("Spam", byteArrayOf(1)), MessageReportOption("Violence", byteArrayOf(2)), MessageReportOption("Something else", byteArrayOf(3))))
        option[0] == 2.toByte() && option.size == 1 -> ReportResultChooseOption("Which kind?", listOf(MessageReportOption("Graphic", byteArrayOf(2, 1)), MessageReportOption("Threats", byteArrayOf(2, 2))))
        option[0] == 3.toByte() && comment.isEmpty() -> ReportResultAddComment(optional = false, option = option)
        else -> ReportResultReported
    }

    suspend fun pushBlocked(id: Long, blocked: Boolean) {
        if (blocked) this.blocked[id] = NOW else this.blocked.remove(id)
        fake.cluster.updates.push(UpdateShort(UpdatePeerBlocked(blocked = blocked, peerId = PeerUser(id)), NOW))
    }

    suspend fun pushBar(id: Long, settings: PeerSettings) {
        bars[id] = settings
        fake.cluster.updates.push(UpdateShort(UpdatePeerSettings(PeerUser(id), settings), NOW))
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetBlocked.CONSTRUCTOR_ID) { request ->
            val call = GetBlocked.decode(request)
            getBlockedCalls += call
            val ordered = blocked.entries.sortedByDescending { it.value }
            val page = ordered.drop(call.offset).take(call.limit)
            val users = people().filter { user -> page.any { it.key == user.id } }
            FakeAnswer.Result(BlockedSlice(ordered.size, page.map { PeerBlocked(PeerUser(it.key), it.value) }, emptyList(), users))
        }
        server.rpc.on(Block.CONSTRUCTOR_ID) { request ->
            val call = Block.decode(request)
            blocks += call
            val id = userOf(call.id) ?: return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
            blocked[id] = NOW
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(Unblock.CONSTRUCTOR_ID) { request ->
            val call = Unblock.decode(request)
            unblocks += call
            userOf(call.id)?.let { blocked.remove(it) }
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(Report.CONSTRUCTOR_ID) { request ->
            val call = Report.decode(request)
            reports += call
            if (call.id.isEmpty()) return@on FakeAnswer.Error(400, "MESSAGE_REQUIRED")
            FakeAnswer.Result(answer(call.option, call.message))
        }
        server.rpc.on(ReportStory.CONSTRUCTOR_ID) { request ->
            val call = ReportStory.decode(request)
            storyReports += call
            FakeAnswer.Result(answer(call.option, call.message))
        }
        server.rpc.on(ReportPeer.CONSTRUCTOR_ID) { request ->
            peerReports += ReportPeer.decode(request)
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(ReportSpam.CONSTRUCTOR_ID) { request ->
            val call = ReportSpam.decode(request)
            spamReports += call
            userOf(call.peer)?.let { bars.remove(it) }
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(HidePeerSettingsBar.CONSTRUCTOR_ID) { request ->
            val call = HidePeerSettingsBar.decode(request)
            hiddenBars += call
            userOf(call.peer)?.let { bars.remove(it) }
            FakeTelegram.BOOL_TRUE
        }
        server.rpc.on(GetPeerSettings.CONSTRUCTOR_ID) { request ->
            val id = userOf(GetPeerSettings.decode(request).peer)
            FakeAnswer.Result(cn.ellerotta.ktproto.api.types.messages.PeerSettings(bars[id] ?: PeerSettings(), emptyList(), people().filter { it.id == id }))
        }
    }
}
