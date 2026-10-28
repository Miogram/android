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
import app.miogram.links.LinkRouter
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputPeerChannel
import cn.ellerotta.ktproto.api.types.InputPeerUser
import cn.ellerotta.ktproto.api.types.InputReportReasonOther
import cn.ellerotta.ktproto.api.types.InputUser
import cn.ellerotta.ktproto.api.types.InputReportReasonSpam
import cn.ellerotta.ktproto.api.types.PeerSettings
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import javax.inject.Inject
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
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
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "w412dp-h892dp")
class SafetyFlowTest {
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

    @Inject
    lateinit var router: LinkRouter

    private fun openAlice() {
        compose.logIn()
        compose.onNodeWithText("Alice Example").performClick()
        compose.waitForTag("chatHeader")
    }

    private fun openChatMenu() {
        compose.onNodeWithTag("avatar").performTouchInput { longClick() }
        compose.waitForTag("chatMenu")
    }

    private fun present(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theBlockedListUnblocksAndPicksSomeoneToBlock() {
        compose.logIn()
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("settings")
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("openPrivacySettings"))
        compose.onNodeWithTag("openPrivacySettings").performClick()
        compose.waitForText("1 user")
        compose.onNodeWithTag("blockedUsersRow").performClick()
        compose.waitForTag("blocked_u507")
        compose.waitForText("Frank")

        compose.onNodeWithTag("blockUser").performClick()
        compose.waitForTag("pickChats")
        compose.waitForTag("dialog_u501")
        compose.onNodeWithTag("dialog_u501").performClick()
        compose.waitUntilIdle { fake.safety.blocks.isNotEmpty() }
        assertEquals(501L, (fake.safety.blocks.single().id as InputPeerUser).userId)
        compose.waitForTag("blocked_u501")

        compose.onNodeWithTag("unblock_u507").performClick()
        compose.waitUntilIdle { fake.safety.unblocks.isNotEmpty() }
        assertEquals(507L, (fake.safety.unblocks.single().id as InputPeerUser).userId)
        compose.waitUntilIdle { !present("blocked_u507") }
        assertTrue(present("blocked_u501"))
    }

    @Test
    fun aChatIsBlockedFromItsMenuAndTheBannerTakesTheComposersPlace() {
        openAlice()
        compose.waitForTag("messageInput")
        openChatMenu()
        compose.onNodeWithTag("menu_block").performClick()
        compose.waitForTag("confirmBlock")
        assertTrue(fake.safety.blocks.isEmpty())
        compose.onNodeWithTag("confirmBlock").performClick()
        compose.waitForTag("blockedBanner")
        assertEquals(501L, (fake.safety.blocks.single().id as InputPeerUser).userId)
        assertTrue(!present("messageInput"))

        openChatMenu()
        compose.waitForTag("menu_unblock")
        compose.onNodeWithTag("menu_unblock").performClick()
        compose.waitUntilIdle { !present("blockedBanner") }
        assertEquals(1, fake.safety.unblocks.count { (it.id as InputPeerUser).userId == 501L })
        compose.waitForTag("messageInput")

        runBlocking { fake.safety.pushBlocked(fake.alice.id, true) }
        compose.waitForTag("blockedBanner")
        compose.onNodeWithTag("unblockChat").performClick()
        compose.waitUntilIdle { !present("blockedBanner") }
        assertEquals(2, fake.safety.unblocks.count { (it.id as InputPeerUser).userId == 501L })
    }

    @Test
    fun aChatIsReportedWithTheReasonsAndCommentsAreRequiredWhereAsked() {
        openAlice()
        openChatMenu()
        compose.onNodeWithTag("menu_report").performClick()
        compose.waitForTag("reportSheet")
        compose.waitForTag("reportOption_Spam")
        compose.onNodeWithTag("reportOption_Spam").performClick()
        compose.waitForTag("reportClose")
        assertEquals(InputReportReasonSpam, fake.safety.peerReports.single().reason)
        compose.onNodeWithTag("reportClose").performClick()
        compose.waitUntilIdle { !present("reportSheet") }

        openChatMenu()
        compose.onNodeWithTag("menu_report").performClick()
        compose.waitForTag("reportOption_Other")
        compose.onNodeWithTag("reportOption_Other").performClick()
        compose.waitForTag("reportComment")
        assertEquals(1, fake.safety.peerReports.size)
        compose.onNodeWithTag("reportComment").performTextInput("fake giveaway")
        compose.onNodeWithTag("reportSend").performClick()
        compose.waitForTag("reportClose")
        assertEquals(InputReportReasonOther, fake.safety.peerReports.last().reason)
        assertEquals("fake giveaway", fake.safety.peerReports.last().message)
    }

