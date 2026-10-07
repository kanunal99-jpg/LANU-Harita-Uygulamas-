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
    fun warningBucketsCoverFinalFiveKilometersIn500MeterSteps() {
        assertEquals(5000, SafetyCameraWarningPolicy.warningBucket(5000.0))
        assertEquals(5000, SafetyCameraWarningPolicy.warningBucket(4999.0))
        assertEquals(4500, SafetyCameraWarningPolicy.warningBucket(4500.0))
        assertEquals(1000, SafetyCameraWarningPolicy.warningBucket(999.0))
        assertEquals(500, SafetyCameraWarningPolicy.warningBucket(1.0))
    }

    @Test
    fun drivingWarningRadiusIsAlwaysFiveKilometers() {
        assertEquals(5000, SafetyCameraWarningPolicy.warningRadiusMeters(40f))
        assertEquals(5000, SafetyCameraWarningPolicy.warningRadiusMeters(70f))
        assertEquals(5000, SafetyCameraWarningPolicy.warningRadiusMeters(120f))
    }

    @Test
    fun drivingWarningNeverStartsBeforeFiveKilometers() {
        assertNull(SafetyCameraWarningPolicy.evaluate(camera, 5000.1, 40f))
        assertNull(SafetyCameraWarningPolicy.evaluate(camera, 20_000.0, 120f))
        assertNotNull(SafetyCameraWarningPolicy.evaluate(camera, 5000.0, 120f))
    }

    @Test
    fun announcementMilestonesDescendEveryFiveHundredMeters() {
        assertEquals(5000, SafetyCameraWarningPolicy.announcementMilestone(4999.0))
        assertEquals(4500, SafetyCameraWarningPolicy.announcementMilestone(4499.0))
        assertEquals(4000, SafetyCameraWarningPolicy.announcementMilestone(3999.0))
        assertEquals(3500, SafetyCameraWarningPolicy.announcementMilestone(3499.0))
        assertEquals(3000, SafetyCameraWarningPolicy.announcementMilestone(2999.0))
        assertEquals(2500, SafetyCameraWarningPolicy.announcementMilestone(2499.0))
        assertEquals(2000, SafetyCameraWarningPolicy.announcementMilestone(1999.0))
        assertEquals(1500, SafetyCameraWarningPolicy.announcementMilestone(1499.0))
        assertEquals(1000, SafetyCameraWarningPolicy.announcementMilestone(999.0))
        assertEquals(500, SafetyCameraWarningPolicy.announcementMilestone(499.0))
        assertNull(SafetyCameraWarningPolicy.announcementMilestone(5000.1))
        assertNull(SafetyCameraWarningPolicy.announcementMilestone(0.0))
    }

    @Test
    fun etaIsComputedOnlyAtMeaningfulDrivingSpeed() {
        assertEquals(150, SafetyCameraWarningPolicy.estimateSecondsToCamera(5_000.0, 120f))
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
