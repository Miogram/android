package cn.ellerotta.core.ui.components.chats

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

fun formatChatDate(epochSeconds: Int, nowMillis: Long, locale: Locale, use24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    if (epochSeconds <= 0) return ""
    val date = Instant.ofEpochSecond(epochSeconds.toLong()).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val days = ChronoUnit.DAYS.between(date.toLocalDate(), now.toLocalDate())
    return when {
        days == 0L -> date.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))
        days in 1..6 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        date.year == now.year -> date.format(DateTimeFormatter.ofPattern(if (locale.language == "en") "MMM d" else "d MMM", locale))
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
    }
}
