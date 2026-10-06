package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyCameraRouteFilterPolicyTest {
    @Test
    fun keepsAheadCameraAndDropsBehindAndOffRouteCameras() {
        val route = listOf(
            GeoPoint(41.0000, 29.0000),
            GeoPoint(41.0100, 29.0000),
            GeoPoint(41.0200, 29.0000),
            GeoPoint(41.0300, 29.0000)
        )
        val user = GeoPoint(41.0170, 29.0000)
        val behind = SafetyCamera(1L, GeoPoint(41.0020, 29.0000))
        val ahead = SafetyCamera(2L, GeoPoint(41.0250, 29.0000))
        val offRoute = SafetyCamera(3L, GeoPoint(41.0250, 29.0100))

        val result = SafetyCameraRouteFilterPolicy.relevantForRoute(
            cameras = listOf(behind, ahead, offRoute),
            route = route,
            userPoint = user
        )

        assertEquals(listOf(2L), result.map { it.id })
    }
}
