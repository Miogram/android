package app.miogram

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import app.miogram.music.MusicSessionCallback
import app.miogram.music.MusicSessionLabels
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.nextOrder
import cn.ellerotta.core.ui.R
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicSessionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val player = FakeMusicPlayer().apply {
        setMediaItems((1..3).map { MediaItem.Builder().setMediaId("self/$it").setUri("tg://music/doc$it?dc=2&size=10").build() })
        prepare()
    }
    private var order = MusicOrder.Chat
    private val callback = MusicSessionCallback(MusicSessionLabels("In chat order", "Shuffle", "Newest first", "Repeat"), { order }) {
        order = nextOrder(order)
        player.shuffleModeEnabled = order == MusicOrder.Shuffle
    }
    private val session = MediaSession.Builder(context, player).setId("music-test").setCallback(callback).build()
    private val controller = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        "com.example.headphones", 0, 0, 0, 0, false, Bundle.EMPTY, false,
    )

    @After
    fun tearDown() {
        session.release()
    }

    @Test
    fun controllersGetTheOrderAndRepeatNextToThePlayersCommands() {
        val result = callback.onConnect(session, controller)
        assertTrue(result.isAccepted)
        assertTrue(result.availableSessionCommands.contains(SessionCommand(MusicSessionCallback.ACTION_ORDER, Bundle.EMPTY)))
        assertTrue(result.availableSessionCommands.contains(SessionCommand(MusicSessionCallback.ACTION_REPEAT, Bundle.EMPTY)))
        listOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM).forEach {
            assertTrue(result.availablePlayerCommands.contains(it))
        }
        assertFalse(result.availablePlayerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(result.availablePlayerCommands.contains(Player.COMMAND_SET_MEDIA_ITEM))
        assertEquals(listOf(CommandButton.ICON_SHUFFLE_OFF, CommandButton.ICON_REPEAT_OFF), result.mediaButtonPreferences!!.map { it.icon })
        assertEquals(listOf("In chat order", "Repeat"), result.mediaButtonPreferences!!.map { it.displayName.toString() })
    }

    @Test
    fun customCommandsStepThroughTheOrdersAndRepeat() {
        assertEquals(SessionResult.RESULT_SUCCESS, command(MusicSessionCallback.ACTION_ORDER))
        assertTrue(player.shuffleModeEnabled)
        assertEquals(CommandButton.ICON_SHUFFLE_ON, callback.buttons(player)[0].icon)
        assertEquals("Shuffle", callback.buttons(player)[0].displayName.toString())
        command(MusicSessionCallback.ACTION_ORDER)
        assertFalse(player.shuffleModeEnabled)
        assertEquals(MusicOrder.Reverse, order)
        assertEquals(CommandButton.ICON_UNDEFINED, callback.buttons(player)[0].icon)
        assertEquals(R.drawable.ic_swap_vert, callback.buttons(player)[0].iconResId)
        assertEquals("Newest first", callback.buttons(player)[0].displayName.toString())
        command(MusicSessionCallback.ACTION_ORDER)
        assertEquals(MusicOrder.Chat, order)
        assertEquals(CommandButton.ICON_SHUFFLE_OFF, callback.buttons(player)[0].icon)

        val modes = (1..3).map {
            command(MusicSessionCallback.ACTION_REPEAT)
            player.repeatMode to callback.buttons(player)[1].icon
        }
        assertEquals(
            listOf(
                Player.REPEAT_MODE_ALL to CommandButton.ICON_REPEAT_ALL,
                Player.REPEAT_MODE_ONE to CommandButton.ICON_REPEAT_ONE,
                Player.REPEAT_MODE_OFF to CommandButton.ICON_REPEAT_OFF,
            ),
            modes,
        )
        assertEquals(SessionError.ERROR_NOT_SUPPORTED, command("something.else"))
    }

    @Test
    fun theButtonsFollowModesChangedInTheApp() {
        callback.follow(session)
        order = MusicOrder.Shuffle
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ONE
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(CommandButton.ICON_SHUFFLE_ON, CommandButton.ICON_REPEAT_ONE), session.mediaButtonPreferences.map { it.icon })
        order = MusicOrder.Reverse
        callback.refresh(session)
        assertEquals("Newest first", session.mediaButtonPreferences[0].displayName.toString())
    }

    @Test
    fun aResumingControllerGetsTheQueueWhereItIs() {
        player.seekTo(2, 42_000)
        val resumed = callback.onPlaybackResumption(session, controller, true).get()
        assertEquals(listOf("self/1", "self/2", "self/3"), resumed.mediaItems.map { it.mediaId })
        assertEquals(2, resumed.startIndex)
        assertEquals(42_000, resumed.startPositionMs)

        player.clearMediaItems()
        assertTrue(runCatching { callback.onPlaybackResumption(session, controller, true).get() }.isFailure)
    }

    private fun command(action: String): Int =
        callback.onCustomCommand(session, controller, SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY).get().resultCode
}
