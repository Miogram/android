package cn.ellerotta.core.model

enum class ArticlePosting { Enabled, Premium, Disabled }

data class ArticleLimits(
    val length: Int = 32_768,
    val blocks: Int = 500,
    val depth: Int = 16,
    val media: Int = 50,
    val tableColumns: Int = 20,
)

enum class ArticleLimit { Length, Blocks, Depth, Media, TableColumns }

data class ArticleCounts(val length: Int = 0, val blocks: Int = 0, val depth: Int = 0, val media: Int = 0, val tableColumns: Int = 0) {
    fun over(limits: ArticleLimits): Set<ArticleLimit> = buildSet {
        if (length > limits.length) add(ArticleLimit.Length)
        if (blocks > limits.blocks) add(ArticleLimit.Blocks)
        if (depth > limits.depth) add(ArticleLimit.Depth)
        if (media > limits.media) add(ArticleLimit.Media)
        if (tableColumns > limits.tableColumns) add(ArticleLimit.TableColumns)
    }
}

data class ArticleGate(val posting: ArticlePosting = ArticlePosting.Premium, val premium: Boolean = false, val limits: ArticleLimits = ArticleLimits()) {
    val available: Boolean get() = posting != ArticlePosting.Disabled

    val allowed: Boolean get() = posting == ArticlePosting.Enabled || (posting == ArticlePosting.Premium && premium)
}

fun ArticleMessage.counts(): ArticleCounts {
    var length = 0
    var count = 0
    var depth = 0
    var media = 0
    var columns = 0
    fun text(text: ArticleText, level: Int) {
        fun walk(piece: ArticleText, wrappers: Int) {
            depth = maxOf(depth, level + wrappers)
            when (piece) {
                ArticleText.Empty -> Unit
                is ArticleText.Plain -> length += piece.text.length
                is ArticleText.Concat -> piece.parts.forEach { walk(it, wrappers) }
                is ArticleText.CustomEmoji -> length += piece.alt.length
                is ArticleText.Math -> {
                    length += piece.source.length
                    depth = maxOf(depth, level + wrappers + 1)
                }
                is ArticleText.Image -> {
                    media++
                    depth = maxOf(depth, level + wrappers + 1)
                }
                is ArticleText.Diff -> {
                    walk(piece.text, wrappers + 1)
                    walk(piece.old, wrappers + 1)
                }
                is ArticleText.Styled -> walk(piece.text, wrappers + 1)
                is ArticleText.Url -> walk(piece.text, wrappers + 1)
                is ArticleText.Email -> walk(piece.text, wrappers + 1)
                is ArticleText.Phone -> walk(piece.text, wrappers + 1)
                is ArticleText.Mention -> walk(piece.text, wrappers + 1)
                is ArticleText.MentionName -> walk(piece.text, wrappers + 1)
                is ArticleText.Hashtag -> walk(piece.text, wrappers + 1)
                is ArticleText.Cashtag -> walk(piece.text, wrappers + 1)
                is ArticleText.BotCommand -> walk(piece.text, wrappers + 1)
                is ArticleText.BankCard -> walk(piece.text, wrappers + 1)
                is ArticleText.Date -> walk(piece.text, wrappers + 1)
                is ArticleText.Button -> walk(piece.text, wrappers + 1)
                is ArticleText.Anchor -> walk(piece.text, wrappers + 1)
            }
        }
        walk(text, 0)
    }
    fun caption(caption: ArticleCaption, level: Int) {
        text(caption.text, level)
        text(caption.credit, level)
    }
    fun block(block: ArticleBlock, level: Int) {
        count++
        depth = maxOf(depth, level)
        when (block) {
            is ArticleBlock.Paragraph -> text(block.text, level)
            is ArticleBlock.Heading -> text(block.text, level)
            is ArticleBlock.Preformatted -> text(block.text, level)
            is ArticleBlock.Footer -> text(block.text, level)
            is ArticleBlock.Thinking -> text(block.text, level)
            is ArticleBlock.Items -> block.items.forEach { item ->
                count++
                val single = item.textItem
                if (single != null) text(single, level + 1) else item.blocks.forEach { block(it, level + 1) }
            }
            is ArticleBlock.Quote -> {
                text(block.text, level)
                text(block.author, level)
            }
            is ArticleBlock.QuoteBlocks -> {
                block.blocks.forEach { block(it, level + 1) }
                text(block.author, level)
            }
            is ArticleBlock.Pullquote -> {
                text(block.text, level)
                text(block.author, level)
            }
            is ArticleBlock.Details -> {
                text(block.title, level)
                block.blocks.forEach { block(it, level + 1) }
            }
            is ArticleBlock.Table -> {
                text(block.title, level)
                count += block.rows.size
                block.rows.forEach { row ->
                    columns = maxOf(columns, row.sumOf { it.colspan.coerceAtLeast(1) })
                    row.forEach { cell -> cell.text?.let { text(it, level + 1) } }
                }
            }
            is ArticleBlock.Math -> length += block.source.length
            is ArticleBlock.Photo -> {
                media++
                caption(block.caption, level)
            }
            is ArticleBlock.Video -> {
                media++
                caption(block.caption, level)
            }
            is ArticleBlock.Audio -> {
                media++
                caption(block.caption, level)
            }
            is ArticleBlock.Document -> {
                media++
                caption(block.caption, level)
            }
            is ArticleBlock.Collage -> {
                block.items.forEach { block(it, level + 1) }
                caption(block.caption, level)
            }
            is ArticleBlock.Slideshow -> {
                block.items.forEach { block(it, level + 1) }
                caption(block.caption, level)
            }
            is ArticleBlock.Map -> caption(block.caption, level)
            is ArticleBlock.Buttons -> block.buttons.forEach { text(it.text, level + 1) }
            ArticleBlock.Divider, is ArticleBlock.Anchor, ArticleBlock.Unsupported -> Unit
        }
    }
    blocks.forEach { block(it, 1) }
    return ArticleCounts(length, count, depth, media, columns)
}

