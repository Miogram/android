package cn.ellerotta.core.model

data class CreatedChat(val key: String, val title: String)

data class MissingInvitee(val userKey: String, val name: String, val premiumWouldAllow: Boolean = false)

enum class UsernameAvailability { Available, Taken, Invalid, TooManyPublic }

sealed interface UsernameStatus {
    data object Idle : UsernameStatus
    data object Checking : UsernameStatus
    data class Problem(val problem: UsernameProblem) : UsernameStatus
    data class Checked(val username: String, val availability: UsernameAvailability) : UsernameStatus
}

enum class UsernameProblem { TooShort, TooLong, BadStart, BadCharacter, BadEnd }

const val USERNAME_MIN = 4
const val USERNAME_MAX = 32

fun usernameProblem(username: String): UsernameProblem? = when {
    username.length < USERNAME_MIN -> UsernameProblem.TooShort
    username.length > USERNAME_MAX -> UsernameProblem.TooLong
    !username[0].isAsciiLetter() -> UsernameProblem.BadStart
    !username.all { it.isAsciiLetter() || it in '0'..'9' || it == '_' } -> UsernameProblem.BadCharacter
    username.last() == '_' -> UsernameProblem.BadEnd
    else -> null
}

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

const val CHAT_TITLE_MAX = 128
const val CHAT_ABOUT_MAX = 255

fun chatTitleOf(name: String): String = name.trim().take(CHAT_TITLE_MAX)

fun pickableContacts(contacts: List<ContactItem>, query: String): List<ContactItem> = contacts.filter { it.matches(query) }

fun toggled(selected: List<String>, key: String): List<String> = if (key in selected) selected - key else selected + key
