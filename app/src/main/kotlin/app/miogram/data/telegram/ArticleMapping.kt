package app.miogram.data.telegram

import cn.ellerotta.core.model.ArticleAlign
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleButton
import cn.ellerotta.core.model.ArticleCaption
import cn.ellerotta.core.model.ArticleCell
import cn.ellerotta.core.model.ArticleListItem
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleStyle
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.core.model.ArticleVerticalAlign
import cn.ellerotta.core.model.EntityType
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.flatten
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.GeoPoint
import cn.ellerotta.ktproto.api.types.InputPageBlockMap
import cn.ellerotta.ktproto.api.types.PageBlockAnchor
import cn.ellerotta.ktproto.api.types.PageBlockAudio
import cn.ellerotta.ktproto.api.types.PageBlockAuthorDate
import cn.ellerotta.ktproto.api.types.PageBlockBlockquote
import cn.ellerotta.ktproto.api.types.PageBlockBlockquoteBlocks
import cn.ellerotta.ktproto.api.types.PageBlockButtonRow
import cn.ellerotta.ktproto.api.types.PageBlockChannel
import cn.ellerotta.ktproto.api.types.PageBlockCollage
import cn.ellerotta.ktproto.api.types.PageBlockCover
import cn.ellerotta.ktproto.api.types.PageBlockDetails
import cn.ellerotta.ktproto.api.types.PageBlockDivider
import cn.ellerotta.ktproto.api.types.PageBlockDocument
import cn.ellerotta.ktproto.api.types.PageBlockEmbed
import cn.ellerotta.ktproto.api.types.PageBlockEmbedPost
import cn.ellerotta.ktproto.api.types.PageBlockFooter
import cn.ellerotta.ktproto.api.types.PageBlockHeader
import cn.ellerotta.ktproto.api.types.PageBlockHeading1
import cn.ellerotta.ktproto.api.types.PageBlockHeading2
import cn.ellerotta.ktproto.api.types.PageBlockHeading3
import cn.ellerotta.ktproto.api.types.PageBlockHeading4
import cn.ellerotta.ktproto.api.types.PageBlockHeading5
import cn.ellerotta.ktproto.api.types.PageBlockHeading6
import cn.ellerotta.ktproto.api.types.PageBlockKicker
import cn.ellerotta.ktproto.api.types.PageBlockList
import cn.ellerotta.ktproto.api.types.PageBlockMap
import cn.ellerotta.ktproto.api.types.PageBlockMath
import cn.ellerotta.ktproto.api.types.PageBlockOrderedList
import cn.ellerotta.ktproto.api.types.PageBlockParagraph
import cn.ellerotta.ktproto.api.types.PageBlockPhoto
import cn.ellerotta.ktproto.api.types.PageBlockPreformatted
import cn.ellerotta.ktproto.api.types.PageBlockPullquote
import cn.ellerotta.ktproto.api.types.PageBlockRelatedArticles
import cn.ellerotta.ktproto.api.types.PageBlockSlideshow
import cn.ellerotta.ktproto.api.types.PageBlockSubheader
import cn.ellerotta.ktproto.api.types.PageBlockSubtitle
import cn.ellerotta.ktproto.api.types.PageBlockTable
import cn.ellerotta.ktproto.api.types.PageBlockThinking
import cn.ellerotta.ktproto.api.types.PageBlockTitle
import cn.ellerotta.ktproto.api.types.PageBlockUnsupported
import cn.ellerotta.ktproto.api.types.PageBlockVideo
import cn.ellerotta.ktproto.api.types.PageButton
import cn.ellerotta.ktproto.api.types.PageCaption
import cn.ellerotta.ktproto.api.types.PageListItemBlocks
import cn.ellerotta.ktproto.api.types.PageListItemText
import cn.ellerotta.ktproto.api.types.PageListOrderedItemBlocks
import cn.ellerotta.ktproto.api.types.PageListOrderedItemText
import cn.ellerotta.ktproto.api.types.PageTableCell
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.RichMessage
import cn.ellerotta.ktproto.api.types.TextAnchor
import cn.ellerotta.ktproto.api.types.TextAutoEmail
import cn.ellerotta.ktproto.api.types.TextAutoPhone
import cn.ellerotta.ktproto.api.types.TextAutoUrl
import cn.ellerotta.ktproto.api.types.TextBankCard
import cn.ellerotta.ktproto.api.types.TextBold
import cn.ellerotta.ktproto.api.types.TextBotCommand
import cn.ellerotta.ktproto.api.types.TextButton
import cn.ellerotta.ktproto.api.types.TextCashtag
import cn.ellerotta.ktproto.api.types.TextConcat
import cn.ellerotta.ktproto.api.types.TextCustomEmoji
import cn.ellerotta.ktproto.api.types.TextDate
import cn.ellerotta.ktproto.api.types.TextDiff
import cn.ellerotta.ktproto.api.types.TextEmail
import cn.ellerotta.ktproto.api.types.TextEmpty
import cn.ellerotta.ktproto.api.types.TextFixed
import cn.ellerotta.ktproto.api.types.TextHashtag
import cn.ellerotta.ktproto.api.types.TextImage
import cn.ellerotta.ktproto.api.types.TextItalic
import cn.ellerotta.ktproto.api.types.TextMarked
import cn.ellerotta.ktproto.api.types.TextMath
import cn.ellerotta.ktproto.api.types.TextMention
import cn.ellerotta.ktproto.api.types.TextMentionName
import cn.ellerotta.ktproto.api.types.TextPhone
import cn.ellerotta.ktproto.api.types.TextPlain
import cn.ellerotta.ktproto.api.types.TextSpoiler
import cn.ellerotta.ktproto.api.types.TextStrike
import cn.ellerotta.ktproto.api.types.TextSubscript
import cn.ellerotta.ktproto.api.types.TextSuperscript
import cn.ellerotta.ktproto.api.types.TextUnderline
import cn.ellerotta.ktproto.api.types.TextUrl
import cn.ellerotta.ktproto.api.types.TypePageBlock
import cn.ellerotta.ktproto.api.types.TypeRichText