val ArticleListItem.textItem: ArticleText?
    get() = (blocks.singleOrNull() as? ArticleBlock.Paragraph)?.text

val ArticleText.entitiesOnly: Boolean
    get() {
        var only = true
        walkText { piece ->
            when (piece) {
                is ArticleText.Styled -> if (piece.style == ArticleStyle.Subscript || piece.style == ArticleStyle.Superscript || piece.style == ArticleStyle.Marked) only = false
                is ArticleText.Math, is ArticleText.Diff, is ArticleText.Button, is ArticleText.Anchor, is ArticleText.Image -> only = false
                else -> Unit
            }
        }
        return only
    }

fun ArticleMessage.asFormattedText(): FormattedText? {
    if (blocks.any { it !is ArticleBlock.Paragraph || !it.text.entitiesOnly }) return null
    val out = StringBuilder()
    val entities = mutableListOf<TextEntity>()
    blocks.forEachIndexed { index, block ->
        if (index > 0) out.append(PARAGRAPH_BREAK)
        val run = (block as ArticleBlock.Paragraph).text.flatten()
        val start = out.length
        entities += run.entities.map { it.copy(offset = it.offset + start) }
        out.append(run.text)
    }
    val text = out.toString()
    return FormattedText(text, normalizeEntities(text, entities))
}

fun FormattedText.toArticleText(): ArticleText {
    val order = compareBy<TextEntity>({ it.offset }, { -it.length })
    fun build(start: Int, end: Int, inside: List<TextEntity>): ArticleText {
        val parts = mutableListOf<ArticleText>()
        var at = start
        var rest = inside
        while (rest.isNotEmpty()) {
            val first = rest.first()
            if (first.offset > at) parts += ArticleText.Plain(text.substring(at, first.offset))
            val children = mutableListOf<TextEntity>()
            val after = mutableListOf<TextEntity>()
            rest.drop(1).forEach { entity ->
                when {
                    entity.offset >= first.end -> after += entity
                    entity.end <= first.end -> children += entity
                    else -> {
                        children += entity.copy(length = first.end - entity.offset)
                        after += entity.copy(offset = first.end, length = entity.end - first.end)
                    }
                }
            }
            parts += wrapped(first, build(first.offset, first.end, children.sortedWith(order)))
            at = first.end
            rest = after.sortedWith(order)
        }
        if (end > at) parts += ArticleText.Plain(text.substring(at, end))
        return when (parts.size) {
            0 -> ArticleText.Empty
            1 -> parts.single()
            else -> ArticleText.Concat(parts)
        }
    }
    val kept = normalizeEntities(text, entities).filter { it.type.richKind }
    return build(0, text.length, kept.sortedWith(order))
}

