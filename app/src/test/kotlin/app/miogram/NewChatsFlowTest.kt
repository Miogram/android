package app.miogram

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.FakeNewChats.Companion.NEW_ID
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputUser
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class NewChatsFlowTest {
    private val fake = FakeTelegram().apply { groups.inDialogs = true }
    private val dir = Files.createTempDirectory("poc").toFile()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private val erin get() = fake.contacts.erin
    private val frank get() = fake.contacts.frank

    private fun openContacts() {
        compose.logIn()
        compose.onNodeWithTag("openContacts").performClick()
        compose.waitForTag("newGroup")
    }

    private fun openDevsMembers() {
        compose.logIn()
        compose.waitForTag("dialogs")
        compose.onNodeWithTag("dialogs").performScrollToKey("ch$DEVS_ID")
        compose.onNodeWithTag("dialog_ch$DEVS_ID").performClick()
        compose.waitForTag("messageInput")
        compose.onNodeWithTag("title").performClick()
        compose.waitForTag("peerProfile")
    }

    @Test
    fun aGroupFromPickedContactsAsksAboutThoseWhoCannotBeAdded() {
        val chats = fake.groups.newChats
        chats.restricted += frank.id
        openContacts()
        compose.onNodeWithTag("newGroup").performClick()

        compose.waitForTag("pick_u${erin.id}")
        compose.onNodeWithTag("pick_u${erin.id}").performClick()
        compose.onNodeWithTag("pickerSearch").performTextInput("frank")
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("pick_u${erin.id}")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("pick_u${frank.id}").performClick()
        compose.waitForTag("chip_u${erin.id}")
        compose.waitForTag("chip_u${frank.id}")
        compose.onNodeWithTag("pickerConfirm").performClick()

        compose.waitForTag("groupName")
        compose.onNodeWithTag("groupName").performTextInput("   ")
        assertTrue(chats.createCalls.isEmpty())
        compose.onNodeWithTag("groupName").performTextClearance()
        compose.onNodeWithTag("groupName").performTextInput("Book lovers")
        compose.onNodeWithTag("createGroup").performClick()
        compose.waitForTag("sendInviteLink")
        compose.onNodeWithTag("missingNames").assertExists()
        assertEquals("Book lovers", chats.createCalls.single().title)
        assertEquals(listOf(erin.id, frank.id), chats.inviteCalls.single().users.map { (it as InputUser).userId })

        compose.onNodeWithTag("sendInviteLink").performClick()
        compose.waitUntilIdle { fake.savedTexts("https://t.me/+DevsPrimary") == 1 }
        compose.waitForTag("messageInput")
        compose.onNode(hasText("Book lovers") and hasAnyAncestor(hasTestTag("title")), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aChannelBecomesPublicUnderAUsernameCheckedWhileTyping() {
        val chats = fake.groups.newChats
        openContacts()
        compose.onNodeWithTag("newChannel").performClick()
        compose.waitForTag("channelName")
        compose.onNodeWithTag("channelName").performTextInput("Film news")
        compose.onNodeWithTag("channelAbout").performTextInput("every week")
        compose.onNodeWithTag("newChannel").performScrollToNode(hasTestTag("createChannel"))
        compose.onNodeWithTag("createChannel").performClick()

        compose.waitForTag("channelType")
        assertTrue(chats.createCalls.single().broadcast)
        assertEquals("every week", chats.abouts[NEW_ID])
        compose.waitForTag("channelLink")
        compose.onNodeWithTag("typePublic").performClick()
        compose.waitForTag("channelUsername")

        compose.onNodeWithTag("channelUsername").performTextInput("ab")
        compose.waitForText("Use at least 4 characters")
        compose.onNodeWithTag("channelUsername").performTextClearance()
        compose.onNodeWithTag("channelUsername").performTextInput("kotlin")
        compose.waitForText("kotlin is already taken")
        compose.onNodeWithTag("channelUsername").performTextClearance()
        compose.onNodeWithTag("channelUsername").performTextInput("filmnews")
        compose.waitForText("filmnews is available")
        assertEquals(listOf("kotlin", "filmnews"), chats.checked.toList())
        compose.onNodeWithTag("channelTypeDone").performClick()
        compose.waitUntilIdle { chats.usernames[NEW_ID] == "filmnews" }

        compose.waitForTag("pickerSkip")
        compose.onNodeWithTag("pickerSkip").performClick()
        compose.waitForTag("messageInput")
        compose.onNode(hasText("Film news") and hasAnyAncestor(hasTestTag("title")), useUnmergedTree = true).assertExists()
        assertTrue(chats.inviteCalls.isEmpty())
    }

    @Test
    fun adminsAddContactsFromTheMembersScreen() {
        val chats = fake.groups.newChats
        openDevsMembers()
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("members"))
        compose.onNodeWithTag("members").performClick()
        compose.waitForTag("addMembers")
        val loads = fake.groups.members.participantCalls.size
        compose.onNodeWithTag("addMembers").performClick()

        compose.waitForTag("pick_u${erin.id}")
        compose.onNodeWithTag("pick_u${erin.id}").performClick()
        compose.onNodeWithTag("pickerConfirm").performClick()
        compose.waitUntilIdle { chats.inviteCalls.isNotEmpty() }
        assertEquals(erin.id, (chats.inviteCalls.single().users.single() as InputUser).userId)

        compose.waitForTag("addMembers")
        compose.waitUntilIdle { fake.groups.members.participantCalls.size > loads }
    }

    @Test
    fun adminsChangeTheTitleAndDescription() {
        val chats = fake.groups.newChats
        openDevsMembers()
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("editChatInfo"))
        compose.onNodeWithTag("editChatInfo").performClick()
        compose.waitForTag("editName")
        compose.waitForText("Kotlin devs")
        compose.waitForText("all about kotlin devs")

        compose.onNodeWithTag("editChat").performScrollToNode(hasTestTag("saveChat"))
        compose.onNodeWithTag("saveChat").assertIsNotEnabled()
        compose.onNodeWithTag("editName").performTextClearance()
        compose.onNodeWithTag("editName").performTextInput("Kotlin devs 2")
        compose.onNodeWithTag("editChat").performScrollToNode(hasTestTag("saveChat"))
        compose.onNodeWithTag("saveChat").performClick()
        compose.waitUntilIdle { chats.titles[DEVS_ID] == "Kotlin devs 2" }
        assertTrue(DEVS_ID !in chats.abouts, "the description did not change")
        compose.waitForTag("peerProfile")
    }
}
