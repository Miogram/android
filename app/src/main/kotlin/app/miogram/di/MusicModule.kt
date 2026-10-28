package app.miogram.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import app.miogram.data.telegram.MediaRepository
import app.miogram.music.MusicCache
import app.miogram.music.MusicPlayerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MusicModule {
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun player(@ApplicationContext context: Context, media: MediaRepository, cache: MusicCache): MusicPlayerFactory = MusicPlayerFactory {
        val source = CacheDataSource.Factory()
            .setCache(cache.cache)
            .setUpstreamDataSourceFactory(media.musicSource())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val music = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(source))
            .setAudioAttributes(music, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply { setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0)) }
    }
}
