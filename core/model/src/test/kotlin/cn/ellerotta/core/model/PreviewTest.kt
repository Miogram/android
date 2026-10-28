package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewTest {
    private val code = Preview(text = "Login code: 52814. Do not give this code to anyone, even if they say they are from Telegram!")

    @Test
    fun loginCodesInTheNotificationsChatAlwaysHide() {
        assertEquals(listOf(12..16), DialogItem(NOTIFICATIONS_KEY, "Telegram", code, unread = 1).listPreview.hidden)
        assertEquals(listOf(12..16), DialogItem(NOTIFICATIONS_KEY, "Telegram", code, unreadMark = true).listPreview.hidden)
        assertEquals(listOf(12..16), DialogItem(NOTIFICATIONS_KEY, "Telegram", code).listPreview.hidden)
        assertEquals(emptyList(), DialogItem("u42", "Bob", code, unread = 1).listPreview.hidden)
        val both = code.copy(hidden = listOf(0..4))
        assertEquals(listOf(0..4, 12..16), DialogItem(NOTIFICATIONS_KEY, "Telegram", both).listPreview.hidden)
    }

    @Test
    fun loginCodesAreFiveOrSixDigitsAlone() {
        assertEquals(listOf(0..4, 6..11), loginCodes("12345 123456"))
        assertEquals(emptyList(), loginCodes("1234 1234567 +79991234567 2026"))
        assertEquals(listOf(5..9), loginCodes("code:52814."))
    }

    @Test
    fun draftsHideTheirSpoilers() {
        val draft = Draft(FormattedText("ends badly", listOf(TextEntity(5, 5, EntityType.Spoiler), TextEntity(0, 4, EntityType.Bold))))
        assertEquals(listOf(5..9), DialogItem("u2", "Alice", draft = draft).listPreview.hidden)
    }
}
