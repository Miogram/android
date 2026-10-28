package app.miogram

import app.miogram.data.maps.MapsConfig
import app.miogram.data.maps.MapsHttp
import app.miogram.di.MapsModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Singleton

class FakeMapsHttp : MapsHttp {
    val urls: MutableList<String> = CopyOnWriteArrayList()

    @Volatile var failing = false

    override suspend fun get(url: String): ByteArray {
        urls += url
        if (failing) throw IOException("offline")
        val path = url.substringAfter("/maps/api/").substringBefore('?')
        return when (path) {
            "staticmap" -> PNG
            "geocode/json" -> GEOCODE.toByteArray()
            "place/nearbysearch/json" -> NEARBY.toByteArray()
            "place/autocomplete/json" -> if ("input=alexa" in url) AUTOCOMPLETE.toByteArray() else """{"status":"ZERO_RESULTS","predictions":[]}""".toByteArray()
            "place/details/json" -> DETAILS.toByteArray()
            "directions/json" -> when {
                "mode=transit" in url -> """{"status":"ZERO_RESULTS","routes":[]}"""
                "mode=driving" in url -> directions(1900, 300)
                else -> directions(1500, 1140)
            }.toByteArray()
            else -> """{"status":"INVALID_REQUEST"}""".toByteArray()
        }
    }

    fun asked(path: String): List<String> = urls.filter { it.substringAfter("/maps/api/").substringBefore('?') == path }

    companion object {
        const val KEY = "test-key"

        const val LINE = "_yp_IgdypAsS{^{T_b@_S{Y"

        fun directions(metres: Int, seconds: Int) =
            """{"status":"OK","routes":[{"overview_polyline":{"points":"$LINE"},"legs":[{"distance":{"value":$metres,"text":""},"duration":{"value":$seconds,"text":""}}]}]}"""

        val PNG: ByteArray = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGNYcOIZEDFAKAA/fgk5+HrCJgAAAABJRU5ErkJggg==")
        const val NEARBY = """{"status":"OK","results":[
            {"place_id":"ChIJ-berlin","name":"Berlin","vicinity":"Berlin","types":["locality","political"],"geometry":{"location":{"lat":52.52,"lng":13.405}}},
            {"place_id":"ChIJ-alexa","name":"ALEXA","vicinity":"Grunerstraße 20, Berlin","types":["shopping_mall","point_of_interest"],"geometry":{"location":{"lat":52.5196,"lng":13.4155}}},
            {"place_id":"ChIJ-tower","name":"TV Tower","vicinity":"Panoramastraße 1A, Berlin","types":["tourist_attraction"],"geometry":{"location":{"lat":52.5208,"lng":13.4094}}}]}"""
        const val AUTOCOMPLETE = """{"status":"OK","predictions":[{"place_id":"ChIJ-alexa","description":"ALEXA, Grunerstraße, Berlin, Germany","structured_formatting":{"main_text":"ALEXA","secondary_text":"Grunerstraße, Berlin, Germany"}}]}"""
        const val DETAILS = """{"status":"OK","result":{"place_id":"ChIJ-alexa","name":"ALEXA","formatted_address":"Grunerstraße 20, 10179 Berlin, Germany","types":["shopping_mall","point_of_interest"],"geometry":{"location":{"lat":52.5196,"lng":13.4155}}}}"""
        const val GEOCODE = """{"status":"OK","results":[{"formatted_address":"Alexanderplatz 1, 10178 Berlin, Germany"}]}"""
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [MapsModule::class])
object FakeMapsModule {
    @Provides
    fun config(): MapsConfig = MapsConfig(FakeMapsHttp.KEY, sdk = false)

    @Provides
    @Singleton
    fun http(): MapsHttp = FakeMapsHttp()
}
