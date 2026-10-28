package cn.ellerotta.core.ui.components.chat

import cn.ellerotta.core.model.AttachmentDraft
import cn.ellerotta.core.model.AttachmentItem
import cn.ellerotta.core.model.AttachmentKind
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.SendOptions
import cn.ellerotta.core.model.TextEntity
import cn.ellerotta.core.ui.components.common.previewPhoto

internal object SendingPreviewData {
    val draft = AttachmentDraft(
        listOf(
            AttachmentItem(1, "harbour.jpg", AttachmentKind.Photo, 2_400_000, previewPhoto(160)),
            AttachmentItem(2, "clip.mp4", AttachmentKind.Video, 18_000_000, previewPhoto(160, 160, 0xFF2E7D32.toInt(), 0xFFFFB300.toInt())),
            AttachmentItem(3, "song.mp3", AttachmentKind.Audio, 4_100_000),
        ),
    )

    val actions = ChatAttachmentActions({}, {}, {}, { _, _ -> }, {})

    const val FIRST = ChatPreviewData.NOW + 86_400 + 7_200

    val scheduled = listOf(
        MessageItem(11, "Happy birthday! See you at six", out = true, date = FIRST, entities = listOf(TextEntity(0, 14, EntityType.Bold))),
        MessageItem(12, "the report", out = true, date = FIRST + 86_400, media = MediaItem("doc1", "report.pdf", 240_000, 2, isPhoto = false)),
        MessageItem(13, "call me when you are free", out = true, date = SendOptions.WHEN_ONLINE),
    )

    val scheduledActions = ScheduledActions({}, { _, _ -> }, { _, _ -> }, {})
}
