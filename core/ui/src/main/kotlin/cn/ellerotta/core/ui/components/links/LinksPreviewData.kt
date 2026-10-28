package cn.ellerotta.core.ui.components.links

import cn.ellerotta.core.model.ChatInvitePreview
import cn.ellerotta.core.ui.components.common.previewPhoto

internal object LinksPreviewData {
    val groupInvite = ChatInvitePreview(
        hash = "AbCdEf123",
        title = "Weekend hikers",
        about = "Trails, meeting points and photos from our walks",
        participants = 248,
        photo = previewPhoto(start = 0xFF66BB6A.toInt(), end = 0xFF26A69A.toInt()),
    )

    val channelRequest = ChatInvitePreview(hash = "GhIjKl456", title = "Night sky", participants = 12_400, channel = true, requestNeeded = true)

    const val UNSUPPORTED_URL = "https://t.me/addtheme/nightsky"

    const val LOGIN_CODE = "52814"
}
