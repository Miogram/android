package app.miogram

import app.miogram.data.telegram.PeerRegistry
import app.miogram.data.telegram.itemOf
import cn.ellerotta.ktproto.api.types.EmojiStatus
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.PeerChat
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.MessageMediaPhoto
import cn.ellerotta.ktproto.api.types.Photo
import cn.ellerotta.ktproto.api.types.PhotoSize
import cn.ellerotta.ktproto.api.types.User
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class MessageMappingTest {
    @Test
    fun othersMessagesInGroupsCarryTheSendersTagAndStatus() {
        val registry = PeerRegistry { null }
        val now = (System.currentTimeMillis() / 1000).toInt()
        registry.rememberUsers(
            listOf(
                User(id = 7, firstName = "Bob", emojiStatus = EmojiStatus(documentId = 555)),
                User(id = 8, firstName = "Carol", emojiStatus = EmojiStatus(documentId = 556, until = now - 60)),
            ),
        )
        fun message(id: Int, from: Long, out: Boolean = false, rank: String? = null) =
            Message(id = id, peerId = PeerChat(5), date = now, message = "hi", out = out, fromId = PeerUser(from), fromRank = rank)

        val bob = assertNotNull(registry.itemOf(message(1, 7, rank = "editor"), "c5"))
        assertEquals("Bob", bob.senderName)
        assertEquals("editor", bob.senderRank)
        assertEquals(555L, bob.senderEmojiStatus)
        val carol = assertNotNull(registry.itemOf(message(2, 8, rank = " "), "c5"))
        assertNull(carol.senderEmojiStatus)
        assertNull(carol.senderRank)
        assertNull(registry.itemOf(message(3, 7, out = true, rank = "editor"), "c5")?.senderRank)
        assertNull(registry.itemOf(message(4, 7, rank = "editor"), "u7")?.senderEmojiStatus)
        registry.rememberUsers(listOf(User(id = 7, min = true, firstName = "Bob")))
        assertEquals(555L, registry.itemOf(message(5, 7), "c5")?.senderEmojiStatus)
    }

    @Test
    fun mediaSentUnderASpoilerIsCovered() {
        val registry = PeerRegistry { null }
        val photo = Photo(id = 9, accessHash = 1, fileReference = ByteArray(0), date = 0, sizes = listOf(PhotoSize(type = "m", w = 320, h = 240, size = 1000)), dcId = 2)
        fun message(id: Int, spoiler: Boolean) = Message(id = id, peerId = PeerUser(7), date = 0, message = "", media = MessageMediaPhoto(spoiler = spoiler, photo = photo))
        assertEquals(true, registry.itemOf(message(1, spoiler = true), "u7")?.media?.spoiler)
        assertEquals(false, registry.itemOf(message(2, spoiler = false), "u7")?.media?.spoiler)
    }
}
