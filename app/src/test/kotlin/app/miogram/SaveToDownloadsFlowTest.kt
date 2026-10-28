package app.miogram

import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.TelegramModule
import cn.ellerotta.ktproto.testing.FakeCluster
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(TelegramModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class SaveToDownloadsFlowTest {
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("poc").toFile()
    private lateinit var store: FakeMediaStore

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
    lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        hilt.inject()
        store = FakeMediaStore.install()
    }

    @After
    fun tearDown() {
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun aFileIsDownloadedThenCopiedFromTheActionsSheet() {
        val id = fake.addDocumentMessage()
        openSaved()
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("message_$id"))
        compose.onNodeWithTag("message_$id").performTouchInput { longClick(Offset(centerX, height - 4f)) }
        compose.onNodeWithTag("actionSaveDownloads").performClick()

        compose.waitForTag("savedDownload")
        assertTrue(fake.fileOffsets.isNotEmpty())
        val row = store.rows.single()
        assertEquals("Download/ktproto", row.inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals("big.bin", row.inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("application/octet-stream", row.inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertEquals(0, row.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertContentEquals(fake.cluster.fileBytes, row.file.readBytes())
        compose.waitForTag("downloaded_doc${FakeCluster.FILE_ID}", unmerged = true)
    }

    @Test
    fun withTheSettingADownloadIsCopiedByItself() {
        settings.setAutoSaveDownloads(true)
        fake.addDocumentMessage()
        openSaved()
        val key = "doc${FakeCluster.FILE_ID}"
        compose.onNodeWithTag("messages").performScrollToNode(hasTestTag("download_$key"))
        compose.onNodeWithTag("download_$key").performClick()
        compose.waitForTag("savedDownload")
        assertEquals(listOf("big.bin"), store.rows.map { it.inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) })
        assertContentEquals(fake.cluster.fileBytes, store.rows.single().file.readBytes())
    }

    private fun openSaved() {
        compose.logIn()
        compose.openSavedMessages(fake.selfId)
        compose.waitForTag("messages")
    }
}
