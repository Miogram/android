package app.miogram.music

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.miogram.MainActivity
import app.miogram.data.telegram.MediaRepository
import cn.ellerotta.core.model.MusicOrder
import cn.ellerotta.core.ui.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    @Inject lateinit var music: MusicPlayer

    @Inject lateinit var media: MediaRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var follower: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        val labels = MusicSessionLabels(
            inOrder = getString(R.string.music_order_chat),
            shuffle = getString(R.string.music_shuffle),
            newestFirst = getString(R.string.music_order_reverse),
            repeat = getString(R.string.music_repeat),
        )
        val callback = MusicSessionCallback(labels, order = { music.music.value?.order ?: MusicOrder.Chat }, onOrder = music::cycleOrder)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val player = music.player
        val made = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(open)
            .setBitmapLoader(CacheBitmapLoader(MusicArtworkLoader(scope) { kind, key -> if (kind == MusicPlayer.AVATAR) media.avatar(key) else media.sticker(key) }))
            .setMediaButtonPreferences(callback.buttons(player))
            .build()
        follower = callback.follow(made)
        session = made
        scope.launch { music.music.map { it?.order }.distinctUntilChanged().collect { callback.refresh(made) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            music.close()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.let { made ->
            follower?.let(made.player::removeListener)
            made.release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }
}
