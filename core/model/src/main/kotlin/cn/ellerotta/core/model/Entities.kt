package cn.ellerotta.core.model

data class TextEntity(val offset: Int, val length: Int, val type: EntityType) {
    val end: Int get() = offset + length

    fun part(text: String): String = text.substring(offset, end)

    fun overlaps(other: TextEntity): Boolean = offset < other.end && other.offset < end
}

sealed interface EntityType {
    data object Bold : EntityType
    data object Italic : EntityType
    data object Underline : EntityType
    data object Strike : EntityType

    data object Spoiler : EntityType

    data object Code : EntityType

    data class Pre(val language: String = "") : EntityType

    data class Blockquote(val collapsed: Boolean = false) : EntityType

    data object Url : EntityType

    data class TextUrl(val url: String) : EntityType

    data object Mention : EntityType

    data class MentionName(val userId: Long) : EntityType

    data object Hashtag : EntityType
    data object Cashtag : EntityType
    data object BotCommand : EntityType
    data object Email : EntityType
    data object Phone : EntityType
    data object BankCard : EntityType

    data class CustomEmoji(val documentId: Long) : EntityType

    data class FormattedDate(
        val date: Int,
        val relative: Boolean = false,
        val shortTime: Boolean = false,
        val longTime: Boolean = false,
        val shortDate: Boolean = false,
        val longDate: Boolean = false,
        val dayOfWeek: Boolean = false,
    ) : EntityType {
        val formats: Boolean get() = relative || shortTime || longTime || shortDate || longDate || dayOfWeek
    }
}

data class FormattedText(val text: String, val entities: List<TextEntity> = emptyList())

private val EntityType.isBlock: Boolean get() = this is EntityType.Pre || this is EntityType.Blockquote

private val EntityType.isExclusive: Boolean
    get() = when (this) {
        EntityType.Bold, EntityType.Italic, EntityType.Underline, EntityType.Strike, EntityType.Spoiler,
        is EntityType.Pre, is EntityType.Blockquote, is EntityType.CustomEmoji,
        -> false
        else -> true
    }

