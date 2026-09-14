package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocationQualityFilterTest {
    private var now = 1_000_000L
    private val filter = LocationQualityFilter(nowMs = { now })

    private fun fix(
        lat: Double,
        lon: Double,
        timestamp: Long = now,
        accuracy: Float = 5f,
        speed: Float = 40f
    ) = UserLocationData(
        point = GeoPoint(lat, lon),
        speedKmh = speed,
        accuracyMeters = accuracy,
        timestamp = timestamp
    )

    @Test
    fun validFixIsAccepted() {
        assertNotNull(filter.accept(fix(41.0082, 28.9784)))
    }

    @Test
    fun staleFixIsRejected() {
        assertNull(filter.accept(fix(41.0082, 28.9784, timestamp = now - 30_001)))
    }

    @Test
    fun impossibleJumpIsRejected() {
        assertNotNull(filter.accept(fix(41.0082, 28.9784)))
        assertNull(filter.accept(fix(41.1082, 28.9784, timestamp = now + 1_500)))
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        assertNull(filter.accept(fix(91.0, 28.9784)))
        assertNull(filter.accept(fix(41.0082, 181.0)))
    }

    @Test
    fun resetAllowsNewTrack() {
        assertNotNull(filter.accept(fix(41.0082, 28.9784)))
        filter.reset()
        assertNotNull(filter.accept(fix(41.1082, 28.9784)))
    }
}
