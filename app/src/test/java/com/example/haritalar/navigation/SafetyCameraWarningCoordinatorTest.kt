package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SafetyCameraWarningCoordinatorTest {
    private val camera = SafetyCamera(
        id = 7L,
        point = GeoPoint(41.0, 29.0),
        maxSpeed = "50 km/h"
    )

    @Test
    fun sameDistanceBucketIsEmittedOnlyOnce() {
        val coordinator = SafetyCameraWarningCoordinator()
        val point = camera.point

        val first = coordinator.evaluate(listOf(camera), point, 60f)
        val second = coordinator.evaluate(listOf(camera), point, 60f)

        assertNotNull(first)
        assertNull(second)
        assertEquals(60, first?.speedLimitKmh)
        assertEquals(true, first?.overspeed)
        assertEquals(0, first?.distanceBucketMeters)
    }

    @Test
    fun resetAllowsTheSameCameraToWarnAgain() {
        val coordinator = SafetyCameraWarningCoordinator()
        val point = camera.point

        assertNotNull(coordinator.evaluate(listOf(camera), point, 40f))
        coordinator.reset()
        assertNotNull(coordinator.evaluate(listOf(camera), point, 40f))
    }

    @Test
    fun cameraWithoutSpeedLimitNeverCreatesOverspeed() {
        val coordinator = SafetyCameraWarningCoordinator()
        val point = camera.point
        val cameraWithoutLimit = camera.copy(maxSpeed = null)

        val warning = coordinator.evaluate(listOf(cameraWithoutLimit), point, 200f)

        assertNotNull(warning)
        assertEquals(null, warning?.speedLimitKmh)
        assertEquals(false, warning?.overspeed)
    }
}
