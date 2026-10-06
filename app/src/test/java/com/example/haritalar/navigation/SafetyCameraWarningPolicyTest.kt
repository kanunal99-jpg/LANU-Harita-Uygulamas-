package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun warningBucketsCoverTenKilometersIn500MeterSteps() {
        assertEquals(10000, SafetyCameraWarningPolicy.warningBucket(10000.0))
        assertEquals(10000, SafetyCameraWarningPolicy.warningBucket(9999.0))
        assertEquals(5000, SafetyCameraWarningPolicy.warningBucket(4999.0))
        assertEquals(4500, SafetyCameraWarningPolicy.warningBucket(4500.0))
        assertEquals(500, SafetyCameraWarningPolicy.warningBucket(1.0))
    }

    @Test
    fun warningRadiusAdaptsToVehicleSpeed() {
        assertEquals(5000, SafetyCameraWarningPolicy.warningRadiusMeters(40f))
        assertEquals(6500, SafetyCameraWarningPolicy.warningRadiusMeters(70f))
        assertEquals(8000, SafetyCameraWarningPolicy.warningRadiusMeters(90f))
        assertEquals(10000, SafetyCameraWarningPolicy.warningRadiusMeters(110f))
    }

    @Test
    fun outsideAdaptiveEnvelopeProducesNoWarning() {
        assertNull(SafetyCameraWarningPolicy.evaluate(camera, 5000.1, 40f))
        assertNull(SafetyCameraWarningPolicy.evaluate(camera, 8000.1, 95f))
        assertNotNull(SafetyCameraWarningPolicy.evaluate(camera, 9000.0, 120f))
    }

    @Test
    fun announcementMilestonesAreSparseAndEarly() {
        assertEquals(10000, SafetyCameraWarningPolicy.announcementMilestone(9900.0))
        assertEquals(8000, SafetyCameraWarningPolicy.announcementMilestone(7900.0))
        assertEquals(6500, SafetyCameraWarningPolicy.announcementMilestone(6400.0))
        assertEquals(5000, SafetyCameraWarningPolicy.announcementMilestone(4900.0))
        assertEquals(3000, SafetyCameraWarningPolicy.announcementMilestone(2990.0))
        assertEquals(500, SafetyCameraWarningPolicy.announcementMilestone(450.0))
        assertNull(SafetyCameraWarningPolicy.announcementMilestone(10000.1))
    }

    @Test
    fun etaIsComputedOnlyAtMeaningfulDrivingSpeed() {
        assertEquals(300, SafetyCameraWarningPolicy.estimateSecondsToCamera(10_000.0, 120f))
        assertNull(SafetyCameraWarningPolicy.estimateSecondsToCamera(1000.0, 4f))
    }

    @Test
    fun speedLimitIsParsedWithoutInventingMissingLimits() {
        assertEquals(50, SafetyCameraWarningPolicy.parseSpeedLimitKmh("50 km/h"))
        assertEquals(90, SafetyCameraWarningPolicy.parseSpeedLimitKmh("90"))
        assertEquals(80, SafetyCameraWarningPolicy.parseSpeedLimitKmh("50 mph"))
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
