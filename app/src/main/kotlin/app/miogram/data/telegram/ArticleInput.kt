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
import cn.ellerotta.core.model.BotButton
import cn.ellerotta.core.model.BotButtonStyle
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.textItem
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCallback
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCopy
import cn.ellerotta.ktproto.api.types.InlineButtonTypeDisabled
import cn.ellerotta.ktproto.api.types.InlineButtonTypeSwitchInline
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUrl
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUserProfile
import cn.ellerotta.ktproto.api.types.InlineButtonTypeWebView
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputPageBlockMap
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputPhotoFileLocation
import cn.ellerotta.ktproto.api.types.InputRichMessage
import cn.ellerotta.ktproto.api.types.PageBlockAnchor
import cn.ellerotta.ktproto.api.types.PageBlockAudio
import cn.ellerotta.ktproto.api.types.PageBlockBlockquote
import cn.ellerotta.ktproto.api.types.PageBlockBlockquoteBlocks
import cn.ellerotta.ktproto.api.types.PageBlockButtonRow
import cn.ellerotta.ktproto.api.types.PageBlockCollage
import cn.ellerotta.ktproto.api.types.PageBlockDetails
import cn.ellerotta.ktproto.api.types.PageBlockDivider
import cn.ellerotta.ktproto.api.types.PageBlockDocument
import cn.ellerotta.ktproto.api.types.PageBlockFooter
import cn.ellerotta.ktproto.api.types.PageBlockHeading1
import cn.ellerotta.ktproto.api.types.PageBlockHeading2
import cn.ellerotta.ktproto.api.types.PageBlockHeading3
import cn.ellerotta.ktproto.api.types.PageBlockHeading4
import cn.ellerotta.ktproto.api.types.PageBlockHeading5
import cn.ellerotta.ktproto.api.types.PageBlockHeading6
import cn.ellerotta.ktproto.api.types.PageBlockList
import cn.ellerotta.ktproto.api.types.PageBlockMath
import cn.ellerotta.ktproto.api.types.PageBlockOrderedList
import cn.ellerotta.ktproto.api.types.PageBlockParagraph
import cn.ellerotta.ktproto.api.types.PageBlockPhoto
import cn.ellerotta.ktproto.api.types.PageBlockPreformatted
import cn.ellerotta.ktproto.api.types.PageBlockPullquote
import cn.ellerotta.ktproto.api.types.PageBlockSlideshow
import cn.ellerotta.ktproto.api.types.PageBlockTable
import cn.ellerotta.ktproto.api.types.PageBlockThinking
import cn.ellerotta.ktproto.api.types.PageBlockVideo
import cn.ellerotta.ktproto.api.types.PageButton
import cn.ellerotta.ktproto.api.types.PageCaption
import cn.ellerotta.ktproto.api.types.PageListItemBlocks
import cn.ellerotta.ktproto.api.types.PageListItemText
import cn.ellerotta.ktproto.api.types.PageListOrderedItemBlocks
import cn.ellerotta.ktproto.api.types.PageListOrderedItemText
import cn.ellerotta.ktproto.api.types.PageTableCell
import cn.ellerotta.ktproto.api.types.PageTableRow
import cn.ellerotta.ktproto.api.types.RichButtonStyle
import cn.ellerotta.ktproto.api.types.TextAnchor
import cn.ellerotta.ktproto.api.types.TextBold
import cn.ellerotta.ktproto.api.types.TextButton
import cn.ellerotta.ktproto.api.types.TextConcat
import cn.ellerotta.ktproto.api.types.TextCustomEmoji
import cn.ellerotta.ktproto.api.types.TextDate
import cn.ellerotta.ktproto.api.types.TextDiff
import cn.ellerotta.ktproto.api.types.TextEmail
import cn.ellerotta.ktproto.api.types.TextEmpty
import cn.ellerotta.ktproto.api.types.TextFixed
import cn.ellerotta.ktproto.api.types.TextImage
import cn.ellerotta.ktproto.api.types.TextItalic
import cn.ellerotta.ktproto.api.types.TextMarked
import cn.ellerotta.ktproto.api.types.TextMath
import cn.ellerotta.ktproto.api.types.TextMentionName
import cn.ellerotta.ktproto.api.types.TextPhone
import cn.ellerotta.ktproto.api.types.TextPlain
import cn.ellerotta.ktproto.api.types.TextSpoiler
import cn.ellerotta.ktproto.api.types.TextStrike
import cn.ellerotta.ktproto.api.types.TextSubscript
import cn.ellerotta.ktproto.api.types.TextSuperscript
import cn.ellerotta.ktproto.api.types.TextUnderline
import cn.ellerotta.ktproto.api.types.TextUrl
import cn.ellerotta.ktproto.api.types.TypeInlineButtonType
import cn.ellerotta.ktproto.api.types.TypeInputUser
import cn.ellerotta.ktproto.api.types.TypePageBlock
import cn.ellerotta.ktproto.api.types.TypeRichText

