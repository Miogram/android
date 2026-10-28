package cn.ellerotta.core.ui.components.maps

import androidx.compose.runtime.staticCompositionLocalOf
import cn.ellerotta.core.model.GeoMap
import cn.ellerotta.core.model.StaticMap

fun interface MapImages {
    suspend fun load(map: StaticMap): ByteArray?
}

val LocalMapImages = staticCompositionLocalOf<MapImages?> { null }

fun interface GeoMapImages {
    suspend fun load(map: GeoMap): ByteArray?
}

val LocalGeoMapImages = staticCompositionLocalOf<GeoMapImages?> { null }
