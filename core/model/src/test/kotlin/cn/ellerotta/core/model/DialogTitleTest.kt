package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class DialogTitleTest {
    private val stranger = DialogItem("u1", "Ada", kind = PeerKind.User, contact = false, phone = "79991234567")

    @Test
    fun aStrangerWithANumberIsTitledByIt() {
        assertEquals("79991234567", stranger.phoneTitle)
    }

    @Test
    fun contactsBotsSupportSavedMessagesAndHiddenNumbersKeepTheirNames() {
        assertNull(stranger.copy(contact = true).phoneTitle)
        assertNull(stranger.copy(kind = PeerKind.Bot).phoneTitle)
        assertNull(stranger.copy(support = true).phoneTitle)
        assertNull(stranger.copy(kind = PeerKind.Saved, isSaved = true).phoneTitle)
        assertNull(stranger.copy(phone = null).phoneTitle)
        assertNull(stranger.copy(phone = "").phoneTitle)
        assertNull(stranger.copy(kind = PeerKind.Group).phoneTitle)
    }
}
