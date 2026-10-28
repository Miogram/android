package app.miogram

import app.miogram.data.telegram.PremiumSpeedNotice
import app.miogram.data.telegram.PremiumSpeedNotices
import app.miogram.data.telegram.SignInResult
import cn.ellerotta.core.model.AppConfig
import cn.ellerotta.ktproto.api.types.InputDocumentFileLocation
import cn.ellerotta.ktproto.client.downloadFile
import cn.ellerotta.ktproto.testing.FakeAnswer
import cn.ellerotta.ktproto.testing.FakeCluster
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class PremiumSpeedTest {
    @Test
    fun slowedDownloadsAreAnnouncedOncePerNotifyPeriod(): Unit = runBlocking {
        FakeTelegram().use { fake ->
            val dir = Files.createTempDirectory("poc").toFile()
            val repos = TestRepositories(dir, fake)
            withTimeout(90_000) {
                repos.telegram.connect(12345, "hash")
                fake.selfId = assertIs<SignInResult.LoggedIn>(repos.auth.signIn(repos.auth.sendCode("9996624242"), "22222")).profile.id
                fake.streams.numbers["upload_premium_speedup_download"] = 7
                fake.streams.numbers["upload_premium_speedup_notify_period"] = 600
                val notices = PremiumSpeedNotices(repos.telegram, repos.appConfig)
                val time = TestTimeSource()
                notices.timeSource = time
                val seen = CopyOnWriteArrayList<PremiumSpeedNotice>()
                val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { notices.notices.collect { seen += it } }

                val client = repos.telegram.client()
                val location = InputDocumentFileLocation(FakeCluster.FILE_ID, 1, byteArrayOf(), "")
                suspend fun slowedDownload() {
                    fake.cluster.downloadPartErrors[0L] = ConcurrentLinkedQueue(listOf(FakeAnswer.Error(420, "FLOOD_PREMIUM_WAIT_1")))
                    client.downloadFile(location, dcId = 2, size = fake.cluster.fileBytes.size.toLong()).toList()
                }
                suspend fun awaitNotices(count: Int) {
                    while (seen.size < count) delay(20)
                }

                slowedDownload()
                awaitNotices(1)
                assertEquals(listOf(PremiumSpeedNotice(upload = false, speedup = 7)), seen.toList())

                slowedDownload()
                delay(500)
                assertEquals(1, seen.size)

                time += 1.hours
                slowedDownload()
                awaitNotices(2)
                assertEquals(PremiumSpeedNotice(upload = false, speedup = 7), seen.last())
                collector.cancel()
            }
            repos.telegram.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun appConfigTransferSettings() {
        val config = AppConfig(
            mapOf(
                "small_queue_max_active_operations_count" to 3.0,
                "large_queue_max_active_operations_count" to 0.0,
                "upload_premium_speedup_upload" to 4.0,
                "upload_premium_speedup_notify_period" to 60.0,
            ),
        )
        assertEquals(3, config.smallDownloadQueue)
        assertEquals(1, config.largeDownloadQueue, "at least one file downloads")
        assertEquals(4, config.premiumSpeedup(upload = true))
        assertEquals(AppConfig.DEFAULT_PREMIUM_SPEEDUP, config.premiumSpeedup(upload = false))
        assertEquals(60, config.premiumSpeedupNotifyPeriod)
        assertEquals(AppConfig.DEFAULT_LARGE_DOWNLOAD_QUEUE, AppConfig().largeDownloadQueue)
    }
}
