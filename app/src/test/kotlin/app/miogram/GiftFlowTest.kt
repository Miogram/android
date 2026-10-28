package app.miogram

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
class GiftFlowTest {
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
    fun profileGiftsSheetsAndTheChatChip() {
        fake.addGiftServiceMessage()
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

        compose.waitForTag("openChats")
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("giftChip_10")
        compose.onNodeWithTag("giftChipDetail", useUnmergedTree = true).assertTextEquals("50 stars")
        compose.onNodeWithTag("back").performClick()

        compose.waitForTag("openSettings")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("openOwnProfile")
        compose.onNodeWithTag("openOwnProfile").performClick()
        compose.waitForTag("peerProfile")
        openGiftsPage()

        scrollGiftsTo("giftCollection_${FakeTelegram.GIFT_COLLECTION}")
        compose.onNodeWithTag("giftCollection_all").assertExists()
        compose.onNodeWithTag("giftCollection_${FakeTelegram.GIFT_COLLECTION}").performClick()
        compose.waitUntilIdle { gone("gift_giftm70") && !gone("gift_giftm71") }
        compose.onNodeWithTag("giftsMore").assertDoesNotExist()
        compose.onNodeWithTag("giftCollection_all").performClick()
        compose.waitUntilIdle { !gone("gift_giftm70") }

        scrollGiftsTo("giftsMore")
        compose.onNodeWithTag("gift_giftm71").assertExists()
        compose.onNodeWithTag("giftsMore").performClick()
        scrollGiftsTo("gift_giftm72")
        compose.onNodeWithTag("gift_giftm72").performClick()
        compose.waitForTag("giftShow")
        compose.onNodeWithTag("giftShow").performScrollTo().performClick()
        compose.waitUntilIdle { !fake.ownGifts[2].unsaved }
    }

    private fun openGiftsPage() {
        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("peerProfile").performScrollToKey("pages") }.isSuccess }
        compose.waitForTag("profileTab_Gifts")
        compose.onNodeWithTag("profileTab_Gifts").performClick()
        compose.waitForTag("profilePage_Gifts")
    }

    private fun scrollGiftsTo(tag: String) = compose.waitUntilIdle {
        runCatching { compose.onNodeWithTag("profilePage_Gifts").performScrollToNode(hasTestTag(tag)) }.isSuccess
    }

    private fun gone(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
}
