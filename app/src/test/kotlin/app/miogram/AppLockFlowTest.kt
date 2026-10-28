package app.miogram

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.LockModule
import app.miogram.di.TelegramModule
import app.miogram.lock.DeviceAuth
import cn.ellerotta.core.model.AppPrivacy
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
import kotlin.test.assertTrue

@HiltAndroidTest
@UninstallModules(TelegramModule::class, LockModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class AppLockFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private val deviceAuth = FakeDeviceAuth()

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
    val auth: DeviceAuth = deviceAuth

    @BindValue
    @JvmField
    val settings = SettingsStore(ApplicationProvider.getApplicationContext<Context>()).apply {
        setAppPrivacy(AppPrivacy(lock = true, lockAfterMinutes = 0))
    }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun lockHidesTheContentUntilUnlocked() {
        compose.waitForTag("lockScreen")
        compose.waitUntilIdle { deviceAuth.pending != null }
        assertTrue(compose.onAllNodes(hasTestTag("start")).fetchSemanticsNodes().isEmpty())
        deviceAuth.answer(false)
        compose.onNodeWithTag("unlock").performClick()
        assertEquals(2, deviceAuth.prompts)
        deviceAuth.answer(true)
        compose.waitForTag("start")
        compose.waitUntilIdle { compose.onAllNodes(hasTestTag("lockScreen")).fetchSemanticsNodes().isEmpty() }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForTag("lockScreen")
        assertTrue(compose.onAllNodes(hasTestTag("start")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun blockScreenshotsSetsFlagSecure() {
        compose.waitUntilIdle { deviceAuth.pending != null }
        deviceAuth.answer(true)
        compose.waitForTag("start")
        assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        settings.setAppPrivacy(settings.appPrivacy.value.copy(secureWindow = true))
        compose.waitUntilIdle { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
        settings.setAppPrivacy(settings.appPrivacy.value.copy(secureWindow = false))
        compose.waitUntilIdle { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0 }
    }
}

class FakeDeviceAuth : DeviceAuth {
    @Volatile var available = true
    @Volatile var pending: ((Boolean) -> Unit)? = null
    @Volatile var prompts = 0

    override fun available() = available

    override fun authenticate(activity: FragmentActivity, title: String, subtitle: String?, onResult: (Boolean) -> Unit) {
        prompts++
        pending = onResult
    }

    fun answer(confirmed: Boolean) {
        val callback = pending ?: error("no prompt shown")
        pending = null
        callback(confirmed)
    }
}
