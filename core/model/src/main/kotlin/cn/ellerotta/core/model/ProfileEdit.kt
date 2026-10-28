package cn.ellerotta.core.model

data class OwnUsername(val username: String, val active: Boolean, val editable: Boolean)

enum class OwnUsernameProblem { TooShort, TooLong, BadSymbols, StartsWithDigit }

sealed interface UsernameCheck {
    data object Idle : UsernameCheck

    data object Checking : UsernameCheck

    data object Free : UsernameCheck

    data object Taken : UsernameCheck

    data object ForSale : UsernameCheck

    data class Invalid(val problem: OwnUsernameProblem?) : UsernameCheck

    data object Unknown : UsernameCheck
}

object ProfileRules {
    const val NAME_MAX = 64
    const val USERNAME_MIN = 5
    const val USERNAME_MAX = 32
    const val DEFAULT_ABOUT_LIMIT = 70
    const val PREMIUM_ABOUT_LIMIT = 140

    fun usernameProblem(username: String): OwnUsernameProblem? = when {
        username.isEmpty() -> null
        username.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '_' } -> OwnUsernameProblem.BadSymbols
        username.first() in '0'..'9' -> OwnUsernameProblem.StartsWithDigit
        username.length < USERNAME_MIN -> OwnUsernameProblem.TooShort
        username.length > USERNAME_MAX -> OwnUsernameProblem.TooLong
        else -> null
    }

    fun daysIn(month: Int, year: Int?): Int = when (month) {
        2 -> if (year == null || (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun validBirthday(birthday: Birthday): Boolean =
        birthday.month in 1..12 && birthday.day in 1..daysIn(birthday.month, birthday.year) && (birthday.year?.let { it in 1900..9999 } ?: true)
}

data class ProfileEdit(
    val saved: ProfileDetails,
    val firstName: String = saved.firstName,
    val lastName: String = saved.lastName,
    val about: String = saved.about.orEmpty(),
    val username: String = saved.usernameList.firstOrNull { it.editable }?.username.orEmpty(),
    val birthday: Birthday? = saved.birthday,
    val channel: PersonalChannel? = saved.channel,
    val aboutLimit: Int = ProfileRules.DEFAULT_ABOUT_LIMIT,
    val usernameCheck: UsernameCheck = UsernameCheck.Idle,
) {
    val savedUsername: String get() = saved.usernameList.firstOrNull { it.editable }?.username.orEmpty()

    val nameChanged: Boolean get() = firstName.trim() != saved.firstName || lastName.trim() != saved.lastName
    val aboutChanged: Boolean get() = about.trim() != saved.about.orEmpty()
    val usernameChanged: Boolean get() = username != savedUsername
    val birthdayChanged: Boolean get() = birthday != saved.birthday
    val channelChanged: Boolean get() = channel?.key != saved.channel?.key

    val changed: Boolean get() = nameChanged || aboutChanged || usernameChanged || birthdayChanged || channelChanged

    val valid: Boolean
        get() = firstName.isNotBlank() && firstName.trim().length <= ProfileRules.NAME_MAX && lastName.trim().length <= ProfileRules.NAME_MAX &&
            about.trim().length <= aboutLimit && usernameOk && (birthday?.let(ProfileRules::validBirthday) ?: true)

    val usernameOk: Boolean
        get() = !usernameChanged || username.isEmpty() || (ProfileRules.usernameProblem(username) == null &&
            (usernameCheck == UsernameCheck.Free || usernameCheck == UsernameCheck.Unknown))

    val canSave: Boolean get() = changed && valid
}