internal fun articleOf(rich: RichMessage, registry: PeerRegistry?, origin: FileOrigin?, bot: String?): ArticleMessage =
    ArticleMapper(rich, registry, origin, bot).message()

private class ArticleMapper(private val rich: RichMessage, private val registry: PeerRegistry?, private val origin: FileOrigin?, private val bot: String?) {
    private val photos = rich.photos.filterIsInstance<Photo>().associateBy { it.id }
    private val documents = rich.documents.filterIsInstance<Document>().associateBy { it.id }

    fun message() = ArticleMessage(blocks(rich.blocks), rtl = rich.rtl, part = rich.part)

    private fun blocks(blocks: List<TypePageBlock>): List<ArticleBlock> = blocks.mapNotNull(::block)

    private fun block(block: TypePageBlock): ArticleBlock? = when (block) {
        is PageBlockParagraph -> ArticleBlock.Paragraph(text(block.text))
        is PageBlockHeading1 -> ArticleBlock.Heading(1, text(block.text))
        is PageBlockHeading2 -> ArticleBlock.Heading(2, text(block.text))
        is PageBlockHeading3 -> ArticleBlock.Heading(3, text(block.text))
        is PageBlockHeading4 -> ArticleBlock.Heading(4, text(block.text))
        is PageBlockHeading5 -> ArticleBlock.Heading(5, text(block.text))
        is PageBlockHeading6 -> ArticleBlock.Heading(6, text(block.text))
        is PageBlockPreformatted -> ArticleBlock.Preformatted(text(block.text), block.language)
        is PageBlockFooter -> ArticleBlock.Footer(text(block.text))
        is PageBlockThinking -> ArticleBlock.Thinking(text(block.text))
        is PageBlockList -> ArticleBlock.Items(
            block.items.map { item ->
                when (item) {
                    is PageListItemText -> ArticleListItem(listOf(ArticleBlock.Paragraph(text(item.text))), item.checkbox, item.checked)
                    is PageListItemBlocks -> ArticleListItem(blocks(item.blocks), item.checkbox, item.checked)
                }
            },
        )
        is PageBlockOrderedList -> ArticleBlock.Items(
            block.items.map { item ->
                when (item) {
                    is PageListOrderedItemText -> ArticleListItem(listOf(ArticleBlock.Paragraph(text(item.text))), item.checkbox, item.checked, item.num?.ifEmpty { null }, item.value)
                    is PageListOrderedItemBlocks -> ArticleListItem(blocks(item.blocks), item.checkbox, item.checked, item.num?.ifEmpty { null }, item.value)
                }
            },
            ordered = true,
            start = block.start,
            reversed = block.reversed,
        )
        is PageBlockBlockquote -> ArticleBlock.Quote(text(block.text), text(block.caption), block.collapsed)
        is PageBlockBlockquoteBlocks -> ArticleBlock.QuoteBlocks(blocks(block.blocks), text(block.caption))
        is PageBlockPullquote -> ArticleBlock.Pullquote(text(block.text), text(block.caption))
        is PageBlockDetails -> ArticleBlock.Details(text(block.title), blocks(block.blocks), block.open)
        is PageBlockTable -> ArticleBlock.Table(
            title = text(block.title),
            rows = block.rows.map { row -> row.cells.map(::cell) },
            bordered = block.bordered,
            striped = block.striped,
            compact = block.compact,
        )
        is PageBlockMath -> ArticleBlock.Math(block.source)
        PageBlockDivider -> ArticleBlock.Divider
        is PageBlockPhoto -> ArticleBlock.Photo(photo(block.photoId), caption(block.caption), block.spoiler, block.url)
        is PageBlockVideo -> ArticleBlock.Video(document(block.videoId), caption(block.caption), block.spoiler, block.autoplay, block.loop)
        is PageBlockCollage -> ArticleBlock.Collage(blocks(block.items), caption(block.caption))
        is PageBlockSlideshow -> ArticleBlock.Slideshow(blocks(block.items), caption(block.caption))
        is PageBlockAudio -> ArticleBlock.Audio(document(block.audioId), caption(block.caption))
        is PageBlockDocument -> ArticleBlock.Document(document(block.documentId), caption(block.caption))
        is PageBlockMap -> (block.geo as? GeoPoint)?.let { geo -> ArticleBlock.Map(geo.lat, geo.long, block.zoom, block.w, block.h, caption(block.caption)) }
        is PageBlockButtonRow -> ArticleBlock.Buttons(
            block.buttons.map(::button),
            when {
                block.alignLeft -> ArticleAlign.Start
                block.alignCenter -> ArticleAlign.Center
                block.alignRight -> ArticleAlign.End
                else -> null
            },
        )
        is PageBlockAnchor -> ArticleBlock.Anchor(block.name)
        is PageBlockCover -> block(block.cover)
        PageBlockUnsupported -> ArticleBlock.Unsupported
        is PageBlockTitle, is PageBlockSubtitle, is PageBlockHeader, is PageBlockSubheader, is PageBlockKicker, is PageBlockAuthorDate,
        is PageBlockEmbed, is PageBlockEmbedPost, is PageBlockChannel, is PageBlockRelatedArticles,
        -> null
        is InputPageBlockMap -> null
    }

