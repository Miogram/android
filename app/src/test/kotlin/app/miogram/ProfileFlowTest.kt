package app.miogram

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ProfileFlowTest {
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

    @Inject lateinit var navigator: Navigator

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun aliceProfileHeader() {
        openAliceProfile()

        compose.waitForTag("emojiStatus")
        compose.waitForTag("profilePattern")
        compose.waitForTag("pinnedGift_gift62_${FakeTelegram.NOW}")
        compose.waitForTag("profileMusic")
        compose.onNodeWithTag("musicTitle", useUnmergedTree = true).assertTextEquals("Profile Song 1")

        runBlocking {
            fake.stories.pushDeleted(FakeStories.ALICE, 1)
            fake.stories.pushDeleted(FakeStories.ALICE, 2)
        }
        val ringed = SemanticsMatcher("states stories") { it.config.getOrNull(SemanticsProperties.StateDescription)?.contains("stories") == true }
        compose.waitUntilIdle { compose.onAllNodes(ringed, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("profileAvatar").performClick()
        compose.waitForTag("page_0")
        compose.onNodeWithTag("viewerPosition").assertExists()
    }

    @Test
    fun aliceProfileChannel() {
        openAliceProfile()

        compose.waitForTag("personalChannel")
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("datacenter"))
        compose.onNodeWithTag("datacenter").assertTextContains("DC 2")
        compose.onNodeWithTag("peerProfile").performScrollToNode(hasTestTag("personalChannel"))
        compose.onNodeWithTag("channelTitle", useUnmergedTree = true).assertTextEquals("Kotlin News")
        compose.onNodeWithTag("channelSubscribers", useUnmergedTree = true).assertTextEquals("1,200 subscribers")
        compose.onNodeWithTag("channelPost", useUnmergedTree = true).assertTextEquals("post 120")

        compose.onNodeWithTag("personalChannel").performClick()
        compose.waitForTag("highlight_120")
    }

    @Test
    fun muteForEightHours() {
        openAliceProfile()

        compose.waitForTag("toggleMute")
        compose.onNodeWithTag("toggleMute").performClick()
        compose.waitForTag("muteForEightHours")
        compose.onNodeWithTag("muteForEightHours").performClick()
        compose.waitUntilIdle { fake.lastNotifySettings != null }
        val now = System.currentTimeMillis() / 1000
        val until = assertNotNull(fake.lastNotifySettings?.settings?.muteUntil)
        assertTrue(until - now in 8 * 3_600 - 60..8 * 3_600, "${until - now}")
        assertEquals(until, fake.muteUntil[fake.alice.id])
        compose.waitUntilIdle { compose.onAllNodes(hasContentDescription("Unmute")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("toggleMute").performClick()
        compose.waitUntilIdle { fake.alice.id !in fake.muted }
        assertEquals(0, fake.lastNotifySettings?.settings?.muteUntil)
        compose.onNodeWithTag("muteMenu").assertDoesNotExist()
    }

    @Test
    fun ownSongsPutInOrder() {
        logIn()
        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("openOwnProfile")
        compose.onNodeWithTag("openOwnProfile").performClick()

        compose.waitForTag("profileMusic")
        compose.waitUntilIdle { compose.onAllNodes(hasText("Profile Song 3"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profileMusic").performTouchInput { longClick() }
        compose.waitForTag("songOrderSheet")
        val (third, fourth, fifth) = fake.ownSongs.map { "doc${it.id}" }
        compose.waitForTag("songOrder_$fifth")
        compose.onNodeWithTag("songUp_$third").assertIsNotEnabled()
        compose.onNodeWithTag("songDown_$fifth").assertIsNotEnabled()

        compose.onNodeWithTag("songDown_$third").performClick()
        compose.waitUntilIdle { fake.saveMusicCalls.isNotEmpty() }
        assertEquals(listOf(third to fourth), fake.saveMusicCalls.map { (song, after) -> "doc$song" to after?.let { "doc$it" } })
        assertEquals(listOf(fourth, third, fifth), fake.ownSongs.map { "doc${it.id}" })
        compose.onNodeWithTag("songUp_$fourth").assertIsNotEnabled()
        compose.onNodeWithTag("songUp_$fifth").performClick()
        compose.waitUntilIdle { fake.saveMusicCalls.size == 2 }
        compose.onNodeWithTag("songUp_$fifth").performClick()
        compose.waitUntilIdle { fake.saveMusicCalls.size == 3 }
        assertEquals(fifth to null, fake.saveMusicCalls.last().let { "doc${it.first}" to it.second })
        assertEquals(listOf(fifth, fourth, third), fake.ownSongs.map { "doc${it.id}" })
        compose.waitUntilIdle { compose.onAllNodes(hasText("Profile Song 5"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theOwnProfileIsTheOneProfileScreen() {
        hilt.inject()
        logIn()
        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("openOwnProfile")
        compose.onNodeWithTag("openOwnProfile").performClick()
        compose.waitForTag("peerProfile")
        compose.waitForText("Test User")
        compose.onNodeWithTag("message").assertDoesNotExist()
        compose.onNodeWithTag("toggleMute").assertDoesNotExist()
        val own = Route.PeerProfile("u${fake.selfId}", "Saved Messages")
        compose.runOnIdle { navigator.go(own) }
        compose.waitUntilIdle { navigator.current == own }
        compose.waitForText("Test User")
        compose.onNodeWithTag("message").assertDoesNotExist()
    }

    private fun openAliceProfile() {
        logIn()
        compose.waitForText("Alice Example")
        compose.onNodeWithText("Alice Example").performClick()
        compose.waitForTag("title")
        compose.onNodeWithTag("title").performClick()
        compose.waitForTag("peerProfile")
    }

    private fun logIn() {
        compose.waitForTag("start")
        compose.onNodeWithTag("start").performClick()
        compose.waitForTag("phone")
        compose.onNodeWithTag("phone").performTextClearance()
        compose.onNodeWithTag("phone").performTextInput("6627777")
        compose.onNodeWithTag("sendCode").performClick()
        compose.waitForTag("confirmPhone")
        compose.onNodeWithTag("confirmPhone").performClick()
        compose.waitForTag("code")
        compose.onNodeWithTag("code").performTextInput("22222")
    }
}
