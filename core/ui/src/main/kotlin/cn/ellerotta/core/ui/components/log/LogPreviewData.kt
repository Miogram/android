package cn.ellerotta.core.ui.components.log

import cn.ellerotta.core.model.LogLine

internal object LogPreviewData {
    val lines: List<LogLine> = listOf(
        "connecting to dc 2",
        "auth key exchange: req_pq_multi",
        "auth key exchange: done",
        "-> help.getConfig",
        "<- config: this dc 2, 5 dcs",
        "-> messages.getDialogs limit 100",
        "<- messages.dialogsSlice: 100 dialogs, 214 messages",
        "-> updates.getState",
        "<- updates.state pts 4102 qts 0 seq 88",
        "update: updateUserStatus online",
        "update: updateNewMessage in c202",
        "-> messages.readHistory c202",
    ).mapIndexed { index, text -> LogLine(time = 1_792_065_600_000L + index * 137L, text = text) }
}
