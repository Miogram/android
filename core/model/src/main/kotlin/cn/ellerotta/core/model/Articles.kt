package cn.ellerotta.core.model

import java.net.URLDecoder

data class ArticleMessage(val blocks: List<ArticleBlock>, val rtl: Boolean = false, val part: Boolean = false)

enum class ArticleAlign { Start, Center, End }

enum class ArticleVerticalAlign { Top, Middle, Bottom }

data class ArticleCaption(val text: ArticleText = ArticleText.Empty, val credit: ArticleText = ArticleText.Empty) {
    val isEmpty: Boolean get() = text.isEmpty && credit.isEmpty
}

data class ArticleListItem(
    val blocks: List<ArticleBlock>,
    val checkbox: Boolean = false,
    val checked: Boolean = false,
    val num: String? = null,
    val value: Int? = null,
)

data class ArticleCell(
    val text: ArticleText?,
    val header: Boolean = false,
    val align: ArticleAlign = ArticleAlign.Start,
    val valign: ArticleVerticalAlign = ArticleVerticalAlign.Top,
    val colspan: Int = 1,
    val rowspan: Int = 1,
)

data class ArticleButton(val text: ArticleText, val action: BotButton)

sealed interface ArticleBlock {
    data class Paragraph(val text: ArticleText) : ArticleBlock

    data class Heading(val level: Int, val text: ArticleText) : ArticleBlock

    data class Preformatted(val text: ArticleText, val language: String = "") : ArticleBlock

    data class Footer(val text: ArticleText) : ArticleBlock

    data class Thinking(val text: ArticleText) : ArticleBlock

    data class Items(
        val items: List<ArticleListItem>,
        val ordered: Boolean = false,
        val start: Int? = null,
        val reversed: Boolean = false,
    ) : ArticleBlock {
        fun number(index: Int): String? {
            if (!ordered) return null
            val item = items[index]
            item.num?.let { return it }
            item.value?.let { return "$it" }
            start?.let { return "${if (reversed) it - index else it + index}" }
            return "${if (reversed) items.size - index else index + 1}"
        }
    }

    data class Quote(val text: ArticleText, val author: ArticleText = ArticleText.Empty, val collapsed: Boolean = false) : ArticleBlock

    data class QuoteBlocks(val blocks: List<ArticleBlock>, val author: ArticleText = ArticleText.Empty) : ArticleBlock

    data class Pullquote(val text: ArticleText, val author: ArticleText = ArticleText.Empty) : ArticleBlock

    data class Details(val title: ArticleText, val blocks: List<ArticleBlock>, val open: Boolean = false) : ArticleBlock

    data class Table(
        val title: ArticleText,
        val rows: List<List<ArticleCell>>,
        val bordered: Boolean = false,
        val striped: Boolean = false,
        val compact: Boolean = false,
    ) : ArticleBlock

    data class Math(val source: String) : ArticleBlock

    data object Divider : ArticleBlock

    data class Photo(val media: MediaItem?, val caption: ArticleCaption = ArticleCaption(), val spoiler: Boolean = false, val url: String? = null) : ArticleBlock

    data class Video(
        val media: MediaItem?,
        val caption: ArticleCaption = ArticleCaption(),
        val spoiler: Boolean = false,
        val autoplay: Boolean = false,
        val loop: Boolean = false,
    ) : ArticleBlock

    data class Collage(val items: List<ArticleBlock>, val caption: ArticleCaption = ArticleCaption()) : ArticleBlock

    data class Slideshow(val items: List<ArticleBlock>, val caption: ArticleCaption = ArticleCaption()) : ArticleBlock

    data class Audio(val media: MediaItem?, val caption: ArticleCaption = ArticleCaption()) : ArticleBlock

    data class Document(val media: MediaItem?, val caption: ArticleCaption = ArticleCaption()) : ArticleBlock

    data class Map(
        val latitude: Double,
        val longitude: Double,
        val zoom: Int,
        val width: Int,
        val height: Int,
        val caption: ArticleCaption = ArticleCaption(),
    ) : ArticleBlock

