package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.TrafficSignalBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiViewportPolicyTest {
    private val bbox = TrafficSignalBoundingBox(
        south = 40.85,
        west = 29.10,
        north = 41.05,
        east = 29.40
    )

    @Test
    fun categorySearchCanStartEarlierThanGenericPoiLayer() {
        assertTrue(PoiViewportPolicy.canQuery(10.5f, PoiCategory.FUEL))
        assertFalse(PoiViewportPolicy.canQuery(10.5f, null))
        assertTrue(PoiViewportPolicy.canQuery(13f, null))
    }

    @Test
    fun viewportRadiusIsBoundedForProviderSafety() {
        val radius = PoiViewportPolicy.searchRadiusMeters(bbox, 11f)
        assertTrue(radius in 4_000..20_000)
    }

    @Test
    fun lowZoomThinsDensePoiMarkersWithoutInventingData() {
        val points = (0 until 50).map { i ->
            PoiItem(
                id = "fuel_$i",
                name = "Benzinlik $i",
                category = PoiCategory.FUEL,
                point = GeoPoint(
                    40.95 + (i % 10) * 0.001,
                    29.25 + (i / 10) * 0.001
                )
            )
        }

        val visible = PoiViewportPolicy.visiblePois(points, bbox, 11f)

        assertTrue(visible.size <= PoiViewportPolicy.maxVisiblePois(11f))
        assertTrue(visible.all { it in points })
        assertEquals(visible.map { it.id }.distinct().size, visible.size)
    }

    @Test
    fun highZoomShowsMoreRealPoisThanLowZoom() {
        val points = (0 until 80).map { i ->
            PoiItem(
                id = "poi_$i",
                name = "POI $i",
                category = PoiCategory.PHARMACY,
                point = GeoPoint(
                    40.95 + (i % 20) * 0.0008,
                    29.25 + (i / 20) * 0.0008
                )
            )
        }

        val low = PoiViewportPolicy.visiblePois(points, bbox, 12f)
        val high = PoiViewportPolicy.visiblePois(points, bbox, 17f)

        assertTrue(high.size >= low.size)
    }
}
