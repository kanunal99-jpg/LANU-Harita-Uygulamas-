package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCameraAreaPolicyTest {
    @Test
    fun navigationPrefetchCoversTenKilometerWarningEnvelopeWithBuffer() {
        val center = GeoPoint(41.015137, 28.979530)
        val bbox = SafetyCameraAreaPolicy.boundingBoxAround(center)

        assertTrue(bbox.isValid())
        assertTrue(bbox.contains(GeoPoint(41.105000, 28.979530)))
        assertTrue(bbox.contains(GeoPoint(41.015137, 29.110000)))
        assertTrue(bbox.contains(center))
    }

    @Test
    fun viewportLoadingStopsBelowRadarDisplayZoom() {
        val bbox = SafetyCameraAreaPolicy.boundingBoxAround(GeoPoint(41.015137, 28.979530))
        assertFalse(SafetyCameraAreaPolicy.shouldLoadViewport(bbox, 11.9f))
        assertTrue(SafetyCameraAreaPolicy.shouldLoadViewport(bbox, 12.0f))
    }

    @Test
    fun refreshRequiresMeaningfulMovement() {
        val start = GeoPoint(41.015137, 28.979530)
        val nearby = GeoPoint(41.020000, 28.979530)
        val farther = GeoPoint(41.030000, 28.979530)

        assertFalse(SafetyCameraAreaPolicy.shouldRefresh(start, nearby))
        assertTrue(SafetyCameraAreaPolicy.shouldRefresh(start, farther))
    }
}
