package cn.ellerotta.core.model

data class MusicTrack(
    val dialogKey: String,
    val messageId: Int,
    val media: MediaItem,
    val senderName: String? = null,
    val senderId: Long = 0,
    val avatarKey: String? = null,
    val out: Boolean = false,
    val unread: Boolean = false,
    val saved: Boolean = false,
) {
    val id: String get() = if (saved) "$dialogKey/saved/${media.key}" else "$dialogKey/$messageId"

    val title: String get() = media.title?.takeIf { it.isNotBlank() } ?: media.fileName.substringBeforeLast('.').ifEmpty { media.fileName }

    val performer: String? get() = media.performer?.takeIf { it.isNotBlank() }

    val isVoice: Boolean get() = media.kind == MediaKind.Voice

    val slot: String get() = "$id#${media.key}"

    val isLong: Boolean get() = !isVoice && media.duration >= LONG_TRACK_SECONDS

    val hasSpeed: Boolean get() = isVoice || isLong
}

const val LONG_TRACK_SECONDS = 20 * 60

data class MusicSleep(val leftMs: Long = 0, val endOfTrack: Boolean = false)

val SLEEP_MINUTES = listOf(15, 30, 45, 60)

fun MediaItem.cover(): MediaItem? = sizes.maxByOrNull { it.width * it.height }?.let(::atSize)

enum class MusicRepeat { Off, All, One }

enum class MusicOrder { Chat, Reverse, Shuffle }

data class MusicModes(val shuffle: Boolean = false, val reverse: Boolean = false, val repeat: MusicRepeat = MusicRepeat.Off) {
    val order: MusicOrder get() = when {
        shuffle -> MusicOrder.Shuffle
        reverse -> MusicOrder.Reverse
        else -> MusicOrder.Chat
    }

    fun with(order: MusicOrder): MusicModes = when (order) {
        MusicOrder.Chat -> copy(shuffle = false, reverse = false)
        MusicOrder.Reverse -> copy(shuffle = false, reverse = true)
        MusicOrder.Shuffle -> copy(shuffle = true)
    }
}

fun nextOrder(order: MusicOrder): MusicOrder = when (order) {
    MusicOrder.Chat -> MusicOrder.Shuffle
    MusicOrder.Shuffle -> MusicOrder.Reverse
    MusicOrder.Reverse -> MusicOrder.Chat
}

fun shuffleKey(track: MusicTrack, seed: Long): Long {
    var hash = -0x340d631b7bdddcdbL
    for (char in track.slot) hash = (hash xor char.code.toLong()) * 0x100000001b3L
    var mixed = hash + seed * -0x61c8864680b583ebL
    mixed = (mixed xor (mixed ushr 30)) * -0x40a7b892e31b1a47L
    mixed = (mixed xor (mixed ushr 27)) * -0x6b2fb644ecceee15L
    return mixed xor (mixed ushr 31)
}

fun arranged(tracks: List<MusicTrack>, order: MusicOrder, current: MusicTrack?, seed: Long): List<MusicTrack> = when (order) {
    MusicOrder.Chat -> tracks
    MusicOrder.Reverse -> tracks.asReversed().toList()
    MusicOrder.Shuffle -> {
        val first = current?.slot?.let { slot -> tracks.firstOrNull { it.slot == slot } }
        listOfNotNull(first) + tracks.filter { it.slot != first?.slot }.sortedBy { shuffleKey(it, seed) }
    }
}

fun grown(queue: List<MusicTrack>, at: Int, older: List<MusicTrack>, newer: List<MusicTrack>, order: MusicOrder, seed: Long): List<MusicTrack> = when (order) {
    MusicOrder.Chat -> older + queue + newer
    MusicOrder.Reverse -> newer.asReversed() + queue + older.asReversed()
    MusicOrder.Shuffle -> {
        val split = (at + 1).coerceIn(0, queue.size)
        val coming = (older + newer).sortedBy { shuffleKey(it, seed) }.toMutableList()
        val ahead = ArrayList<MusicTrack>()
        for (entry in queue.subList(split, queue.size)) {
            val key = shuffleKey(entry, seed)
            while (coming.isNotEmpty() && shuffleKey(coming.first(), seed) < key) ahead += coming.removeAt(0)
            ahead += entry
        }
        queue.subList(0, split) + ahead + coming
    }
}

fun playNext(queue: List<MusicTrack>, at: Int, index: Int): List<MusicTrack> = when {
    index == at || index !in queue.indices -> queue
    index > at -> queue.moved(index, at + 1)
    else -> queue.moved(index, at)
}

