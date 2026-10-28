package cn.ellerotta.core.model

import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedMediaTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun message(id: Int, date: Int = id, text: String = "", entities: List<TextEntity> = emptyList(), link: LinkPreview? = null) =
        MessageItem(id, text, out = false, date = date, entities = entities, link = link)

    @Test
    fun onlyTabsWithSomethingInThemShowInTheirOrder() {
        val counts = mapOf(SharedTab.Voice to 2, SharedTab.Media to 5, SharedTab.Files to 0, SharedTab.Groups to 1)
        assertEquals(listOf(SharedTab.Media, SharedTab.Voice, SharedTab.Groups), counts.visibleTabs())
        val all = SharedTab.entries.associateWith { 1 }
        assertEquals(listOf(SharedTab.Media, SharedTab.Files, SharedTab.Music, SharedTab.Voice, SharedTab.Links, SharedTab.Gifs, SharedTab.Groups), all.visibleTabs())
        assertTrue(emptyMap<SharedTab, Int>().visibleTabs().isEmpty())
    }

    @Test
    fun theChosenTabStaysWhileItIsVisibleElseTheFirstOneShows() {
        val shared = SharedMedia(counts = mapOf(SharedTab.Files to 3, SharedTab.Links to 1))
        assertEquals(SharedTab.Files, shared.tab)
        assertEquals(SharedTab.Links, shared.copy(chosen = SharedTab.Links).tab)
        assertEquals(SharedTab.Files, shared.copy(chosen = SharedTab.Music).tab)
        assertNull(SharedMedia().tab)
    }

    @Test
    fun pagesGoOnBelowTheOldestIdAndAShortPageEndsTheList() {
        val first = SharedList().appended(listOf(message(30), message(20), message(10)), limit = 3)
        assertFalse(first.complete)
        assertEquals(10, first.nextOffset())
        assertEquals(0, SharedList().nextOffset())
        val second = first.appended(listOf(message(10), message(5)), limit = 3)
        assertEquals(listOf(30, 20, 10, 5), second.items.map { it.id })
        assertTrue(second.complete)
    }

    @Test
    fun messagesGroupIntoRunsOfAMonthInTheirOrder() {
        val march = 1_773_000_000
        val feb = march - 30 * 86_400
        val runs = listOf(message(4, march + 10), message(3, march), message(2, feb), message(1, march - 400 * 86_400)).byMonth(utc)
        assertEquals(listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 2), YearMonth.of(2025, 2)), runs.map { it.first })
        assertEquals(listOf(listOf(4, 3), listOf(2), listOf(1)), runs.map { run -> run.second.map { it.id } })
        assertEquals(3, listOf(message(3, march), message(2, feb), message(1, march)).byMonth(utc).size)
    }

    @Test
    fun linksAreTheUrlEntitiesTheTextUrlsAndThePreviewOnceEach() {
        val text = "read https://example.org/a and this one, plus https://example.org/a again"
        val first = text.indexOf("https")
        val again = text.lastIndexOf("https")
        val entities = listOf(
            TextEntity(first, 21, EntityType.Url),
            TextEntity(text.indexOf("this one"), 8, EntityType.TextUrl("https://hidden.example/x")),
            TextEntity(again, 21, EntityType.Url),
            TextEntity(0, 4, EntityType.Bold),
        )
        val preview = LinkPreview("https://example.org/a", title = "A")
        assertEquals(listOf("https://example.org/a", "https://hidden.example/x"), message(1, text = text, entities = entities, link = preview).links())
        assertEquals(listOf("https://example.org/p"), message(2, link = LinkPreview("https://example.org/p")).links())
        assertTrue(message(3, text = "no links here").links().isEmpty())
        assertTrue(message(4, text = "short", entities = listOf(TextEntity(2, 30, EntityType.Url))).links().isEmpty())
    }

    @Test
    fun aBareHostGetsAScheme() {
        assertEquals("https://example.org/a", openableUrl("example.org/a"))
        assertEquals("http://example.org", openableUrl("http://example.org"))
        assertEquals("tg://resolve?domain=x", openableUrl("tg://resolve?domain=x"))
    }
}
