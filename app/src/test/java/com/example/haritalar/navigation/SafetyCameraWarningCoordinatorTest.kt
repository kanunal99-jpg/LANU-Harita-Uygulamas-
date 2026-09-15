package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SafetyCameraWarningCoordinatorTest {
    @Test
    fun sameCameraAndBucketIsAnnouncedOnlyOnce() {
        val coordinator = SafetyCameraWarningCoordinator()
        val camera = SafetyCamera(
            id = 1L,
            point = GeoPoint(41.009, 29.0),
            maxSpeed = "50",
            source = "OpenStreetMap"
        )
        val user = GeoPoint(41.0, 29.0)

        assertNotNull(coordinator.evaluate(listOf(camera), user, speedKmh = 40f))
        assertNull(coordinator.evaluate(listOf(camera), user, speedKmh = 40f))
    }

    @Test
    fun resetAllowsTheSameBucketToBeAnnouncedAgain() {
        val coordinator = SafetyCameraWarningCoordinator()
        val camera = SafetyCamera(
            id = 2L,
            point = GeoPoint(41.009, 29.0),
            maxSpeed = "50",
            source = "OpenStreetMap"
        )
        val user = GeoPoint(41.0, 29.0)

        assertNotNull(coordinator.evaluate(listOf(camera), user, speedKmh = 40f))
        coordinator.reset()
        assertNotNull(coordinator.evaluate(listOf(camera), user, speedKmh = 40f))
    }
}