data class MusicState(
    val queue: List<MusicTrack>,
    val index: Int,
    val playing: Boolean,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val order: MusicOrder = MusicOrder.Chat,
    val repeat: MusicRepeat = MusicRepeat.Off,
    val cover: ByteArray? = null,
    val speed: Float = 1f,
    val sleep: MusicSleep? = null,
    val lyrics: String? = null,
) {
    val track: MusicTrack get() = queue[index.coerceIn(0, queue.lastIndex)]

    val lengthMs: Long get() = durationMs.takeIf { it > 0 } ?: track.media.duration * 1000L

    fun isTrack(dialogKey: String, messageId: Int): Boolean = track.dialogKey == dialogKey && track.messageId == messageId

    fun voicePlayback(): VoicePlayback? {
        val voice = track.takeIf { it.isVoice } ?: return null
        return VoicePlayback(voice.media.key, playing = playing, positionMs = positionMs, durationMs = lengthMs, loading = buffering && positionMs == 0L, speed = speed)
    }
}

val VOICE_SPEEDS = listOf(1f, 1.5f, 2f)

fun nextVoiceSpeed(speed: Float): Float = VOICE_SPEEDS.getOrNull(VOICE_SPEEDS.indexOf(speed) + 1) ?: VOICE_SPEEDS.first()

fun voiceQueue(tapped: MusicTrack, found: List<MusicTrack>): List<MusicTrack> =
    listOf(tapped) + found
        .filter { it.dialogKey == tapped.dialogKey && it.isVoice && it.messageId > tapped.messageId }
        .distinctBy { it.messageId }
        .sortedBy { it.messageId }
        .takeWhile { !it.out && it.unread }

fun voiceQuery(afterId: Int, limit: Int = VOICE_PAGE): MusicQuery = MusicQuery(offsetId = afterId, addOffset = -limit, limit = limit, minId = afterId)

const val VOICE_PAGE = 20

enum class MusicPage { Around, Older, Newer }

data class MusicQuery(val offsetId: Int, val addOffset: Int, val limit: Int, val minId: Int = 0)

data class MusicPlaylist(
    val dialogKey: String,
    val tracks: List<MusicTrack>,
    val olderDone: Boolean = false,
    val newerDone: Boolean = false,
    val limit: Int = MUSIC_PAGE,
) {
    fun query(page: MusicPage, aroundId: Int = tracks.first().messageId): MusicQuery = when (page) {
        MusicPage.Around -> MusicQuery(offsetId = aroundId + 1, addOffset = -limit / 2, limit = limit)
        MusicPage.Older -> MusicQuery(offsetId = tracks.first().messageId, addOffset = 0, limit = limit)
        MusicPage.Newer -> tracks.last().messageId.let { newest -> MusicQuery(offsetId = newest, addOffset = -limit, limit = limit, minId = newest) }
    }

    fun merged(page: MusicPage, found: List<MusicTrack>, aroundId: Int = tracks.first().messageId): MusicPlaylist {
        val known = tracks.mapTo(HashSet()) { it.messageId }
        val same = found.filter { it.dialogKey == dialogKey }.distinctBy { it.messageId }
        val all = (tracks + same.filter { it.messageId !in known }).sortedBy { it.messageId }
        val older = same.count { it.messageId < aroundId }
        val newer = same.count { it.messageId > aroundId }
        return when (page) {
            MusicPage.Around -> copy(tracks = all, olderDone = older < limit - limit / 2 - 1, newerDone = newer < limit / 2)
            MusicPage.Older -> copy(tracks = all, olderDone = same.count { it.messageId < tracks.first().messageId } < limit)
            MusicPage.Newer -> copy(tracks = all, newerDone = same.count { it.messageId > tracks.last().messageId } < limit - 1)
        }
    }

    fun pageFor(index: Int): MusicPage? = when {
        !olderDone && index < MUSIC_AHEAD -> MusicPage.Older
        !newerDone && index >= tracks.size - MUSIC_AHEAD -> MusicPage.Newer
        else -> null
    }

    fun pageAhead(upcoming: Int): MusicPage? = when {
        upcoming >= MUSIC_AHEAD -> null
        !olderDone -> MusicPage.Older
        !newerDone -> MusicPage.Newer
        else -> null
    }
}

const val MUSIC_PAGE = 20

const val MUSIC_AHEAD = 3
