package cn.ellerotta.core.model

enum class EmojiCategory { People, Animals, Food, Activity, Travel, Objects, Symbols, Flags }

data class EmojiGroup(val category: EmojiCategory, val emoji: List<String>)

object EmojiCatalog {
    val groups: List<EmojiGroup> by lazy { parseEmojiCatalog(resource("emoji.txt")) }

    val tones: Map<String, List<String>> by lazy { parseEmojiTones(resource("emoji-tones.txt")) }

    private val bases: Map<String, String> by lazy { tones.flatMap { (base, toned) -> toned.map { it to base } }.toMap() }

    fun baseOf(emoji: String): String = bases[emoji] ?: emoji

    private fun resource(name: String) = EmojiCatalog::class.java.getResourceAsStream(name)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
}

fun parseEmojiCatalog(text: String): List<EmojiGroup> = text.lineSequence()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .mapNotNull { line ->
        val name = line.substringBefore('\t')
        val category = EmojiCategory.entries.firstOrNull { it.name.lowercase() == name } ?: return@mapNotNull null
        EmojiGroup(category, line.substringAfter('\t').split(' ').filter { it.isNotEmpty() })
    }
    .toList()

fun parseEmojiTones(text: String): Map<String, List<String>> = text.lineSequence()
    .filter { it.isNotBlank() && !it.startsWith("#") && '\t' in it }
    .mapNotNull { line ->
        val toned = line.substringAfter('\t').split(' ').filter { it.isNotEmpty() }
        if (toned.size == SKIN_TONES) line.substringBefore('\t') to toned else null
    }
    .toMap()

const val SKIN_TONES = 5

fun encodeEmojiTones(tones: Map<String, String>): String = tones.entries.joinToString("\n") { "${it.key}\t${it.value}" }

fun decodeEmojiTones(text: String?): Map<String, String> = text.orEmpty().lines().mapNotNull { line ->
    val base = line.substringBefore('\t', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    line.substringAfter('\t').takeIf { it.isNotEmpty() }?.let { base to it }
}.toMap()

fun emojiTonesWith(tones: Map<String, String>, base: String, toned: String): Map<String, String> = if (toned == base) tones - base else tones + (base to toned)

data class PickedEmoji(val emoji: String, val documentId: Long = 0) {
    val custom: Boolean get() = documentId != 0L
}

fun recentEmoji(recent: List<PickedEmoji>, picked: PickedEmoji, max: Int = MAX_RECENT_EMOJI): List<PickedEmoji> =
    (listOf(picked) + recent.filter { it != picked }).take(max)

fun encodeRecentEmoji(list: List<PickedEmoji>): String = list.joinToString("\n") { "${it.documentId}\t${it.emoji}" }

fun decodeRecentEmoji(text: String?): List<PickedEmoji> = text.orEmpty().lines().mapNotNull { line ->
    val id = line.substringBefore('\t', "").toLongOrNull() ?: return@mapNotNull null
    line.substringAfter('\t').takeIf { it.isNotEmpty() }?.let { PickedEmoji(it, id) }
}

const val MAX_RECENT_EMOJI = 40

fun FormattedText.withEmoji(emoji: PickedEmoji, start: Int, end: Int): Pair<FormattedText, Int> {
    val replaced = replaced(start, end, emoji.emoji)
    val placed = if (emoji.custom) {
        FormattedText(replaced.text, normalizeEntities(replaced.text, replaced.entities + TextEntity(start, emoji.emoji.length, EntityType.CustomEmoji(emoji.documentId))))
    } else {
        replaced
    }
    return placed to start + emoji.emoji.length
}

fun FormattedText.deletedBackwards(start: Int, end: Int): Pair<FormattedText, Int> {
    if (start != end) return replaced(start, end, "") to start
    if (start <= 0) return this to 0
    val custom = entities.firstOrNull { it.type is EntityType.CustomEmoji && it.end == start }
    val from = custom?.offset ?: clusterStart(text, start)
    return replaced(from, start, "") to from
}

private fun FormattedText.replaced(start: Int, end: Int, piece: String): FormattedText {
    val newText = text.replaceRange(start, end, piece)
    val delta = piece.length - (end - start)
    val moved = entities.mapNotNull { entity ->
        when {
            entity.end <= start -> entity
            entity.offset >= end -> entity.copy(offset = entity.offset + delta)
            entity.type is EntityType.CustomEmoji -> null
            entity.offset < start && entity.end > end -> entity.copy(length = entity.length + delta)
            entity.offset < start -> TextEntity(entity.offset, start - entity.offset, entity.type)
            entity.end > end -> TextEntity(start + piece.length, entity.end - end, entity.type)
            else -> null
        }
    }
    return FormattedText(newText, normalizeEntities(newText, moved))
}

internal fun clusterStart(text: String, index: Int): Int {
    var i = index
    var indicators = 0
    var j = index
    while (j > 0 && text.codePointBefore(j) in REGIONAL_INDICATORS) {
        indicators++
        j -= Character.charCount(text.codePointBefore(j))
    }
    if (indicators > 0) {
        val take = if (indicators % 2 == 0) 2 else 1
        repeat(take) { i -= Character.charCount(text.codePointBefore(i)) }
        return i
    }
    while (i > 0) {
        var cp = text.codePointBefore(i)
        i -= Character.charCount(cp)
        while (i > 0 && isEmojiModifier(cp)) {
            cp = text.codePointBefore(i)
            i -= Character.charCount(cp)
        }
        if (i > 0 && text.codePointBefore(i) == ZERO_WIDTH_JOINER) {
            i -= 1
            continue
        }
        break
    }
    return i
}

private fun isEmojiModifier(cp: Int): Boolean =
    cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || cp == 0x20E3 || cp in 0xE0020..0xE007F

private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
private const val ZERO_WIDTH_JOINER = 0x200D
