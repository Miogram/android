package cn.ellerotta.core.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import cn.ellerotta.core.model.Birthday
import cn.ellerotta.core.model.CropRect
import cn.ellerotta.core.model.OwnUsername
import cn.ellerotta.core.model.PeerKind
import cn.ellerotta.core.model.PersonalChannel
import cn.ellerotta.core.model.ProfileDetails
import cn.ellerotta.core.model.ProfileEdit
import cn.ellerotta.core.model.UsernameCheck
import cn.ellerotta.core.model.OwnUsernameProblem
import cn.ellerotta.core.ui.components.profile.AvatarCropScreen
import cn.ellerotta.core.ui.components.profile.EditProfileActions
import cn.ellerotta.core.ui.components.profile.EditProfileScreen
import cn.ellerotta.core.ui.theme.MioTheme
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditProfileTest {
    @get:Rule
    val compose = createComposeRule()

    private val saved = ProfileDetails(
        key = "u1", id = 1, name = "Ann Lee", kind = PeerKind.Saved, firstName = "Ann", lastName = "Lee", about = "hello", isSelf = true,
        usernameList = listOf(OwnUsername("annlee", true, true), OwnUsername("frag_one", true, false), OwnUsername("frag_two", true, false), OwnUsername("frag_off", false, false)),
        birthday = Birthday(5, 2, 1990),
    )

    private fun show(
        initial: ProfileEdit = ProfileEdit(saved),
        channels: List<PersonalChannel>? = null,
        onBack: () -> Unit = {},
        extra: EditProfileActions.() -> Unit = {},
        check: (String) -> UsernameCheck = { UsernameCheck.Idle },
        onSave: () -> Unit = {},
    ) {
        compose.setContent {
            MioTheme {
                var edit by remember { mutableStateOf(initial) }
                val actions = EditProfileActions(
                    onFirstName = { edit = edit.copy(firstName = it) },
                    onLastName = { edit = edit.copy(lastName = it) },
                    onAbout = { edit = edit.copy(about = it.take(edit.aboutLimit)) },
                    onUsername = { edit = edit.copy(username = it, usernameCheck = check(it)) },
                    onBirthday = { edit = edit.copy(birthday = it) },
                    onChannel = { edit = edit.copy(channel = it) },
                    onSave = onSave,
                )
                EditProfileScreen(edit, emptyMap(), channels, busy = false, onBack = onBack, actions = actions)
            }
        }
    }

    private fun scrollTo(tag: String) = compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag(tag))

    @Test
    fun savingIsOnWhileSomethingChangedAndEverythingIsValid() {
        show()
        compose.onNodeWithTag("editSave").assertIsNotEnabled()
        scrollTo("editSaveButton")
        compose.onNodeWithTag("editSaveButton").assertIsNotEnabled()
        scrollTo("editFirstName")
        compose.onNodeWithTag("editFirstName").performTextReplacement("Anna")
        compose.onNodeWithTag("editSave").assertIsEnabled()
        scrollTo("editSaveButton")
        compose.onNodeWithTag("editSaveButton").assertIsEnabled()
        scrollTo("editFirstName")
        compose.onNodeWithTag("editFirstName").performTextReplacement("")
        compose.onNodeWithTag("editSave").assertIsNotEnabled()
        compose.onNodeWithText("Enter a first name").assertExists()
        compose.onNodeWithTag("editFirstName").performTextReplacement("Ann")
        compose.onNodeWithTag("editSave").assertIsNotEnabled()
    }

    @Test
    fun theBioCountsItsCharactersAndStopsAtTheLimit() {
        show(initial = ProfileEdit(saved, aboutLimit = 12))
        scrollTo("editAbout")
        compose.onNodeWithTag("editAboutCounter", useUnmergedTree = true).assertTextEquals("5 of 12")
        compose.onNodeWithTag("editAbout").performTextReplacement("a longer bio that is clipped")
        compose.onNodeWithTag("editAboutCounter", useUnmergedTree = true).assertTextEquals("12 of 12")
    }

    @Test
    fun theUsernameShowsItsVerdict() {
        val verdicts = mapOf(
            "tester_free" to UsernameCheck.Free,
            "taken_one" to UsernameCheck.Taken,
            "for_sale" to UsernameCheck.ForSale,
            "abc" to UsernameCheck.Invalid(OwnUsernameProblem.TooShort),
            "1abcdef" to UsernameCheck.Invalid(OwnUsernameProblem.StartsWithDigit),
            "ab-cdef" to UsernameCheck.Invalid(OwnUsernameProblem.BadSymbols),
            "typing" to UsernameCheck.Checking,
        )
        show(check = { verdicts.getValue(it) })
        val expected = mapOf(
            "tester_free" to "@tester_free is available",
            "taken_one" to "This username is already taken",
            "for_sale" to "This username is taken, it can be bought on Fragment",
            "abc" to "A username has at least 5 characters",
            "1abcdef" to "A username cannot start with a number",
            "ab-cdef" to "Only a–z, 0–9 and underscores are allowed",
            "typing" to "Checking…",
        )
        for ((name, text) in expected) {
            scrollTo("editUsername")
            compose.onNodeWithTag("editUsername").performTextReplacement(name)
            compose.onNodeWithTag("editUsernameVerdict", useUnmergedTree = true).assertTextEquals(text)
            if (name == "tester_free") compose.onNodeWithTag("editSave").assertIsEnabled() else compose.onNodeWithTag("editSave").assertIsNotEnabled()
        }
    }

    @Test
    fun leavingWithChangesAsksFirst() {
        var left = 0
        show(onBack = { left++ })
        compose.onNodeWithTag("back").performClick()
        assertEquals(1, left)

        compose.onNodeWithTag("editFirstName").performTextReplacement("Anna")
        compose.onNodeWithTag("back").performClick()
        assertEquals(1, left)
        compose.onNodeWithText("Discard your changes?").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        assertEquals(1, left)
        compose.onAllNodesWithTag("confirmDiscard").assertCountEquals(0)

        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("confirmDiscard").performClick()
        assertEquals(2, left)
    }

    @Test
    fun collectibleUsernamesAreSwitchedAndMoved() {
        val toggled = mutableListOf<String>()
        val moved = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            MioTheme {
                val actions = EditProfileActions(onToggleUsername = { toggled += it.username }, onMoveUsername = { name, up -> moved += name.username to up })
                EditProfileScreen(ProfileEdit(saved), emptyMap(), null, busy = false, onBack = {}, actions = actions)
            }
        }
        compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag("editUsernames"))
        compose.onAllNodesWithTag("usernameSwitch_annlee").assertCountEquals(0)
        compose.onNodeWithTag("usernameSwitch_frag_one").performClick()
        compose.onNodeWithTag("usernameSwitch_frag_off").performClick()
        assertEquals(listOf("frag_one", "frag_off"), toggled)
        compose.onNodeWithTag("usernameUp_annlee").assertIsNotEnabled()
        compose.onNodeWithTag("usernameDown_frag_two").assertIsNotEnabled()
        compose.onNodeWithTag("usernameDown_annlee").performClick()
        compose.onNodeWithTag("usernameUp_frag_two").performClick()
        assertEquals(listOf("annlee" to false, "frag_two" to true), moved)
        compose.onAllNodesWithTag("usernameUp_frag_off").assertCountEquals(0)
        compose.onNodeWithTag("usernameSwitch_frag_one").assertIsOn()
        compose.onNodeWithTag("usernameSwitch_frag_off").assertIsOff()
    }

    @Test
    fun theBirthdayIsRemovedAndTheChannelChosen() {
        val news = PersonalChannel("ch1", "Kotlin News")
        show(channels = listOf(news, PersonalChannel("ch2", "My notes")), initial = ProfileEdit(saved, channel = news))
        compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag("editBirthday"))
        compose.onNodeWithTag("editBirthdayRemove").performClick()
        compose.onNodeWithText("Not set").assertExists()
        compose.onNodeWithTag("editSave").assertIsEnabled()

        compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag("editChannel"))
        compose.onNodeWithTag("editChannel").performClick()
        compose.onNodeWithTag("channel_ch2").performClick()
        compose.onNodeWithText("My notes").assertExists()
        compose.onNodeWithTag("editChannel").performClick()
        compose.onNodeWithTag("channelRemove").performClick()
        compose.onNodeWithText("None").assertExists()
    }

    @Test
    fun noPublicChannelsSaysSo() {
        show(channels = emptyList())
        compose.onNodeWithTag("editProfile").performScrollToNode(hasTestTag("editChannel"))
        compose.onNodeWithTag("editChannel").performClick()
        compose.onNodeWithTag("channelsEmpty").assertExists()
    }

    private fun jpeg(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 120, 200)) }.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }.toByteArray()

    @Test
    fun theCropStartsOnTheCentredSquareAndZoomsAndDrags() {
        val applied = mutableListOf<CropRect>()
        fun apply() {
            compose.onNodeWithTag("cropApply").performScrollTo().performClick()
        }
        compose.setContent { MioTheme { AvatarCropScreen(jpeg(400, 200), unreadable = false, busy = false, onBack = {}, onApply = { applied += it }) } }
        compose.waitForIdle()
        apply()
        assertEquals(CropRect(left = 100, top = 0, side = 200), applied.last())

        compose.onNodeWithTag("cropZoom").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        apply()
        assertEquals(CropRect(left = 150, top = 50, side = 100), applied.last())

        compose.onNodeWithTag("cropArea").performTouchInput { swipeLeft() }
        apply()
        val dragged = applied.last()
        assertEquals(100, dragged.side)
        assertTrue(dragged.left > 150, "$dragged")
    }

    @Test
    fun anUnreadablePhotoCannotBeCropped() {
        compose.setContent { MioTheme { AvatarCropScreen(null, unreadable = true, busy = false, onBack = {}, onApply = {}) } }
        compose.onNodeWithTag("cropUnreadable").assertExists()
        compose.onAllNodesWithTag("cropApply").assertCountEquals(0)
    }
}