private val EntityType.richKind: Boolean
    get() = when (this) {
        EntityType.Bold, EntityType.Italic, EntityType.Underline, EntityType.Strike, EntityType.Spoiler, EntityType.Code,
        is EntityType.TextUrl, is EntityType.MentionName, is EntityType.CustomEmoji, is EntityType.FormattedDate,
        -> true
        else -> false
    }

private fun FormattedText.wrapped(entity: TextEntity, inner: ArticleText): ArticleText = when (val type = entity.type) {
    EntityType.Bold -> ArticleText.Styled(ArticleStyle.Bold, inner)
    EntityType.Italic -> ArticleText.Styled(ArticleStyle.Italic, inner)
    EntityType.Underline -> ArticleText.Styled(ArticleStyle.Underline, inner)
    EntityType.Strike -> ArticleText.Styled(ArticleStyle.Strike, inner)
    EntityType.Spoiler -> ArticleText.Styled(ArticleStyle.Spoiler, inner)
    EntityType.Code -> ArticleText.Styled(ArticleStyle.Fixed, inner)
    is EntityType.TextUrl -> ArticleText.Url(inner, type.url)
    is EntityType.MentionName -> ArticleText.MentionName(inner, type.userId)
    is EntityType.CustomEmoji -> ArticleText.CustomEmoji(type.documentId, entity.part(text))
    is EntityType.FormattedDate -> ArticleText.Date(inner, type)
    else -> inner
}

enum class ArticleRowKind { Paragraph, Heading1, Heading2, Heading3, Bullet, Numbered, Check, Quote, Code, Divider, Math, Table, Media, Block }

enum class ArticleMediaKind { Photo, Video, Audio, File }

data class ArticleUpload(
    val id: Long,
    val kind: ArticleMediaKind,
    val name: String,
    val uploaded: Long = 0,
    val total: Long = 0,
    val media: MediaItem? = null,
    val failed: Boolean = false,
) {
    val done: Boolean get() = media != null
}

data class ArticleRow(
    val kind: ArticleRowKind,
    val text: FormattedText = FormattedText(""),
    val checked: Boolean = false,
    val language: String = "",
    val block: ArticleBlock? = null,
    val id: Int = 0,
    val cells: List<List<FormattedText>> = emptyList(),
    val header: Boolean = false,
    val upload: ArticleUpload? = null,
)

fun newTableRow(id: Int, first: FormattedText = FormattedText("")): ArticleRow = ArticleRow(
    ArticleRowKind.Table,
    id = id,
    cells = listOf(listOf(first, FormattedText("")), listOf(FormattedText(""), FormattedText(""))),
    header = true,
    block = ArticleBlock.Table(ArticleText.Empty, emptyList(), bordered = true),
)

fun tableOf(cells: List<List<FormattedText>>, header: Boolean, base: ArticleBlock.Table? = null): ArticleBlock.Table {
    val rows = cells.mapIndexed { index, row ->
        row.map { text -> ArticleCell(text.trimmed().takeIf { it.text.isNotEmpty() }?.toArticleText(), header = header && index == 0) }
    }
    return base?.copy(rows = rows) ?: ArticleBlock.Table(ArticleText.Empty, rows, bordered = true)
}

fun mediaBlockOf(upload: ArticleUpload, caption: ArticleCaption = ArticleCaption()): ArticleBlock = when (upload.kind) {
    ArticleMediaKind.Photo -> ArticleBlock.Photo(upload.media, caption)
    ArticleMediaKind.Video -> ArticleBlock.Video(upload.media, caption)
    ArticleMediaKind.Audio -> ArticleBlock.Audio(upload.media, caption)
    ArticleMediaKind.File -> ArticleBlock.Document(upload.media, caption)
}

