package app.miogram

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.LinkAnnotation
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputMessagesFilterEmpty
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import kotlin.test.assertEquals
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
class ChatSearchFlowTest {
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

    @Test
    fun aPickedResultOpensTheMessage() {
        fake.addSavedMessages(60)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("message_69")

        compose.onNodeWithTag("avatar").performTouchInput { longClick() }

        compose.onNodeWithTag("chatSearch").performClick()
        compose.onNodeWithTag("searchField").performTextInput("note 1")
        compose.waitUntilIdle { fake.searchCalls.any { it.q == "note 1" } }
        val call = fake.searchCalls.last { it.q == "note 1" }
        assertEquals(InputMessagesFilterEmpty, call.filter)
        assertEquals(0, call.offsetId)

        compose.waitForTag("searchResult_29")
        compose.onNodeWithTag("searchResults").performScrollToNode(hasTestTag("searchResult_11"))
        compose.onNodeWithTag("searchResult_11").performClick()
        compose.waitUntilIdle { fake.historyCalls.any { it.first == 0L && it.second == 12 && it.third == -25 } }
        compose.waitForTag("highlight_11")
        compose.onNodeWithTag("searchCounter").assertExists()

        compose.onNodeWithTag("searchNewer").performClick()
        compose.waitForTag("highlight_20")
    }

    @Test
    fun aHashtagSearchesTheChat() {
        val tagged = fake.addHashtagMessage()
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForText("weekly #kotlin news")

        compose.onNode(hasText("weekly #kotlin news"), useUnmergedTree = true).performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "#kotlin" }
        compose.waitUntilIdle { fake.searchCalls.any { it.q == "#kotlin" } }
        val field = SemanticsMatcher("search field") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "#kotlin" }
        compose.onNode(field).assertExists()
        compose.waitForTag("searchResult_$tagged")
    }
}
