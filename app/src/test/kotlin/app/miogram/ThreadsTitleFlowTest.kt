package app.miogram

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.assertHasNoClickAction
import app.miogram.FakeDiscussion.Companion.BLOG_ID
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
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
class ThreadsTitleFlowTest {
    private val fake = FakeTelegram().apply { groups.discussion.inDialogs = true }
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
        fake.close()
        dir.deleteRecursively()
    }

    private fun waitForText(text: String) = compose.waitUntilIdle { compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theTitleCountsTheCommentsAndFollowsNewOnes() {
        compose.logIn()
        compose.waitForTag("dialogs")
        compose.onNodeWithTag("dialogs").performScrollToKey("ch$BLOG_ID")
        compose.onNodeWithTag("dialog_ch$BLOG_ID").performClick()
        compose.waitForTag("messageInput")
        compose.scrollToMessageNode(hasTestTag("replies_3"))
        compose.onNodeWithTag("replies_3", useUnmergedTree = true).performClick()
        waitForText("2 Comments")

        compose.onNodeWithTag("title").assertHasNoClickAction()
        compose.onNodeWithTag("title").performClick()
        compose.waitForTag("messageInput")

        runBlocking { fake.groups.discussion.pushComment("third") }
        waitForText("3 Comments")
    }
}
