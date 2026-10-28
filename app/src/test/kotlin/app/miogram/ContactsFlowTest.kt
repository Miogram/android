package app.miogram

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
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
class ContactsFlowTest {
    private val fake = FakeTelegram()
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

    private fun openContacts() {
        compose.logIn()
        compose.onNodeWithTag("openContacts").performClick()
        compose.waitForTag("contact_u${fake.contacts.erin.id}")
    }

    private fun top(key: String): Float = compose.onNodeWithTag("contact_$key").fetchSemanticsNode().positionInRoot.y

    @Test
    fun theListIsSortedAndSearched() {
        openContacts()
        compose.waitForTag("contact_u502")
        assertTrue(top("u506") < top("u507") && top("u507") < top("u502"))
        compose.onNodeWithTag("contact_u506").assertTextContains("online", substring = true)

        compose.onNodeWithTag("contactsSearch").performClick()
        compose.onNodeWithTag("contactsSearch").performTextInput("car")
        compose.waitForTag("contactsGlobalTitle")
        compose.waitForTag("contact_u${fake.carol.id}")
        assertTrue(compose.onAllNodesWithTag("contact_u506").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aContactIsAddedByNumberAndDeleted() {
        openContacts()

        compose.onNodeWithTag("addContact").performClick()
        compose.waitForTag("contactPhone")
        compose.onNodeWithTag("contactPhone").performTextInput("+1 555 000 9999")
        compose.onNodeWithTag("contactFirstName").performTextInput("Nobody")
        compose.onNodeWithTag("confirmAddContact").performClick()
        compose.waitForText("No Telegram account has this phone number")

        compose.onNodeWithTag("contactPhone").performTextClearance()
        compose.onNodeWithTag("contactPhone").performTextInput("+1 555 000 1111")
        compose.onNodeWithTag("confirmAddContact").performClick()
        compose.waitUntilIdle { compose.onAllNodesWithTag("contactPhone").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("contactList").performScrollToNode(hasTestTag("contact_u${fake.carol.id}"))
        assertEquals("15550001111", fake.contacts.imports.last().contacts.single().phone)

        compose.onNodeWithTag("contact_u${fake.bob.id}").performTouchInput { longClick() }
        compose.waitForTag("confirmDeleteContact")
        compose.onNodeWithTag("confirmDeleteContact").performClick()
        compose.waitUntilIdle { compose.onAllNodesWithTag("contact_u${fake.bob.id}").fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf(listOf(fake.bob.id)), fake.contacts.deletes)
    }

    @Test
    fun aTapOpensTheChatAndAProfileAddsItsUser() {
        openContacts()
        compose.onNodeWithTag("contact_u${fake.contacts.erin.id}").performClick()
        compose.waitForTag("title")
        compose.onNodeWithTag("title").assertTextContains("Erin Online", substring = true)

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithTag("openChats").performClick()
        compose.waitForText("Alice Example")
        compose.onNodeWithText("Alice Example").performClick()
        compose.waitForTag("title")
        compose.onNodeWithTag("title").performClick()
        compose.waitForTag("peerProfile")
        compose.onNodeWithTag("more").performClick()
        compose.waitForTag("addToContacts")
        compose.onNodeWithTag("addToContacts").performClick()
        compose.waitForTag("sharePhone")
        compose.onNodeWithTag("sharePhone").performClick()
        compose.onNodeWithTag("confirmAddContact").performClick()
        compose.waitUntilIdle { fake.contacts.adds.isNotEmpty() }
        val added = fake.contacts.adds.single()
        assertEquals("Alice", added.firstName)
        assertEquals("Example", added.lastName)
        assertTrue(added.addPhonePrivacyException)
        compose.onNodeWithTag("more").performClick()
        compose.waitForTag("deleteContact")
    }
}
