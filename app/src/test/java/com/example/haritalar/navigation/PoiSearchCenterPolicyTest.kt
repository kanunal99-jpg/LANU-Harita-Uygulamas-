package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.TrafficSignalBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoiSearchCenterPolicyTest {
    private val viewport = TrafficSignalBoundingBox(
        south = 40.90,
        west = 29.15,
        north = 41.00,
        east = 29.35
    )

    @Test
    fun freeBrowsingUsesViewportCenterEvenWhenLiveLocationExists() {
        val live = GeoPoint(41.10, 29.50)
        assertEquals(
            GeoPoint(40.95, 29.25),
            PoiSearchCenterPolicy.resolve(MapTrackingMode.FREE, live, viewport)
        )
    }

    @Test
    fun followModeFallsBackToViewportWhenGpsIsUnavailable() {
        assertEquals(
            GeoPoint(40.95, 29.25),
            PoiSearchCenterPolicy.resolve(MapTrackingMode.FOLLOW_USER, null, viewport)
        )
    }

    @Test
    fun viewportRadiusExpandsCoverageButStaysWithinOverpassLimit() {
        val radius = PoiSearchCenterPolicy.radiusMeters(viewport)
        assertEquals(9864, radius, 1500)

        val hugeViewport = TrafficSignalBoundingBox(39.0, 26.0, 42.0, 31.0)
        assertEquals(20_000, PoiSearchCenterPolicy.radiusMeters(hugeViewport))
    }

    @Test
    fun returnsNullOnlyWhenNeitherMapNorGpsCanProvideCenter() {
        assertNull(PoiSearchCenterPolicy.resolve(MapTrackingMode.FREE, null, null))
    }
}
