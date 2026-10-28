package cn.ellerotta.core.ui.components.safety

import cn.ellerotta.core.model.BlockedList
import cn.ellerotta.core.model.BlockedUser
import cn.ellerotta.core.model.PeerBar
import cn.ellerotta.core.model.PeerReportReason
import cn.ellerotta.core.model.ReportOption
import cn.ellerotta.core.model.ReportState
import cn.ellerotta.core.model.ReportStep
import cn.ellerotta.core.model.ReportTarget
import cn.ellerotta.core.ui.components.common.previewPhoto

internal object SafetyPreviewData {
    val users = listOf(
        BlockedUser("u1", "Alice Example", avatarKey = "a1", username = "alice"),
        BlockedUser("u2", "Bob", username = null),
        BlockedUser("u3", "Carol With A Rather Long Name To Wrap", username = "carol_writes"),
    )
    val blocked = BlockedList(users, total = 3, read = 3, loaded = true)
    val avatars = mapOf("a1" to previewPhoto())

    private val target = ReportTarget.Messages("u1", listOf(5))

    val menu = ReportState(
        target,
        ReportStep.Choose("What is wrong with these messages?", listOf(ReportOption("01", "Spam"), ReportOption("02", "Violence"), ReportOption("03", "Something else"))),
    )
    val peerReasons = ReportState(ReportTarget.Peer("u1"), ReportStep.Choose(null, PeerReportReason.entries.map { ReportOption(it.name, reason = it) }))
    val comment = ReportState(target, ReportStep.Comment(optional = false, option = "03"))
    val optionalComment = ReportState(target, ReportStep.Comment(optional = true, option = "03"))
    val loading = ReportState(target, null, sending = true)
    val done = ReportState(target, ReportStep.Done)

    val stranger = PeerBar(reportSpam = true, addContact = true, block = true)
}
