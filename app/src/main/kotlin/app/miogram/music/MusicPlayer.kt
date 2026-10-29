package app.miogram.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.miogram.data.SettingsStore
import app.miogram.data.telegram.MediaRepository
import app.miogram.data.telegram.MessagesRepository
import cn.ellerotta.core.model.MusicModes
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.model.MusicPage
import cn.ellerotta.core.model.MusicPlaylist
import cn.ellerotta.core.model.MusicRepeat
import cn.ellerotta.core.model.MusicSleep
import cn.ellerotta.core.model.MusicState
import cn.ellerotta.core.model.MusicTrack
import cn.ellerotta.core.model.arranged
import cn.ellerotta.core.model.cover
import cn.ellerotta.core.model.grown
import cn.ellerotta.core.model.moved
import cn.ellerotta.core.model.nextOrder
import cn.ellerotta.core.model.nextVoiceSpeed
import cn.ellerotta.core.model.playNext as playingNext
import cn.ellerotta.core.model.voiceQuery
import cn.ellerotta.core.model.voiceQueue
import cn.ellerotta.core.ui.R
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun interface MusicPlayerFactory {
    fun create(): Player
}

@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messages: MessagesRepository,
    private val media: MediaRepository,
    private val settings: SettingsStore,
    private val factory: MusicPlayerFactory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow<MusicState?>(null)
    private var created: Player? = null
    private var playlist: MusicPlaylist? = null
    private var tracks: List<MusicTrack> = emptyList()
    private var queue: List<MusicTrack> = emptyList()
    private val removed = HashSet<String>()
    private var seed = 0L
    private var loading: Job? = null
    private var generation = 0
    private var ticker: Job? = null
    private var sleepUntil: Long? = null
    private var sleepAtEnd = false
    private var sleeper: Job? = null
    private var lyrics: String? = null
    private val covers = object : LinkedHashMap<String, ByteArray>(COVERS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > COVERS
    }
    private val coversAsked = HashSet<String>()
    private var controller: ListenableFuture<MediaController>? = null

    val music: StateFlow<MusicState?> = state.asStateFlow()

    val player: Player
        get() = created ?: factory.create().also { made ->
            created = made
            made.addListener(listener)
            made.addListener(transitions)
        }

    private val voice: Boolean get() = tracks.firstOrNull()?.isVoice == true

    private val order: MusicOrder get() = if (voice) MusicOrder.Chat else settings.musicModes.value.order

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) && !voice && queue.isNotEmpty()) {
                val modes = settings.musicModes.value
                if (player.shuffleModeEnabled != modes.shuffle) {
                    settings.setMusicModes(modes.copy(shuffle = player.shuffleModeEnabled))
                    rearrange()
                }
            }
            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED) && !voice && queue.isNotEmpty()) {
                settings.setMusicModes(settings.musicModes.value.copy(repeat = repeatOf(player.repeatMode)))
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) lyrics = null
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) lyrics = lyricsOf(player.currentTracks)
            publish()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                applySpeed()
                loadCover()
                loadMore()
                listened()
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) tick()
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_ENDED && sleepAtEnd) clearSleep()
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_ENDED && queue.lastOrNull()?.isVoice == true) {
                close()
            }
        }
    }

    private val transitions = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!sleepAtEnd) return
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            created?.pause()
            clearSleep()
            publish()
        }
    }

    fun play(track: MusicTrack) {
        val now = state.value
        if (now != null && now.track.slot == track.slot) {
            toggle()
            return
        }
        val at = queue.indexOfFirst { it.slot == track.slot }
        if (at >= 0) {
            playAt(at)
            return
        }
        start(listOf(track), 0)
        playlist = MusicPlaylist(track.dialogKey, tracks).takeUnless { track.isVoice }
        val list = playlist
        if (list != null) load(list, MusicPage.Around, track.messageId) else loadVoices(track)
    }

    fun playList(list: List<MusicTrack>, index: Int = 0) {
        val track = list.getOrNull(index) ?: return
        val now = state.value
        if (now != null && now.track.slot == track.slot) {
            toggle()
            return
        }
        start(list, index)
        playlist = null
    }

    private fun start(list: List<MusicTrack>, index: Int) {
        loading?.cancel()
        generation++
        removed.clear()
        seed = Random.nextLong()
        tracks = list
        queue = arranged(list, order, list[index], seed)
        player.setMediaItems(queue.map(::itemOf), queue.indexOfFirst { it.slot == list[index].slot }, 0)
        player.shuffleModeEnabled = order == MusicOrder.Shuffle
        player.repeatMode = if (voice) Player.REPEAT_MODE_OFF else modeOf(settings.musicModes.value.repeat)
        lyrics = null
        applySpeed()
        player.prepare()
        player.play()
        connect()
        publish()
        loadCover()
        listened()
    }

    fun playAt(index: Int) {
        if (index !in queue.indices) return
        if (index != player.currentMediaItemIndex || player.playbackState == Player.STATE_ENDED) player.seekTo(index, 0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun toggle() {
        val current = created ?: return
        if (playing(current)) {
            current.pause()
            return
        }
        if (current.playbackState == Player.STATE_ENDED) current.seekTo(0, 0)
        if (current.playbackState == Player.STATE_IDLE) current.prepare()
        current.play()
    }

    fun next() {
        created?.seekToNext()
    }

    fun previous() {
        created?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        created?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        if (voice) return
        val modes = settings.musicModes.value
        changeModes(modes.copy(shuffle = !modes.shuffle))
    }

    fun setOrder(order: MusicOrder) {
        if (voice) return
        changeModes(settings.musicModes.value.with(order))
    }

    fun cycleOrder() = setOrder(nextOrder(order))

    private fun changeModes(modes: MusicModes) {
        settings.setMusicModes(modes)
        val current = created ?: return
        if (queue.isEmpty()) return
        rearrange()
        current.shuffleModeEnabled = modes.shuffle
    }

    fun cycleRepeat() {
        if (voice) return
        val current = created ?: return
        current.repeatMode = nextRepeatMode(current.repeatMode)
    }

    fun move(from: Int, to: Int) {
        sync(queue.moved(from, to))
        publish()
    }

    fun playNext(index: Int) {
        val at = created?.currentMediaItemIndex ?: return
        sync(playingNext(queue, at, index))
        publish()
    }

    fun remove(index: Int) {
        val track = queue.getOrNull(index) ?: return
        if (index == created?.currentMediaItemIndex) return
        removed += track.slot
        tracks = tracks.filter { it.slot != track.slot }
        playlist = playlist?.copy(tracks = tracks)
        sync(queue.filter { it.slot != track.slot })
        publish()
    }

    fun pause() {
        created?.takeIf { it.playWhenReady }?.pause()
    }

    fun cycleSpeed() {
        val track = state.value?.track ?: return
        val speed = when {
            track.isVoice -> nextVoiceSpeed(settings.voiceSpeed.value).also(settings::setVoiceSpeed)
            track.isLong -> nextVoiceSpeed(settings.longSpeed.value).also(settings::setLongSpeed)
            else -> return
        }
        created?.setPlaybackSpeed(speed)
    }

    private fun applySpeed() {
        val current = created ?: return
        val track = queue.getOrNull(current.currentMediaItemIndex) ?: return
        val speed = when {
            track.isVoice -> settings.voiceSpeed.value
            track.isLong -> settings.longSpeed.value
            else -> 1f
        }
        if (current.playbackParameters.speed != speed) current.setPlaybackSpeed(speed)
    }

    fun sleep(minutes: Int?) {
        clearSleep()
        if (minutes != null) {
            val ms = minutes * 60_000L
            sleepUntil = SystemClock.elapsedRealtime() + ms
            sleeper = scope.launch {
                delay(ms)
                created?.pause()
                clearSleep()
                publish()
            }
        }
        publish()
        tick()
    }

    fun sleepAtEnd() {
        clearSleep()
        sleepAtEnd = true
        publish()
    }

    private fun clearSleep() {
        sleeper?.cancel()
        sleeper = null
        sleepUntil = null
        sleepAtEnd = false
        tick()
    }

    fun close() {
        loading?.cancel()
        generation++
        ticker?.cancel()
        created?.run {
            stop()
            clearMediaItems()
        }
        tracks = emptyList()
        queue = emptyList()
        removed.clear()
        clearSleep()
        lyrics = null
        covers.clear()
        coversAsked.clear()
        playlist = null
        state.value = null
        controller?.let(MediaController::releaseFuture)
        controller = null
        context.stopService(Intent(context, MusicService::class.java))
    }

    private fun connect() {
        if (controller != null) return
        controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
    }

    private fun rearrange() {
        val current = queue.getOrNull(created?.currentMediaItemIndex ?: return)
        sync(arranged(tracks, order, current, seed))
        publish()
        loadMore()
    }

    private fun sync(target: List<MusicTrack>) {
        val current = created ?: return
        val now = queue.mapTo(ArrayList()) { it.slot }
        val wanted = target.map { it.slot }
        val keep = wanted.toHashSet()
        for (i in now.indices.reversed()) {
            if (now[i] in keep) continue
            current.removeMediaItem(i)
            now.removeAt(i)
        }
        val staying = now.toHashSet()
        wanted.filter { it in staying }.forEachIndexed { i, slot ->
            val from = now.indexOf(slot)
            if (from == i) return@forEachIndexed
            current.moveMediaItem(from, i)
            now.add(i, now.removeAt(from))
        }
        target.forEachIndexed { i, track ->
            if (now.getOrNull(i) == track.slot) return@forEachIndexed
            current.addMediaItem(i, itemOf(track))
            now.add(i, track.slot)
        }
        queue = target
    }

    private fun load(of: MusicPlaylist, page: MusicPage, aroundId: Int) {
        val asked = generation
        if (loading?.isActive == true) return
        val query = of.query(page, aroundId)
        loading = scope.launch {
            val found = try {
                messages.music(of.dialogKey, query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            val now = playlist ?: return@launch
            if (asked != generation) return@launch
            apply(now.merged(page, found.filter { it.slot !in removed }, aroundId))
            loading = null
            loadMore()
        }
    }

    private fun loadVoices(tapped: MusicTrack) {
        val asked = generation
        loading = scope.launch {
            val found = try {
                messages.voices(tapped.dialogKey, voiceQuery(tapped.messageId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            if (asked != generation) return@launch
            val next = voiceQueue(tapped, found).drop(1)
            loading = null
            if (next.isEmpty()) return@launch
            tracks = tracks + next
            sync(queue + next)
            publish()
        }
    }

    private fun listened() {
        val index = created?.currentMediaItemIndex ?: return
        val track = queue.getOrNull(index)?.takeIf { it.isVoice && it.unread } ?: return
        mark(track, unread = false)
        scope.launch {
            try {
                messages.readContents(track.dialogKey, listOf(track.messageId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mark(track, unread = true)
            }
        }
        publish()
    }

    private fun mark(track: MusicTrack, unread: Boolean) {
        fun List<MusicTrack>.marked() = map { if (it.slot == track.slot) it.copy(unread = unread) else it }
        tracks = tracks.marked()
        queue = queue.marked()
    }

    private fun apply(merged: MusicPlaylist) {
        val known = tracks.mapTo(HashSet()) { it.slot }
        val first = tracks.first().messageId
        val (older, newer) = merged.tracks.filter { it.slot !in known }.partition { it.messageId < first }
        tracks = merged.tracks
        playlist = merged
        val at = created?.currentMediaItemIndex ?: 0
        sync(grown(queue, at, older, newer, order, seed))
        publish()
    }

    private fun loadMore() {
        val list = playlist ?: return
        val at = created?.currentMediaItemIndex ?: return
        val playing = queue.getOrNull(at) ?: return
        val page = if (order == MusicOrder.Shuffle) {
            list.pageAhead(queue.size - 1 - at)
        } else {
            list.pageFor(tracks.indexOfFirst { it.slot == playing.slot })
        } ?: return
        load(list, page, list.tracks.first().messageId)
    }

    private fun loadCover() {
        val track = queue.getOrNull(created?.currentMediaItemIndex ?: return) ?: return
        val avatar = track.avatarKey.takeIf { track.isVoice }
        val cover = track.media.cover()?.key.takeUnless { track.isVoice }
        if (avatar == null && cover == null) return
        if (covers.containsKey(track.slot) || !coversAsked.add(track.slot)) return
        scope.launch {
            val bytes = runCatching { if (avatar != null) media.avatar(avatar) else media.sticker(cover!!) }.getOrNull()
            coversAsked -= track.slot
            if (bytes == null) return@launch
            covers[track.slot] = bytes
            publish()
        }
    }

    private fun publish() {
        val current = created
        if (current == null || queue.isEmpty() || current.mediaItemCount == 0) {
            state.value = null
            return
        }
        val index = current.currentMediaItemIndex.coerceIn(queue.indices)
        val track = queue[index]
        state.value = MusicState(
            queue = queue,
            index = index,
            playing = playing(current),
            buffering = current.playbackState == Player.STATE_BUFFERING,
            positionMs = current.currentPosition.coerceAtLeast(0),
            durationMs = current.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
            order = order,
            repeat = repeatOf(current.repeatMode),
            cover = covers[track.slot] ?: track.media.thumb.takeUnless { track.isVoice },
            speed = current.playbackParameters.speed,
            sleep = when {
                sleepAtEnd -> MusicSleep(endOfTrack = true)
                else -> sleepUntil?.let { MusicSleep(leftMs = (it - SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
            },
            lyrics = lyrics,
        )
    }

    private fun tick() {
        val due = created?.isPlaying == true || sleepUntil != null
        if (!due) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                publish()
            }
        }
    }

    private fun itemOf(track: MusicTrack): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setDurationMs(track.media.duration * 1000L)
            .setIsPlayable(true)
            .setIsBrowsable(false)
        if (track.isVoice) {
            val voice = context.getString(R.string.music_voice)
            metadata.setTitle(track.senderName ?: voice).setArtist(voice)
            track.avatarKey?.let { metadata.setArtworkUri(avatarUri(it)) }
        } else {
            val cover = track.media.cover()
            metadata.setTitle(track.title).setArtist(track.performer).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            if (cover != null) metadata.setArtworkUri(coverUri(cover.key))
            else track.media.thumb?.let { metadata.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        }
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(media.musicUri(track.media))
            .setMediaMetadata(metadata.build())
            .build()
    }

    companion object {
        const val COVER_SCHEME = "tg-cover"
        const val COVER = "cover"
        const val AVATAR = "avatar"

        fun coverUri(key: String): Uri = Uri.Builder().scheme(COVER_SCHEME).authority(COVER).appendPath(key).build()

        fun avatarUri(key: String): Uri = Uri.Builder().scheme(COVER_SCHEME).authority(AVATAR).appendPath(key).build()
        private const val TICK_MS = 500L
        private const val COVERS = 8
    }
}

internal fun nextRepeatMode(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

internal fun repeatOf(mode: Int): MusicRepeat = when (mode) {
    Player.REPEAT_MODE_ALL -> MusicRepeat.All
    Player.REPEAT_MODE_ONE -> MusicRepeat.One
    else -> MusicRepeat.Off
}

internal fun modeOf(repeat: MusicRepeat): Int = when (repeat) {
    MusicRepeat.Off -> Player.REPEAT_MODE_OFF
    MusicRepeat.All -> Player.REPEAT_MODE_ALL
    MusicRepeat.One -> Player.REPEAT_MODE_ONE
}

private fun playing(player: Player): Boolean =
    player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE
