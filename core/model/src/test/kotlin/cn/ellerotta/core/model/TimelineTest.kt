package cn.ellerotta.core.model

import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

class TimelineTest {
    private fun message(id: Int, date: Int, streaming: Boolean = false, ephemeral: Int? = null) =
        MessageItem(id, "m$id", out = false, date = date, streaming = streaming, ephemeral = ephemeral?.let { EphemeralInfo(it, "u504") })

    @Test
    fun streamedDraftsComeLast() {
        val messages = listOf(message(1, 10), message(2, 20))
        assertSame(messages, timelineOf(messages, emptyList(), emptyList()))
        assertEquals(listOf(1, 2, -1), timelineOf(messages, emptyList(), listOf(message(-1, 5, streaming = true))).map { it.id })
    }

    @Test
    fun ephemeralMessagesGoInByDate() {
        val messages = listOf(message(1, 10), message(2, 20), message(3, 30))
        val early = message(EPHEMERAL_IDS + 7, 5, ephemeral = 7)
        val sameSecond = message(EPHEMERAL_IDS + 8, 20, ephemeral = 8)
        val late = message(EPHEMERAL_IDS + 9, 40, ephemeral = 9)
        assertEquals(
            listOf(EPHEMERAL_IDS + 7, 1, 2, EPHEMERAL_IDS + 8, 3, EPHEMERAL_IDS + 9, -1),
            timelineOf(messages, listOf(late, sameSecond, early), listOf(message(-1, 1, streaming = true))).map { it.id },
        )
        assertEquals(listOf(EPHEMERAL_IDS + 7), timelineOf(emptyList(), listOf(early), emptyList()).map { it.id })
    }

    @Test
    fun appConfigReadsNumbersWithDefaults() {
        assertEquals(12, AppConfig(mapOf("message_typing_draft_ttl" to 12.0)).typingDraftTtl)
        assertEquals(AppConfig.DEFAULT_TYPING_DRAFT_TTL, AppConfig().typingDraftTtl)
        assertEquals(AppConfig.DEFAULT_TYPING_DRAFT_TTL, AppConfig(mapOf("message_typing_draft_ttl" to "soon")).typingDraftTtl)
    }
}
