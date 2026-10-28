package app.miogram.di

import app.miogram.BuildConfig
import app.miogram.data.maps.MapsConfig
import app.miogram.data.maps.MapsHttp
import app.miogram.data.maps.UrlMapsHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object MapsModule {
    @Provides
    fun config(): MapsConfig = MapsConfig(BuildConfig.MAPS_API_KEY)

    @Provides
    fun http(http: UrlMapsHttp): MapsHttp = http
}
