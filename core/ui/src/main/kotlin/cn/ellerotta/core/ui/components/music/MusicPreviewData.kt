package cn.ellerotta.core.ui.components.music

import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicRepeat
import cn.ellerotta.core.model.MusicSleep
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.ui.components.common.previewPhoto

internal object MusicPreviewData {
    private const val CHAT = "u2002"

    private fun song(id: Int, title: String?, performer: String?, duration: Int, fileName: String = "track_$id.mp3") = MusicTrack(
        dialogKey = CHAT,
        messageId = id,
        media = MediaItem(
            key = "doc$id",
            fileName = fileName,
            size = 4_000_000,
            dcId = 2,
            isPhoto = false,
            kind = MediaKind.Music,
            duration = duration,
            title = title,
            performer = performer,
        ),
    )

    val queue = listOf(
        song(10, "Sunrise Drive", "The Night Owls", 214),
        song(11, "Paper Boats on a Very Long and Winding River", "Ada & the Lanterns", 352),
        song(12, "Coastline", "Miro", 187),
        song(13, null, null, 240, fileName = "demo_take_3.mp3"),
        song(14, "Evening Tram", "The Night Owls", 199),
    )

    val actions = MusicActions(
        onToggle = {},
        onNext = {},
        onPrevious = {},
        onSeek = {},
        onShuffle = {},
        onRepeat = {},
        onPlayAt = {},
        onClose = {},
        onSpeed = {},
    )

    val playing = MusicState(
        queue = queue,
        index = 1,
        playing = true,
        positionMs = 62_000,
        durationMs = 352_000,
        order = MusicOrder.Shuffle,
        repeat = MusicRepeat.All,
        cover = previewPhoto(width = 256, start = 0xFFFFB85C.toInt(), end = 0xFFE86A9A.toInt()),
    )

    val bluePaused = playing.copy(playing = false, order = MusicOrder.Reverse, repeat = MusicRepeat.Off, cover = previewPhoto(width = 256, start = 0xFF1B3A6B.toInt(), end = 0xFF4FB3D9.toInt()))

    val longTalk = MusicState(
        queue = listOf(song(20, "Late Night Radio, Episode 12", "Night Owls Podcast", 48 * 60)),
        index = 0,
        playing = true,
        positionMs = 754_000,
        durationMs = 2_880_000,
        cover = previewPhoto(width = 256, start = 0xFF2E7D4F.toInt(), end = 0xFFB8E06A.toInt()),
        speed = 1.5f,
        sleep = MusicSleep(leftMs = 892_000),
        lyrics = "tonight we talk about the sea\nand the boats that never came back",
    )

    val pausedNoCover = playing.copy(index = 3, playing = false, positionMs = 0, durationMs = 0, order = MusicOrder.Reverse, repeat = MusicRepeat.One, cover = null)

    private fun voiceTrack(id: Int, duration: Int, unread: Boolean) = MusicTrack(
        dialogKey = CHAT,
        messageId = id,
        media = MediaItem(
            key = "doc$id",
            fileName = "voice_$id.ogg",
            size = 24_000,
            dcId = 2,
            isPhoto = false,
            kind = MediaKind.Voice,
            duration = duration,
            waveform = List(48) { (it * 7 % 29) + 2 },
        ),
        senderName = "Maria Lopez",
        senderId = 2002,
        avatarKey = "photo_maria",
        unread = unread,
    )

    val voice = MusicState(
        queue = listOf(voiceTrack(30, 37, unread = false), voiceTrack(31, 12, unread = true)),
        index = 0,
        playing = true,
        positionMs = 12_000,
        durationMs = 37_000,
        cover = previewPhoto(start = 0xFFE86A9A.toInt(), end = 0xFFFFB85C.toInt()),
        speed = 1.5f,
    )

    val voicePausedNoPhoto = voice.copy(index = 1, playing = false, positionMs = 0, durationMs = 0, cover = null, speed = 1f)
}
