package cn.ellerotta.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class SharedTab(val pageSize: Int) {
    Media(60),
    Files(30),
    Music(30),
    Voice(30),
    Links(30),
    Gifs(60),
    Groups(50),
}

fun Map<SharedTab, Int>.visibleTabs(): List<SharedTab> = SharedTab.entries.filter { (this[it] ?: 0) > 0 }

data class SharedList(
    val items: List<MessageItem> = emptyList(),
    val complete: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

data class SharedMedia(
    val counts: Map<SharedTab, Int> = emptyMap(),
    val chosen: SharedTab? = null,
    val lists: Map<SharedTab, SharedList> = emptyMap(),
    val common: List<DialogItem> = emptyList(),
    val commonComplete: Boolean = false,
    val commonLoading: Boolean = false,
) {
    val tabs: List<SharedTab> get() = counts.visibleTabs()

    val tab: SharedTab? get() = chosen?.takeIf { it in tabs } ?: tabs.firstOrNull()

    fun list(tab: SharedTab): SharedList = lists[tab] ?: SharedList()
}

fun SharedList.nextOffset(): Int = items.minOfOrNull { it.id } ?: 0

fun SharedList.appended(more: List<MessageItem>, limit: Int): SharedList =
    SharedList(items = (items + more).distinctBy { it.id }.sortedByDescending { it.id }, complete = more.size < limit)

fun monthOf(date: Int, zone: ZoneId = ZoneId.systemDefault()): YearMonth = YearMonth.from(Instant.ofEpochSecond(date.toLong()).atZone(zone))

fun List<MessageItem>.byMonth(zone: ZoneId = ZoneId.systemDefault()): List<Pair<YearMonth, List<MessageItem>>> {
    val runs = mutableListOf<Pair<YearMonth, MutableList<MessageItem>>>()
    for (item in this) {
        val month = monthOf(item.date, zone)
        if (runs.lastOrNull()?.first == month) runs.last().second += item else runs += month to mutableListOf(item)
    }
    return runs
}

fun MessageItem.links(): List<String> {
    val found = entities.mapNotNull { entity ->
        when (val type = entity.type) {
            is EntityType.Url -> if (entity.end <= text.length) entity.part(text) else null
            is EntityType.TextUrl -> type.url
            else -> null
        }
    }
    return (found + listOfNotNull(link?.url)).distinct()
}

fun openableUrl(url: String): String = if (url.contains("://") || url.startsWith("mailto:") || url.startsWith("tg:")) url else "https://$url"
