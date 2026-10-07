package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCameraAreaPolicyTest {
    @Test
    fun navigationPrefetchCoversFinalFiveKilometerEnvelopeWithBuffer() {
        val center = GeoPoint(41.015137, 28.979530)
        val bbox = SafetyCameraAreaPolicy.boundingBoxAround(center)

        assertTrue(bbox.isValid())
        assertTrue(bbox.contains(GeoPoint(41.060000, 28.979530)))
        assertTrue(bbox.contains(center))
    }

    @Test
    fun routePrefetchCentersCoverLongRouteBeforeNavigationStarts() {
        val route = listOf(
            GeoPoint(41.0, 29.0),
            GeoPoint(41.2, 29.0),
            GeoPoint(41.4, 29.0),
            GeoPoint(41.6, 29.0)
        )

        val centers = SafetyCameraAreaPolicy.routePrefetchCenters(route)

        assertTrue(centers.size >= 4)
        assertTrue(centers.first().distanceTo(route.first()) < 5.0)
        assertTrue(centers.last().distanceTo(route.last()) < SafetyCameraAreaPolicy.ROUTE_PREFETCH_SPACING_METERS)
    }

    @Test
    fun refreshRequiresMeaningfulMovement() {
        val start = GeoPoint(41.015137, 28.979530)
        val nearby = GeoPoint(41.020000, 28.979530)
        val farther = GeoPoint(41.040000, 28.979530)

        assertFalse(SafetyCameraAreaPolicy.shouldRefresh(start, nearby))
        assertTrue(SafetyCameraAreaPolicy.shouldRefresh(start, farther))
    }
}