    private fun cell(cell: PageTableCell) = ArticleCell(
        text = cell.text?.let(::text),
        header = cell.header,
        align = when {
            cell.alignCenter -> ArticleAlign.Center
            cell.alignRight -> ArticleAlign.End
            else -> ArticleAlign.Start
        },
        valign = when {
            cell.valignMiddle -> ArticleVerticalAlign.Middle
            cell.valignBottom -> ArticleVerticalAlign.Bottom
            else -> ArticleVerticalAlign.Top
        },
        colspan = (cell.colspan ?: 1).coerceAtLeast(1),
        rowspan = (cell.rowspan ?: 1).coerceAtLeast(1),
    )

    private fun caption(caption: PageCaption) = ArticleCaption(text(caption.text), text(caption.credit))

    private fun button(button: PageButton): ArticleButton {
        val label = text(button.text)
        return ArticleButton(label, buttonOf(label.flatten().text, button.type, richStyleOf(button.style), bot))
    }

    private fun text(text: TypeRichText): ArticleText = when (text) {
        TextEmpty -> ArticleText.Empty
        is TextPlain -> ArticleText.Plain(text.text)
        is TextConcat -> ArticleText.Concat(text.texts.map(::text))
        is TextBold -> ArticleText.Styled(ArticleStyle.Bold, text(text.text))
        is TextItalic -> ArticleText.Styled(ArticleStyle.Italic, text(text.text))
        is TextUnderline -> ArticleText.Styled(ArticleStyle.Underline, text(text.text))
        is TextStrike -> ArticleText.Styled(ArticleStyle.Strike, text(text.text))
        is TextFixed -> ArticleText.Styled(ArticleStyle.Fixed, text(text.text))
        is TextSubscript -> ArticleText.Styled(ArticleStyle.Subscript, text(text.text))
        is TextSuperscript -> ArticleText.Styled(ArticleStyle.Superscript, text(text.text))
        is TextMarked -> ArticleText.Styled(ArticleStyle.Marked, text(text.text))
        is TextSpoiler -> ArticleText.Styled(ArticleStyle.Spoiler, text(text.text))
        is TextUrl -> ArticleText.Url(text(text.text), text.url)
        is TextEmail -> ArticleText.Email(text(text.text), text.email)
        is TextPhone -> ArticleText.Phone(text(text.text), text.phone)
        is TextAutoUrl -> text(text.text).let { ArticleText.Url(it, it.flatten().text, auto = true) }
        is TextAutoEmail -> text(text.text).let { ArticleText.Email(it, it.flatten().text, auto = true) }
        is TextAutoPhone -> text(text.text).let { ArticleText.Phone(it, it.flatten().text, auto = true) }
        is TextMention -> ArticleText.Mention(text(text.text))
        is TextMentionName -> ArticleText.MentionName(text(text.text), text.userId)
        is TextHashtag -> ArticleText.Hashtag(text(text.text))
        is TextCashtag -> ArticleText.Cashtag(text(text.text))
        is TextBotCommand -> ArticleText.BotCommand(text(text.text))
        is TextBankCard -> ArticleText.BankCard(text(text.text))
        is TextCustomEmoji -> ArticleText.CustomEmoji(text.documentId, text.alt)
        is TextMath -> ArticleText.Math(text.source)
        is TextDate -> ArticleText.Date(
            text(text.text),
            EntityType.FormattedDate(text.date, text.relative, text.shortTime, text.longTime, text.shortDate, text.longDate, text.dayOfWeek),
        )
        is TextDiff -> ArticleText.Diff(text(text.text), text(text.oldText))
        is TextButton -> text(text.text).let { label -> ArticleText.Button(label, buttonOf(label.flatten().text, text.type, richStyleOf(text.style), bot)) }
        is TextAnchor -> ArticleText.Anchor(text(text.text), text.name)
        is TextImage -> ArticleText.Image(text.documentId, text.w, text.h, document(text.documentId))
    }

    private fun photo(id: Long): MediaItem? {
        val photo = photos[id] ?: return null
        return registry?.photoItem(photo, origin)
    }

    private fun document(id: Long): MediaItem? {
        val doc = documents[id] ?: return null
        return registry?.documentOf(null, doc, origin) ?: documentItem(null, doc)
    }
}
