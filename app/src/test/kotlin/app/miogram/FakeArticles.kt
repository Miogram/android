package app.miogram

import app.miogram.data.telegram.articleOf
import cn.ellerotta.core.model.counts
import cn.ellerotta.ktproto.api.functions.messages.GetRichMessage
import cn.ellerotta.ktproto.api.functions.messages.UploadMedia
import cn.ellerotta.ktproto.api.types.ChatEmpty
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.DocumentAttributeImageSize
import cn.ellerotta.ktproto.api.types.GeoPoint
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCallback
import cn.ellerotta.ktproto.api.types.InlineButtonTypeCopy
import cn.ellerotta.ktproto.api.types.InlineButtonTypeDisabled
import cn.ellerotta.ktproto.api.types.InlineButtonTypeSwitchInline
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUrl
import cn.ellerotta.ktproto.api.types.InlineButtonTypeUserProfile
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputFile
import cn.ellerotta.ktproto.api.types.InputFileBig
import cn.ellerotta.ktproto.api.types.InputGeoPoint
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputPageBlockMap
import cn.ellerotta.ktproto.api.types.InputPeerEmpty
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputRichMessage
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
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
import cn.ellerotta.ktproto.api.types.PageTableRow
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.RichButtonStyle
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
import cn.ellerotta.ktproto.api.types.TypeInputRichMessage
import cn.ellerotta.ktproto.api.types.TypePageBlock
import cn.ellerotta.ktproto.api.types.messages.Messages
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeCluster
import cn.ellerotta.ktproto.testing.FakeTelegramServer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay

class FakeArticles(private val fake: FakeTelegram) {
    val richCalls: MutableList<Int> = CopyOnWriteArrayList()

    @Volatile var richDelay: Long = 0

    private val articles: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    @Volatile var posting = "enabled"

    val limits: MutableMap<String, Int> = ConcurrentHashMap()

    @Volatile var premium = false

    fun configHash(): Int = if (posting == "enabled" && limits.isEmpty()) 0 else (posting.hashCode() * 31 + limits.hashCode()) and 0xffff

    val uploadedPhotos: MutableMap<Long, Photo> = ConcurrentHashMap()
    val uploadedDocuments: MutableMap<Long, Document> = ConcurrentHashMap()

    val uploadMediaCalls: MutableList<UploadMedia> = CopyOnWriteArrayList()

    val uploadedBytes: MutableMap<Long, ByteArray> = ConcurrentHashMap()

    @Volatile private var nextUploaded = UPLOADED_MEDIA_ID

    private fun knownPhotos(): Map<Long, Photo> = mapOf(FakeTelegram.PHOTO_ID to fake.photo) + uploadedPhotos

    private fun knownDocuments(): Map<Long, Document> = (documents() + uploadedDocuments.values).associateBy { it.id }

    fun accept(input: TypeInputRichMessage): Pair<RichMessage?, String?> {
        val rich = input as? InputRichMessage ?: return null to "RICH_MESSAGE_INVALID"
        when {
            posting == "disabled" -> return null to "RICH_MESSAGE_POSTING_DISABLED"
            posting == "premium" && !premium -> return null to "PREMIUM_ACCOUNT_REQUIRED"
        }
        val photos = rich.photos.orEmpty().map { (it as? InputPhoto)?.let { photo -> knownPhotos()[photo.id] } ?: return null to "RICH_MESSAGE_MEDIA_INVALID" }
        val documents = rich.documents.orEmpty().map { (it as? InputDocument)?.let { doc -> knownDocuments()[doc.id] } ?: return null to "RICH_MESSAGE_MEDIA_INVALID" }
        val blocks = rich.blocks.map(::stored)
        val named = NamedMedia().also { it.collect(blocks) }
        if (!photos.map { it.id }.containsAll(named.photos) || !documents.map { it.id }.containsAll(named.documents)) return null to "RICH_MESSAGE_MEDIA_INVALID"
        if (documents.any { it.id in named.files && (it.mimeType.startsWith("image/") || it.attributes.any { attribute -> attribute is DocumentAttributeImageSize }) }) return null to "RICH_MESSAGE_DOCUMENT_INVALID"
        val message = RichMessage(blocks = blocks, photos = photos, documents = documents)
        val counts = articleOf(message, null, null, null).counts()
        fun limit(key: String, default: Int) = limits[key] ?: default
        return when {
            counts.length > limit("rich_message_length_limit", 32_768) -> null to "RICH_MESSAGE_TOO_LONG"
            counts.blocks > limit("rich_message_max_blocks", 500) -> null to "RICH_MESSAGE_BLOCKS_TOO_MANY"
            counts.depth > limit("rich_message_max_depth", 16) -> null to "RICH_MESSAGE_TOO_DEEP"
            counts.media > limit("rich_message_max_media", 50) -> null to "RICH_MESSAGE_MEDIA_TOO_MANY"
            counts.tableColumns > limit("rich_message_max_table_cols", 20) -> null to "RICH_MESSAGE_TABLE_COLS_TOO_MANY"
            else -> message to null
        }
    }

