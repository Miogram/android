package app.miogram

import app.miogram.data.MediaFileInfo
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.documentOf
import cn.ellerotta.core.model.ArticleBlock
import cn.ellerotta.core.model.ArticleCaption
import cn.ellerotta.core.model.ArticleMediaKind
import cn.ellerotta.core.model.ArticleMessage
import cn.ellerotta.core.model.ArticleText
import cn.ellerotta.ktproto.api.functions.messages.UploadMedia
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.DocumentAttributeAudio
import cn.ellerotta.ktproto.api.types.DocumentAttributeFilename
import cn.ellerotta.ktproto.api.types.DocumentAttributeImageSize
import cn.ellerotta.ktproto.api.types.DocumentAttributeVideo
import cn.ellerotta.ktproto.api.types.InputDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedDocument
import cn.ellerotta.ktproto.api.types.InputMediaUploadedPhoto
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPhoto
import cn.ellerotta.ktproto.api.types.InputRichMessage
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.PageBlockAudio
import cn.ellerotta.ktproto.api.types.PageBlockDocument
import cn.ellerotta.ktproto.api.types.PageBlockPhoto
import cn.ellerotta.ktproto.api.types.PageBlockVideo
import cn.ellerotta.ktproto.api.types.TextPlain
import cn.ellerotta.ktproto.api.types.TypeInputFile
import cn.ellerotta.ktproto.client.Upload
import cn.ellerotta.ktproto.client.UploadSource
import cn.ellerotta.ktproto.client.uploadFile
import cn.ellerotta.ktproto.session.RpcException
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class ArticleMediaTest {
    private fun session(block: suspend (FakeTelegram, TestRepositories) -> Unit): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val repos = TestRepositories(Files.createTempDirectory("poc").toFile(), fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
                block(fake, repos)
            }
        }
    }

    private fun bytes(size: Int, seed: Int) = Random(seed).nextBytes(size)

    @Test
    fun eachKindUploadsAsItsMediaAndGoesOutById() = session { fake, repos ->
        repos.dialogs.dialogs()
        val photoBytes = bytes(40_000, 1)
        val progress = mutableListOf<Pair<Long, Long>>()
        val photo = repos.articles.upload(ArticleMediaKind.Photo, "cat.jpg", "image/jpeg", UploadSource.of(photoBytes)) { done, total -> progress += done to total }
        assertEquals(photoBytes.size.toLong() to photoBytes.size.toLong(), progress.last())
        val clip = repos.articles.upload(ArticleMediaKind.Video, "clip.mov", "video/quicktime", UploadSource.of(bytes(1_200_000, 2)), MediaFileInfo(width = 1280, height = 720, duration = 3.5))
        val song = repos.articles.upload(ArticleMediaKind.Audio, "song.mp3", "audio/mpeg", UploadSource.of(bytes(20_000, 3)), MediaFileInfo(duration = 200.4, title = "Song", performer = "Band"))
        val file = repos.articles.upload(ArticleMediaKind.File, "dot.png", "image/png", UploadSource.of(bytes(5_000, 4)))

        val calls = fake.articles.uploadMediaCalls
        assertEquals(List(4) { InputPeerSelf }, calls.map { it.peer })
        assertIs<InputMediaUploadedPhoto>(calls[0].media)
        assertContentEquals(photoBytes, fake.articles.uploadedBytes.getValue(FakeArticles.UPLOADED_MEDIA_ID))
        val video = assertIs<InputMediaUploadedDocument>(calls[1].media)
        assertEquals("video/mp4", video.mimeType)
        assertEquals(listOf(DocumentAttributeVideo(supportsStreaming = true, duration = 3.5, w = 1280, h = 720)), video.attributes)
        val audio = assertIs<InputMediaUploadedDocument>(calls[2].media)
        assertEquals("audio/mpeg", audio.mimeType)
        assertEquals(listOf(DocumentAttributeAudio(duration = 200, title = "Song", performer = "Band"), DocumentAttributeFilename("song.mp3")), audio.attributes)
        val plain = assertIs<InputMediaUploadedDocument>(calls[3].media)
        assertTrue(plain.forceFile)
        assertEquals("application/octet-stream", plain.mimeType)
        assertEquals(listOf(DocumentAttributeFilename("dot.png")), plain.attributes)

        val caption = ArticleCaption(ArticleText.Plain("a cat"))
        val article = ArticleMessage(listOf(ArticleBlock.Photo(photo, caption), ArticleBlock.Video(clip), ArticleBlock.Audio(song), ArticleBlock.Document(file)))
        repos.articles.send("self", article)
        val rich = assertIs<InputRichMessage>(assertNotNull(fake.lastSendMessage).richMessage)
        val ids = (0L..3L).map { FakeArticles.UPLOADED_MEDIA_ID + it }
        assertEquals(listOf(ids[0]), rich.photos!!.map { (it as InputPhoto).id })
        assertEquals(ids.drop(1), rich.documents!!.map { (it as InputDocument).id })
        val photoBlock = assertIs<PageBlockPhoto>(rich.blocks[0])
        assertEquals(ids[0], photoBlock.photoId)
        assertEquals(TextPlain("a cat"), photoBlock.caption.text)
        assertEquals(ids[1], assertIs<PageBlockVideo>(rich.blocks[1]).videoId)
        assertEquals(ids[2], assertIs<PageBlockAudio>(rich.blocks[2]).audioId)
        assertEquals(ids[3], assertIs<PageBlockDocument>(rich.blocks[3]).documentId)
        val back = assertNotNull(repos.messages.history("self").single { it.id == fake.lastSentId }.article)
        assertEquals(article.blocks.map { (it.media())?.key }, back.blocks.map { it.media()?.key })
        assertEquals(caption, (back.blocks[0] as ArticleBlock.Photo).caption)
    }

    private fun ArticleBlock.media() = when (this) {
        is ArticleBlock.Photo -> media
        is ArticleBlock.Video -> media
        is ArticleBlock.Audio -> media
        is ArticleBlock.Document -> media
        else -> null
    }

    @Test
    fun aDocumentBlockOfAnImageIsRefused() = session { fake, repos ->
        repos.dialogs.dialogs()
        val png = bytes(3_000, 5)
        val client = repos.telegram.client()
        var file: TypeInputFile? = null
        client.uploadFile("dot.png", UploadSource.of(png)).collect { if (it is Upload.Done) file = it.file }
        val uploaded = assertIs<MessageMediaDocument>(
            client.invoke(UploadMedia(peer = InputPeerSelf, media = InputMediaUploadedDocument(file = file!!, mimeType = "image/png", attributes = listOf(DocumentAttributeImageSize(16, 16), DocumentAttributeFilename("dot.png"))))),
        )
        val image = repos.telegram.peers.documentOf(uploaded, uploaded.document as Document, null)
        val refused = assertFailsWith<RpcException> { repos.articles.send("self", ArticleMessage(listOf(ArticleBlock.Document(image)))) }
        assertEquals("RICH_MESSAGE_DOCUMENT_INVALID", refused.errorMessage)
        val sent = fake.lastSentId
        val asFile = repos.articles.upload(ArticleMediaKind.File, "dot.png", "image/png", UploadSource.of(png))
        repos.articles.send("self", ArticleMessage(listOf(ArticleBlock.Document(asFile))))
        assertTrue(fake.lastSentId != sent)
        assertNull(assertIs<InputMediaUploadedDocument>(fake.articles.uploadMediaCalls.last().media).attributes.firstOrNull { it is DocumentAttributeImageSize })
    }

    @Test
    fun mediaCountTowardsTheServersLimit() = session { fake, repos ->
        repos.dialogs.dialogs()
        fake.articles.limits["rich_message_max_media"] = 1
        val first = repos.articles.upload(ArticleMediaKind.Photo, "a.jpg", "image/jpeg", UploadSource.of(bytes(1_000, 6)))
        val second = repos.articles.upload(ArticleMediaKind.Photo, "b.jpg", "image/jpeg", UploadSource.of(bytes(1_000, 7)))
        val two = ArticleMessage(listOf(ArticleBlock.Photo(first), ArticleBlock.Photo(second)))
        assertEquals("RICH_MESSAGE_MEDIA_TOO_MANY", assertFailsWith<RpcException> { repos.articles.send("self", two) }.errorMessage)
        repos.articles.send("self", ArticleMessage(listOf(ArticleBlock.Photo(first))))
    }
}