internal fun inputRichMessageOf(
    article: ArticleMessage,
    photo: (MediaItem) -> InputPhoto?,
    document: (MediaItem) -> InputDocument?,
    user: (Long) -> TypeInputUser?,
    withUsers: Boolean = true,
): InputRichMessage = ArticleInputMapper(photo, document, user, withUsers).message(article)

internal fun PeerRegistry.inputRichMessageOf(article: ArticleMessage, withUsers: Boolean = true): InputRichMessage =
    inputRichMessageOf(article, ::inputPhotoOf, { inputDocuments[it.key] }, ::inputUser, withUsers)

internal fun PeerRegistry.inputPhotoOf(item: MediaItem): InputPhoto? =
    (locations[item.key] as? InputPhotoFileLocation)?.let { InputPhoto(it.id, it.accessHash, it.fileReference) }

private class ArticleInputMapper(
    private val photo: (MediaItem) -> InputPhoto?,
    private val document: (MediaItem) -> InputDocument?,
    private val user: (Long) -> TypeInputUser?,
    private val withUsers: Boolean,
) {
    private val photos = LinkedHashMap<Long, InputPhoto>()
    private val documents = LinkedHashMap<Long, InputDocument>()
    private val users = LinkedHashMap<Long, TypeInputUser>()

    fun message(article: ArticleMessage): InputRichMessage {
        val blocks = blocks(article.blocks)
        return InputRichMessage(
            blocks = blocks,
            photos = photos.values.toList().ifEmpty { null },
            documents = documents.values.toList().ifEmpty { null },
            users = users.values.toList().ifEmpty { null },
        )
    }

    private fun blocks(blocks: List<ArticleBlock>): List<TypePageBlock> = blocks.mapNotNull(::block)

    private fun photoId(media: MediaItem?): Long? = media?.let(photo)?.also { photos[it.id] = it }?.id

    private fun documentId(media: MediaItem?): Long? = media?.let(document)?.also { documents[it.id] = it }?.id

    private fun block(block: ArticleBlock): TypePageBlock? = when (block) {
        is ArticleBlock.Paragraph -> PageBlockParagraph(text(block.text))
        is ArticleBlock.Heading -> when (block.level) {
            1 -> PageBlockHeading1(text(block.text))
            2 -> PageBlockHeading2(text(block.text))
            3 -> PageBlockHeading3(text(block.text))
            4 -> PageBlockHeading4(text(block.text))
            5 -> PageBlockHeading5(text(block.text))
            else -> PageBlockHeading6(text(block.text))
        }
        is ArticleBlock.Preformatted -> PageBlockPreformatted(text(block.text), block.language)
        is ArticleBlock.Footer -> PageBlockFooter(text(block.text))
        is ArticleBlock.Thinking -> PageBlockThinking(text(block.text))
        is ArticleBlock.Items -> if (block.ordered) {
            PageBlockOrderedList(reversed = block.reversed, items = block.items.map(::orderedItem), start = block.start)
        } else {
            PageBlockList(block.items.map(::item))
        }
        is ArticleBlock.Quote -> PageBlockBlockquote(collapsed = block.collapsed, text = text(block.text), caption = text(block.author))
        is ArticleBlock.QuoteBlocks -> PageBlockBlockquoteBlocks(blocks(block.blocks), text(block.author))
        is ArticleBlock.Pullquote -> PageBlockPullquote(text(block.text), text(block.author))
        is ArticleBlock.Details -> PageBlockDetails(open = block.open, blocks = blocks(block.blocks), title = text(block.title))
        is ArticleBlock.Table -> PageBlockTable(
            bordered = block.bordered,
            striped = block.striped,
            compact = block.compact,
            title = text(block.title),
            rows = block.rows.map { row -> PageTableRow(row.map(::cell)) },
        )
        is ArticleBlock.Math -> PageBlockMath(block.source)
        ArticleBlock.Divider -> PageBlockDivider
        is ArticleBlock.Photo -> photoId(block.media)?.let { PageBlockPhoto(spoiler = block.spoiler, photoId = it, caption = caption(block.caption)) }
        is ArticleBlock.Video -> documentId(block.media)?.let { PageBlockVideo(autoplay = block.autoplay, loop = block.loop, spoiler = block.spoiler, videoId = it, caption = caption(block.caption)) }
        is ArticleBlock.Collage -> blocks(block.items).takeIf { it.isNotEmpty() }?.let { PageBlockCollage(it, caption(block.caption)) }
        is ArticleBlock.Slideshow -> blocks(block.items).takeIf { it.isNotEmpty() }?.let { PageBlockSlideshow(it, caption(block.caption)) }
        is ArticleBlock.Audio -> documentId(block.media)?.let { PageBlockAudio(it, caption(block.caption)) }
        is ArticleBlock.Document -> documentId(block.media)?.let { PageBlockDocument(it, caption(block.caption)) }
        is ArticleBlock.Map -> InputPageBlockMap(InputGeoPoint(block.latitude, block.longitude), block.zoom, block.width, block.height, caption(block.caption))
        is ArticleBlock.Buttons -> PageBlockButtonRow(
            alignLeft = block.align == ArticleAlign.Start,
            alignCenter = block.align == ArticleAlign.Center,
            alignRight = block.align == ArticleAlign.End,
            buttons = block.buttons.map(::button),
        )
        is ArticleBlock.Anchor -> PageBlockAnchor(block.name)
        ArticleBlock.Unsupported -> null
    }

    private fun item(item: ArticleListItem) = item.textItem?.let { PageListItemText(item.checkbox, item.checked, text(it)) }
        ?: PageListItemBlocks(item.checkbox, item.checked, blocks(item.blocks))

    private fun orderedItem(item: ArticleListItem) = item.textItem?.let { PageListOrderedItemText(item.checkbox, item.checked, item.num, text(it), item.value) }
        ?: PageListOrderedItemBlocks(item.checkbox, item.checked, item.num, blocks(item.blocks), item.value)

    private fun cell(cell: ArticleCell) = PageTableCell(
        header = cell.header,
        alignCenter = cell.align == ArticleAlign.Center,
        alignRight = cell.align == ArticleAlign.End,
        valignMiddle = cell.valign == ArticleVerticalAlign.Middle,
        valignBottom = cell.valign == ArticleVerticalAlign.Bottom,
        text = cell.text?.let(::text),
        colspan = cell.colspan.takeIf { it > 1 },
        rowspan = cell.rowspan.takeIf { it > 1 },
    )

    private fun caption(caption: ArticleCaption) = PageCaption(text(caption.text), text(caption.credit))

    private fun button(button: ArticleButton): PageButton {
        val (type, style) = buttonType(button.action)
        return PageButton(text(button.text), type, style)
    }

    private fun buttonType(action: BotButton): Pair<TypeInlineButtonType, RichButtonStyle?> {
        val type = when (action) {
            is BotButton.Url -> InlineButtonTypeUrl(action.url)
            is BotButton.LoginUrl -> InlineButtonTypeUrl(action.url)
            is BotButton.WebApp -> if (action.simple) InlineButtonTypeDisabled else InlineButtonTypeWebView(action.url)
            is BotButton.Copy -> InlineButtonTypeCopy(action.copyText)
            is BotButton.Callback -> InlineButtonTypeCallback(data = action.data)
            is BotButton.SwitchInline -> InlineButtonTypeSwitchInline(samePeer = action.samePeer, query = action.query)
            is BotButton.UserProfile -> {
                val input = if (withUsers) user(action.userId) else null
                if (input != null) users[action.userId] = input
                if (input != null || !withUsers) InlineButtonTypeUserProfile(action.userId) else InlineButtonTypeDisabled
            }
            is BotButton.Text, is BotButton.Disabled, is BotButton.Unsupported -> InlineButtonTypeDisabled
        }
        val style = when (action.style) {
            BotButtonStyle.Default -> null
            BotButtonStyle.Primary -> RichButtonStyle(bgPrimary = true)
            BotButtonStyle.Danger -> RichButtonStyle(bgDanger = true)
            BotButtonStyle.Success -> RichButtonStyle(bgSuccess = true)
            BotButtonStyle.Link -> RichButtonStyle(link = true)
        }
        return type to style
    }

    private fun text(text: ArticleText): TypeRichText = when (text) {
        ArticleText.Empty -> TextEmpty
        is ArticleText.Plain -> TextPlain(text.text)
        is ArticleText.Concat -> TextConcat(text.parts.map(::text))
        is ArticleText.Styled -> {
            val inner = text(text.text)
            when (text.style) {
                ArticleStyle.Bold -> TextBold(inner)
                ArticleStyle.Italic -> TextItalic(inner)
                ArticleStyle.Underline -> TextUnderline(inner)
                ArticleStyle.Strike -> TextStrike(inner)
                ArticleStyle.Fixed -> TextFixed(inner)
                ArticleStyle.Subscript -> TextSubscript(inner)
                ArticleStyle.Superscript -> TextSuperscript(inner)
                ArticleStyle.Marked -> TextMarked(inner)
                ArticleStyle.Spoiler -> TextSpoiler(inner)
            }
        }
        is ArticleText.Url -> if (text.auto) text(text.text) else TextUrl(text(text.text), text.url, 0)
        is ArticleText.Email -> if (text.auto) text(text.text) else TextEmail(text(text.text), text.email)
        is ArticleText.Phone -> if (text.auto) text(text.text) else TextPhone(text(text.text), text.phone)
        is ArticleText.MentionName -> TextMentionName(text(text.text), text.userId)
        is ArticleText.Mention -> text(text.text)
        is ArticleText.Hashtag -> text(text.text)
        is ArticleText.Cashtag -> text(text.text)
        is ArticleText.BotCommand -> text(text.text)
        is ArticleText.BankCard -> text(text.text)
        is ArticleText.CustomEmoji -> TextCustomEmoji(text.documentId, text.alt)
        is ArticleText.Math -> TextMath(text.source)
        is ArticleText.Date -> with(text.date) { TextDate(relative, shortTime, longTime, shortDate, longDate, dayOfWeek, text(text.text), date) }
        is ArticleText.Diff -> TextDiff(text(text.text), text(text.old))
        is ArticleText.Button -> buttonType(text.action).let { (type, style) -> TextButton(text(text.text), type, style) }
        is ArticleText.Anchor -> TextAnchor(text(text.text), text.name)
        is ArticleText.Image -> documentId(text.media)?.let { TextImage(it, text.width, text.height) } ?: TextEmpty
    }
}
