package cn.ellerotta.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileEditTest {
    private val saved = ProfileDetails(
        key = "u1", id = 1, name = "Ann Lee", kind = PeerKind.Saved, firstName = "Ann", lastName = "Lee", about = "hello",
        usernameList = listOf(OwnUsername("annlee", active = true, editable = true), OwnUsername("fragment", active = true, editable = false)),
        birthday = Birthday(5, 2, 1990), isSelf = true,
    )

    @Test
    fun usernamesFollowTelegramsRules() {
        assertNull(ProfileRules.usernameProblem(""))
        assertNull(ProfileRules.usernameProblem("ann_lee_99"))
        assertEquals(OwnUsernameProblem.TooShort, ProfileRules.usernameProblem("ann"))
        assertEquals(OwnUsernameProblem.TooLong, ProfileRules.usernameProblem("a".repeat(33)))
        assertNull(ProfileRules.usernameProblem("a".repeat(32)))
        assertEquals(OwnUsernameProblem.BadSymbols, ProfileRules.usernameProblem("ann-lee"))
        assertEquals(OwnUsernameProblem.BadSymbols, ProfileRules.usernameProblem("ännlee"))
        assertEquals(OwnUsernameProblem.StartsWithDigit, ProfileRules.usernameProblem("1annlee"))
        assertEquals(OwnUsernameProblem.BadSymbols, ProfileRules.usernameProblem("a-"))
    }

    @Test
    fun birthdaysAreRealDates() {
        assertTrue(ProfileRules.validBirthday(Birthday(29, 2, null)))
        assertTrue(ProfileRules.validBirthday(Birthday(29, 2, 2000)))
        assertFalse(ProfileRules.validBirthday(Birthday(29, 2, 1900)))
        assertFalse(ProfileRules.validBirthday(Birthday(31, 4, 1990)))
        assertFalse(ProfileRules.validBirthday(Birthday(1, 13, null)))
        assertFalse(ProfileRules.validBirthday(Birthday(0, 1, null)))
        assertFalse(ProfileRules.validBirthday(Birthday(1, 1, 1850)))
    }

    @Test
    fun nothingTypedMeansNothingToSave() {
        val edit = ProfileEdit(saved)
        assertFalse(edit.changed)
        assertFalse(edit.canSave)
        assertEquals("annlee", edit.username)
        assertFalse(edit.copy(firstName = " Ann ", about = "hello ").changed)
    }

    @Test
    fun eachFieldMakesAChange() {
        val edit = ProfileEdit(saved)
        assertTrue(edit.copy(firstName = "Anna").nameChanged)
        assertTrue(edit.copy(lastName = "").nameChanged)
        assertTrue(edit.copy(about = "").aboutChanged)
        assertTrue(edit.copy(username = "").usernameChanged)
        assertTrue(edit.copy(birthday = null).birthdayChanged)
        assertTrue(edit.copy(channel = PersonalChannel("ch5", "News")).channelChanged)
        assertTrue(edit.copy(firstName = "Anna").canSave)
    }

    @Test
    fun invalidInputKeepsSaveOff() {
        val edit = ProfileEdit(saved, aboutLimit = 10)
        assertFalse(edit.copy(firstName = "  ").canSave)
        assertFalse(edit.copy(about = "x".repeat(11)).canSave)
        assertTrue(edit.copy(about = "x".repeat(10)).canSave)
        assertFalse(edit.copy(birthday = Birthday(30, 2, null)).canSave)
        assertFalse(edit.copy(lastName = "l".repeat(65)).canSave)
    }

    @Test
    fun aTypedUsernameNeedsAFreeVerdict() {
        val typed = ProfileEdit(saved, username = "newname")
        assertFalse(typed.copy(usernameCheck = UsernameCheck.Checking).canSave)
        assertFalse(typed.copy(usernameCheck = UsernameCheck.Taken).canSave)
        assertFalse(typed.copy(usernameCheck = UsernameCheck.ForSale).canSave)
        assertFalse(typed.copy(usernameCheck = UsernameCheck.Invalid(null)).canSave)
        assertTrue(typed.copy(usernameCheck = UsernameCheck.Free).canSave)
        assertTrue(typed.copy(usernameCheck = UsernameCheck.Unknown).canSave)
        assertFalse(typed.copy(username = "ab", usernameCheck = UsernameCheck.Free).canSave)
        assertTrue(typed.copy(username = "").canSave)
        assertFalse(ProfileEdit(saved, usernameCheck = UsernameCheck.Taken).canSave)
    }

    @Test
    fun theBioLimitComesFromTheConfig() {
        assertEquals(70, AppConfig().aboutLimit(premium = false))
        assertEquals(140, AppConfig().aboutLimit(premium = true))
        val config = AppConfig(mapOf("about_length_limit_default" to 80.0, "about_length_limit_premium" to 200.0))
        assertEquals(80, config.aboutLimit(premium = false))
        assertEquals(200, config.aboutLimit(premium = true))
    }
}
