package app.miogram.data.telegram

import cn.ellerotta.core.model.BlockedList
import cn.ellerotta.core.model.BlockedUser
import cn.ellerotta.core.model.PeerBar
import cn.ellerotta.core.model.PeerReportReason
import cn.ellerotta.core.model.ReportOption
import cn.ellerotta.core.model.ReportStep
import cn.ellerotta.core.model.ReportTarget
import cn.ellerotta.ktproto.api.functions.account.ReportPeer
import cn.ellerotta.ktproto.api.functions.contacts.Block
import cn.ellerotta.ktproto.api.functions.contacts.GetBlocked
import cn.ellerotta.ktproto.api.functions.contacts.Unblock
import cn.ellerotta.ktproto.api.functions.messages.GetPeerSettings
import cn.ellerotta.ktproto.api.functions.messages.HidePeerSettingsBar
import cn.ellerotta.ktproto.api.functions.messages.Report
import cn.ellerotta.ktproto.api.functions.messages.ReportSpam
import cn.ellerotta.ktproto.api.functions.users.GetFullUser
import cn.ellerotta.ktproto.api.functions.stories.Report as ReportStory
import cn.ellerotta.ktproto.api.types.InputReportReasonChildAbuse
import cn.ellerotta.ktproto.api.types.InputReportReasonFake
import cn.ellerotta.ktproto.api.types.InputReportReasonIllegalDrugs
import cn.ellerotta.ktproto.api.types.InputReportReasonOther
import cn.ellerotta.ktproto.api.types.InputReportReasonPersonalDetails
import cn.ellerotta.ktproto.api.types.InputReportReasonPornography
import cn.ellerotta.ktproto.api.types.InputReportReasonSpam
import cn.ellerotta.ktproto.api.types.InputReportReasonViolence
import cn.ellerotta.ktproto.api.types.PeerSettings
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.ReportResultAddComment
import cn.ellerotta.ktproto.api.types.ReportResultChooseOption
import cn.ellerotta.ktproto.api.types.ReportResultReported
import cn.ellerotta.ktproto.api.types.TypeReportReason
import cn.ellerotta.ktproto.api.types.TypeReportResult
import cn.ellerotta.ktproto.api.types.User
import cn.ellerotta.ktproto.api.types.contacts.Blocked
import cn.ellerotta.ktproto.api.types.contacts.BlockedSlice
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Singleton

@Singleton
class SafetyRepository @Inject constructor(private val telegram: TelegramClientHolder) {
    private val registry get() = telegram.peers

    suspend fun blocked(offset: Int, limit: Int = PAGE): BlockedList {
        val answer = telegram.client().invoke(GetBlocked(offset = offset, limit = limit))
        val (entries, people, total) = when (answer) {
            is BlockedSlice -> Triple(answer.blocked, answer.users, answer.count)
            is Blocked -> Triple(answer.blocked, answer.users, offset + answer.blocked.size)
        }
        registry.rememberUsers(people)
        val users = people.filterIsInstance<User>().associateBy { it.id }
        val list = entries.mapNotNull { blocked ->
            val user = (blocked.peerId as? PeerUser)?.let { users[it.userId] } ?: return@mapNotNull null
            val key = "u${user.id}"
            registry.blocked[key] = true
            BlockedUser(key, nameOf(user), registry.avatarKey(avatarOf(key, user)), user.username, blocked.date)
        }
        return BlockedList(list, total, entries.size, loaded = true).also { if (offset == 0 && limit == PAGE) telegram.memory["blocked"] = it }
    }

    fun storedBlocked(): BlockedList? = telegram.memory["blocked"]

    suspend fun blockedCount(): Int = blocked(0, 1).total

    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val changes: SharedFlow<Unit> = changed.asSharedFlow()

    suspend fun block(key: String) {
        telegram.client().invoke(Block(id = registry.peer(key)))
        registry.blocked[key] = true
        changed.tryEmit(Unit)
    }

    suspend fun unblock(key: String) {
        telegram.client().invoke(Unblock(id = registry.peer(key)))
        registry.blocked[key] = false
        changed.tryEmit(Unit)
    }

    fun isSelf(key: String): Boolean = telegram.userId?.let { (peerOfKey(key) as? PeerUser)?.userId == it } == true

    suspend fun isBlocked(key: String): Boolean {
        if (peerOfKey(key) !is PeerUser) return false
        val full = telegram.client().invoke(GetFullUser(registry.user(key))).fullUser
        registry.blocked[key] = full.blocked
        return full.blocked
    }

    suspend fun bar(key: String): PeerBar {
        val answer = telegram.client().invoke(GetPeerSettings(registry.peer(key)))
        registry.rememberUsers(answer.users)
        registry.rememberChats(answer.chats)
        return barOf(answer.settings)
    }

    suspend fun hideBar(key: String) {
        telegram.client().invoke(HidePeerSettingsBar(registry.peer(key)))
    }

    suspend fun reportSpam(key: String) {
        telegram.client().invoke(ReportSpam(registry.peer(key)))
    }

    suspend fun report(target: ReportTarget, option: String?, comment: String = ""): ReportStep = when (target) {
        is ReportTarget.Peer -> reportPeer(target.key, option, comment)
        is ReportTarget.Messages -> stepOf(telegram.client().invoke(Report(registry.peer(target.key), target.ids, bytesOf(option), comment.trim())))
        is ReportTarget.Story -> stepOf(telegram.client().invoke(ReportStory(registry.peer(target.key), listOf(target.id), bytesOf(option), comment.trim())))
    }

    private suspend fun reportPeer(key: String, option: String?, comment: String): ReportStep {
        if (option == null) return ReportStep.Choose(null, PeerReportReason.entries.map { ReportOption(it.name, reason = it) })
        val reason = PeerReportReason.valueOf(option)
        if (reason == PeerReportReason.Other && comment.isBlank()) return ReportStep.Comment(optional = false, option = option)
        telegram.client().invoke(ReportPeer(registry.peer(key), reasonOf(reason), comment.trim()))
        return ReportStep.Done
    }

    private fun reasonOf(reason: PeerReportReason): TypeReportReason = when (reason) {
        PeerReportReason.Spam -> InputReportReasonSpam
        PeerReportReason.Violence -> InputReportReasonViolence
        PeerReportReason.Pornography -> InputReportReasonPornography
        PeerReportReason.ChildAbuse -> InputReportReasonChildAbuse
        PeerReportReason.IllegalDrugs -> InputReportReasonIllegalDrugs
        PeerReportReason.PersonalDetails -> InputReportReasonPersonalDetails
        PeerReportReason.Fake -> InputReportReasonFake
        PeerReportReason.Other -> InputReportReasonOther
    }

    private fun stepOf(result: TypeReportResult): ReportStep = when (result) {
        is ReportResultChooseOption -> ReportStep.Choose(result.title, result.options.map { ReportOption(hexOf(it.option), it.text) })
        is ReportResultAddComment -> ReportStep.Comment(result.optional, hexOf(result.option))
        ReportResultReported -> ReportStep.Done
    }

    companion object {
        const val PAGE = 50
    }
}

internal fun barOf(settings: PeerSettings) = PeerBar(reportSpam = settings.reportSpam, addContact = settings.addContact, block = settings.blockContact)

internal fun hexOf(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

internal fun bytesOf(hex: String?): ByteArray = if (hex.isNullOrEmpty()) ByteArray(0) else ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
