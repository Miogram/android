package app.miogram

import app.miogram.data.telegram.SignInResult
import cn.ellerotta.ktproto.api.types.Document
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageMediaDocument
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.PhotoStrippedSize
import cn.ellerotta.ktproto.client.expandStrippedThumbnail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationMediaTest {
    private fun incoming(id: Int, media: cn.ellerotta.ktproto.api.types.TypeMessageMedia?, text: String = "") =
        Message(id = id, peerId = PeerUser(501), fromId = PeerUser(501), date = 1_700_000_000, message = text, media = media)

    @Test
    fun aMessageGivesItsSmallestImageAndNothingElseIsFetched(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            withTimeout(90_000) {
                val repos = TestRepositories(Files.createTempDirectory("poc").toFile(), fake)
                repos.telegram.connect(12345, "hash")
                val request = repos.auth.sendCode("+999 66 1 4242")
                assertIs<SignInResult.LoggedIn>(repos.auth.signIn(request, "11111"))
                val images = repos.notificationMedia
                val stripped = fake.photo.sizes.filterIsInstance<PhotoStrippedSize>().single()
                val small = Photo(id = FakeTelegram.PHOTO_ID, accessHash = 1, fileReference = byteArrayOf(4), date = 1_700_000_000, dcId = 2, sizes = listOf(stripped, PhotoSize("m", 200, 150, fake.photoSizes.getValue("m").size)))
                val broken = Photo(id = FakeTelegram.PHOTO_ID, accessHash = 1, fileReference = byteArrayOf(4), date = 1_700_000_000, dcId = 2, sizes = listOf(stripped, PhotoSize("y", 500, 375, 5_000)))
                val dog = fake.stickerDocuments().first { it.id == FakeTelegram.DOG_ID }
                val video = fake.videoDocument()
                fake.incoming += listOf(
                    incoming(1, MessageMediaPhoto(photo = fake.photo)),
                    incoming(2, MessageMediaDocument(document = dog)),
                    incoming(3, MessageMediaDocument(video = true, document = Document(id = video.id, accessHash = video.accessHash, fileReference = video.fileReference, date = video.date, mimeType = video.mimeType, size = video.size, thumbs = listOf(PhotoSize("m", 320, 180, fake.videoThumbBytes.size)), dcId = video.dcId, attributes = video.attributes))),
                    incoming(4, MessageMediaPhoto(spoiler = true, photo = fake.photo)),
                    incoming(5, MessageMediaPhoto(ttlSeconds = 10, photo = fake.photo)),
                    incoming(6, null, "just words"),
                    incoming(7, MessageMediaPhoto(photo = small)),
                    incoming(8, MessageMediaPhoto(photo = broken)),
                )

                val photo = assertNotNull(images.image("u501", 1))
                assertEquals("image/jpeg", photo.mime)
                assertContentEquals(fake.photoSizes.getValue("m"), photo.bytes)
                assertEquals(listOf(listOf(1)), fake.getMessagesCalls.toList())
                assertEquals(listOf("m"), fake.photoTypes.toList())

                assertContentEquals(fake.photoSizes.getValue("m"), assertNotNull(images.image("u501", 7)).bytes)

                val blur = assertNotNull(images.image("u501", 8))
                assertTrue(blur.bytes.size < fake.photoSizes.getValue("m").size)
                assertContentEquals(expandStrippedThumbnail(stripped.bytes), blur.bytes)

                val sticker = assertNotNull(images.image("u501", 2))
                assertEquals("image/webp", sticker.mime)
                assertContentEquals(fake.webpBytes, sticker.bytes)

                val thumb = assertNotNull(images.image("u501", 3))
                assertEquals("image/jpeg", thumb.mime)
                assertContentEquals(fake.videoThumbBytes, thumb.bytes)

                assertNull(images.image("u501", 4))
                assertNull(images.image("u501", 5))
                assertNull(images.image("u501", 6))
                assertNull(images.image("u501", 99))
            }
        }
    }
}
