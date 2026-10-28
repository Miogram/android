package cn.ellerotta.core.ui.components.safety

import cn.ellerotta.core.model.PeerBar
import cn.ellerotta.core.model.ReportState

class ChatSafetyState(
    val blocked: Boolean = false,
    val canBlock: Boolean = false,
    val bar: PeerBar = PeerBar(),
    val report: ReportState? = null,
)

class ChatSafetyActions(
    val onBlock: () -> Unit,
    val onUnblock: () -> Unit,
    val onReportChat: () -> Unit,
    val onReportMessages: (List<Int>) -> Unit,
    val onAddContact: (firstName: String, lastName: String, sharePhone: Boolean) -> Unit,
    val onReportSpam: () -> Unit,
    val onDismissBar: () -> Unit,
    val report: ReportActions,
)