private fun captioned(block: ArticleBlock, text: ArticleText): ArticleBlock = when (block) {
    is ArticleBlock.Photo -> block.copy(caption = block.caption.copy(text = text))
    is ArticleBlock.Video -> block.copy(caption = block.caption.copy(text = text))
    is ArticleBlock.Audio -> block.copy(caption = block.caption.copy(text = text))
    is ArticleBlock.Document -> block.copy(caption = block.caption.copy(text = text))
    else -> block
}

fun ArticleRow.currentUpload(uploads: Map<Long, ArticleUpload>): ArticleUpload? = upload?.let { uploads[it.id] ?: it }

fun uploadsPending(rows: List<ArticleRow>, uploads: Map<Long, ArticleUpload>): Boolean =
    rows.any { row -> row.kind == ArticleRowKind.Media && row.currentUpload(uploads)?.done == false }

private val LIST_ROWS = setOf(ArticleRowKind.Bullet, ArticleRowKind.Numbered, ArticleRowKind.Check)

fun articleOfRows(rows: List<ArticleRow>, uploads: Map<Long, ArticleUpload> = emptyMap()): ArticleMessage {
    val blocks = mutableListOf<ArticleBlock>()
    var listKind: ArticleRowKind? = null
    val items = mutableListOf<ArticleListItem>()
    fun flush() {
        if (items.isNotEmpty()) blocks += ArticleBlock.Items(items.toList(), ordered = listKind == ArticleRowKind.Numbered)
        items.clear()
        listKind = null
    }
    rows.forEach { row ->
        if (row.kind != listKind) flush()
        val text = row.text.trimmed()
        val blank = text.text.isEmpty()
        when (row.kind) {
            ArticleRowKind.Paragraph -> if (!blank) blocks += ArticleBlock.Paragraph(text.toArticleText())
            ArticleRowKind.Heading1 -> if (!blank) blocks += ArticleBlock.Heading(1, text.toArticleText())
            ArticleRowKind.Heading2 -> if (!blank) blocks += ArticleBlock.Heading(2, text.toArticleText())
            ArticleRowKind.Heading3 -> if (!blank) blocks += ArticleBlock.Heading(3, text.toArticleText())
            ArticleRowKind.Bullet, ArticleRowKind.Numbered, ArticleRowKind.Check -> {
                listKind = row.kind
                if (!blank) items += ArticleListItem(listOf(ArticleBlock.Paragraph(text.toArticleText())), checkbox = row.kind == ArticleRowKind.Check, checked = row.kind == ArticleRowKind.Check && row.checked)
            }
            ArticleRowKind.Quote -> if (!blank) blocks += ArticleBlock.Quote(text.toArticleText())
            ArticleRowKind.Code -> row.text.text.trim('\n').takeIf { it.isNotBlank() }?.let { blocks += ArticleBlock.Preformatted(ArticleText.Plain(it), row.language.trim()) }
            ArticleRowKind.Math -> if (!blank) blocks += ArticleBlock.Math(text.text)
            ArticleRowKind.Divider -> blocks += ArticleBlock.Divider
            ArticleRowKind.Table -> if (row.cells.any { line -> line.any { it.text.isNotBlank() } }) blocks += tableOf(row.cells, row.header, row.block as? ArticleBlock.Table)
            ArticleRowKind.Media -> {
                val caption = if (blank) ArticleText.Empty else text.toArticleText()
                val upload = row.currentUpload(uploads)
                when {
                    upload != null -> blocks += mediaBlockOf(upload, ArticleCaption(caption))
                    row.block != null -> blocks += captioned(row.block, caption)
                }
            }
            ArticleRowKind.Block -> row.block?.let { blocks += it }
        }
    }
    flush()
    return ArticleMessage(blocks)
}

fun ArticleMessage.toRows(): List<ArticleRow> = blocks.flatMap(::rowsOf).mapIndexed { index, row -> row.copy(id = index) }

