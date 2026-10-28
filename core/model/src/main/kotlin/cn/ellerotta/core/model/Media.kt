package cn.ellerotta.core.model

data class MediaSize(val type: String, val width: Int, val height: Int, val bytes: Long)

data class LinkPreview(
    val url: String,
    val siteName: String? = null,
    val title: String? = null,
    val description: String? = null,
    val photo: MediaItem? = null,
)

fun List<MediaSize>.fitting(side: Int): MediaSize? =
    filter { maxOf(it.width, it.height) >= side }.minByOrNull { it.width * it.height } ?: maxByOrNull { it.width * it.height }

fun MediaItem.atSize(size: MediaSize): MediaItem = copy(
    key = "$key@${size.type}",
    fileName = "${key}_${size.type}.jpg",
    size = size.bytes.takeIf { it > 0 },
    isPhoto = true,
    kind = MediaKind.Photo,
    width = size.width,
    height = size.height,
    sizes = emptyList(),
)

fun MediaItem.preview(side: Int): MediaItem? {
    val size = sizes.fitting(side) ?: return if (kind == MediaKind.Photo) this else null
    if (kind == MediaKind.Photo && size == sizes.maxByOrNull { it.width * it.height }) return this
    return atSize(size)
}

val MediaItem.isVisual: Boolean get() = kind == MediaKind.Photo || kind == MediaKind.Video || kind == MediaKind.Gif

val MediaItem.savable: Boolean get() = kind in SAVABLE

val MediaItem.autoSaves: Boolean get() = savable && kind != MediaKind.Gif && '@' !in key

val MediaItem.saveMimeType: String get() = mimeType ?: when (kind) {
    MediaKind.Photo -> "image/jpeg"
    MediaKind.Video, MediaKind.Round, MediaKind.Gif -> "video/mp4"
    MediaKind.Voice -> "audio/ogg"
    MediaKind.Music -> "audio/mpeg"
    else -> "application/octet-stream"
}

private val SAVABLE = setOf(MediaKind.Photo, MediaKind.Video, MediaKind.Round, MediaKind.Voice, MediaKind.Music, MediaKind.Gif, MediaKind.File)
