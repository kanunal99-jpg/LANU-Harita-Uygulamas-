package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.TrafficSignalBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiSearchCenterPolicyTest {
    private val now = 10_000_000L
    private val bbox = TrafficSignalBoundingBox(
        south = 40.8,
        west = 29.1,
        north = 41.0,
        east = 29.3
    )

    @Test
    fun freeMapUsesVisibleViewportCenter() {
        val fresh = UserLocationData(
            point = GeoPoint(40.9, 29.4),
            accuracyMeters = 10f,
            timestamp = now
        )

        assertEquals(
            GeoPoint(40.9, 29.2),
            PoiSearchCenterPolicy.resolve(
                trackingMode = MapTrackingMode.FREE,
                freshRoutingLocation = fresh,
                lastKnownLocation = fresh,
                viewport = bbox,
                nowMillis = now
            )
        )
    }

    @Test
    fun followModeUsesLastKnownForPoiWhenRoutingFixIsNotFresh() {
        val lastKnown = UserLocationData(
            point = GeoPoint(40.91, 29.22),
            accuracyMeters = 80f,
            timestamp = now - 120_000L
        )

        assertEquals(
            lastKnown.point,
            PoiSearchCenterPolicy.resolve(
                trackingMode = MapTrackingMode.FOLLOW_USER,
                freshRoutingLocation = null,
                lastKnownLocation = lastKnown,
                viewport = bbox,
                nowMillis = now
            )
        )
    }

    @Test
    fun ancientLastKnownFallsBackToViewport() {
        val old = UserLocationData(
            point = GeoPoint(40.7, 29.0),
            accuracyMeters = 50f,
            timestamp = now - PoiSearchCenterPolicy.MAX_LAST_KNOWN_AGE_MS - 1L
        )

        assertEquals(
            PoiSearchCenterPolicy.viewportCenter(bbox),
            PoiSearchCenterPolicy.resolve(
                trackingMode = MapTrackingMode.FOLLOW_USER,
                freshRoutingLocation = null,
                lastKnownLocation = old,
                viewport = bbox,
                nowMillis = now
            )
        )
    }

    @Test
    fun noValidSourceReturnsNull() {
        assertNull(
            PoiSearchCenterPolicy.resolve(
                trackingMode = MapTrackingMode.FOLLOW_USER,
                freshRoutingLocation = null,
                lastKnownLocation = null,
                viewport = null,
                nowMillis = now
            )
        )
    }

    @Test
    fun refreshRequiresMeaningfulMovement() {
        val start = GeoPoint(41.0, 29.0)
        assertTrue(
            PoiSearchCenterPolicy.shouldRefresh(
                start,
                GeoPoint(41.03, 29.0)
            )
        )
    }
}
