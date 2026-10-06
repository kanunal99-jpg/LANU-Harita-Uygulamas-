package com.example.haritalar.navigation

import com.example.haritalar.data.traffic.TrafficRouteMatcher
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera

/**
 * Keeps navigation warnings on the active route and biased ahead of the vehicle.
 * It deliberately does not infer camera direction when OSM direction metadata is absent.
 */
object SafetyCameraRouteFilterPolicy {
    const val DEFAULT_ROUTE_CORRIDOR_METERS = 180.0

    fun relevantForRoute(
        cameras: List<SafetyCamera>,
        route: List<GeoPoint>,
        userPoint: GeoPoint,
        corridorMeters: Double = DEFAULT_ROUTE_CORRIDOR_METERS
    ): List<SafetyCamera> {
        if (route.size < 2) return cameras
        val userSegment = nearestSegmentIndex(userPoint, route) ?: return cameras

        return cameras.filter { camera ->
            if (!TrafficRouteMatcher.isPointNearPolyline(camera.point, route, corridorMeters)) {
                return@filter false
            }
            val cameraSegment = nearestSegmentIndex(camera.point, route) ?: return@filter false
            cameraSegment >= (userSegment - 1).coerceAtLeast(0)
        }
    }

    private fun nearestSegmentIndex(point: GeoPoint, route: List<GeoPoint>): Int? {
        if (route.size < 2) return null
        return (0 until route.lastIndex).minByOrNull { index ->
            TrafficRouteMatcher.distanceToSegmentMeters(point, route[index], route[index + 1])
        }
    }
}
