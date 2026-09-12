package com.example.haritalar.data

import com.example.haritalar.data.cache.TrafficSignalCache
import com.example.haritalar.data.network.TrafficSignalService
import com.example.haritalar.data.repository.TrafficSignalRepository
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox
import com.example.haritalar.model.TrafficSignalFetchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrafficSignalIntegrationTest {

    @Test
    fun testParseOverpassResponse_validJson() {
        val service = TrafficSignalService()
        val sampleOverpassJson = """
        {
            "version": 0.6,
            "generator": "Overpass API",
            "elements": [
                {
                    "type": "node",
                    "id": 1001,
                    "lat": 41.0125,
                    "lon": 28.9812,
                    "tags": {
                        "highway": "traffic_signals",
                        "crossing": "traffic_signals",
                        "traffic_signals:sound": "yes",
                        "traffic_signals:vibration": "yes",
                        "traffic_signals:arrow": "yes",
                        "traffic_signals:direction": "forward",
                        "ref": "TS-4101"
                    }
                },
                {
                    "type": "node",
                    "id": 1002,
                    "lat": 41.0130,
                    "lon": 28.9820,
                    "tags": {
                        "crossing": "traffic_signals"
                    }
                },
                {
                    "type": "node",
                    "id": 1003,
                    "lat": 999.0,
                    "lon": 28.9820,
                    "tags": {
                        "highway": "traffic_signals"
                    }
                }
            ]
        }
        """.trimIndent()

        val parsed = service.parseOsmResponse(sampleOverpassJson)
        assertEquals(2, parsed.size)

        val first = parsed.first { it.id == 1001L }
        assertEquals(41.0125, first.point.latitude, 0.0001)
        assertEquals(28.9812, first.point.longitude, 0.0001)
        assertEquals("traffic_signals", first.crossing)
        assertTrue(first.hasSound)
        assertTrue(first.hasVibration)
        assertTrue(first.hasArrow)
        assertEquals("forward", first.direction)
        assertEquals("TS-4101", first.reference)
        assertEquals("Yaya & Kavşak Sinyalizasyonu", first.displayTitle)
        assertTrue(first.displaySubtitle.contains("OSM #1001"))
        assertTrue(first.displaySubtitle.contains("Sesli"))

        val second = parsed.first { it.id == 1002L }
        assertEquals(1002L, second.id)
        assertFalse(second.hasSound)
        assertEquals("Yaya & Kavşak Sinyalizasyonu", second.displayTitle)
    }

    @Test
    fun testParseOverpassResponse_emptyOrMalformed() {
        val service = TrafficSignalService()
        assertEquals(0, service.parseOsmResponse("").size)
        assertEquals(0, service.parseOsmResponse("<html>Error 504</html>").size)
        assertEquals(0, service.parseOsmResponse("{\"elements\": []}").size)
    }

    @Test
    fun testTrafficSignalCache_inMemoryAndDeduplication() = runBlocking {
        val cache = TrafficSignalCache(trafficSignalDao = null, ttlMillis = 10_000L)
        val bbox = TrafficSignalBoundingBox(south = 40.0, west = 28.0, north = 42.0, east = 30.0)

        val signal1 = TrafficSignal(
            id = 501L,
            point = GeoPoint(41.0, 29.0),
            crossing = "traffic_signals"
        )
        val signal2 = TrafficSignal(
            id = 502L,
            point = GeoPoint(41.5, 29.5),
            crossing = null
        )

        cache.putSignals(bbox, listOf(signal1, signal2, signal1)) // signal1 duplicated

        val cached = cache.getSignalsForBoundingBox(bbox)
        assertEquals(2, cached.size)
        assertTrue(cached.any { it.id == 501L })
        assertTrue(cached.any { it.id == 502L })

        // Outside bounding box query should return empty
        val outsideBbox = TrafficSignalBoundingBox(south = 10.0, west = 10.0, north = 12.0, east = 12.0)
        val emptyResult = cache.getSignalsForBoundingBox(outsideBbox)
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun testTrafficSignalRepository_zoomCutoff() = runBlocking {
        val repo = TrafficSignalRepository()
        val bbox = TrafficSignalBoundingBox(south = 41.0, west = 28.9, north = 41.1, east = 29.0)

        // Far zoom level (e.g. 10.0f) should skip query to preserve bandwidth and performance
        val result = repo.getTrafficSignalsForViewport(bbox, zoomLevel = 10.0f)
        assertTrue(result is TrafficSignalFetchResult.Success)
        val success = result as TrafficSignalFetchResult.Success
        assertTrue(success.signals.isEmpty())
        assertFalse(success.fromCache)
    }

    @Test
    fun testTrafficSignalBoundingBox_validation() {
        val validBox = TrafficSignalBoundingBox(south = 41.0, west = 28.0, north = 41.2, east = 28.5)
        assertTrue(validBox.isValid())
        assertTrue(validBox.contains(GeoPoint(41.1, 28.2)))
        assertFalse(validBox.contains(GeoPoint(42.0, 28.2)))

        val invertedBox = TrafficSignalBoundingBox(south = 42.0, west = 28.0, north = 41.0, east = 28.5)
        assertFalse(invertedBox.isValid())
    }
}
