package cn.ellerotta.core.ui.components.auth

import cn.ellerotta.core.model.CodeRequest
import cn.ellerotta.core.model.CodeType
import cn.ellerotta.core.model.ConfigSummary
import cn.ellerotta.core.ui.R

internal object AuthPreviewData {
    private const val PHONE = "4915112345678"
    private const val TEST_PHONE = "9996621234"

    val appCode = CodeRequest(phone = PHONE, hash = "preview", codeLength = 5, homeDc = 2, type = CodeType.App, nextType = CodeType.Sms)

    fun smsCode(testNumber: Boolean = false): CodeRequest = CodeRequest(
        phone = if (testNumber) TEST_PHONE else PHONE,
        hash = "preview",
        codeLength = 5,
        homeDc = 2,
        type = CodeType.Sms,
        nextType = CodeType.Call,
        timeoutSeconds = 120,
        sentAtMillis = System.currentTimeMillis(),
    )

    val missedCallCode = CodeRequest(phone = PHONE, hash = "preview", codeLength = 4, homeDc = 2, type = CodeType.MissedCall)

    const val PASSWORD_HINT = "the usual one"

    const val QR_URL = "tg://login?token=AQGcXkFnAAAAAOxCyWkzPq3hcLb8bH0iUvF_q1mD6R2y5w"

    val countries = listOf(
        Country("AR", 54, "Argentina"),
        Country("AU", 61, "Australia"),
        Country("BR", 55, "Brazil"),
        Country("CA", 1, "Canada"),
        Country("DE", 49, "Germany"),
        Country("ES", 34, "Spain"),
        Country("FR", 33, "France"),
        Country("GB", 44, "United Kingdom"),
        Country("IT", 39, "Italy"),
        Country("JP", 81, "Japan"),
        Country("KZ", 7, "Kazakhstan"),
        Country("NL", 31, "Netherlands"),
        Country("US", 1, "United States"),
    )

    const val API_ID = "12345"
    const val API_HASH = "0123456789abcdef0123456789abcdef"
    const val VERSION = "0.1.0"

    val config = ConfigSummary(
        thisDc = 2,
        testMode = true,
        dcs = listOf("dc1 149.154.175.10:443", "dc2 149.154.167.40:443", "dc3 149.154.175.117:443"),
    )

    const val RESOLUTION_FAILURE = "The saved login could not be read: no space left on the device"

    val errors = listOf(
        R.string.error_phone_invalid,
        R.string.error_phone_flood,
        R.string.error_code_expired,
        R.string.error_api_missing,
    )
}
