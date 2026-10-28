package cn.ellerotta.core.model

data class SendOptions(val silent: Boolean = false, val scheduleDate: Int? = null) {
    val scheduled: Boolean get() = scheduleDate != null

    companion object {
        const val WHEN_ONLINE = 0x7FFFFFFE

        val None = SendOptions()

        const val MIN_AHEAD = 60
    }
}

enum class AttachmentKind {
    Photo,
    Video,
    Audio,
    File,
    ;

    val visual: Boolean get() = this == Photo || this == Video

    companion object {
        private val PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp")

        fun of(mimeType: String): AttachmentKind = when {
            mimeType in PHOTO_TYPES -> Photo
            mimeType.startsWith("video/") -> Video
            mimeType.startsWith("audio/") -> Audio
            else -> File
        }
    }
}

data class AttachmentItem(val id: Int, val name: String, val kind: AttachmentKind, val size: Long, val preview: ByteArray? = null) {
    override fun equals(other: Any?): Boolean =
        other is AttachmentItem && id == other.id && name == other.name && kind == other.kind && size == other.size && preview.contentEquals(other.preview)

    override fun hashCode(): Int = ((id * 31 + name.hashCode()) * 31 + kind.hashCode()) * 31 + (preview?.contentHashCode() ?: 0)
}

data class AttachmentDraft(val items: List<AttachmentItem>, val asFile: Boolean = false, val spoiler: Boolean = false) {
    val canSpoiler: Boolean get() = !asFile && items.any { it.kind.visual }

    companion object {
        const val MAX_ITEMS = 10
    }
}

fun <T> albumsOf(items: List<T>, asFile: Boolean, kind: (T) -> AttachmentKind): List<List<T>> {
    fun group(item: T): Int = when {
        asFile -> 0
        kind(item).visual -> 1
        kind(item) == AttachmentKind.Audio -> 2
        else -> 0
    }
    val albums = ArrayList<MutableList<T>>()
    var lastGroup = -1
    for (item in items) {
        val current = group(item)
        val open = albums.lastOrNull()
        if (open != null && current == lastGroup && open.size < AttachmentDraft.MAX_ITEMS) open += item else albums += mutableListOf(item)
        lastGroup = current
    }
    return albums
}

data class OutgoingLast(val preview: Preview, val date: Int, val failed: Boolean)

data class OutgoingUploads(val files: Int, val sent: Long, val total: Long)
