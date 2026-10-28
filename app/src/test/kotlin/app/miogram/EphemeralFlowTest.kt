package app.miogram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import app.miogram.links.LinkRouter
import cn.ellerotta.core.model.EPHEMERAL_IDS
import cn.ellerotta.core.ui.R
import cn.ellerotta.ktproto.api.types.InputReplyToEphemeralMessage
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.InputUserSelf
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class EphemeralFlowTest {
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

    @Inject
    lateinit var router: LinkRouter

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun gone(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()

    private fun longPress(id: Int) {
        compose.scrollToMessageNode(hasTestTag("message_$id"))
        compose.onNodeWithTag("message_$id").performTouchInput { longClick(Offset(centerX, height - 4f)) }
    }

    @Test
    fun ephemeralMessagesAreShownAnsweredPressedAndDeleted() {
        hilt.inject()
        compose.logIn()
        compose.runOnUiThread { router.submit("tg://resolve?domain=helperbot") }
        compose.waitForText("Pick an action")
        val bot = fake.helperBot.id

        val id = runBlocking { fake.ephemeral.botSays("Only you can read this", button = "Reveal") }
        val row = EPHEMERAL_IDS + id
        compose.waitForText("Only you can read this")
        compose.waitForTag("ephemeral_$row", unmerged = true)
        compose.onNodeWithText(compose.activity.getString(R.string.chat_ephemeral_you)).assertExists()

        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("button_${row}_0_0"))
        compose.onNodeWithTag("button_${row}_0_0").performClick()
        compose.waitUntilIdle { fake.ephemeral.callbackCalls.isNotEmpty() }
        assertEquals(id, fake.ephemeral.callbackCalls.single().id)
        assertTrue(fake.bots.callbackCalls.isEmpty())
        compose.waitUntilIdle { ShadowToast.showedToast("Secret pong") }

        longPress(row)
        compose.waitForTag("actionReply")
        assertTrue(gone("actionForward") && gone("actionSelect") && gone("actionEdit"))
        compose.onNodeWithTag("actionReply").performClick()
        compose.onNodeWithTag("messageInput").performTextInput("thanks")
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.ephemeral.sendCalls.isNotEmpty() }
        val answer = fake.ephemeral.sendCalls.single()
        assertEquals("thanks", answer.message)
        assertEquals(bot, (answer.receiverId as InputUser).userId)
        assertEquals(id, (answer.replyTo as InputReplyToEphemeralMessage).id)
        assertNull(fake.lastSendMessage)
        compose.waitForText("thanks")
        compose.waitForText(compose.activity.getString(R.string.chat_ephemeral_to, "helperbot"))

        compose.onNodeWithTag("messageInput").performTextInput("/secret now")
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.ephemeral.sendCalls.size == 2 }
        assertEquals("/secret now", fake.ephemeral.sendCalls.last().message)
        assertNull(fake.ephemeral.sendCalls.last().replyTo)
        compose.onNodeWithTag("messageInput").performTextInput("/help")
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.message == "/help" }
        assertEquals(2, fake.ephemeral.sendCalls.size)

        runBlocking { fake.ephemeral.edit(id, "Only you can read this, edited") }
        compose.waitForText("Only you can read this, edited")

        longPress(row)
        compose.waitForTag("actionDelete")
        compose.onNodeWithTag("actionDelete").performClick()
        compose.waitForTag("confirmDelete")
        assertTrue(gone("revoke"))
        compose.onNodeWithTag("confirmDelete").performClick()
        compose.waitUntilIdle { fake.ephemeral.deleteCalls.isNotEmpty() && gone("message_$row") }
        assertEquals(id, fake.ephemeral.deleteCalls.single().id)
        assertEquals(InputUserSelf, fake.ephemeral.deleteCalls.single().receiverId)

        val other = runBlocking { fake.ephemeral.botSays("Soon gone") }
        compose.waitForText("Soon gone")
        runBlocking { fake.ephemeral.delete(listOf(other)) }
        compose.waitUntilIdle { gone("message_${EPHEMERAL_IDS + other}") }
    }
}
