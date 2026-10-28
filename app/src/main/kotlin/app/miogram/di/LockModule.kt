package app.miogram.di

import app.miogram.lock.BiometricDeviceAuth
import app.miogram.lock.DeviceAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LockModule {
    @Binds
    abstract fun deviceAuth(auth: BiometricDeviceAuth): DeviceAuth
}