private fun rowsOf(block: ArticleBlock): List<ArticleRow> {
    val kept = listOf(ArticleRow(ArticleRowKind.Block, block = block))
    fun text(kind: ArticleRowKind, text: ArticleText) = if (text.entitiesOnly) listOf(ArticleRow(kind, text.formatted())) else kept
    return when (block) {
        is ArticleBlock.Paragraph -> text(ArticleRowKind.Paragraph, block.text)
        is ArticleBlock.Heading -> when (block.level) {
            1 -> text(ArticleRowKind.Heading1, block.text)
            2 -> text(ArticleRowKind.Heading2, block.text)
            3 -> text(ArticleRowKind.Heading3, block.text)
            else -> kept
        }
        is ArticleBlock.Quote -> if (block.author.isEmpty && !block.collapsed) text(ArticleRowKind.Quote, block.text) else kept
        is ArticleBlock.Preformatted -> {
            val run = block.text.flatten()
            if (run.entities.isEmpty() && run.marks.isEmpty()) listOf(ArticleRow(ArticleRowKind.Code, FormattedText(run.text), language = block.language)) else kept
        }
        is ArticleBlock.Math -> listOf(ArticleRow(ArticleRowKind.Math, FormattedText(block.source)))
        is ArticleBlock.Table -> tableRow(block)?.let(::listOf) ?: kept
        is ArticleBlock.Photo -> mediaRow(block, block.media, block.caption)?.let(::listOf) ?: kept
        is ArticleBlock.Video -> mediaRow(block, block.media, block.caption)?.let(::listOf) ?: kept
        is ArticleBlock.Audio -> mediaRow(block, block.media, block.caption)?.let(::listOf) ?: kept
        is ArticleBlock.Document -> mediaRow(block, block.media, block.caption)?.let(::listOf) ?: kept
        ArticleBlock.Divider -> listOf(ArticleRow(ArticleRowKind.Divider))
        is ArticleBlock.Items -> {
            val texts = block.items.map { it.textItem }
            val plain = texts.all { it != null && it.entitiesOnly } && block.items.all { it.num == null && it.value == null } && !block.reversed && (block.start ?: 1) == 1
            val checks = block.items.all { it.checkbox }
            val kind = when {
                !plain -> null
                block.ordered -> ArticleRowKind.Numbered.takeIf { block.items.none { it.checkbox } }
                checks -> ArticleRowKind.Check
                block.items.none { it.checkbox } -> ArticleRowKind.Bullet
                else -> null
            }
            if (kind == null) kept else block.items.zip(texts) { item, text -> ArticleRow(kind, text!!.formatted(), checked = item.checked) }
        }
        else -> kept
    }
}

private fun mediaRow(block: ArticleBlock, media: MediaItem?, caption: ArticleCaption): ArticleRow? =
    if (media != null && caption.text.entitiesOnly) ArticleRow(ArticleRowKind.Media, caption.text.formatted(), block = block) else null

private fun tableRow(table: ArticleBlock.Table): ArticleRow? {
    val width = table.rows.firstOrNull()?.size ?: return null
    val plain = { cell: ArticleCell -> cell.colspan == 1 && cell.rowspan == 1 && cell.align == ArticleAlign.Start && cell.valign == ArticleVerticalAlign.Top }
    if (table.rows.any { it.size != width || !it.all(plain) }) return null
    val cells = table.rows.map { row ->
        row.map { cell ->
            val text = cell.text ?: return@map FormattedText("")
            if (!text.entitiesOnly) return null
            text.formatted()
        }
    }
    val header = table.rows.first().all { it.header }
    if (table.rows.drop(if (header) 1 else 0).any { row -> row.any { it.header } }) return null
    if (table.rows.first().any { it.header } && !header) return null
    return ArticleRow(ArticleRowKind.Table, cells = cells, header = header, block = table)
}

private fun ArticleText.formatted(): FormattedText = flatten().let { FormattedText(it.text, it.entities) }