    data class Buttons(val buttons: List<ArticleButton>, val align: ArticleAlign? = null) : ArticleBlock

    data class Anchor(val name: String) : ArticleBlock

    data object Unsupported : ArticleBlock
}

enum class ArticleStyle { Bold, Italic, Underline, Strike, Fixed, Subscript, Superscript, Marked, Spoiler }

sealed interface ArticleText {
    data object Empty : ArticleText

    data class Plain(val text: String) : ArticleText

    data class Concat(val parts: List<ArticleText>) : ArticleText

    data class Styled(val style: ArticleStyle, val text: ArticleText) : ArticleText

    data class Url(val text: ArticleText, val url: String, val auto: Boolean = false) : ArticleText

    data class Email(val text: ArticleText, val email: String, val auto: Boolean = false) : ArticleText

    data class Phone(val text: ArticleText, val phone: String, val auto: Boolean = false) : ArticleText

    data class Mention(val text: ArticleText) : ArticleText

    data class MentionName(val text: ArticleText, val userId: Long) : ArticleText

    data class Hashtag(val text: ArticleText) : ArticleText

    data class Cashtag(val text: ArticleText) : ArticleText

    data class BotCommand(val text: ArticleText) : ArticleText

    data class BankCard(val text: ArticleText) : ArticleText

    data class CustomEmoji(val documentId: Long, val alt: String) : ArticleText

    data class Math(val source: String) : ArticleText

    data class Date(val text: ArticleText, val date: EntityType.FormattedDate) : ArticleText

    data class Diff(val text: ArticleText, val old: ArticleText) : ArticleText

    data class Button(val text: ArticleText, val action: BotButton) : ArticleText

    data class Anchor(val text: ArticleText, val name: String) : ArticleText

    data class Image(val documentId: Long, val width: Int, val height: Int, val media: MediaItem? = null) : ArticleText

    val isEmpty: Boolean get() = flatten().text.isEmpty()
}

sealed interface ArticleMarkType {
    data object Subscript : ArticleMarkType
    data object Superscript : ArticleMarkType
    data object Marked : ArticleMarkType

    data object Removed : ArticleMarkType
    data object Added : ArticleMarkType
    data object Changed : ArticleMarkType

    data class Math(val source: String) : ArticleMarkType

    data class Button(val action: BotButton) : ArticleMarkType

    data class Anchor(val name: String) : ArticleMarkType

    data class Image(val media: MediaItem, val width: Int, val height: Int) : ArticleMarkType
}

data class ArticleMark(val offset: Int, val length: Int, val type: ArticleMarkType) {
    val end: Int get() = offset + length
}

data class ArticleRun(val text: String, val entities: List<TextEntity>, val marks: List<ArticleMark>)