    private fun stored(block: TypePageBlock): TypePageBlock = when (block) {
        is InputPageBlockMap -> (block.geo as InputGeoPoint).let { geo -> PageBlockMap(GeoPoint(long = geo.long, lat = geo.lat, accessHash = 0), block.zoom, block.w, block.h, block.caption) }
        else -> block
    }

    private class NamedMedia {
        val photos = HashSet<Long>()
        val documents = HashSet<Long>()
        val files = HashSet<Long>()

        fun collect(blocks: List<TypePageBlock>) {
            blocks.forEach { block ->
                when (block) {
                    is PageBlockPhoto -> photos += block.photoId
                    is PageBlockVideo -> documents += block.videoId
                    is PageBlockAudio -> documents += block.audioId
                    is PageBlockDocument -> {
                        documents += block.documentId
                        files += block.documentId
                    }
                    is PageBlockCollage -> collect(block.items)
                    is PageBlockSlideshow -> collect(block.items)
                    is PageBlockBlockquoteBlocks -> collect(block.blocks)
                    is PageBlockDetails -> collect(block.blocks)
                    is PageBlockCover -> collect(listOf(block.cover))
                    is PageBlockList -> block.items.forEach { if (it is PageListItemBlocks) collect(it.blocks) }
                    is PageBlockOrderedList -> block.items.forEach { if (it is PageListOrderedItemBlocks) collect(it.blocks) }
                    else -> Unit
                }
            }
        }
    }

    fun register(server: FakeTelegramServer) {
        server.rpc.on(GetRichMessage.CONSTRUCTOR_ID) { request ->
            val call = GetRichMessage.decode(request)
            richCalls += call.id
            if (call.peer != InputPeerSelf || call.id !in articles) return@on FakeAnswer.Error(400, "MESSAGE_ID_INVALID")
            if (richDelay > 0) delay(richDelay)
            FakeAnswer.Result(Messages(listOf(message(call.id, part = false)), emptyList(), emptyList(), listOf(fake.alice)))
        }
        server.rpc.on(UploadMedia.CONSTRUCTOR_ID) { request ->
            val call = UploadMedia.decode(request)
            uploadMediaCalls += call
            if (call.peer == InputPeerEmpty) return@on FakeAnswer.Error(400, "PEER_ID_INVALID")
            val file = when (val media = call.media) {
                is InputMediaUploadedPhoto -> media.file
                is InputMediaUploadedDocument -> media.file
                else -> return@on FakeAnswer.Error(400, "MEDIA_INVALID")
            }
            val bytes = when (file) {
                is InputFile -> fake.cluster.uploadedFile(file.id, file.parts)
                is InputFileBig -> fake.cluster.uploadedFile(file.id, file.parts)
                else -> null
            } ?: return@on FakeAnswer.Error(400, "FILE_PARTS_INVALID")
            val id = nextUploaded++
            uploadedBytes[id] = bytes
            when (val media = call.media) {
                is InputMediaUploadedDocument -> {
                    val document = Document(id = id, accessHash = 1, fileReference = byteArrayOf(5), date = FakeTelegram.NOW, mimeType = media.mimeType, size = bytes.size.toLong(), dcId = 2, attributes = media.attributes)
                    uploadedDocuments[id] = document
                    FakeAnswer.Result(MessageMediaDocument(document = document))
                }
                else -> {
                    val photo = Photo(id = id, accessHash = 1, fileReference = byteArrayOf(5), date = FakeTelegram.NOW, sizes = listOf(PhotoSize("y", 1280, 960, bytes.size)), dcId = 2)
                    uploadedPhotos[id] = photo
                    FakeAnswer.Result(MessageMediaPhoto(photo = photo))
                }
            }
        }
    }

    fun addArticle(): Int = fake.addSaved { id -> message(id, part = false).also { articles += id } }

    fun addPart(): Int = fake.addSaved { id -> message(id, part = true).also { articles += id } }

    fun addSingle(block: TypePageBlock): Int = fake.addSaved { id ->
        Message(out = true, id = id, peerId = PeerUser(fake.selfId), date = FakeTelegram.NOW, message = "", richMessage = RichMessage(blocks = listOf(block), photos = listOf(fake.photo), documents = documents()))
    }

