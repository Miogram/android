package app.miogram.di

import app.miogram.data.MediaVoiceRecorder
import app.miogram.data.VoiceRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds
    abstract fun recorder(recorder: MediaVoiceRecorder): VoiceRecorder
}
