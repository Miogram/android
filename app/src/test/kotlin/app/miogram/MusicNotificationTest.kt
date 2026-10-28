package app.miogram

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSession as PlatformSession
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import app.miogram.data.telegram.ServerConfig
import app.miogram.data.telegram.TelegramClientHolder
import app.miogram.di.MusicModule
import app.miogram.di.TelegramModule
import app.miogram.music.MusicPlayer
import app.miogram.music.MusicPlayerFactory
import app.miogram.music.MusicService
import cn.ellerotta.core.model.MediaItem
import cn.ellerotta.core.model.MediaKind
import cn.ellerotta.core.model.MusicTrack
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.nio.file.Files
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@HiltAndroidTest
@UninstallModules(TelegramModule::class, MusicModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class MusicNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val fake = FakeTelegram()
    private val dir = Files.createTempDirectory("music").toFile()
    private val engine by lazy { FakeMusicPlayer() }
    private val notifications by lazy { shadowOf(context.getSystemService(NotificationManager::class.java)) }

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val server = ServerConfig(testMode = true, apiId = 12345, apiHash = "hash", appVersion = "test")

    @BindValue
    @JvmField
    val telegram = TelegramClientHolder(dir) { id, hash, logger -> fake.config(id, hash, logger) }

    @BindValue
    @JvmField
    val players = MusicPlayerFactory { engine }

    @Inject lateinit var music: MusicPlayer

    private lateinit var running: ServiceController<MusicService>
    private val service: MusicService get() = running.get()

    @Before
    fun setUp() {
        hilt.inject()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        running = Robolectric.buildService(MusicService::class.java).create()
        val binder = service.onBind(Intent(MediaSessionService.SERVICE_INTERFACE))
        shadowOf(context).setComponentNameAndServiceForBindService(ComponentName(context, MusicService::class.java), binder)
    }

    @After
    fun tearDown() {
        music.close()
        idle()
        running.destroy()
        runBlocking { telegram.close() }
        fake.close()
        dir.deleteRecursively()
    }

    @Test
    fun aSongGetsTheMediaNotificationWithTheSessionsControls() {
        music.play(track(7, MediaItem("doc7", "song.mp3", 10, 2, false, MediaKind.Music, duration = 185, title = "Song 1", performer = "Band")))
        val shown = waitForMedia()
        assertTrue(service.sessions.isNotEmpty(), "the session is added to the service")
        assertTrue(service.isSessionAdded(service.sessions.single()))
        assertEquals(service.sessions.single().platformToken, shown.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, PlatformSession.Token::class.java))
        assertEquals("Song 1", shown.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Band", shown.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val actions = shown.actions.orEmpty().map { it.title.toString() }
        assertTrue(actions.containsAll(listOf("In chat order", "Repeat")), "the order and repeat granted to the notification's controller: $actions")
        assertEquals(5, actions.size, "previous, play or pause, next besides them: $actions")

        music.close()
        idle()
        assertTrue(mediaNotifications().isEmpty(), "no media notification after close")
    }

    @Test
    fun aVoiceMessageGetsItToo() {
        music.play(track(9, MediaItem("doc9", "voice.ogg", 10, 2, false, MediaKind.Voice, duration = 4), sender = "Alice Liddell"))
        val shown = waitForMedia()
        assertEquals("Alice Liddell", shown.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    private fun track(id: Int, media: MediaItem, sender: String? = null) = MusicTrack("self", id, media, senderName = sender)

    private fun mediaNotifications(): List<Notification> =
        notifications.allNotifications.filter { it.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }

    private fun waitForMedia(): Notification {
        val until = System.currentTimeMillis() + 10_000
        while (true) {
            idle()
            mediaNotifications().firstOrNull()?.let { return it }
            check(System.currentTimeMillis() < until) { "no media notification, sessions ${service.sessions}" }
            Thread.sleep(10)
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
