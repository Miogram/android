package app.miogram.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.ui.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class MusicSessionLabels(val inOrder: String, val shuffle: String, val newestFirst: String, val repeat: String)

@OptIn(UnstableApi::class)
class MusicSessionCallback(
    private val labels: MusicSessionLabels,
    private val order: () -> MusicOrder,
    private val onOrder: () -> Unit,
) : MediaSession.Callback {
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(SessionCommand(ACTION_ORDER, Bundle.EMPTY))
            .add(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
            .build()
        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .removeAll(Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SET_MEDIA_ITEM)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(commands)
            .setAvailablePlayerCommands(playerCommands)
            .setMediaButtonPreferences(buttons(session.player))
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val player = session.player
        when (customCommand.customAction) {
            ACTION_ORDER -> onOrder()
            ACTION_REPEAT -> player.repeatMode = nextRepeatMode(player.repeatMode)
            else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onPlaybackResumption(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val player = session.player
        if (player.mediaItemCount == 0) return Futures.immediateFailedFuture(UnsupportedOperationException("nothing to resume"))
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, player.currentMediaItemIndex, player.currentPosition))
    }

    fun buttons(player: Player): List<CommandButton> {
        val orderButton = when (order()) {
            MusicOrder.Chat -> CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF).setDisplayName(labels.inOrder)
            MusicOrder.Shuffle -> CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON).setDisplayName(labels.shuffle)
            MusicOrder.Reverse -> CommandButton.Builder(CommandButton.ICON_UNDEFINED).setCustomIconResId(R.drawable.ic_swap_vert).setDisplayName(labels.newestFirst)
        }
        return listOf(
            orderButton
                .setSessionCommand(SessionCommand(ACTION_ORDER, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(
                when (player.repeatMode) {
                    Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                    Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                    else -> CommandButton.ICON_REPEAT_OFF
                },
            )
                .setDisplayName(labels.repeat)
                .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    fun follow(session: MediaSession): Player.Listener {
        val listener = object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refresh(session)

            override fun onRepeatModeChanged(repeatMode: Int) = refresh(session)
        }
        session.player.addListener(listener)
        return listener
    }

    fun refresh(session: MediaSession) = session.setMediaButtonPreferences(buttons(session.player))

    companion object {
        const val ACTION_ORDER = "app.miogram.music.ORDER"
        const val ACTION_REPEAT = "app.miogram.music.REPEAT"
    }
}

@OptIn(UnstableApi::class)
class MusicArtworkLoader(private val scope: CoroutineScope, private val load: suspend (kind: String, key: String) -> ByteArray?) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        BitmapFactory.decodeByteArray(data, 0, data.size)?.let { Futures.immediateFuture(it) } ?: Futures.immediateFailedFuture(IllegalArgumentException("not an image"))

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val key = uri.lastPathSegment?.takeIf { uri.scheme == MusicPlayer.COVER_SCHEME } ?: return Futures.immediateFailedFuture(IllegalArgumentException("not a cover: $uri"))
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            val bitmap = runCatching { load(uri.authority.orEmpty(), key)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalStateException("no cover for $key"))
        }
        return future
    }
}
