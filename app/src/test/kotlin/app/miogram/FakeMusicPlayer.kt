package app.miogram

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import app.miogram.music.MusicService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.robolectric.Shadows.shadowOf

@OptIn(UnstableApi::class)
class FakeMusicPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var positionMs = 0L
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var shuffle = false
    private var repeat = Player.REPEAT_MODE_OFF
    private var parameters = androidx.media3.common.PlaybackParameters.DEFAULT

    private var autoTransition = false

    val tracks: MutableMap<String, androidx.media3.common.Tracks> = HashMap()

    val speed: Float get() = parameters.speed

    val mediaIds: List<String> get() = items.map { it.mediaId }

    override fun getState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
        .setPlaylist(items.map { item -> MediaItemData.Builder(item.mediaId).setMediaItem(item).setDurationUs(DURATION_US).setIsSeekable(true).setTracks(tracks[item.mediaId] ?: androidx.media3.common.Tracks.EMPTY).build() })
        .setCurrentMediaItemIndex(if (items.isEmpty()) 0 else index.coerceIn(items.indices))
        .setContentPositionMs(positionMs)
        .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlaybackState(if (items.isEmpty() && playbackState != Player.STATE_IDLE) Player.STATE_ENDED else playbackState)
        .setShuffleModeEnabled(shuffle)
        .setRepeatMode(repeat)
        .setPlaybackParameters(parameters)
        .apply { if (autoTransition) setPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 0) }
        .build()
        .also { autoTransition = false }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        items = mediaItems.toList()
        index = if (startIndex == androidx.media3.common.C.INDEX_UNSET) 0 else startIndex
        positionMs = if (startPositionMs == androidx.media3.common.C.TIME_UNSET) 0 else startPositionMs
        return done()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        items = items.subList(0, index) + mediaItems + items.subList(index, items.size)
        if (index <= this.index && items.size > mediaItems.size) this.index += mediaItems.size
        return done()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        items = items.subList(0, fromIndex) + items.subList(toIndex, items.size)
        if (index >= toIndex) index -= toIndex - fromIndex else if (index >= fromIndex) index = fromIndex.coerceAtMost((items.size - 1).coerceAtLeast(0))
        return done()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val playing = items.getOrNull(index)
        val moving = items.subList(fromIndex, toIndex)
        val rest = items.subList(0, fromIndex) + items.subList(toIndex, items.size)
        items = rest.subList(0, newIndex) + moving + rest.subList(newIndex, rest.size)
        if (playing != null) index = items.indexOf(playing)
        return done()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        playbackState = Player.STATE_READY
        return done()
    }

    override fun handleStop(): ListenableFuture<*> {
        playbackState = Player.STATE_IDLE
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> = done()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex != androidx.media3.common.C.INDEX_UNSET) index = mediaItemIndex
        this.positionMs = if (positionMs == androidx.media3.common.C.TIME_UNSET) 0 else positionMs
        return done()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        shuffle = shuffleModeEnabled
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeat = repeatMode
        return done()
    }

    override fun handleSetPlaybackParameters(playbackParameters: androidx.media3.common.PlaybackParameters): ListenableFuture<*> {
        parameters = playbackParameters
        return done()
    }

    fun finish() {
        if (index < items.lastIndex) {
            index++
            autoTransition = true
        } else {
            playbackState = Player.STATE_ENDED
        }
        positionMs = 0
        invalidateState()
    }

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        const val DURATION_US = 185_000_000L
    }
}

fun Application.refuseMusicServiceBind() = shadowOf(this).declareComponentUnbindable(ComponentName(this, MusicService::class.java))
