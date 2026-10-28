package app.miogram

import app.miogram.data.telegram.previewOf
import cn.ellerotta.ktproto.api.types.Message
import cn.ellerotta.ktproto.api.types.MessageEntityBold
import cn.ellerotta.ktproto.api.types.MessageEntitySpoiler
import cn.ellerotta.ktproto.api.types.PeerUser
import kotlin.test.assertEquals
import org.junit.Test

class PreviewMappingTest {
    @Test
    fun spoilersOfTheLastMessageStayHiddenInTheList() {
        val text = "it was the butler all along"
        val message = Message(
            id = 1, peerId = PeerUser(2), date = 0, message = text,
            entities = listOf(MessageEntityBold(0, 2), MessageEntitySpoiler(11, 6), MessageEntitySpoiler(18, 3)),
        )
        val preview = previewOf(message, sender = null)
        assertEquals(text, preview.text)
        assertEquals(listOf(11..16, 18..20), preview.hidden)
        assertEquals(emptyList(), previewOf(Message(id = 2, peerId = PeerUser(2), date = 0, message = text), sender = null).hidden)
    }
}
