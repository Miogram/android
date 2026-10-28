package app.miogram

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
class ChatListActionsFlowTest {
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

    private fun gone(tag: String) = compose.waitUntilIdle { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    private fun openMenu(key: String) {
        compose.waitForTag("dialog_$key")
        compose.onNodeWithTag("dialog_$key").performTouchInput { longClick() }
    }

    @Test
    fun muteForADayFromTheListMenu() {
        compose.logIn()
        openMenu("u501")
        compose.onNodeWithTag("action_Mute").performClick()
        compose.waitForTag("muteForDay")
        compose.onNodeWithTag("muteForDay").performClick()
        compose.waitUntilIdle { fake.lastNotifySettings != null }
        val now = System.currentTimeMillis() / 1000
        val until = assertNotNull(fake.lastNotifySettings?.settings?.muteUntil)
        assertTrue(until - now in 86_400 - 60..86_400, "${until - now}")
        assertEquals(until, fake.muteUntil[fake.alice.id])
        gone("muteForDay")
        compose.waitUntilIdle { fake.alice.id in fake.muted }
        openMenu("u501")
        compose.waitForTag("action_Unmute")
        compose.onNodeWithTag("action_Unmute").performClick()
        compose.waitUntilIdle { fake.alice.id !in fake.muted }
        assertEquals(0, fake.lastNotifySettings?.settings?.muteUntil)
    }

    @Test
    fun aDeletedChatCanBeTakenBackThenIsDeletedForBothSides() {
        compose.logIn()
        openMenu("u501")
        compose.onNodeWithTag("removal_Delete").performScrollTo().performClick()
        compose.onNodeWithTag("removalRevoke").performClick()
        compose.onNodeWithTag("confirmRemoval").performClick()
        gone("dialog_u501")
        compose.waitForTag("removalSnackbar")
        compose.onNodeWithTag("undoRemoval").performClick()
        compose.waitForTag("dialog_u501")
        gone("removalSnackbar")
        assertTrue(fake.removals.historyCalls.isEmpty())

        openMenu("u501")
        compose.onNodeWithTag("removal_Delete").performScrollTo().performClick()
        compose.onNodeWithTag("removalRevoke").performClick()
        compose.onNodeWithTag("confirmRemoval").performClick()
        gone("dialog_u501")
        compose.waitUntilIdle { fake.removals.historyCalls.isNotEmpty() }
        val call = fake.removals.historyCalls.single()
        assertFalse(call.justClear)
        assertTrue(call.revoke)
        gone("removalSnackbar")
        assertTrue(compose.onAllNodes(hasTestTag("dialog_u501")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun clearingAChatKeepsItInTheList() {
        compose.logIn()
        openMenu("u501")
        compose.onNodeWithTag("removal_Clear").performScrollTo().performClick()
        compose.onNodeWithTag("confirmRemoval").performClick()
        compose.waitUntilIdle { fake.removals.historyCalls.isNotEmpty() }
        val call = fake.removals.historyCalls.single()
        assertTrue(call.justClear)
        assertFalse(call.revoke)
        compose.waitForTag("dialog_u501")
    }
}