fun FormattedText.toRows(): List<ArticleRow> {
    val all = normalizeEntities(text, entities)
    val blocks = all.filter { it.type is EntityType.Pre || it.type is EntityType.Blockquote }
    val rows = mutableListOf<ArticleRow>()
    fun slice(start: Int, end: Int): FormattedText {
        val inside = all.filter { it.type !is EntityType.Pre && it.type !is EntityType.Blockquote && it.offset < end && it.end > start }.map { entity ->
            val from = maxOf(entity.offset, start)
            entity.copy(offset = from - start, length = minOf(entity.end, end) - from)
        }
        return FormattedText(text.substring(start, end), inside).trimmed()
    }
    fun paragraphs(start: Int, end: Int) {
        var from = start
        BLANK_LINES.findAll(text.substring(start, end)).forEach { gap ->
            rows += ArticleRow(ArticleRowKind.Paragraph, slice(from, start + gap.range.first))
            from = start + gap.range.last + 1
        }
        rows += ArticleRow(ArticleRowKind.Paragraph, slice(from, end))
    }
    var at = 0
    blocks.forEach { block ->
        if (block.offset > at) paragraphs(at, block.offset)
        rows += when (val type = block.type) {
            is EntityType.Pre -> ArticleRow(ArticleRowKind.Code, FormattedText(block.part(text).trim('\n')), language = type.language)
            else -> ArticleRow(ArticleRowKind.Quote, slice(block.offset, block.end))
        }
        at = block.end
    }
    if (at < text.length || rows.isEmpty()) paragraphs(at, text.length)
    return rows.filter { it.text.text.isNotEmpty() }.ifEmpty { listOf(ArticleRow(ArticleRowKind.Paragraph)) }.mapIndexed { index, row -> row.copy(id = index) }
}

data class ArticleCommand(val kind: ArticleRowKind, val length: Int, val media: ArticleMediaKind? = null)

fun articleCommandOf(text: String): ArticleCommand? {
    if (text == "---") return ArticleCommand(ArticleRowKind.Divider, 3)
    val word = text.substringBefore(' ', missingDelimiterValue = "")
    if (word.isEmpty() && !text.startsWith(' ')) return null
    MEDIA_COMMANDS[word]?.let { return ArticleCommand(ArticleRowKind.Media, word.length + 1, it) }
    val kind = COMMANDS[word] ?: return null
    return ArticleCommand(kind, word.length + 1)
}

private val MEDIA_COMMANDS = mapOf(
    "/image" to ArticleMediaKind.Photo,
    "/photo" to ArticleMediaKind.Photo,
    "/video" to ArticleMediaKind.Video,
    "/audio" to ArticleMediaKind.Audio,
    "/file" to ArticleMediaKind.File,
)

private val COMMANDS = mapOf(
    "/h1" to ArticleRowKind.Heading1,
    "#" to ArticleRowKind.Heading1,
    "/h2" to ArticleRowKind.Heading2,
    "##" to ArticleRowKind.Heading2,
    "/h3" to ArticleRowKind.Heading3,
    "###" to ArticleRowKind.Heading3,
    "/list" to ArticleRowKind.Bullet,
    "-" to ArticleRowKind.Bullet,
    "/numbered" to ArticleRowKind.Numbered,
    "1." to ArticleRowKind.Numbered,
    "/check" to ArticleRowKind.Check,
    "[]" to ArticleRowKind.Check,
    "/quote" to ArticleRowKind.Quote,
    "|" to ArticleRowKind.Quote,
    "/code" to ArticleRowKind.Code,
    "```" to ArticleRowKind.Code,
    "/math" to ArticleRowKind.Math,
    "/table" to ArticleRowKind.Table,
    "/divider" to ArticleRowKind.Divider,
)

fun ArticleMessage.toggled(item: ArticleListItem): ArticleMessage = copy(blocks = blocks.map { toggled(it, item) })

private fun toggled(block: ArticleBlock, item: ArticleListItem): ArticleBlock = when (block) {
    is ArticleBlock.Items -> block.copy(
        items = block.items.map { current ->
            if (current === item) current.copy(checked = !current.checked) else current.copy(blocks = current.blocks.map { toggled(it, item) })
        },
    )
    is ArticleBlock.QuoteBlocks -> block.copy(blocks = block.blocks.map { toggled(it, item) })
    is ArticleBlock.Details -> block.copy(blocks = block.blocks.map { toggled(it, item) })
    else -> block
}

private const val PARAGRAPH_BREAK = "\n\n"
private val BLANK_LINES = Regex("\n[ \t]*\n\\s*")
