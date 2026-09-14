package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCameraWarningPolicyTest {
    private val point = GeoPoint(41.0, 29.0)
    private val camera = SafetyCamera(
        id = 1L,
        point = point,
        maxSpeed = "50"
    )

    @Test
    fun warningBucketsCoverFiveKilometersIn500MeterSteps() {
        assertEquals(5000, SafetyCameraWarningPolicy.warningBucket(5000.0))
        assertEquals(5000, SafetyCameraWarningPolicy.warningBucket(4999.0))
        assertEquals(4500, SafetyCameraWarningPolicy.warningBucket(4500.0))
        assertEquals(500, SafetyCameraWarningPolicy.warningBucket(1.0))
    }

    @Test
    fun outsideFiveKilometersProducesNoWarning() {
        assertNull(SafetyCameraWarningPolicy.evaluate(camera, 5000.1, 40f))
    }

    @Test
    fun speedLimitIsParsedWithoutInventingMissingLimits() {
        assertEquals(50, SafetyCameraWarningPolicy.parseSpeedLimitKmh("50 km/h"))
        assertEquals(90, SafetyCameraWarningPolicy.parseSpeedLimitKmh("90"))
        assertNull(SafetyCameraWarningPolicy.parseSpeedLimitKmh(null))
        assertNull(SafetyCameraWarningPolicy.parseSpeedLimitKmh("unknown"))
    }

    @Test
    fun overspeedIsOnlyTrueWhenSourceProvidesSpeedLimit() {
        val warning = SafetyCameraWarningPolicy.evaluate(camera, 800.0, 61f)
        assertTrue(warning?.overspeed == true)

        val unknownLimit = camera.copy(maxSpeed = null)
        val unknownWarning = SafetyCameraWarningPolicy.evaluate(unknownLimit, 800.0, 200f)
        assertFalse(unknownWarning?.overspeed == true)
    }
}
