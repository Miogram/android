package app.miogram

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.MusicModule
import app.miogram.di.TelegramModule
import app.miogram.music.MusicPlayerFactory
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class, MusicModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class MusicFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private val engine by lazy { FakeMusicPlayer() }

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

    @BindValue
    @JvmField
    val players = MusicPlayerFactory { engine }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun aSongPlaysAndTheMiniPlayerFollowsItAcrossScreens() {
        ApplicationProvider.getApplicationContext<android.app.Application>().refuseMusicServiceBind()
        fake.addMusicMessages(3)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messages")
        val first = "doc${FakeTelegram.MUSIC_ID + 1}"
        compose.scrollToMessageNode(hasTestTag("musicToggle_$first"))
        compose.onNodeWithTag("musicToggle_$first").performClick()

        compose.waitForTag("miniPlayer")
        compose.onNodeWithTag("miniTitle", useUnmergedTree = true).assertTextEquals("Song 1")
        compose.onNodeWithTag("miniPerformer", useUnmergedTree = true).assertTextEquals("Band")
        compose.onNodeWithTag("musicToggle_$first").assertContentDescriptionEquals("Pause")
        compose.waitUntilIdle { engine.mediaItemCount == 3 }
        assertTrue(engine.playWhenReady)

        compose.onNodeWithTag("back").performClick()
        compose.waitForTag("openChats")
        compose.waitForTag("miniPlayer")
        compose.onNodeWithTag("miniToggle").performClick()
        compose.waitUntilIdle { !engine.playWhenReady }
        compose.onNodeWithTag("miniToggle").assertContentDescriptionEquals("Play")

        compose.onNodeWithTag("miniTitle", useUnmergedTree = true).performClick()
        compose.waitForTag("musicSheet")
        compose.onNodeWithTag("sheetNext").performClick()
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("sheetTitle").assertTextEquals("Song 2") }.isSuccess }
        assertEquals(1, engine.currentMediaItemIndex)
        compose.onNodeWithTag("sheetToggle").performClick()
        compose.waitUntilIdle { engine.playWhenReady }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("musicSheet")).fetchSemanticsNodes().isEmpty() }
        compose.waitForTag("openChats")
        compose.onNodeWithTag("miniTitle", useUnmergedTree = true).performClick()
        compose.waitForTag("musicSheet")
        compose.onNodeWithTag("sheetCollapse").performClick()
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("musicSheet")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("miniClose").performClick()
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("miniPlayer")).fetchSemanticsNodes().isEmpty() }
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("musicSheet")).fetchSemanticsNodes().isEmpty() }
        assertEquals(0, engine.mediaItemCount)
        assertFalse(engine.playWhenReady && engine.mediaItemCount > 0)
    }
}