fun ArticleText.flatten(): ArticleRun {
    val out = StringBuilder()
    val entities = mutableListOf<TextEntity>()
    val marks = mutableListOf<ArticleMark>()
    fun mark(start: Int, type: ArticleMarkType) {
        marks += ArticleMark(start, out.length - start, type)
    }
    fun entity(start: Int, type: EntityType) {
        if (out.length > start) entities += TextEntity(start, out.length - start, type)
    }
    fun walk(text: ArticleText) {
        val start = out.length
        when (text) {
            ArticleText.Empty -> Unit
            is ArticleText.Plain -> out.append(text.text)
            is ArticleText.Concat -> text.parts.forEach(::walk)
            is ArticleText.Styled -> {
                walk(text.text)
                when (text.style) {
                    ArticleStyle.Bold -> entity(start, EntityType.Bold)
                    ArticleStyle.Italic -> entity(start, EntityType.Italic)
                    ArticleStyle.Underline -> entity(start, EntityType.Underline)
                    ArticleStyle.Strike -> entity(start, EntityType.Strike)
                    ArticleStyle.Fixed -> entity(start, EntityType.Code)
                    ArticleStyle.Spoiler -> entity(start, EntityType.Spoiler)
                    ArticleStyle.Subscript -> mark(start, ArticleMarkType.Subscript)
                    ArticleStyle.Superscript -> mark(start, ArticleMarkType.Superscript)
                    ArticleStyle.Marked -> mark(start, ArticleMarkType.Marked)
                }
            }
            is ArticleText.Url -> {
                walk(text.text)
                entity(start, EntityType.TextUrl(text.url))
            }
            is ArticleText.Email -> {
                walk(text.text)
                entity(start, EntityType.TextUrl("mailto:${text.email}"))
            }
            is ArticleText.Phone -> {
                walk(text.text)
                entity(start, EntityType.TextUrl("tel:" + text.phone.filter { it.isDigit() || it == '+' }))
            }
            is ArticleText.Mention -> {
                walk(text.text)
                entity(start, EntityType.Mention)
            }
            is ArticleText.MentionName -> {
                walk(text.text)
                entity(start, EntityType.MentionName(text.userId))
            }
            is ArticleText.Hashtag -> {
                walk(text.text)
                entity(start, EntityType.Hashtag)
            }
            is ArticleText.Cashtag -> {
                walk(text.text)
                entity(start, EntityType.Cashtag)
            }
            is ArticleText.BotCommand -> {
                walk(text.text)
                entity(start, EntityType.BotCommand)
            }
            is ArticleText.BankCard -> {
                walk(text.text)
                entity(start, EntityType.BankCard)
            }
            is ArticleText.CustomEmoji -> {
                out.append(text.alt.ifEmpty { DEFAULT_EMOJI })
                entity(start, EntityType.CustomEmoji(text.documentId))
            }
            is ArticleText.Math -> {
                out.append(text.source)
                mark(start, ArticleMarkType.Math(text.source))
            }
            is ArticleText.Date -> {
                walk(text.text)
                entity(start, text.date)
            }
            is ArticleText.Diff -> {
                walk(text.text)
                if (out.length > start) {
                    mark(start, if (text.old.isEmpty) ArticleMarkType.Added else ArticleMarkType.Changed)
                } else {
                    walk(text.old)
                    if (out.length > start) mark(start, ArticleMarkType.Removed)
                }
            }
            is ArticleText.Button -> {
                walk(text.text)
                mark(start, ArticleMarkType.Button(text.action))
            }
            is ArticleText.Anchor -> {
                walk(text.text)
                mark(start, ArticleMarkType.Anchor(text.name))
            }
            is ArticleText.Image -> text.media?.let { media ->
                out.append(OBJECT_CHARACTER)
                mark(start, ArticleMarkType.Image(media, text.width, text.height))
            }
        }
    }
    walk(this)
    val text = out.toString()
    return ArticleRun(text, normalizeEntities(text, entities), marks)
}

sealed interface ArticleAnchor {
    data class Footnote(val text: ArticleText) : ArticleAnchor

    data class Block(val index: Int) : ArticleAnchor
}

fun ArticleMessage.anchors(): Map<String, ArticleAnchor> {
    val found = LinkedHashMap<String, ArticleAnchor>()
    blocks.forEachIndexed { index, top ->
        top.walk(
            onBlock = { block -> if (block is ArticleBlock.Anchor) found.putIfAbsent(anchorName(block.name), ArticleAnchor.Block(index)) },
            onText = { text ->
                text.walkText { piece ->
                    if (piece is ArticleText.Anchor) {
                        val anchor = if (piece.text.isEmpty) ArticleAnchor.Block(index) else ArticleAnchor.Footnote(piece.text)
                        found.putIfAbsent(anchorName(piece.name), anchor)
                    }
                }
            },
        )
    }
    return found
}

fun anchorName(link: String): String =
    runCatching { URLDecoder.decode(link.removePrefix("#"), Charsets.UTF_8) }.getOrDefault(link.removePrefix("#")).lowercase()

fun ArticleMessage.visualMedia(): List<MediaItem> = buildList {
    blocks.forEach { top ->
        top.walk(
            onBlock = { block ->
                when (block) {
                    is ArticleBlock.Photo -> block.media?.let(::add)
                    is ArticleBlock.Video -> block.media?.let(::add)
                    else -> Unit
                }
            },
        )
    }
}

