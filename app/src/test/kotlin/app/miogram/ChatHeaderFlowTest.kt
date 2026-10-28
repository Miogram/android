package app.miogram

import android.net.ConnectivityManager
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import app.miogram.FakeTelegram.Companion.NOW
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.core.ui.components.profile.formatPhone
import cn.ellerotta.ktproto.api.types.PeerUser
import cn.ellerotta.ktproto.api.types.SendMessageCancelAction
import cn.ellerotta.ktproto.api.types.SendMessageRecordAudioAction
import cn.ellerotta.ktproto.api.types.SendMessageTypingAction
import cn.ellerotta.ktproto.api.types.SendMessageUploadPhotoAction
import cn.ellerotta.ktproto.api.types.TypeSendMessageAction
import cn.ellerotta.ktproto.api.types.UpdateChannelUserTyping
import cn.ellerotta.ktproto.api.types.UpdateShort
import cn.ellerotta.ktproto.api.types.UpdateUserTyping
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ChatHeaderFlowTest {
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

    private fun typing(user: Long, action: TypeSendMessageAction) = runBlocking { fake.cluster.updates.push(UpdateShort(UpdateUserTyping(userId = user, action = action), NOW)) }

    private fun typingInDevs(user: Long, action: TypeSendMessageAction) = runBlocking {
        fake.cluster.updates.push(UpdateShort(UpdateChannelUserTyping(channelId = FakeGroups.DEVS_ID, fromId = PeerUser(user), action = action), NOW))
    }

    private fun waitForText(text: String) = compose.waitUntilIdle { compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitWithoutText(text: String) = compose.waitUntilIdle { compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aPrivateChatShowsTheEmojiStatusAndWhatThePeerDoes() {
        fake.aliceShowsStatus = true
        compose.logIn()
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("messageInput")
        compose.waitForTag("emojiStatus", unmerged = true)
        waitForText("last seen a long time ago")

        typing(fake.alice.id, SendMessageRecordAudioAction)
        waitForText("recording audio")
        compose.waitUntilIdle { has("activity_Waves") }
        typing(fake.alice.id, SendMessageUploadPhotoAction(40))
        waitForText("sending a photo")
        compose.waitUntilIdle { has("activity_Bar") }
        typing(fake.alice.id, SendMessageCancelAction)
        waitForText("last seen a long time ago")
        compose.waitUntilIdle { !has("activity_Bar") }
    }

    @Test
    fun aGroupShowsItsMembersWithWhoIsOnlineAndWhoIsTyping() {
        compose.logIn()
        compose.waitForTag("dialogs")
        compose.onNodeWithTag("dialogs").performScrollToKey("ch${FakeGroups.DEVS_ID}")
        compose.onNodeWithTag("dialog_ch${FakeGroups.DEVS_ID}").performClick()
        compose.waitForTag("messageInput")
        waitForText("65 members, ${FakeGroups.DEVS_ONLINE} online")

        typingInDevs(fake.alice.id, SendMessageTypingAction)
        waitForText("Alice is typing")
        typingInDevs(fake.bob.id, SendMessageTypingAction)
        waitForText("Alice and Bob are typing")
        typingInDevs(fake.bob.id, SendMessageUploadPhotoAction(10))
        waitForText("Alice and Bob are typing")
        typingInDevs(fake.alice.id, SendMessageCancelAction)
        waitForText("Bob is sending a photo")
        typingInDevs(fake.bob.id, SendMessageCancelAction)
        waitForText("65 members, ${FakeGroups.DEVS_ONLINE} online")
        waitWithoutText("Bob is sending a photo")
    }

    @Test
    fun theBackPillCountsTheOtherUnreadChatsThatAreNotMuted() {
        fake.groups.inDialogs = false
        compose.logIn()
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("messageInput")
        compose.waitForTag("backBadge", unmerged = true)
        compose.onNode(hasAnyAncestor(hasTestTag("backBadge")) and hasText("1"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun theHeaderWaitsForTheNetworkThenConnectsThenIsQuiet() {
        fake.groups.inDialogs = false
        compose.logIn()
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("messageInput")
        waitForText("last seen a long time ago")

        val connectivity = shadowOf(compose.activity.getSystemService(ConnectivityManager::class.java))
        val network = compose.activity.getSystemService(ConnectivityManager::class.java).activeNetwork!!
        compose.runOnUiThread { connectivity.networkCallbacks.forEach { it.onLost(network) } }
        waitForText("Waiting for network…")
        waitWithoutText("last seen a long time ago")
        compose.runOnUiThread { connectivity.networkCallbacks.forEach { it.onAvailable(network) } }
        waitForText("last seen a long time ago")
        waitWithoutText("Waiting for network…")

        runBlocking { telegram.disconnect() }
        waitForText("Connecting…")
        runBlocking { telegram.reconnect() }
        waitForText("last seen a long time ago")
        waitWithoutText("Connecting…")
    }

    @Test
    fun aStrangersChatIsTitledByTheirNumber() {
        fake.groups.inDialogs = false
        fake.listed += fake.carol
        compose.logIn()
        compose.waitForText("Carol Writer")
        compose.onNode(hasText("Carol Writer")).performClick()
        compose.waitForTag("messageInput")
        val number = formatPhone(compose.activity, fake.carol.phone!!)
        waitForText(number)
        waitWithoutText("Carol Writer")
    }

    @Test
    fun mutedChatsDoNotCountOnTheBackPill() {
        fake.groups.inDialogs = false
        fake.muted += fake.bob.id
        compose.logIn()
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("messageInput")
        compose.waitForText("last seen a long time ago")
        compose.onNodeWithTag("backBadge", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aScamTagShowsAfterTheTitle() {
        fake.aliceIsScam = true
        compose.logIn()
        compose.onNode(hasText("Alice Example")).performClick()
        compose.waitForTag("messageInput")
        compose.waitForTag("scam", unmerged = true)
        waitForText("SCAM")
    }
}
