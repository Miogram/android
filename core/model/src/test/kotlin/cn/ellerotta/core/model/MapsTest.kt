package cn.ellerotta.core.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MapsTest {
    private val berlin = StaticMap(52.52, 13.405, 15, 264, 158, dark = false)

    @Test
    fun aRoutesLineDecodesAndItsTimeRoundsUp() {
        val points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453), points.map { it.latitude to it.longitude })
        assertEquals(emptyList(), Polyline.decode(""))
        assertEquals(1, Polyline.decode("_p~iF~ps|U_ulL").size)
        assertEquals(15, Route(TravelMode.Walking, 1200, 841, "").minutes)
        assertEquals(1, Route(TravelMode.Walking, 10, 5, "").minutes)
    }

    @Test
    fun aRoutesLineIsDrawnByGoogleInTheAccent() {
        val url = StaticMaps.url(berlin.copy(path = "_p~iF~ps|U", pathColor = 0xFF4355B9.toInt()), "key")
        assertTrue("&path=color%3A0x4355b9ff%7Cweight%3A5%7Cenc%3A_p%7EiF%7Eps%7CU" in url, url)
        assertNotEquals(berlin.cacheKey, berlin.copy(path = "_p~iF~ps|U").cacheKey)
        assertFalse("path=" in StaticMaps.url(berlin, "key"))
    }

    @Test
    fun theDeviceDrivesFromTwentyFiveKilometresAnHourAndWalksAgainAfterTwoMinutesSlow() {
        val guess = TravelGuess()
        assertEquals(TravelMode.Walking, guess.next(1.4f, 0))
        assertEquals(TravelMode.Driving, guess.next(12f, 30))
        assertEquals(TravelMode.Driving, guess.next(0f, 60))
        assertEquals(TravelMode.Driving, guess.next(0f, 150))
        assertEquals(TravelMode.Driving, guess.next(9f, 170))
        assertEquals(TravelMode.Driving, guess.next(1f, 200))
        assertEquals(TravelMode.Walking, guess.next(1f, 320))
    }

    @Test
    fun aLivePictureFitsEveryoneInsideItsMargin() {
        val me = GeoPlace(52.52, 13.405)
        val bob = GeoPlace(52.53, 13.42)
        val map = StaticMaps.fit(listOf(me, bob), 160, dark = true, margin = 20)!!
        assertEquals(160, map.width)
        assertEquals(160, map.height)
        assertTrue(map.dark)
        listOf(me, bob).forEach { point ->
            val (dx, dy) = WebMercator.project(point.latitude, point.longitude, map.latitude, map.longitude, map.zoom.toDouble())
            assertTrue(abs(dx) <= 60.0 && abs(dy) <= 60.0, "$dx $dy")
        }
        val (closerX, closerY) = WebMercator.project(bob.latitude, bob.longitude, me.latitude, me.longitude, map.zoom + 1.0)
        assertTrue(maxOf(abs(closerX), abs(closerY)) > 120.0)
        val alone = StaticMaps.fit(listOf(me), 160, dark = false)!!
        assertEquals(StaticMaps.SCREEN_ZOOM, alone.zoom)
        assertEquals(52.52, alone.latitude, 1e-9)
        assertEquals(null, StaticMaps.fit(emptyList(), 160, dark = false))
    }

    @Test
    fun theLiveUpdateAsksAgainAfterAMinuteOrAHundredMetres() {
        val me = GeoPlace(52.52, 13.405)
        val near = GeoPlace(52.52045, 13.405)
        val far = GeoPlace(52.52135, 13.405)
        assertTrue(MapRefresh.due(null, 0, listOf(me), 0))
        assertFalse(MapRefresh.due(listOf(me), 0, listOf(near), 30))
        assertTrue(MapRefresh.due(listOf(me), 0, listOf(near), 60))
        assertTrue(MapRefresh.due(listOf(me), 0, listOf(far), 5))
        assertFalse(MapRefresh.due(listOf(me), 0, listOf(me), 3_600))
        assertTrue(MapRefresh.due(listOf(me), 0, listOf(me, near), 5))
    }

    @Test
    fun aStaticMapAsksForTwiceThePixelsOfItsSizeAtItsPoint() {
        val url = StaticMaps.url(berlin, "a key/+")
        assertTrue(url.startsWith("https://maps.googleapis.com/maps/api/staticmap?"))
        assertTrue("center=52.520000,13.405000" in url)
        assertTrue("zoom=15" in url)
        assertTrue("size=264x158" in url)
        assertTrue("scale=2" in url)
        assertTrue("format=png" in url)
        assertTrue(url.endsWith("&key=a+key%2F%2B"))
        assertFalse("style=" in url)
        assertFalse("markers=" in url)
    }

    @Test
    fun darkMapsTakeTheNightStyle() {
        val url = StaticMaps.url(berlin.copy(dark = true), "k")
        assertEquals(StaticMaps.nightStyle.size, Regex("&style=").findAll(url).count())
        assertTrue("&style=element%3Ageometry%7Ccolor%3A0x242f3e" in url)
    }

    @Test
    fun sizeAndZoomStayWithinWhatTheServiceDraws() {
        val big = StaticMap(0.0, 0.0, 25, 900, 0, dark = false).clamped()
        assertEquals(640, big.width)
        assertEquals(1, big.height)
        assertEquals(StaticMaps.MAX_ZOOM, big.zoom)
        assertEquals(StaticMaps.MIN_ZOOM, StaticMap(0.0, 0.0, 0, 10, 10, false).clamped().zoom)
    }

    @Test
    fun telegramsMapStaysWithinItsLimitsAndIsCachedByPointSizeZoomAndScale() {
        val map = GeoMap(52.52, 13.405, accessHash = 9, width = 264, height = 158, zoom = 15, scale = 2)
        assertEquals("52.520000_13.405000_264x158_z15_x2", map.cacheKey)
        assertEquals(map.cacheKey, map.copy(accessHash = 10).cacheKey)
        assertNotEquals(map.cacheKey, map.copy(scale = 1).cacheKey)
        val clamped = GeoMap(0.0, 0.0, 1, width = 2000, height = 4, zoom = 3, scale = 5).clamped()
        assertEquals(listOf(1024, 16, 13, 3), listOf(clamped.width, clamped.height, clamped.zoom, clamped.scale))
        assertEquals(20, map.copy(zoom = 30).clamped().zoom)
    }

    @Test
    fun bubblesZoomByKindAndScaleByDensity() {
        assertEquals(GeoMaps.PLACE_ZOOM, GeoMaps.zoomOf(GeoPlace(1.0, 2.0)))
        assertEquals(GeoMaps.VENUE_ZOOM, GeoMaps.zoomOf(GeoPlace(1.0, 2.0, "Alexanderplatz")))
        assertEquals(GeoMaps.LIVE_ZOOM, GeoMaps.zoomOf(GeoPlace(1.0, 2.0, live = LiveInfo(period = 900))))
        assertEquals(1, GeoMaps.scaleOf(1.5f))
        assertEquals(2, GeoMaps.scaleOf(2f))
        assertEquals(2, GeoMaps.scaleOf(3.5f))
    }

    @Test
    fun theCacheKeyIsPointZoomSizeAndTheme() {
        assertEquals("52.520000_13.405000_15_264x158_light", berlin.cacheKey)
        assertNotEquals(berlin.cacheKey, berlin.copy(dark = true).cacheKey)
        assertNotEquals(berlin.cacheKey, berlin.copy(zoom = 16).cacheKey)
        assertNotEquals(berlin.cacheKey, berlin.copy(width = 200).cacheKey)
        assertNotEquals(berlin.cacheKey, berlin.copy(latitude = 52.5201).cacheKey)
    }

    @Test
    fun webMercatorPlacesPointsLikeGooglesMaps(): Unit {
        assertEquals(128.0, WebMercator.x(0.0, 0.0), 1e-9)
        assertEquals(128.0, WebMercator.y(0.0, 0.0), 1e-9)
        assertEquals(512.0, WebMercator.worldSize(1.0), 1e-9)
        val (lat, lon) = WebMercator.offset(52.52, 13.405, 15.0, 120.0, -80.0)
        val (dx, dy) = WebMercator.project(lat, lon, 52.52, 13.405, 15.0)
        assertEquals(120.0, dx, 1e-6)
        assertEquals(-80.0, dy, 1e-6)
        val (wrapped, _) = WebMercator.project(10.0, -179.9, 10.0, 179.9, 5.0)
        assertTrue(wrapped > 0 && wrapped < 10)
        assertTrue(WebMercator.y(53.0, 10.0) < WebMercator.y(52.0, 10.0))
        assertTrue(abs(WebMercator.latitude(-1e6, 3.0) - WebMercator.MAX_LATITUDE) < 1e-9)
    }

    @Test
    fun distancesReadInTheLocalesUnits() {
        assertEquals(DistanceUnits.Imperial, DistanceUnits.of("US"))
        assertEquals(DistanceUnits.Imperial, DistanceUnits.of("lr"))
        assertEquals(DistanceUnits.Metric, DistanceUnits.of("GB"))
        assertEquals(DistanceUnits.Metric, DistanceUnits.of("RU"))
        assertEquals(DistanceUnits.Metric, DistanceUnits.of(""))

        fun metric(m: Double) = DistanceAmount.of(m, DistanceUnits.Metric).let { it.text to it.unit }
        assertEquals("250" to DistanceUnit.Metres, metric(249.6))
        assertEquals("1" to DistanceUnit.Kilometres, metric(1000.0))
        assertEquals("1.5" to DistanceUnit.Kilometres, metric(1520.0))
        assertEquals("12" to DistanceUnit.Kilometres, metric(12_345.0))

        fun imperial(m: Double) = DistanceAmount.of(m, DistanceUnits.Imperial).let { it.text to it.unit }
        assertEquals("66" to DistanceUnit.Feet, imperial(20.0))
        assertEquals("820" to DistanceUnit.Feet, imperial(250.0))
        assertEquals("1.2" to DistanceUnit.Miles, imperial(2000.0))
        assertEquals("62" to DistanceUnit.Miles, imperial(100_000.0))
        assertEquals("0.25" to DistanceUnit.Miles, imperial(402.0))
        assertEquals("0.5" to DistanceUnit.Miles, imperial(805.0))
    }

    @Test
    fun aPlaceLinksToTheWayThereAndSharesAsText() {
        val place = GeoPlace(52.52, 13.405, "Alexanderplatz", "10178 Berlin")
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=52.520000,13.405000", place.directionsUri)
        assertEquals("Alexanderplatz\n10178 Berlin\nhttps://www.google.com/maps/search/?api=1&query=52.520000,13.405000", place.shareText)
        assertEquals("https://www.google.com/maps/search/?api=1&query=52.520000,13.405000", GeoPlace(52.52, 13.405).shareText)
    }

    @Test
    fun liveSharersAreOnePerSenderTheAccountFirst() {
        fun live(id: Int, key: String, out: Boolean = false, updated: Int = 0, expires: Int? = null, stopped: Boolean = false) =
            MessageItem(id = id, text = "", out = out, date = 0, place = GeoPlace(1.0, 2.0, live = LiveInfo(period = 900, expires = expires, stopped = stopped, updated = updated, senderKey = key)))
        val messages = listOf(
            live(1, "u2", updated = 50),
            live(2, "u3", updated = 90),
            live(3, "u2", updated = 10),
            live(4, "u1", out = true, updated = 5),
            live(5, "u4", expires = 100),
            live(6, "u5", stopped = true),
            MessageItem(id = 7, text = "a point", out = false, date = 0, place = GeoPlace(1.0, 2.0)),
        )
        assertEquals(listOf(4, 2, 3), liveSharers(messages, now = 100).map { it.messageId })
        assertTrue(liveSharers(messages, now = 100).first().self)
    }

    @Test
    fun proximityRadiiGoFrom50MetresTo100KilometresInRoundedSteps() {
        val metric = ProximityRadii.steps(DistanceUnits.Metric)
        assertEquals(50, metric.first())
        assertEquals(100_000, metric.last())
        assertTrue(500 in metric && 1500 in metric && 25_000 in metric)
        val imperial = ProximityRadii.steps(DistanceUnits.Imperial)
        assertEquals(61, imperial.first())
        assertEquals(96_561, imperial.last())
        assertTrue(imperial.all { it in ProximityRadii.MIN..ProximityRadii.MAX })
        listOf(metric, imperial).forEach { steps -> assertEquals(steps.sorted(), steps.distinct()) }
        assertEquals(metric.indexOf(500), ProximityRadii.nearest(metric, 480))
        assertEquals(0, ProximityRadii.nearest(metric, 1))
        assertEquals(402, imperial[ProximityRadii.nearest(imperial, 400)])
    }
}
