package app.miogram

import android.view.WindowManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.api.types.InputPrivacyKeyStatusTimestamp
import cn.ellerotta.ktproto.api.types.InputPrivacyValueDisallowAll
import cn.ellerotta.ktproto.api.types.InputPrivacyValueDisallowUsers
import cn.ellerotta.ktproto.api.types.InputUser
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class PrivacyFlowTest {
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
    fun showAndChangePrivacy() {
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
        compose.waitForText("Alice Example")
        compose.onNodeWithTag("openSettings").performClick()
        compose.waitForTag("settings")
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("openPrivacySettings"))
        compose.onNodeWithTag("openPrivacySettings").performClick()

        compose.waitUntilIdle { runCatching { compose.onNodeWithTag("privacy").performScrollToNode(hasTestTag("privacy_LastSeen")) }.isSuccess }
        compose.waitForText("My contacts (−1)")
        compose.waitForText("Nobody")
        compose.waitForText("Everybody")
        compose.onNodeWithTag("privacy").performScrollToNode(hasTestTag("privacy_LastSeen"))
        compose.onNodeWithTag("privacy_LastSeen").performClick()
        compose.waitForTag("privacyChoice_Nobody")
        compose.onNodeWithTag("privacyChoice_Nobody").performClick()
        compose.waitUntilIdle { fake.lastSetPrivacy != null }
        val sent = assertNotNull(fake.lastSetPrivacy)
        assertEquals(InputPrivacyKeyStatusTimestamp, sent.key)
        assertEquals(2, sent.rules.size)
        val kept = assertIs<InputPrivacyValueDisallowUsers>(sent.rules[0])
        assertEquals(listOf(501L to 9L), kept.users.map { (it as InputUser).userId to it.accessHash })
        assertEquals(InputPrivacyValueDisallowAll, sent.rules[1])
        compose.waitForText("Nobody (−1)")

        compose.onNodeWithTag("privacy").performScrollToNode(hasTestTag("hideReadTime"))
        compose.onNodeWithTag("hideReadTime").performClick()
        compose.waitUntilIdle { fake.globalPrivacy.hideReadMarks }
        assertTrue(fake.globalPrivacy.keepArchivedUnmuted)

        compose.onNodeWithTag("privacy").performScrollToNode(hasTestTag("accountTtl"))
        compose.waitForText("1 year")
        compose.onNodeWithTag("accountTtl").performClick()
        compose.waitForTag("ttl_180")
        compose.onNodeWithTag("ttl_180").performClick()
        compose.waitUntilIdle { fake.accountTtl == 180 }
        compose.waitForText("6 months")

        val secure = { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
        assertFalse(secure())
        compose.onNodeWithTag("privacy").performScrollToNode(hasTestTag("secureWindow"))
        compose.onNodeWithTag("secureWindow").performClick()
        compose.waitUntilIdle { secure() }
        compose.onNodeWithTag("secureWindow").performClick()
        compose.waitUntilIdle { !secure() }
    }
}