fun ArticleMessage.audioMedia(): List<MediaItem> = buildList {
    blocks.forEach { top -> top.walk(onBlock = { block -> if (block is ArticleBlock.Audio) block.media?.let(::add) }) }
}

fun ArticleMessage.customEmojiIds(): Set<Long> {
    val ids = LinkedHashSet<Long>()
    blocks.forEach { top -> top.walk(onText = { text -> text.walkText { if (it is ArticleText.CustomEmoji) ids += it.documentId } }) }
    return ids
}

fun ArticleBlock.walk(onBlock: (ArticleBlock) -> Unit = {}, onText: (ArticleText) -> Unit = {}) {
    onBlock(this)
    fun caption(caption: ArticleCaption) {
        onText(caption.text)
        onText(caption.credit)
    }
    when (this) {
        is ArticleBlock.Paragraph -> onText(text)
        is ArticleBlock.Heading -> onText(text)
        is ArticleBlock.Preformatted -> onText(text)
        is ArticleBlock.Footer -> onText(text)
        is ArticleBlock.Thinking -> onText(text)
        is ArticleBlock.Items -> items.forEach { item -> item.blocks.forEach { it.walk(onBlock, onText) } }
        is ArticleBlock.Quote -> {
            onText(text)
            onText(author)
        }
        is ArticleBlock.QuoteBlocks -> {
            blocks.forEach { it.walk(onBlock, onText) }
            onText(author)
        }
        is ArticleBlock.Pullquote -> {
            onText(text)
            onText(author)
        }
        is ArticleBlock.Details -> {
            onText(title)
            blocks.forEach { it.walk(onBlock, onText) }
        }
        is ArticleBlock.Table -> {
            onText(title)
            rows.forEach { row -> row.forEach { cell -> cell.text?.let(onText) } }
        }
        is ArticleBlock.Photo -> caption(caption)
        is ArticleBlock.Video -> caption(caption)
        is ArticleBlock.Collage -> {
            items.forEach { it.walk(onBlock, onText) }
            caption(caption)
        }
        is ArticleBlock.Slideshow -> {
            items.forEach { it.walk(onBlock, onText) }
            caption(caption)
        }
        is ArticleBlock.Audio -> caption(caption)
        is ArticleBlock.Document -> caption(caption)
        is ArticleBlock.Map -> caption(caption)
        is ArticleBlock.Buttons -> buttons.forEach { onText(it.text) }
        is ArticleBlock.Math, ArticleBlock.Divider, is ArticleBlock.Anchor, ArticleBlock.Unsupported -> Unit
    }
}

fun ArticleText.walkText(visit: (ArticleText) -> Unit) {
    visit(this)
    when (this) {
        is ArticleText.Concat -> parts.forEach { it.walkText(visit) }
        is ArticleText.Styled -> text.walkText(visit)
        is ArticleText.Url -> text.walkText(visit)
        is ArticleText.Email -> text.walkText(visit)
        is ArticleText.Phone -> text.walkText(visit)
        is ArticleText.Mention -> text.walkText(visit)
        is ArticleText.MentionName -> text.walkText(visit)
        is ArticleText.Hashtag -> text.walkText(visit)
        is ArticleText.Cashtag -> text.walkText(visit)
        is ArticleText.BotCommand -> text.walkText(visit)
        is ArticleText.BankCard -> text.walkText(visit)
        is ArticleText.Date -> text.walkText(visit)
        is ArticleText.Diff -> {
            text.walkText(visit)
            old.walkText(visit)
        }
        is ArticleText.Button -> text.walkText(visit)
        is ArticleText.Anchor -> text.walkText(visit)
        ArticleText.Empty, is ArticleText.Plain, is ArticleText.CustomEmoji, is ArticleText.Math, is ArticleText.Image -> Unit
    }
}

enum class ArticleLabel { Photo, Video, Collage, Slideshow, Formula, Table, Map, Audio, File, Unsupported }

