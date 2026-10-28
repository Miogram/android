package cn.ellerotta.core.ui.components.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.ui.R
import cn.ellerotta.core.ui.components.auth.PhoneNumbers
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun formatPhone(context: Context, digits: String): String {
    val numbers = PhoneNumbers.get(context)
    val (code, national) = numbers.splitCode(digits.filter(Char::isDigit))
    return numbers.international(code, national)
}

fun isBirthdayToday(birthday: Birthday, today: LocalDate = LocalDate.now()): Boolean = birthday.day == today.dayOfMonth && birthday.month == today.monthValue

@Composable
fun birthdayText(birthday: Birthday, locale: Locale, today: LocalDate = LocalDate.now()): String {
    val month = Month.of(birthday.month.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, locale)
    val date = "${birthday.day} $month"
    val year = birthday.year ?: return date
    var age = today.year - year
    if (today.monthValue < birthday.month || (today.monthValue == birthday.month && today.dayOfMonth < birthday.day)) age--
    return stringResource(R.string.profile_birthday_age, "$date $year", pluralStringResource(R.plurals.profile_years, age, age))
}

@Composable
fun rememberProfileNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}
