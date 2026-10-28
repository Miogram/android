package app.miogram

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.miogram.data.telegram.AuthRepository
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.SignInResult
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import app.miogram.push.PushHandler
import app.miogram.push.PushRegistrationWorker
import cn.ellerotta.ktproto.testing.FakeAnswer
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class PushTokenFlowTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @Inject lateinit var push: PushHandler

    @Inject lateinit var auth: AuthRepository

    @Before
    fun setUp() {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    private fun job(): WorkInfo? = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PushRegistrationWorker.TOKEN_NAME).get().lastOrNull()

    private fun networkIsBack(info: WorkInfo) = assertNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(info.id)

    @Test
    fun aNewTokenWaitsForTheNetworkThenRegistersOnceWhateverRotatedMeanwhile(): Unit = runBlocking {
        withTimeout(90_000) {
            telegram.connect(12345, "hash")
            val request = auth.sendCode("+999 66 1 4242")
            fake.selfId = assertIs<SignInResult.LoggedIn>(auth.signIn(request, "11111")).profile.id

            push.onNewToken("fcm-1")
            push.onNewToken("fcm-2")
            val waiting = assertNotNull(job())
            assertEquals(WorkInfo.State.ENQUEUED, waiting.state)
            assertEquals(NetworkType.CONNECTED, waiting.constraints.requiredNetworkType)
            delay(300)
            assertTrue(fake.cluster.push.registerRequests.isEmpty(), "nothing is sent before the network is back")

            networkIsBack(waiting)
            while (job()?.state != WorkInfo.State.SUCCEEDED) delay(50)
            assertEquals(listOf("fcm-2"), fake.cluster.push.registerRequests.map { it.token })
            assertEquals(true, fake.cluster.push.registerRequests.single().noMuted)
            assertEquals(256, fake.cluster.push.registerRequests.single().secret.size)
            assertEquals(WorkInfo.State.ENQUEUED, WorkManager.getInstance(context).getWorkInfosForUniqueWork(PushRegistrationWorker.NAME).get().single().state)
        }
    }

    @Test
    fun aRefusedRegistrationIsTriedAgainWithABackoff(): Unit = runBlocking {
        withTimeout(90_000) {
            telegram.connect(12345, "hash")
            val request = auth.sendCode("+999 66 1 4242")
            fake.selfId = assertIs<SignInResult.LoggedIn>(auth.signIn(request, "11111")).profile.id

            fake.cluster.push.registerErrors += FakeAnswer.Error(400, "TOKEN_INVALID")
            push.onNewToken("fcm-3")
            networkIsBack(assertNotNull(job()))
            while (job()?.state != WorkInfo.State.SUCCEEDED) {
                val waiting = assertNotNull(job())
                if (waiting.state == WorkInfo.State.ENQUEUED && waiting.runAttemptCount > 0) networkIsBack(waiting)
                delay(50)
            }
            assertTrue(assertNotNull(job()).runAttemptCount >= 1, "a refused attempt came before the one that worked")
            assertTrue(fake.cluster.push.registerErrors.isEmpty(), "the refusal was used up")
            assertEquals(listOf("fcm-3"), fake.cluster.push.registerRequests.map { it.token })
        }
    }
}
