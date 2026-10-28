package cn.ellerotta.core.ui.components.auth

import android.content.Context
import android.telephony.TelephonyManager
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.text.Collator
import java.util.Locale

data class Country(val region: String, val code: Int, val name: String) {
    val flag: String
        get() = if (region.length == 2 && region.all { it in 'A'..'Z' }) {
            region.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
        } else {
            "🧪"
        }
}

class PhoneNumbers private constructor(private val util: PhoneNumberUtil) {
    fun isCode(code: Int): Boolean = code == TEST_CODE || util.getRegionCodeForCountryCode(code) != UNKNOWN

    fun countries(locale: Locale): List<Country> {
        val collator = Collator.getInstance(locale)
        return util.supportedRegions
            .map { Country(it, util.getCountryCodeForRegion(it), Locale.Builder().setRegion(it).build().getDisplayCountry(locale)) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun country(code: Int, locale: Locale, testName: String): Country? = when {
        code == TEST_CODE -> Country("", TEST_CODE, testName)
        else -> util.getRegionCodeForCountryCode(code).takeIf { it != UNKNOWN }
            ?.let { Country(it, code, Locale.Builder().setRegion(it).build().getDisplayCountry(locale)) }
    }

    fun splitCode(digits: String): Pair<String, String> {
        for (length in 1..minOf(3, digits.length)) {
            if (isCode(digits.take(length).toInt())) return digits.take(length) to digits.drop(length)
        }
        return digits.take(4) to digits.drop(4)
    }

    fun format(code: String, national: String): String {
        if (national.isEmpty()) return ""
        if (code.toIntOrNull() == TEST_CODE) return testFormat(national)
        val region = code.toIntOrNull()?.let { util.getRegionCodeForCountryCode(it) } ?: UNKNOWN
        if (region == UNKNOWN) return national
        val formatter = util.getAsYouTypeFormatter(region)
        var out = ""
        for (c in "+$code$national") out = formatter.inputDigit(c)
        val prefix = "+$code"
        val formatted = out.removePrefix(prefix).trim()
        return if (formatted.filter(Char::isDigit) == national) formatted else national
    }

    fun mask(code: String): String? {
        val number = code.toIntOrNull() ?: return null
        if (number == TEST_CODE) return "00 0 0000"
        val region = util.getRegionCodeForCountryCode(number).takeIf { it != UNKNOWN } ?: return null
        val example = util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE) ?: return null
        val national = util.getNationalSignificantNumber(example)
        return format(code, national).map { if (it.isDigit()) '0' else it }.joinToString("")
    }

    fun isPossible(code: String, national: String): Boolean {
        if (code.toIntOrNull() == TEST_CODE) return national.length == 7 && national.startsWith("66")
        return try {
            util.isPossibleNumber(util.parse("+$code$national", UNKNOWN))
        } catch (_: NumberParseException) {
            false
        }
    }

    fun international(code: String, national: String): String = "+$code ${format(code, national)}".trim()

    fun defaultCode(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val region = listOfNotNull(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
            .map { it.uppercase(Locale.ROOT) }
            .firstOrNull { it.length == 2 && util.getCountryCodeForRegion(it) != 0 }
        return (region?.let { util.getCountryCodeForRegion(it) } ?: 1).toString()
    }

    private fun testFormat(national: String): String = buildString {
        national.forEachIndexed { i, c ->
            if (i == 2 || i == 3) append(' ')
            append(c)
        }
    }

    companion object {
        const val TEST_CODE = 999
        private const val UNKNOWN = "ZZ"

        @Volatile
        private var instance: PhoneNumbers? = null

        fun get(context: Context): PhoneNumbers = instance ?: synchronized(this) {
            instance ?: PhoneNumbers(PhoneNumberUtil.createInstance(context.applicationContext)).also { instance = it }
        }
    }
}