fun normalizeEntities(text: String, entities: List<TextEntity>): List<TextEntity> {
    val size = text.length
    val fixed = entities.mapNotNull { entity ->
        var start = entity.offset.coerceIn(0, size)
        var end = (entity.offset.toLong() + entity.length).coerceIn(0, size.toLong()).toInt()
        if (start in 1 until size && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start--
        if (end in 1 until size && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end++
        if (end <= start) null else entity.copy(offset = start, length = end - start)
    }.sortedWith(compareBy<TextEntity> { it.offset }.thenByDescending { it.length })
    val blocks = mutableListOf<TextEntity>()
    val exclusive = mutableListOf<TextEntity>()
    val emoji = mutableListOf<TextEntity>()
    return fixed
        .filter { entity -> !entity.type.isBlock || blocks.none { it.overlaps(entity) }.also { if (it) blocks += entity } }
        .filter { entity -> entity.type is EntityType.Pre || blocks.none { it.type is EntityType.Pre && it.overlaps(entity) } }
        .filter { entity -> !entity.type.isExclusive || exclusive.none { it.overlaps(entity) }.also { if (it) exclusive += entity } }
        .filter { entity -> entity.type !is EntityType.CustomEmoji || emoji.none { it.overlaps(entity) }.also { if (it) emoji += entity } }
}

fun linkTarget(text: String, entity: TextEntity): String? {
    val part = entity.part(text)
    return when (val type = entity.type) {
        EntityType.Url -> part
        is EntityType.TextUrl -> type.url
        EntityType.Mention -> "https://t.me/" + part.removePrefix("@")
        is EntityType.MentionName -> "tg://user?id=${type.userId}"
        EntityType.Email -> "mailto:$part"
        EntityType.Phone -> "tel:" + part.filter { it.isDigit() || it == '+' }
        else -> null
    }
}

sealed interface TextBlock {
    val text: String

    data class Paragraph(override val text: String, val entities: List<TextEntity>) : TextBlock

    data class Quote(override val text: String, val entities: List<TextEntity>, val collapsed: Boolean) : TextBlock

    data class Pre(override val text: String, val language: String) : TextBlock
}

fun blocksOf(text: String, entities: List<TextEntity>): List<TextBlock> {
    val blocks = entities.filter { it.type.isBlock }
    if (blocks.isEmpty()) return listOf(TextBlock.Paragraph(text, entities))
    val result = mutableListOf<TextBlock>()
    var cursor = 0
    fun paragraph(start: Int, end: Int) {
        var to = end
        if (to > start && text[to - 1] == '\n') to--
        if (to > start) result += TextBlock.Paragraph(text.substring(start, to), slice(entities, start, to))
    }
    for (block in blocks) {
        if (block.offset > cursor) paragraph(cursor, block.offset)
        var end = block.end
        if (end > block.offset + 1 && text[end - 1] == '\n') end--
        val part = text.substring(block.offset, end)
        result += when (val type = block.type) {
            is EntityType.Pre -> TextBlock.Pre(part, type.language)
            is EntityType.Blockquote -> TextBlock.Quote(part, slice(entities, block.offset, end), type.collapsed)
            else -> error("not a block")
        }
        cursor = block.end
        if (cursor < text.length && text[cursor] == '\n') cursor++
    }
    if (cursor < text.length) paragraph(cursor, text.length)
    return result
}

private fun slice(entities: List<TextEntity>, start: Int, end: Int): List<TextEntity> = entities.mapNotNull { entity ->
    if (entity.type.isBlock) return@mapNotNull null
    val from = maxOf(entity.offset, start)
    val to = minOf(entity.end, end)
    if (to <= from) null else TextEntity(from - start, to - from, entity.type)
}

fun FormattedText.edited(newText: String): FormattedText {
    if (newText == text) return this
    val max = minOf(text.length, newText.length)
    var prefix = 0
    while (prefix < max && text[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (suffix < max - prefix && text[text.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
    val oldEnd = text.length - suffix
    val newEnd = newText.length - suffix
    val delta = newEnd - oldEnd
    val moved = entities.mapNotNull { entity ->
        val start = when {
            entity.offset < prefix -> entity.offset
            entity.offset >= oldEnd -> entity.offset + delta
            else -> newEnd
        }
        val end = when {
            entity.end <= prefix -> entity.end
            entity.end > oldEnd -> entity.end + delta
            else -> prefix
        }
        if (end <= start) null else TextEntity(start, end - start, entity.type)
    }
    return FormattedText(newText, normalizeEntities(newText, moved))
}

private fun EntityType.sameKind(other: EntityType): Boolean = this::class == other::class

private fun List<TextEntity>.without(start: Int, end: Int, cut: (TextEntity) -> Boolean): List<TextEntity> = flatMap { entity ->
    if (!cut(entity) || !entity.overlaps(TextEntity(start, end - start, entity.type))) return@flatMap listOf(entity)
    listOfNotNull(
        TextEntity(entity.offset, start - entity.offset, entity.type).takeIf { start > entity.offset },
        TextEntity(end, entity.end - end, entity.type).takeIf { entity.end > end },
    )
}

fun FormattedText.toggled(type: EntityType, start: Int, end: Int): FormattedText {
    if (end <= start) return this
    val same = entities.filter { it.type.sameKind(type) }
    val covered = (start until end).all { at -> same.any { at >= it.offset && at < it.end } }
    val remove = if (type is EntityType.TextUrl) type.url.isBlank() else covered
    if (remove) {
        return copy(entities = normalizeEntities(text, entities.without(start, end) { it.type.sameKind(type) }))
    }
    var from = start
    var to = end
    val others = entities.without(start, end) { it.type.sameKind(type) && it.type != type }.filter { entity ->
        val joins = entity.type == type && entity.offset <= to && entity.end >= from
        if (joins) {
            from = minOf(from, entity.offset)
            to = maxOf(to, entity.end)
        }
        !joins
    }
    return copy(entities = normalizeEntities(text, others + TextEntity(from, to - from, type)))
}

fun FormattedText.cleared(start: Int, end: Int): FormattedText {
    if (end <= start) return this
    val kept = entities.without(start, end) { it.type !is EntityType.MentionName && it.type !is EntityType.CustomEmoji }
    return copy(entities = normalizeEntities(text, kept))
}

fun FormattedText.trimmed(): FormattedText {
    val lead = text.length - text.trimStart().length
    val result = text.trim()
    val moved = entities.mapNotNull { entity ->
        val start = (entity.offset - lead).coerceIn(0, result.length)
        var end = (entity.end - lead).coerceIn(0, result.length)
        while (end > start && result[end - 1].isWhitespace()) end--
        if (end <= start) null else TextEntity(start, end - start, entity.type)
    }
    return FormattedText(result, normalizeEntities(result, moved))
}

fun customEmojiIds(messages: List<MessageItem>): Set<Long> =
    messages.flatMapTo(LinkedHashSet()) { message ->
        val poll = message.poll?.let { poll -> listOf(poll.question) + poll.answers.map { it.text } + listOfNotNull(poll.results.solution) }.orEmpty()
        (message.entities + poll.flatMap { it.entities }).mapNotNull { (it.type as? EntityType.CustomEmoji)?.documentId } +
            message.article?.customEmojiIds().orEmpty() +
            message.reactions.mapNotNull { (it.reaction as? Reaction.CustomEmoji)?.documentId } +
            message.buttons.flatten().mapNotNull { it.icon } +
            listOfNotNull(message.senderEmojiStatus)
    }
