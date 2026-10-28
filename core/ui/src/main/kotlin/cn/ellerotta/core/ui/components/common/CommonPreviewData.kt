package cn.ellerotta.core.ui.components.common

import cn.ellerotta.core.model.UiError

internal object CommonPreviewData {
    const val NAME = "Alice Johnson"
    const val SEED = 101L

    val photo: ByteArray = previewPhoto(start = 0xFFFFB74D.toInt(), end = 0xFFE86A9A.toInt())

    val mediaPhoto: ByteArray = previewPhoto(width = 360, height = 240, start = 0xFF263238.toInt(), end = 0xFF5C6BC0.toInt())

    val names = listOf(
        "Alice Johnson", "Bob Carter", "Kate Wilson", "Leo Park", "Nina Brooks", "Oscar Reed",
        "Paula Diaz", "Quentin Hale", "Rosa Lind", "Sam Ortiz", "Tara Quinn", "Uma Patel",
    )

    val errors: List<UiError> = listOf(
        UiError.SessionEnded,
        UiError.Rpc(400, "CHAT_ADMIN_REQUIRED"),
        UiError.Rpc(420, "FLOOD_WAIT_300"),
        UiError.Message("The file is too large to send"),
    )
}
