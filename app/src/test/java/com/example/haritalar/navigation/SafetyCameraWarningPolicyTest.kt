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
    fun multipleOrConditionalOsmLimitsNeverBecomeVerifiedNumericLimit() {
        val ambiguous = listOf(
            "50;70", "50; 80", "50 @ (Mo-Fr 07:00-19:00)",
            "30 mph;50", "TR:urban", "signals", "none",
            "maxspeed=50", "zone:30", "50-70", "50 km/h (night)",
            "50.5", "50,5", "90mph extra", "-50", "999"
        )
        ambiguous.forEach { raw ->
            assertNull("Conditional/ambiguous OSM maxspeed must remain unknown: $raw",
                SafetyCameraWarningPolicy.parseSpeedLimitKmh(raw))
        }
        assertEquals(50, SafetyCameraWarningPolicy.parseSpeedLimitKmh("50.0 km/h"))
        assertEquals(50, SafetyCameraWarningPolicy.parseSpeedLimitKmh("50,0 km/h"))
        assertEquals(80, SafetyCameraWarningPolicy.parseSpeedLimitKmh("50 mph"))
        assertEquals(90, SafetyCameraWarningPolicy.parseSpeedLimitKmh("90"))
        assertEquals(70, SafetyCameraWarningPolicy.parseSpeedLimitKmh("70 kph"))
    }

    @Test
    fun sourceConditionalSpeedNeverTriggersOverspeedWarning() {
        val conditional = camera.copy(maxSpeed = "50 @ (Mo-Fr 07:00-19:00)")
        val multiple = camera.copy(maxSpeed = "50;90")
        for (item in listOf(conditional, multiple)) {
            val warning = SafetyCameraWarningPolicy.evaluate(item, 1_200.0, 130f)
            assertNotNull(warning)
            assertNull(warning?.speedLimitKmh)
            assertFalse(warning?.overspeed == true)
        }
    }

    @Test
    fun malformedGpsDistanceAndSpeedAreHandledWithoutFalseWarnings() {
        assertNull(SafetyCameraWarningPolicy.evaluate(camera, Double.NaN, 80f))
        assertNull(SafetyCameraWarningPolicy.evaluate(camera, Double.POSITIVE_INFINITY, 80f))
        assertNull(SafetyCameraWarningPolicy.evaluate(camera, -12.0, 80f))
        val invalidSpeedWarning = SafetyCameraWarningPolicy.evaluate(camera, 450.0, Float.NaN)
        assertNotNull(invalidSpeedWarning)
        assertFalse(invalidSpeedWarning?.overspeed == true)
        assertNull(invalidSpeedWarning?.estimatedSecondsToCamera)
        assertFalse(SafetyCameraWarningPolicy.evaluate(camera, 450.0, Float.POSITIVE_INFINITY)?.overspeed == true)
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
