package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.TrafficSignalBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiSearchAreaPolicyTest {
    @Test
    fun followModeUsesBoundedEightKmCoverage() {
        val center = GeoPoint(41.0, 29.0)
        val huge = TrafficSignalBoundingBox(40.0, 28.0, 42.0, 30.0)
        assertEquals(
            8_000,
            PoiSearchAreaPolicy.radiusMeters(MapTrackingMode.FOLLOW_USER, center, huge)
        )
    }

    @Test
    fun freeBrowsingExpandsWithViewportButNeverOverloadsProvider() {
        val center = GeoPoint(41.0, 29.0)
        val city = TrafficSignalBoundingBox(40.85, 28.80, 41.15, 29.20)
        val radius = PoiSearchAreaPolicy.radiusMeters(MapTrackingMode.FREE, center, city)
        assertTrue(radius in 4_000..20_000)

        val countryScale = TrafficSignalBoundingBox(35.0, 25.0, 43.0, 45.0)
        assertEquals(
            20_000,
            PoiSearchAreaPolicy.radiusMeters(MapTrackingMode.FREE, center, countryScale)
        )
    }
}
