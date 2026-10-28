package app.miogram

import android.os.Looper
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.miogram.FakeGroups.Companion.DEVS_ID
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.ChannelParticipantsRecent
import cn.ellerotta.ktproto.api.types.InputMessageEntityMentionName
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputUser
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "w412dp-h892dp")
class MentionsFlowTest {
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

    private fun openDialog(key: String) {
        compose.waitForTag("dialogs")
        compose.onNodeWithTag("dialogs").performScrollToKey(key)
        compose.onNodeWithTag("dialog_$key").performClick()
        compose.waitForTag("messageInput")
    }

    private fun type(text: String) = compose.onNodeWithTag("messageInput").performTextInput(text)

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun anInlineBotAnswersUnderItsThreeLetterName() {
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messageInput")

        type("@gif kotlin")
        compose.waitForTag("inline_kotlin")
        assertEquals("gif", fake.resolvedNames.last())
        assertEquals("kotlin", fake.bots.inlineCalls.last().query)
        assertEquals("", fake.bots.inlineCalls.first().offset)

        compose.onNodeWithTag("inline_kotlin").performClick()
        compose.waitUntilIdle { fake.bots.inlineSends.isNotEmpty() }
        assertEquals("kotlin", fake.bots.inlineSends.single().id)
        compose.waitForText("Kotlin is a modern language")
    }

    @Test
    fun membersAreMentionedByUsernameOrByName() {
        compose.logIn()
        openDialog("ch$DEVS_ID")

        val calls = fake.groups.members.participantCalls
        val before = calls.size
        type("hi @car")
        compose.waitForTag("mention_u${fake.carol.id}")
        compose.onNodeWithTag("mention_u${fake.carol.id}").performClick()
        compose.onNode(editableText("hi @carol_writer ")).assertExists()
        assertEquals(ChannelParticipantsRecent, calls.last().filter)

        type("and @ali")
        compose.waitForTag("mention_u${fake.alice.id}")
        compose.onNodeWithTag("mention_u${fake.alice.id}").performClick()
        compose.onNode(editableText("hi @carol_writer and Alice ")).assertExists()
        assertFalse(shown("mentions"))
        assertEquals(before + 1, calls.size)

        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.message == "hi @carol_writer and Alice" }
        val mention = fake.lastSendMessage!!.entities.orEmpty().single() as InputMessageEntityMentionName
        assertEquals(21 to 5, mention.offset to mention.length)
        assertEquals(fake.alice.id, (mention.userId as InputUser).userId)
    }

    @Test
    fun aPrivateChatOffersTheOtherSide() {
        compose.logIn()
        openDialog("u${fake.alice.id}")

        type("@")
        compose.waitForTag("mention_u${fake.alice.id}")
        compose.onNodeWithTag("mention_u${fake.alice.id}").performClick()
        type("hi")
        compose.onNode(editableText("Alice hi")).assertExists()
        compose.onNodeWithTag("send").performClick()
        compose.waitUntilIdle { fake.lastSendMessage?.message == "Alice hi" }
        val mention = fake.lastSendMessage!!.entities.orEmpty().single() as InputMessageEntityMentionName
        assertEquals(0 to 5, mention.offset to mention.length)
    }

    @Test
    fun aBotsNameAndASpaceTurnsMentionsIntoInlineResults() {
        compose.logIn()
        openDialog("ch$DEVS_ID")

        type("@gi")
        compose.waitForTag("mention_u${FakeBots.GIF_BOT_ID}")
        compose.onNodeWithTag("mention_u${FakeBots.GIF_BOT_ID}").performClick()
        compose.waitForTag("composerHint", unmerged = true)
        type("kotlin")
        compose.waitForTag("inline_kotlin")
        assertFalse(shown("mentions"))
        assertEquals(DEVS_ID, (fake.bots.inlineCalls.last().peer as InputPeerChannel).channelId)

        compose.onNodeWithTag("messageInput").performTextClearance()
        type("@car")
        compose.waitForTag("mention_u${fake.carol.id}")
        assertFalse(shown("inlineResults"))
        compose.onNodeWithTag("mention_u${fake.carol.id}").performClick()
        compose.onNode(editableText("@carol_writer ")).assertExists()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        compose.waitForIdle()
        assertTrue("carol_writer" !in fake.resolvedNames, fake.resolvedNames.toString())
        assertFalse(shown("inlineResults"))
    }
}
