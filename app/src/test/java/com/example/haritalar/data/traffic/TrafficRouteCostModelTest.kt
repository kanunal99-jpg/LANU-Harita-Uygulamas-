package com.example.haritalar.data.traffic

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrafficRouteCostModelTest {
    @Test
    fun emptySegments_neverClaimClearTraffic() {
        val status = TrafficRouteCostModel.calculateTrafficStatus(
            segments = emptyList(),
            hasProvider = true
        )

        assertFalse(status.verified)
        assertEquals(TrafficLevel.UNKNOWN, status.trafficLevel)
        assertEquals(0L, status.delaySeconds)
        assertEquals("Canlı trafik doğrulanamadı • temel ETA korunuyor", status.message)
    }

    @Test
    fun verifiedSegments_reportLiveTraffic() {
        val segment = TrafficSegment(
            coordinates = listOf(GeoPoint(41.0, 29.0), GeoPoint(41.001, 29.001)),
            currentSpeed = 30.0,
            freeFlowSpeed = 60.0,
            delaySeconds = 90L,
            confidence = 0.9,
            roadClosure = false
        )

        val status = TrafficRouteCostModel.calculateTrafficStatus(
            segments = listOf(segment),
            hasProvider = true
        )

        assertEquals(true, status.verified)
        assertEquals(TrafficLevel.HEAVY, status.trafficLevel)
        assertEquals(90L, status.delaySeconds)
        assertEquals("TomTom Traffic Flow API v4", status.sourceName)
        assertEquals(true, status.isLiveApi)
        assertEquals("Doğrulanan 1 segment ortalama hızı: 30 km/h", status.rawSampleDetails)
    }
}
