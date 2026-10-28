package cn.ellerotta.core.ui

import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem

private val now = (System.currentTimeMillis() / 1000).toInt()

internal fun photoMessage(id: Int, groupedId: Long? = null) = MessageItem(
    id, "", out = groupedId != null || id % 2 == 0, date = now - id, groupedId = groupedId,
    media = MediaItem("photo$id", "photo$id.jpg", 1000, 2, true, kind = MediaKind.Photo, thumb = ByteArray(0), width = 800, height = 600),
)

internal fun videoMessage(id: Int) = MessageItem(
    id, "", out = false, date = now - id,
    media = MediaItem("video$id", "video$id.mp4", 1000, 2, false, kind = MediaKind.Video, width = 800, height = 600, duration = 12),
)

internal fun gifMessage(id: Int) = MessageItem(
    id, "", out = false, date = now - id,
    media = MediaItem("gif$id", "gif$id.mp4", 1000, 2, false, kind = MediaKind.Gif, width = 800, height = 600),
)

internal fun stickerMessage(id: Int) = MessageItem(
    id, "", out = true, date = now - id,
    media = MediaItem("sticker$id", "s$id.webp", 1000, 2, false, kind = MediaKind.Sticker, emoji = "\uD83D\uDC31"),
)

internal fun roundMessage(id: Int) = MessageItem(
    id, "", out = false, date = now - id,
    media = MediaItem("round$id", "r$id.mp4", 1000, 2, false, kind = MediaKind.Round, width = 240, height = 240, duration = 5),
)
