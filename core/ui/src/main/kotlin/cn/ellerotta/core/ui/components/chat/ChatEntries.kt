package cn.ellerotta.core.ui.components.chat

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.isVisual
import cn.ellerotta.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

internal sealed interface ChatEntry {
    val key: String

    data class Bubble(
        val message: MessageItem,
        val position: BubblePosition,
        val album: List<MessageItem> = emptyList(),
        override val key: String = album.firstOrNull()?.groupedId?.let { "g$it" } ?: "m${message.replacedDraft ?: message.id}",
    ) : ChatEntry {
        fun shows(id: Int?): Boolean = id != null && (message.id == id || album.any { it.id == id })
    }

    data class Day(val date: LocalDate) : ChatEntry {
        override val key = "d$date"
    }

    data object Unread : ChatEntry {
        override val key = "unread"
    }
}

internal fun entries(messages: List<MessageItem>, zone: ZoneId, unreadAfter: Int? = null): List<ChatEntry> {
    val firstUnread = unreadAfter?.let { after -> messages.firstOrNull { !it.out && it.id > after && !it.pending }?.id }
    fun day(m: MessageItem) = Instant.ofEpochSecond(m.date.toLong()).atZone(zone).toLocalDate()
    val groups = albums(messages)
    fun sameRun(upper: List<MessageItem>, lower: List<MessageItem>): Boolean {
        val a = upper.last()
        val b = lower.first()
        return a.out == b.out && a.senderId == b.senderId && a.forward == b.forward &&
            (a.service == null || a.call != null) && (b.service == null || b.call != null) && a.gift == null && b.gift == null &&
            upper.all { it.buttons.isEmpty() } && (a.ephemeral == null) == (b.ephemeral == null) &&
            kotlin.math.abs(a.date - b.date) < RUN_SECONDS && day(a) == day(b)
    }
    val out = mutableListOf<ChatEntry>()
    val keys = HashSet<String>()
    for (i in groups.indices.reversed()) {
        val group = groups[i]
        val before = groups.getOrNull(i - 1)
        val after = groups.getOrNull(i + 1)
        val position = BubblePosition(first = before == null || !sameRun(before, group), last = after == null || !sameRun(group, after))
        val bubble = if (group.size == 1) {
            ChatEntry.Bubble(group.single(), position)
        } else {
            ChatEntry.Bubble(group.firstOrNull { it.text.isNotEmpty() } ?: group.first(), position, group)
        }
        out += if (keys.add(bubble.key)) bubble else bubble.copy(key = "m${bubble.message.id}").also { keys.add(it.key) }
        if (group.any { it.id == firstUnread }) out += ChatEntry.Unread
        val first = group.first()
        if (before == null || day(before.last()) != day(first)) out += ChatEntry.Day(day(first))
    }
    return out
}

internal fun albums(messages: List<MessageItem>): List<List<MessageItem>> {
    val out = mutableListOf<MutableList<MessageItem>>()
    for (message in messages) {
        val last = out.lastOrNull()?.last()
        val album = message.groupedId
        val kind = albumKind(message)
        if (album != null && kind != null && last != null && last.groupedId == album && last.out == message.out && albumKind(last) == kind) {
            out.last() += message
        } else {
            out += mutableListOf(message)
        }
    }
    return out
}

private enum class AlbumKind { Visual, File, Music }

private fun albumKind(message: MessageItem): AlbumKind? {
    val media = message.media ?: return null
    return when {
        media.isVisual -> AlbumKind.Visual
        media.kind == MediaKind.File -> AlbumKind.File
        media.kind == MediaKind.Music -> AlbumKind.Music
        else -> null
    }
}

private const val RUN_SECONDS = 10 * 60

@Composable
internal fun dayText(date: LocalDate, zone: ZoneId): String {
    val today = LocalDate.now(zone)
    val locale = LocalConfiguration.current.locales[0]
    val ago = ChronoUnit.DAYS.between(date, today)
    return when {
        ago == 0L -> stringResource(R.string.chat_today)
        ago == 1L -> stringResource(R.string.chat_yesterday)
        else -> remember(date, today, locale) { dayLabel(date, today, locale) }
    }
}

internal fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale): String {
    val ago = ChronoUnit.DAYS.between(date, today)
    if (ago in 2..6) return date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
    val skeleton = if (date.year == today.year) "MMMMd" else "yMMMMd"
    return date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}