const val ARTICLE_PREVIEW_MAX = 1024
const val ARTICLE_DRAFT_PREVIEW_MAX = 150

fun ArticleMessage.preview(label: (ArticleLabel) -> String, max: Int = ARTICLE_PREVIEW_MAX): String {
    val parts = mutableListOf<String>()
    var length = 0
    fun add(part: String) {
        if (part.isBlank() || length > max) return
        parts += part
        length += part.length + 2
    }
    fun inline(text: ArticleText): String = buildString { previewText(text, label, this) }
    fun block(block: ArticleBlock) {
        when (block) {
            is ArticleBlock.Paragraph -> add(inline(block.text))
            is ArticleBlock.Heading -> add(inline(block.text))
            is ArticleBlock.Preformatted -> add(inline(block.text))
            is ArticleBlock.Footer -> add(inline(block.text))
            is ArticleBlock.Thinking -> add(inline(block.text))
            is ArticleBlock.Quote -> add(inline(block.text))
            is ArticleBlock.Pullquote -> add(inline(block.text))
            is ArticleBlock.QuoteBlocks -> block.blocks.forEach(::block)
            is ArticleBlock.Details -> add(inline(block.title))
            is ArticleBlock.Items -> block.items.forEachIndexed { index, item ->
                val marker = when {
                    item.checkbox -> if (item.checked) "\u2705 " else "\u2B1C "
                    block.ordered -> "${block.number(index)}. "
                    else -> "\u2022 "
                }
                val first = parts.size
                item.blocks.forEach(::block)
                if (parts.size > first) parts[first] = marker + parts[first]
            }
            is ArticleBlock.Table -> add(inline(block.title).ifBlank { label(ArticleLabel.Table) })
            is ArticleBlock.Math -> add(label(ArticleLabel.Formula))
            is ArticleBlock.Photo -> add(label(ArticleLabel.Photo))
            is ArticleBlock.Video -> add(label(ArticleLabel.Video))
            is ArticleBlock.Collage -> add(label(ArticleLabel.Collage))
            is ArticleBlock.Slideshow -> add(label(ArticleLabel.Slideshow))
            is ArticleBlock.Audio -> add("\uD83C\uDFB5 " + (block.media?.fileName ?: label(ArticleLabel.Audio)))
            is ArticleBlock.Document -> add("\uD83D\uDCCE " + (block.media?.fileName ?: label(ArticleLabel.File)))
            is ArticleBlock.Map -> add(listOf(label(ArticleLabel.Map), inline(block.caption.text)).filter { it.isNotBlank() }.joinToString(" "))
            is ArticleBlock.Buttons -> add(block.buttons.map { inline(it.text) }.filter { it.isNotBlank() }.joinToString(" "))
            ArticleBlock.Unsupported -> add(label(ArticleLabel.Unsupported))
            ArticleBlock.Divider, is ArticleBlock.Anchor -> Unit
        }
    }
    blocks.forEach(::block)
    val joined = parts.joinToString("  ")
    if (joined.length <= max) return joined
    var cut = max - 1
    if (cut > 0 && joined[cut - 1].isHighSurrogate()) cut--
    return joined.substring(0, cut).trimEnd() + "\u2026"
}

val ArticleMessage.mediaLike: Boolean
    get() = when (blocks.singleOrNull()) {
        is ArticleBlock.Table, is ArticleBlock.Math, is ArticleBlock.Audio, is ArticleBlock.Document, is ArticleBlock.Map,
        is ArticleBlock.Photo, is ArticleBlock.Video, is ArticleBlock.Collage, is ArticleBlock.Slideshow,
        -> true
        else -> false
    }

fun ArticleMessage.plainText(): String = blocks.flatMap(::plainLines).joinToString("\n")

