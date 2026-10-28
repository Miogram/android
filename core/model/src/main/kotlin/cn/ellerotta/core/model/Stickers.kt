package cn.ellerotta.core.model

data class StickerSetInfo(
    val key: String,
    val title: String,
    val shortName: String,
    val count: Int,
    val installed: Boolean,
    val thumb: MediaItem? = null,
)

fun List<StickerSetInfo>.orderedBy(setKeys: List<String>): List<StickerSetInfo> {
    val index = setKeys.withIndex().associate { it.value to it.index }
    return sortedBy { index[it.key] ?: Int.MAX_VALUE }
}

data class StickerSetDetail(val info: StickerSetInfo, val stickers: List<MediaItem>)

data class StickerPanel(
    val recent: List<MediaItem> = emptyList(),
    val faved: List<MediaItem> = emptyList(),
    val sets: List<StickerSetInfo> = emptyList(),
)

data class FeaturedSet(val info: StickerSetInfo, val covers: List<MediaItem>)

data class FeaturedSets(val sets: List<FeaturedSet> = emptyList(), val unread: Set<String> = emptySet()) {
    fun withInstalled(setKey: String, installed: Boolean): FeaturedSets =
        copy(sets = sets.map { if (it.info.key == setKey) it.copy(info = it.info.copy(installed = installed)) else it })

    fun read(setKeys: Collection<String>): FeaturedSets = copy(unread = unread - setKeys.toSet())
}

data class EmojiCategoryGroup(val title: String, val iconId: Long, val icon: MediaItem? = null, val emoticons: List<String>, val greeting: Boolean = false) {
    val searchKey: String get() = "\u0000category:$title"
}

data class ChatStickerState(
    val panel: StickerPanel? = null,
    val setStickers: Map<String, List<MediaItem>> = emptyMap(),
    val suggestions: List<MediaItem> = emptyList(),
    val openSet: StickerSetDetail? = null,
    val emojiSets: List<StickerSetInfo>? = null,
    val emojiSearch: EmojiSearch? = null,
    val recentEmoji: List<PickedEmoji> = emptyList(),
    val emojiTones: Map<String, String> = emptyMap(),
    val stickerSearch: StickerSearch? = null,
    val gifs: GifPanel = GifPanel(),
    val keyboardHeight: Int = 0,
    val featured: FeaturedSets? = null,
    val featuredEmoji: FeaturedSets? = null,
    val emojiCategories: List<EmojiCategoryGroup> = emptyList(),
    val stickerCategories: List<EmojiCategoryGroup> = emptyList(),
)

data class EmojiSearch(val query: String, val emoji: List<String> = emptyList(), val custom: List<MediaItem> = emptyList())

data class StickerSearch(val query: String, val stickers: List<MediaItem>? = null)

data class GifResults(val query: String, val gifs: List<MediaItem> = emptyList(), val nextOffset: String? = null, val loading: Boolean = false)

data class GifPanel(val saved: List<MediaItem>? = null, val trending: GifResults? = null, val search: GifResults? = null)

data class GifPage(val gifs: List<MediaItem>, val nextOffset: String?)

enum class StickerFormat { Lottie, Video, Image }

fun stickerFormat(bytes: ByteArray): StickerFormat = when {
    bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte() -> StickerFormat.Lottie
    bytes.size >= 4 && bytes[0] == 0x1a.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xdf.toByte() && bytes[3] == 0xa3.toByte() -> StickerFormat.Video
    else -> StickerFormat.Image
}

fun stickerOutline(encoded: ByteArray): String = buildString(encoded.size * 3 + 2) {
    append('M')
    for (byte in encoded) {
        val num = byte.toInt() and 0xff
        if (num >= 128 + 64) {
            append(OUTLINE_LOOKUP[num - 128 - 64])
        } else {
            if (num >= 128) append(',') else if (num >= 64) append('-')
            append(num and 63)
        }
    }
    append('z')
}

const val STICKER_OUTLINE_BOX = 512f

private const val OUTLINE_LOOKUP = "AACAAAAHAAALMAAAQASTAVAAAZaacaaaahaaalmaaaqastava.az0123456789-,"

val MediaItem.playsAsSticker: Boolean get() = kind == MediaKind.Sticker && (animated || mimeType == "video/webm")

fun MediaItem.stickerPreview(): MediaItem? {
    val size = previewSize() ?: return null
    return MediaItem("$key@${size.type}", "${key}_${size.type}.webp", size.bytes, dcId, isPhoto = false, kind = MediaKind.Sticker, emoji = emoji, width = size.width, height = size.height, mimeType = "image/webp")
}

val MediaItem.stickerPreviewKey: String? get() = previewSize()?.let { "$key@${it.type}" }

private fun MediaItem.previewSize(): MediaSize? = if (playsAsSticker) sizes.maxByOrNull { it.width * it.height } else null

fun isSingleEmoji(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_EMOJI_CHARS) return false
    var pictographs = 0
    var flags = 0
    var i = 0
    while (i < trimmed.length) {
        val cp = trimmed.codePointAt(i)
        when {
            cp == ZWJ -> pictographs--
            cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || cp == KEYCAP -> Unit
            cp in 0x1F1E6..0x1F1FF -> flags++
            isPictograph(cp) -> pictographs++
            else -> return false
        }
        i += Character.charCount(cp)
    }
    return pictographs + (flags + 1) / 2 == 1
}

private fun isPictograph(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp in 0x2B00..0x2BFF || cp == 0x00A9 || cp == 0x00AE || cp in 0x2190..0x21FF

fun jumboEmojiCount(text: String): Int {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_EMOJI_CHARS * JUMBO_MAX) return 0
    var count = 0
    var flags = 0
    var joined = false
    var i = 0
    while (i < trimmed.length) {
        val cp = trimmed.codePointAt(i)
        val next = i + Character.charCount(cp)
        when {
            cp == ZWJ -> joined = true
            cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || cp == KEYCAP || cp in 0xE0020..0xE007F -> Unit
            Character.isWhitespace(cp) -> {
                joined = false
                flags = 0
            }
            cp in 0x1F1E6..0x1F1FF -> if (flags++ % 2 == 0) count++
            isPictograph(cp) -> {
                val colour = cp >= 0x1F000 || (next < trimmed.length && trimmed.codePointAt(next) == 0xFE0F)
                if (!colour) return 0
                if (!joined) count++
                joined = false
            }
            else -> return 0
        }
        i = next
    }
    return count.takeIf { it in 1..JUMBO_MAX } ?: 0
}

const val JUMBO_MAX = 3

private const val ZWJ = 0x200D
private const val KEYCAP = 0x20E3
private const val MAX_EMOJI_CHARS = 16
