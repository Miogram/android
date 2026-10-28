package app.miogram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputPeerSelf
import cn.ellerotta.ktproto.api.types.InputPeerUser
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
class ForwardFlowTest {
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
    fun selectedMessagesAreForwardedToAPickedChat() {
        fake.addSavedMessages(2)
        val forwarded = fake.addForwardedMessage()
        compose.logIn()
        compose.openSavedMessages(fake.selfId)

        compose.waitForTag("forward_$forwarded")
        compose.waitUntilIdle { compose.onAllNodes(hasText("Kotlin News") and hasAnyAncestor(hasTestTag("forward_$forwarded")), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_10"))
        compose.onNodeWithTag("message_10").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionSelect").performClick()
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("select_11"))
        compose.onNodeWithTag("select_11").performClick()
        compose.onNodeWithTag("forwardSelected").performClick()
        compose.waitForTag("forwardSheet")
        compose.onNodeWithTag("dropAuthor").performClick()
        compose.onNodeWithTag("forwardTargets").performScrollToNode(hasTestTag("forwardTo_u501"))
        compose.onNodeWithTag("forwardTo_u501").performClick()
        compose.waitForTag("forwardStrip")
        assertTrue(fake.lastForward == null)
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.lastForward != null }
        val call = assertNotNull(fake.lastForward)
        assertEquals(InputPeerSelf, call.fromPeer)
        assertEquals(listOf(10, 11), call.id)
        assertEquals(2, call.randomId.toSet().size)
        assertEquals(501L, (call.toPeer as InputPeerUser).userId)
        assertTrue(call.dropAuthor)
        assertFalse(call.dropMediaCaptions)
        compose.waitForTag("title")
        compose.waitUntilIdle { compose.onAllNodes(hasText("Alice Example") and hasTestTag("title")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("back").performClick()

        compose.waitForTag("forward_$forwarded")
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("forward_$forwarded"))
        compose.onNodeWithTag("forward_$forwarded").performClick()
        compose.waitUntilIdle { fake.historyCalls.any { it.first == FakeTelegram.NEWS_ID && it.second == 6 } }
        compose.waitForTag("joinChat")
    }

    @Test
    fun aReplyQuoteJumpsToTheOriginalFurtherBack() {
        fake.addSavedMessages(60)
        val reply = fake.addReply(replyTo = 10)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)

        compose.waitForTag("message_$reply")
        compose.onNode(hasTestTag("replyQuote") and hasAnyAncestor(hasTestTag("message_$reply"))).performClick()
        compose.waitUntilIdle { fake.historyCalls.any { it.first == 0L && it.second == 11 && it.third == -25 } }
        compose.waitForTag("highlight_10")
    }

    @Test
    fun aLiveReplyInAPrivateChatIsNoThread() {
        fake.addSavedMessages(3)
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("message_12")
        val reply = runBlocking { fake.pushReply(replyTo = 10) }
        compose.waitForTag("message_$reply")
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_10"))
        assertTrue(compose.onAllNodes(hasTestTag("replies_10"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