private fun plainLines(block: ArticleBlock): List<String> {
    val lines = mutableListOf<String>()
    fun text(text: ArticleText) {
        val plain = text.flatten().text.replace(OBJECT_CHARACTER, "")
        if (plain.isNotBlank()) lines += plain
    }
    fun caption(caption: ArticleCaption) {
        text(caption.text)
        text(caption.credit)
    }
    when (block) {
        is ArticleBlock.Paragraph -> text(block.text)
        is ArticleBlock.Heading -> text(block.text)
        is ArticleBlock.Preformatted -> text(block.text)
        is ArticleBlock.Footer -> text(block.text)
        is ArticleBlock.Thinking -> text(block.text)
        is ArticleBlock.Items -> block.items.forEachIndexed { index, item ->
            val marker = when {
                item.checkbox -> if (item.checked) "\u2705 " else "\u2B1C "
                block.ordered -> "${block.number(index)}. "
                else -> "\u2022 "
            }
            val inner = item.blocks.flatMap(::plainLines).ifEmpty { listOf("") }
            lines += marker + inner.first()
            inner.drop(1).forEach { lines += " ".repeat(marker.length) + it }
        }
        is ArticleBlock.Quote -> {
            text(block.text)
            text(block.author)
        }
        is ArticleBlock.QuoteBlocks -> {
            block.blocks.forEach { lines += plainLines(it) }
            text(block.author)
        }
        is ArticleBlock.Pullquote -> {
            text(block.text)
            text(block.author)
        }
        is ArticleBlock.Details -> {
            text(block.title)
            block.blocks.forEach { lines += plainLines(it) }
        }
        is ArticleBlock.Table -> {
            text(block.title)
            block.rows.forEach { row ->
                val cells = row.map { cell -> cell.text?.flatten()?.text?.replace(OBJECT_CHARACTER, "").orEmpty() }
                if (cells.any { it.isNotBlank() }) lines += cells.joinToString("\t")
            }
        }
        is ArticleBlock.Math -> if (block.source.isNotBlank()) lines += block.source
        is ArticleBlock.Photo -> caption(block.caption)
        is ArticleBlock.Video -> caption(block.caption)
        is ArticleBlock.Collage -> {
            block.items.forEach { lines += plainLines(it) }
            caption(block.caption)
        }
        is ArticleBlock.Slideshow -> {
            block.items.forEach { lines += plainLines(it) }
            caption(block.caption)
        }
        is ArticleBlock.Audio -> caption(block.caption)
        is ArticleBlock.Document -> caption(block.caption)
        is ArticleBlock.Map -> caption(block.caption)
        is ArticleBlock.Buttons, ArticleBlock.Divider, is ArticleBlock.Anchor, ArticleBlock.Unsupported -> Unit
    }
    return lines
}

private fun previewText(text: ArticleText, label: (ArticleLabel) -> String, out: StringBuilder) {
    when (text) {
        ArticleText.Empty, is ArticleText.Image -> Unit
        is ArticleText.Plain -> out.append(text.text)
        is ArticleText.Concat -> text.parts.forEach { previewText(it, label, out) }
        is ArticleText.Styled -> previewText(text.text, label, out)
        is ArticleText.Url -> previewText(text.text, label, out)
        is ArticleText.Email -> previewText(text.text, label, out)
        is ArticleText.Phone -> previewText(text.text, label, out)
        is ArticleText.Mention -> previewText(text.text, label, out)
        is ArticleText.MentionName -> previewText(text.text, label, out)
        is ArticleText.Hashtag -> previewText(text.text, label, out)
        is ArticleText.Cashtag -> previewText(text.text, label, out)
        is ArticleText.BotCommand -> previewText(text.text, label, out)
        is ArticleText.BankCard -> previewText(text.text, label, out)
        is ArticleText.CustomEmoji -> out.append(text.alt.ifEmpty { DEFAULT_EMOJI })
        is ArticleText.Math -> out.append(label(ArticleLabel.Formula))
        is ArticleText.Date -> previewText(text.text, label, out)
        is ArticleText.Diff -> previewText(if (text.text.isEmpty) text.old else text.text, label, out)
        is ArticleText.Button -> previewText(text.text, label, out)
        is ArticleText.Anchor -> previewText(text.text, label, out)
    }
}

private const val DEFAULT_EMOJI = "\uD83D\uDE00"
private const val OBJECT_CHARACTER = "\uFFFC"
