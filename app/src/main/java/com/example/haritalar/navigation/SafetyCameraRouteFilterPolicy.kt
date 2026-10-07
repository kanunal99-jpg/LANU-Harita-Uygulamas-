package com.example.haritalar.navigation

import com.example.haritalar.data.traffic.TrafficRouteMatcher
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import kotlin.math.cos

/**
 * Keeps navigation warnings on the active route and ahead of the vehicle.
 * Camera direction is never inferred when OSM direction metadata is absent.
 */
object SafetyCameraRouteFilterPolicy {
    data class CameraAhead(
        val camera: SafetyCamera,
        val distanceAheadMeters: Double
    )

    const val DEFAULT_ROUTE_CORRIDOR_METERS = 180.0
    private const val BACKTRACK_TOLERANCE_METERS = 120.0

    fun relevantForRoute(
        cameras: List<SafetyCamera>,
        route: List<GeoPoint>,
        userPoint: GeoPoint,
        corridorMeters: Double = DEFAULT_ROUTE_CORRIDOR_METERS
    ): List<SafetyCamera> {
        if (route.size < 2) return cameras
        val userProgress = routeProgressMeters(userPoint, route) ?: return cameras

        return cameras.filter { camera ->
            if (!TrafficRouteMatcher.isPointNearPolyline(camera.point, route, corridorMeters)) {
                return@filter false
            }
            val cameraProgress = routeProgressMeters(camera.point, route) ?: return@filter false
            cameraProgress + BACKTRACK_TOLERANCE_METERS >= userProgress
        }
    }

    fun camerasAhead(
        cameras: List<SafetyCamera>,
        route: List<GeoPoint>,
        userPoint: GeoPoint,
        corridorMeters: Double = DEFAULT_ROUTE_CORRIDOR_METERS
    ): List<CameraAhead> {
        if (route.size < 2) return emptyList()
        val userProgress = routeProgressMeters(userPoint, route) ?: return emptyList()

        return cameras.mapNotNull { camera ->
            if (!TrafficRouteMatcher.isPointNearPolyline(camera.point, route, corridorMeters)) {
                return@mapNotNull null
            }
            val cameraProgress = routeProgressMeters(camera.point, route) ?: return@mapNotNull null
            val distanceAhead = cameraProgress - userProgress
            if (distanceAhead < -BACKTRACK_TOLERANCE_METERS) return@mapNotNull null
            CameraAhead(
                camera = camera,
                distanceAheadMeters = distanceAhead.coerceAtLeast(0.0)
            )
        }.sortedBy { it.distanceAheadMeters }
    }

    fun distanceAheadMeters(
        camera: SafetyCamera,
        route: List<GeoPoint>,
        userPoint: GeoPoint,
        corridorMeters: Double = DEFAULT_ROUTE_CORRIDOR_METERS
    ): Double? {
        return camerasAhead(
            cameras = listOf(camera),
            route = route,
            userPoint = userPoint,
            corridorMeters = corridorMeters
        ).firstOrNull()?.distanceAheadMeters
    }

    /**
     * Projects a point to the nearest route segment and returns cumulative distance
     * from route start. This avoids treating an entire previous segment as "ahead".
     */
    private fun routeProgressMeters(point: GeoPoint, route: List<GeoPoint>): Double? {
        if (route.size < 2) return null

        var cumulative = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        var bestProgress: Double? = null

        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            val segmentLength = a.distanceTo(b)
            if (segmentLength <= 0.0) continue

            val projection = projectOnSegment(point, a, b)
            if (projection.distanceMeters < bestDistance) {
                bestDistance = projection.distanceMeters
                bestProgress = cumulative + (segmentLength * projection.t)
            }
            cumulative += segmentLength
        }

        return bestProgress
    }

    private data class SegmentProjection(
        val t: Double,
        val distanceMeters: Double
    )

    private fun projectOnSegment(point: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val latRef = Math.toRadians((a.latitude + b.latitude + point.latitude) / 3.0)
        val metersPerLat = 111_132.92 - 559.82 * cos(2 * latRef)
        val metersPerLon = 111_412.84 * cos(latRef)

        val px = (point.longitude - a.longitude) * metersPerLon
        val py = (point.latitude - a.latitude) * metersPerLat
        val bx = (b.longitude - a.longitude) * metersPerLon
        val by = (b.latitude - a.latitude) * metersPerLat

        val denom = bx * bx + by * by
        if (denom <= 0.0) return SegmentProjection(0.0, point.distanceTo(a))

        val t = ((px * bx + py * by) / denom).coerceIn(0.0, 1.0)
        val dx = px - (t * bx)
        val dy = py - (t * by)
        return SegmentProjection(t = t, distanceMeters = kotlin.math.sqrt(dx * dx + dy * dy))
    }
}
