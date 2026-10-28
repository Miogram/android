package cn.ellerotta.core.ui.components.profile

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.Presence
import cn.ellerotta.core.ui.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

@Composable
fun presenceText(presence: Presence, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    return when (presence) {
        Presence.Online -> stringResource(R.string.presence_online)
        Presence.Recently -> stringResource(R.string.presence_recently)
        Presence.LastWeek -> stringResource(R.string.presence_week)
        Presence.LastMonth -> stringResource(R.string.presence_month)
        Presence.LongAgo -> stringResource(R.string.presence_long_ago)
        Presence.Bot -> stringResource(R.string.presence_bot)
        Presence.Unknown -> ""
        is Presence.Members -> {
            val members = pluralStringResource(if (presence.channel) R.plurals.presence_subscribers else R.plurals.presence_members, presence.count, presence.count)
            val online = presence.online
            if (online != null && online > 1) stringResource(R.string.presence_members_online, members, online) else members
        }
        is Presence.Offline -> {
            val seconds = nowMillis / 1000 - presence.wasOnline
            val seen = Instant.ofEpochSecond(presence.wasOnline.toLong()).atZone(zone)
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val days = ChronoUnit.DAYS.between(seen.toLocalDate(), now.toLocalDate())
            val time = seen.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale))
            when {
                seconds < 60 -> stringResource(R.string.presence_just_now)
                seconds < 3600 -> pluralStringResource(R.plurals.presence_minutes, (seconds / 60).toInt(), (seconds / 60).toInt())
                days == 0L -> pluralStringResource(R.plurals.presence_hours, (seconds / 3600).toInt(), (seconds / 3600).toInt())
                days == 1L -> stringResource(R.string.presence_yesterday, time)
                else -> stringResource(R.string.presence_date, seen.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)))
            }
        }
    }
}