    private fun message(id: Int, part: Boolean) = Message(
        out = true,
        id = id,
        peerId = PeerUser(fake.selfId),
        date = FakeTelegram.NOW,
        message = "plain fallback",
        media = MessageMediaPhoto(photo = fake.photo),
        richMessage = RichMessage(part = part, blocks = if (part) blocks().take(PART_BLOCKS) else blocks(), photos = listOf(fake.photo), documents = documents()),
    )

    private fun documents() = listOf(fake.videoDocument(), song, file, image)

    private val song = Document(
        id = SONG_ID,
        accessHash = 1,
        fileReference = byteArrayOf(9),
        date = FakeTelegram.NOW,
        mimeType = "audio/mpeg",
        size = 4_000,
        dcId = 2,
        attributes = listOf(DocumentAttributeAudio(duration = 30, title = "Song", performer = "Band"), DocumentAttributeFilename("song.mp3")),
    )

    private val file = Document(
        id = FakeCluster.FILE_ID,
        accessHash = 1,
        fileReference = byteArrayOf(1),
        date = FakeTelegram.NOW,
        mimeType = "application/octet-stream",
        size = fake.cluster.fileBytes.size.toLong(),
        dcId = fake.cluster.fileDcId,
        attributes = listOf(DocumentAttributeFilename("big.bin")),
    )

    private val image = Document(
        id = IMAGE_ID,
        accessHash = 1,
        fileReference = byteArrayOf(3),
        date = FakeTelegram.NOW,
        mimeType = "image/png",
        size = 100,
        dcId = 2,
        attributes = listOf(DocumentAttributeImageSize(16, 16), DocumentAttributeFilename("dot.png")),
    )

    private fun p(text: String) = TextPlain(text)

    private fun caption(text: String, credit: String = "") = PageCaption(p(text), if (credit.isEmpty()) TextEmpty else p(credit))

    fun inline() = TextConcat(
        listOf(
            p("plain "),
            TextBold(p("bold")), p(" "),
            TextItalic(p("italic")), p(" "),
            TextUnderline(p("under")), p(" "),
            TextStrike(p("strike")), p(" "),
            TextFixed(p("fixed")), p(" "),
            TextSubscript(p("sub")), p(" "),
            TextSuperscript(p("sup")), p(" "),
            TextMarked(p("marked")), p(" "),
            TextUrl(p("link"), "https://core.telegram.org/api/instant-view", 0), p(" "),
            TextEmail(p("mail"), "a@b.co"), p(" "),
            TextPhone(p("call"), "+1 650 253 0000"), p(" "),
            TextAutoUrl(p("example.com")), p(" "),
            TextAutoEmail(p("c@d.co")), p(" "),
            TextAutoPhone(p("+16502530000")), p(" "),
            TextMention(p("@carol_writer")), p(" "),
            TextMentionName(p("Alice"), fake.alice.id), p(" "),
            TextHashtag(p("#kotlin")), p(" "),
            TextCashtag(p("\$TON")), p(" "),
            TextBotCommand(p("/start")), p(" "),
            TextBankCard(p("4111111111111111")), p(" "),
            TextSpoiler(p("secret")), p(" "),
            TextCustomEmoji(FakeTelegram.EMOJI_STAR, "\u2B50"), p(" "),
            TextMath("x^2"), p(" "),
            TextDate(relative = true, text = p("tomorrow"), date = FakeTelegram.NOW + 86_400), p(" "),
            TextDiff(p("new"), p("old")), p(" "),
            TextDiff(TextEmpty, p("gone")), p(" "),
            TextDiff(p("added"), TextEmpty), p(" "),
            TextButton(p("Ping"), InlineButtonTypeCallback(data = "ping".encodeToByteArray()), RichButtonStyle(bgPrimary = true)), p(" "),
            TextAnchor(p("a footnote"), "Note"), p(" "),
            TextUrl(p("see note"), "#note", 0), p(" "),
            TextAnchor(TextEmpty, "here"),
            TextImage(IMAGE_ID, 16, 16),
        ),
    )