    @Test
    fun aMessageIsReportedThroughTheMenuTelegramSends() {
        hilt.inject()
        compose.logIn()
        compose.runOnUiThread { router.submit("https://t.me/kotlinnews/30") }
        compose.waitForTag("highlight_30")
        compose.onNodeWithTag("message_30").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.waitForTag("actionReport")
        compose.onNodeWithTag("actionReport").performClick()
        compose.waitForText("What is wrong?")
        val sent = fake.safety.reports.single()
        assertEquals(listOf(30), sent.id)
        assertEquals(FakeTelegram.NEWS_ID, (sent.peer as InputPeerChannel).channelId)
        assertContentEquals(ByteArray(0), sent.option)
        compose.onNodeWithText("Violence").performClick()
        compose.waitForText("Which kind?")
        compose.onNodeWithText("Threats").performClick()
        compose.waitForTag("reportClose")
        assertContentEquals(byteArrayOf(2, 2), fake.safety.reports.last().option)
        compose.onNodeWithTag("reportClose").performClick()
        compose.waitUntilIdle { !present("reportSheet") }
    }

    @Test
    fun theActionBarOfAStrangerOffersItsButtonsAndIsDismissed() {
        fake.safety.bars[fake.alice.id] = PeerSettings(reportSpam = true, addContact = true, blockContact = true)
        openAlice()
        compose.waitForTag("peerBar")
        compose.waitForTag("peerBarAdd")
        compose.waitForTag("peerBarBlock")
        compose.waitForTag("peerBarSpam")

        compose.onNodeWithTag("peerBarDismiss").performClick()
        compose.waitUntilIdle { !present("peerBar") }
        compose.waitUntilIdle { fake.safety.hiddenBars.isNotEmpty() }
        assertEquals(501L, (fake.safety.hiddenBars.single().peer as InputPeerUser).userId)
    }

    @Test
    @Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "w320dp-h470dp")
    fun addingToTheContactsFromTheBarAsksForTheNameAndTakesTheButtonOff() {
        fake.safety.bars[fake.alice.id] = PeerSettings(reportSpam = true, addContact = true)
        openAlice()
        compose.waitForTag("peerBarAdd")
        compose.onNodeWithTag("peerBarAdd").performClick()
        compose.waitForTag("confirmAddContact")
        compose.onNodeWithTag("confirmAddContact").performClick()
        compose.waitUntilIdle { fake.contacts.adds.isNotEmpty() }
        val added = fake.contacts.adds.single()
        assertEquals(501L, (added.id as InputUser).userId)
        assertEquals("Alice", added.firstName)
        assertEquals("Example", added.lastName)
        compose.waitUntilIdle { !present("peerBarAdd") }
        assertTrue(present("peerBarSpam"))
    }

    @Test
    fun spamFromTheBarIsReportedAndBlockingAsksFirst() {
        fake.safety.bars[fake.alice.id] = PeerSettings(reportSpam = true, blockContact = true)
        openAlice()
        compose.waitForTag("peerBarBlock")
        compose.onNodeWithTag("peerBarBlock").performClick()
        compose.waitForTag("confirmBlock")
        assertTrue(fake.safety.blocks.isEmpty())
        compose.onNodeWithTag("confirmBlock").performClick()
        compose.waitForTag("blockedBanner")
        assertTrue(!present("peerBar"))
        compose.onNodeWithTag("unblockChat").performClick()
        compose.waitUntilIdle { !present("blockedBanner") }

        fake.safety.bars[fake.alice.id] = PeerSettings(reportSpam = true)
        runBlocking { fake.safety.pushBar(fake.alice.id, PeerSettings(reportSpam = true)) }
        compose.waitForTag("peerBarSpam")
        compose.onNodeWithTag("peerBarSpam").performClick()
        compose.waitUntilIdle { fake.safety.spamReports.isNotEmpty() }
        assertEquals(501L, (fake.safety.spamReports.single().peer as InputPeerUser).userId)
        compose.waitUntilIdle { !present("peerBar") }
    }

    @Test
    fun aStoryIsReportedFromTheViewer() {
        compose.logIn()
        compose.waitUntilIdle {
            compose.onAllNodes(hasTestTag("dialogAvatar_u501"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        fake.stories.holdFiles = CompletableDeferred()
        compose.onNode(hasTestTag("dialogAvatar_u501"), useUnmergedTree = true).performClick()
        compose.waitForTag("story_u501_2")
        compose.onNodeWithTag("storyMenu").performClick()
        compose.waitForTag("storyReport")
        compose.onNodeWithTag("storyReport").performClick()
        compose.waitForText("What is wrong?")
        val first = fake.safety.storyReports.single()
        assertEquals(listOf(2), first.id)
        compose.onNodeWithText("Spam").performClick()
        compose.waitForTag("reportClose")
        assertEquals(2, fake.safety.storyReports.size)
        assertContentEquals(byteArrayOf(1), fake.safety.storyReports.last().option)
        compose.onNodeWithTag("reportClose").performClick()
        compose.waitUntilIdle { !present("reportSheet") }
        fake.stories.holdFiles?.complete(Unit)
    }
}
