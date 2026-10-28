package cn.ellerotta.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import cn.ellerotta.core.model.ContactItem
import cn.ellerotta.core.model.MissingInvitee
import cn.ellerotta.core.model.UsernameAvailability
import cn.ellerotta.core.model.UsernameProblem
import cn.ellerotta.core.model.UsernameStatus
import cn.ellerotta.core.model.toggled
import cn.ellerotta.core.ui.components.contacts.ContactsContent
import cn.ellerotta.core.ui.components.groups.ChannelTypeScreen
import cn.ellerotta.core.ui.components.groups.EditChatScreen
import cn.ellerotta.core.ui.components.groups.MemberPickerScreen
import cn.ellerotta.core.ui.components.groups.MemberPickerState
import cn.ellerotta.core.ui.components.groups.MissingInviteesDialog
import cn.ellerotta.core.ui.components.groups.NewGroupScreen
import cn.ellerotta.core.ui.theme.MioTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewChatsTest {
    @get:Rule
    val compose = createComposeRule()

    private val erin = ContactItem("u1", 1, "Erin Online")
    private val bob = ContactItem("u2", 2, "Bob")

    @Test
    fun theContactsPageOffersANewGroupOnlyOutsideASearch() {
        var group = 0
        var query by mutableStateOf("")
        compose.setContent {
            MioTheme {
                ContactsContent(
                    contacts = listOf(erin), loading = false, query = query, globalResults = emptyList(), avatars = emptyMap(), now = 0,
                    listState = rememberLazyListState(), contentPadding = PaddingValues(),
                    onOpen = {}, onDelete = {}, onNewGroup = { group++ },
                )
            }
        }
        compose.onNodeWithTag("newGroup").performClick()
        assertEquals(1, group)
        query = "erin"
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasTestTag("newGroup")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("contact_u1").assertExists()
    }

    @Test
    fun thePickerTogglesRowsAndChipsAndOffersItsButtonOnlyWithPicks() {
        var picked by mutableStateOf(emptyList<String>())
        var confirmed = 0
        compose.setContent {
            MioTheme {
                MemberPickerScreen(
                    title = "Add", state = MemberPickerState(listOf(erin, bob), listOf(erin, bob).filter { it.key in picked }, loaded = true), avatars = emptyMap(),
                    confirmLabel = "Next", busy = false, onBack = {}, onQuery = {}, onToggle = { picked = toggled(picked, it.key) }, onConfirm = { confirmed++ },
                )
            }
        }
        assertTrue(compose.onAllNodes(hasTestTag("pickerConfirm")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("pick_u1").performClick()
        compose.onNodeWithTag("pick_u2").performClick()
        compose.waitUntil { compose.onAllNodes(hasTestTag("pickerConfirm")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("chip_u1").assertExists()
        compose.onNodeWithTag("pickerConfirm").performClick()
        assertEquals(1, confirmed)

        compose.onNodeWithTag("chip_u1").performClick()
        assertEquals(listOf("u2"), picked)
    }

    @Test
    fun theContactsPageOffersANewChannel() {
        var channel = 0
        compose.setContent {
            MioTheme {
                ContactsContent(
                    contacts = listOf(erin), loading = false, query = "", globalResults = emptyList(), avatars = emptyMap(), now = 0,
                    listState = rememberLazyListState(), contentPadding = PaddingValues(),
                    onOpen = {}, onDelete = {}, onNewChannel = { channel++ },
                )
            }
        }
        compose.onNodeWithTag("newChannel").performClick()
        assertEquals(1, channel)
        assertTrue(compose.onAllNodes(hasTestTag("newGroup")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theSkipButtonTakesTheConfirmButtonsPlaceWhileNobodyIsPicked() {
        var skipped = 0
        var picked by mutableStateOf(emptyList<ContactItem>())
        compose.setContent {
            MioTheme {
                MemberPickerScreen(
                    title = "Add", state = MemberPickerState(listOf(erin, bob), picked, loaded = true), avatars = emptyMap(),
                    confirmLabel = "Add", busy = false, onBack = {}, onQuery = {}, onToggle = {}, onConfirm = {}, skipLabel = "Skip", onSkip = { skipped++ },
                )
            }
        }
        compose.onNodeWithTag("pickerSkip").performClick()
        assertEquals(1, skipped)
        assertTrue(compose.onAllNodes(hasTestTag("pickerConfirm")).fetchSemanticsNodes().isEmpty())
        picked = listOf(erin)
        compose.waitUntil { compose.onAllNodes(hasTestTag("pickerSkip")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("pickerConfirm").assertExists()
    }

    @Test
    fun aGroupNeedsAName() {
        var name by mutableStateOf("")
        var created = 0
        compose.setContent {
            MioTheme {
                NewGroupScreen(
                    name = name, photo = null, members = listOf(erin, bob), avatars = emptyMap(), busy = false, onBack = {},
                    onName = { name = it }, onPickPhoto = {}, onRemovePhoto = {}, onCreate = { created++ },
                )
            }
        }
        compose.onNodeWithTag("createGroup").assertIsNotEnabled()
        compose.onNodeWithTag("groupName").performTextInput("   ")
        compose.onNodeWithTag("createGroup").assertIsNotEnabled()
        compose.onNodeWithTag("groupName").performTextInput("Club")
        compose.onNodeWithTag("createGroup").assertIsEnabled().performClick()
        assertEquals(1, created)
        compose.onNodeWithText("2 members").assertExists()
    }

    @Test
    fun aPublicChannelNeedsAUsernameTelegramAccepted() {
        var status by mutableStateOf<UsernameStatus>(UsernameStatus.Idle)
        var username by mutableStateOf("filmnews")
        var public by mutableStateOf(true)
        compose.setContent {
            MioTheme {
                ChannelTypeScreen(
                    public = public, username = username, status = status, link = "https://t.me/+AbCd", busy = false,
                    onBack = {}, onPublic = { public = it }, onUsername = { username = it }, onDone = {},
                )
            }
        }
        compose.onNodeWithTag("channelTypeDone").assertIsNotEnabled()
        status = UsernameStatus.Checking
        compose.onNodeWithText("Checking…").assertExists()
        compose.onNodeWithTag("channelTypeDone").assertIsNotEnabled()
        status = UsernameStatus.Problem(UsernameProblem.TooShort)
        compose.onNodeWithText("Use at least 4 characters").assertExists()
        compose.onNodeWithTag("channelTypeDone").assertIsNotEnabled()
        status = UsernameStatus.Checked("filmnews", UsernameAvailability.Taken)
        compose.onNodeWithText("filmnews is already taken").assertExists()
        compose.onNodeWithTag("channelTypeDone").assertIsNotEnabled()
        status = UsernameStatus.Checked("film", UsernameAvailability.Available)
        compose.onNodeWithTag("channelTypeDone").assertIsNotEnabled()
        status = UsernameStatus.Checked("filmnews", UsernameAvailability.Available)
        compose.onNodeWithText("filmnews is available").assertExists()
        compose.onNodeWithTag("channelTypeDone").assertIsEnabled()

        public = false
        compose.onNodeWithTag("channelLink").assertExists()
        compose.onNodeWithTag("channelTypeDone").assertIsEnabled()
    }

    @Test
    fun aPrivateChannelWaitsForItsLink() {
        var link by mutableStateOf<String?>(null)
        compose.setContent {
            MioTheme {
                ChannelTypeScreen(public = false, username = "", status = UsernameStatus.Idle, link = link, busy = false, onBack = {}, onPublic = {}, onUsername = {}, onDone = {})
            }
        }
        compose.onNodeWithTag("channelTypeDone").assertIsNotEnabled()
        link = "https://t.me/+AbCd"
        compose.onNodeWithTag("channelLink").assertExists()
        compose.onNodeWithTag("channelTypeDone").assertIsEnabled()
    }

    @Test
    fun theMissingInviteesAreNamedAndSentALinkOrSkipped() {
        var sent = 0
        var skipped = 0
        compose.setContent {
            MioTheme {
                MissingInviteesDialog(listOf(MissingInvitee("u1", "Erin"), MissingInvitee("u2", "Bob")), onSend = { sent++ }, onSkip = { skipped++ })
            }
        }
        compose.onNodeWithText("Erin, Bob").assertExists()
        compose.onNodeWithText("2 people cannot be added", substring = true).assertExists()
        compose.onNodeWithTag("sendInviteLink").performClick()
        compose.onNodeWithTag("skipInviteLink").performClick()
        assertEquals(1 to 1, sent to skipped)
    }

    @Test
    fun theEditPageSavesOnlyWhatChanged() {
        var changed by mutableStateOf(false)
        var name by mutableStateOf("Kotlin devs")
        var saved = 0
        compose.setContent {
            MioTheme {
                EditChatScreen(
                    channel = false, name = name, about = "about", photo = null, changed = changed, busy = false, onBack = {},
                    onName = { name = it; changed = true }, onAbout = {}, onPickPhoto = {}, onRemovePhoto = {}, onSave = { saved++ },
                )
            }
        }
        compose.onNodeWithTag("editChat").performScrollToNode(hasTestTag("saveChat"))
        compose.onNodeWithTag("saveChat").assertIsNotEnabled()
        compose.onNodeWithTag("editName").performTextInput("!")
        compose.onNodeWithTag("editChat").performScrollToNode(hasTestTag("saveChat"))
        compose.onNodeWithTag("saveChat").assertIsEnabled().performClick()
        assertEquals(1, saved)
        name = ""
        compose.onNodeWithTag("editChat").performScrollToNode(hasTestTag("saveChat"))
        compose.onNodeWithTag("saveChat").assertIsNotEnabled()
    }
}
