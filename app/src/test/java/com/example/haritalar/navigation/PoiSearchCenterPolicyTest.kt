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
    fun returnsNullOnlyWhenNeitherMapNorGpsCanProvideCenter() {
        assertNull(PoiSearchCenterPolicy.resolve(MapTrackingMode.FREE, null, null))
    }
}
