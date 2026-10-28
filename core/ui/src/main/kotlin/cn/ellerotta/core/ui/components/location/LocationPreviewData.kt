package cn.ellerotta.core.ui.components.location

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import cn.ellerotta.core.model.GeoPlace
import cn.ellerotta.core.model.LiveInfo
import cn.ellerotta.core.model.LivePeriods
import cn.ellerotta.core.model.LiveSharer
import cn.ellerotta.core.model.MessageItem
import cn.ellerotta.core.model.PlaceSuggestion
import cn.ellerotta.core.model.Route
import cn.ellerotta.core.model.TravelMode
import cn.ellerotta.core.ui.components.attach.LocationShareState
import cn.ellerotta.core.ui.components.attach.LocationStatus
import cn.ellerotta.core.model.StaticMap
import cn.ellerotta.core.model.Venue
import cn.ellerotta.core.ui.components.common.previewPhoto
import cn.ellerotta.core.ui.components.maps.GeoMapImages
import cn.ellerotta.core.ui.components.maps.MapImages
import java.io.ByteArrayOutputStream

internal object LocationPreviewData {
    val NOW = System.currentTimeMillis() / 1000

    val maps = MapImages { map -> previewMap(map) }

    val geoMaps = GeoMapImages { map -> previewMap(StaticMap(map.latitude, map.longitude, map.zoom, map.width, map.height, dark = false)) }

    private fun previewMap(map: StaticMap): ByteArray {
        val width = map.width
        val height = map.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dark = map.dark
        fun paint(color: Long, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toInt()
            if (stroke > 0) {
                style = Paint.Style.STROKE
                strokeWidth = stroke
            }
        }
        canvas.drawColor((if (dark) 0xFF242F3E else 0xFFF2EFE9).toInt())
        canvas.drawRect(width * 0.55f, height * 0.08f, width * 0.92f, height * 0.45f, paint(if (dark) 0xFF263C3F else 0xFFCDE6C0))
        canvas.drawLine(-20f, height * 0.85f, width * 0.45f, height * 1.1f, paint(if (dark) 0xFF17263C else 0xFFAAD3DF, 30f))
        val road = paint(if (dark) 0xFF38414E else 0xFFFFFFFF, 7f)
        var x = width * 0.12f
        while (x < width) {
            canvas.drawLine(x, 0f, x + width * 0.05f, height.toFloat(), road)
            x += width * 0.22f
        }
        var y = height * 0.2f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y - height * 0.04f, road)
            y += height * 0.3f
        }
        canvas.drawLine(0f, height * 0.65f, width.toFloat(), height * 0.15f, paint(if (dark) 0xFF746855 else 0xFFF7D58A, 12f))
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    val alicePhoto = previewPhoto(start = 0xFFE86A9A.toInt(), end = 0xFFF5A623.toInt())
    val avatars = mapOf("alice" to alicePhoto)

    val point = GeoPlace(52.5219, 13.4132, accessHash = 1)
    val venue = GeoPlace(52.5219, 13.4132, "Alexanderplatz", "Alexanderplatz, 10178 Berlin", venue = Venue(Venue.GOOGLE, "ChIJbygR2x5OqEcRbhbkZsMB_DA", "transit_station"), accessHash = 2)

    private val aliceLive = LiveInfo(period = LivePeriods.FOREVER, heading = 45, updated = (NOW - 120).toInt(), senderKey = "u501", senderName = "Alice Moreau", senderAvatar = "alice")
    private val bobLive = LiveInfo(period = LivePeriods.FOREVER, updated = (NOW - 30).toInt(), senderKey = "u502", senderName = "Bob")
    private val ownLive = LiveInfo(period = LivePeriods.FOREVER, updated = NOW.toInt(), senderKey = "u1", senderName = "Ian")

    val alice = LiveSharer(11, self = false, GeoPlace(52.5232, 13.4110, live = aliceLive, accessHash = 3))
    val bob = LiveSharer(12, self = false, GeoPlace(52.5201, 13.4170, live = bobLive))
    val own = LiveSharer(13, self = true, GeoPlace(52.5210, 13.4141, live = ownLive))
    val sharers = listOf(own, alice, bob)

    val alerted = listOf(own.copy(place = own.place.copy(live = ownLive.copy(radius = 500))), alice, bob)

    val me = GeoPlace(52.5185, 13.4090)

    val route = Route(TravelMode.Walking, metres = 520, seconds = 420, polyline = "sop_Ig}ypA_D{E_Dz@sD{JsDkH")

    val pointMessage = MessageItem(1, "", out = false, date = NOW.toInt(), place = point)
    val venueMessage = MessageItem(2, "", out = true, date = NOW.toInt(), read = true, place = venue)
    val liveMessage = MessageItem(3, "", out = false, date = NOW.toInt(), place = alice.place)

    val nearby = listOf(
        GeoPlace(52.5196, 13.4155, "ALEXA", "Grunerstraße 20, Berlin", venue = Venue(Venue.GOOGLE, "p1", "shopping_mall")),
        GeoPlace(52.5208, 13.4094, "TV Tower", "Panoramastraße 1A, Berlin", venue = Venue(Venue.GOOGLE, "p2", "tourist_attraction")),
        GeoPlace(52.5215, 13.4122, "World Clock", "Alexanderplatz, Berlin", venue = Venue(Venue.GOOGLE, "p3", "point_of_interest")),
    )

    val picker = LocationShareState(
        LocationStatus.Found, me, accuracy = 12, maps = true,
        picked = GeoPlace(52.5219, 13.4132, "Alexanderplatz 1", "10178 Berlin, Germany"),
        nearby = nearby,
    )

    val searching = picker.copy(
        query = "alexa",
        suggestions = listOf(
            PlaceSuggestion("p1", "ALEXA", "Grunerstraße, Berlin, Germany"),
            PlaceSuggestion("p4", "Alexanderplatz", "Berlin, Germany"),
            PlaceSuggestion("p5", "Alexandrinenstraße", "Berlin, Germany"),
        ),
    )
}