    private fun blocks(): List<TypePageBlock> = listOf(
        PageBlockHeading1(p("Release notes")),
        PageBlockParagraph(inline()),
        PageBlockHeading2(p("Heading two")),
        PageBlockHeading3(p("Heading three")),
        PageBlockHeading4(p("Heading four")),
        PageBlockHeading5(p("Heading five")),
        PageBlockHeading6(p("Heading six")),
        PageBlockPreformatted(p("fun main() {}"), "kotlin"),
        PageBlockFooter(p("footer")),
        PageBlockThinking(p("thinking")),
        PageBlockList(
            listOf(
                PageListItemText(text = p("first")),
                PageListItemText(checkbox = true, text = p("todo")),
                PageListItemText(checkbox = true, checked = true, text = p("done")),
                PageListItemBlocks(blocks = listOf(PageBlockParagraph(p("nested")), PageBlockList(listOf(PageListItemText(text = p("deeper")))))),
            ),
        ),
        PageBlockOrderedList(
            reversed = true,
            start = 3,
            items = listOf(
                PageListOrderedItemText(num = "a", text = p("lettered")),
                PageListOrderedItemText(value = 7, text = p("valued")),
                PageListOrderedItemText(text = p("counted")),
            ),
        ),
        PageBlockBlockquote(collapsed = true, text = p("quoted"), caption = p("Alice")),
        PageBlockBlockquoteBlocks(listOf(PageBlockParagraph(p("inside"))), p("Bob")),
        PageBlockPullquote(p("pulled"), p("Carol")),
        PageBlockDetails(blocks = listOf(PageBlockParagraph(p("hidden"))), title = p("More")),
        PageBlockTable(
            bordered = true,
            striped = true,
            compact = true,
            title = p("Scores"),
            rows = listOf(
                PageTableRow(listOf(PageTableCell(header = true, text = p("Name")), PageTableCell(header = true, alignCenter = true, text = p("Points")))),
                PageTableRow(listOf(PageTableCell(text = p("Alice")), PageTableCell(alignRight = true, valignMiddle = true, text = p("3")))),
                PageTableRow(listOf(PageTableCell(colspan = 2, valignBottom = true, text = p("Bob")))),
                PageTableRow(listOf(PageTableCell(rowspan = 2, text = p("Tall")), PageTableCell())),
            ),
        ),
        PageBlockMath("E = mc^2"),
        PageBlockDivider,
        PageBlockPhoto(spoiler = true, photoId = FakeTelegram.PHOTO_ID, caption = caption("A photo", "by me")),
        PageBlockVideo(loop = true, videoId = FakeTelegram.VIDEO_ID, caption = caption("A clip")),
        PageBlockCollage(listOf(PageBlockPhoto(photoId = FakeTelegram.PHOTO_ID, caption = caption("")), PageBlockVideo(videoId = FakeTelegram.VIDEO_ID, caption = caption(""))), caption("Side by side")),
        PageBlockSlideshow(listOf(PageBlockPhoto(photoId = FakeTelegram.PHOTO_ID, caption = caption("")), PageBlockPhoto(photoId = MISSING_ID, caption = caption(""))), caption("")),
        PageBlockAudio(SONG_ID, caption("")),
        PageBlockDocument(FakeCluster.FILE_ID, caption("The file")),
        PageBlockMap(GeoPoint(long = 13.4, lat = 52.5, accessHash = 1), 15, 600, 300, caption("Berlin")),
        PageBlockButtonRow(
            alignCenter = true,
            buttons = listOf(
                PageButton(p("Site"), InlineButtonTypeUrl("https://core.telegram.org")),
                PageButton(p("Copy"), InlineButtonTypeCopy("copied text"), RichButtonStyle(link = true)),
                PageButton(p("Alice"), InlineButtonTypeUserProfile(fake.alice.id), RichButtonStyle(bgSuccess = true)),
                PageButton(p("Search"), InlineButtonTypeSwitchInline(samePeer = true, query = "cats")),
                PageButton(p("Off"), InlineButtonTypeDisabled, RichButtonStyle(bgDanger = true)),
            ),
        ),
        PageBlockAnchor("end"),
        PageBlockUnsupported,
        PageBlockCover(PageBlockParagraph(p("covered"))),
        PageBlockTitle(p("Title")),
        PageBlockSubtitle(p("Subtitle")),
        PageBlockHeader(p("Header")),
        PageBlockSubheader(p("Subheader")),
        PageBlockKicker(p("Kicker")),
        PageBlockAuthorDate(p("Author"), FakeTelegram.NOW),
        PageBlockEmbed(url = "https://example.com", caption = caption("")),
        PageBlockEmbedPost("https://example.com", 0, 0, "Author", FakeTelegram.NOW, emptyList(), caption("")),
        PageBlockChannel(ChatEmpty(1)),
        PageBlockRelatedArticles(p("Related"), emptyList()),
    )

    companion object {
        const val SONG_ID = 4_545L
        const val IMAGE_ID = 4_646L

        const val UPLOADED_MEDIA_ID = 9_000L

        const val MISSING_ID = 4_747L
        const val PART_BLOCKS = 3

        const val INLINE = "plain bold italic under strike fixed sub sup marked link mail call example.com c@d.co +16502530000 @carol_writer Alice #kotlin \$TON /start 4111111111111111 secret \u2B50 x^2 tomorrow new gone added Ping a footnote see note \uFFFC"
    }
}
