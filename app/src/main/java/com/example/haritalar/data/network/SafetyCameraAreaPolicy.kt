package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCameraBoundingBox
import kotlin.math.cos
import kotlin.math.max

/**
 * Pure geometry policy for safety-camera prefetching.
 * Keeps navigation warnings independent from the currently visible map viewport.
 */
object SafetyCameraAreaPolicy {
    const val NAVIGATION_PREFETCH_RADIUS_METERS = 8_000.0
    const val NAVIGATION_REFRESH_DISTANCE_METERS = 2_000.0

    const val ROUTE_PREFETCH_RADIUS_METERS = 12_000.0
    const val ROUTE_PREFETCH_SPACING_METERS = 18_000.0

    fun boundingBoxAround(
        center: GeoPoint,
        radiusMeters: Double = NAVIGATION_PREFETCH_RADIUS_METERS
    ): SafetyCameraBoundingBox {
        val safeRadius = radiusMeters.coerceIn(500.0, 25_000.0)
        val latDelta = safeRadius / 111_320.0
        val cosLat = max(0.15, cos(Math.toRadians(center.latitude)))
        val lonDelta = safeRadius / (111_320.0 * cosLat)
        return SafetyCameraBoundingBox(
            south = (center.latitude - latDelta).coerceAtLeast(-90.0),
            west = (center.longitude - lonDelta).coerceAtLeast(-180.0),
            north = (center.latitude + latDelta).coerceAtMost(90.0),
            east = (center.longitude + lonDelta).coerceAtMost(180.0)
        )
    }

    fun shouldRefresh(previousCenter: GeoPoint?, currentCenter: GeoPoint): Boolean {
        if (previousCenter == null) return true
        return previousCenter.distanceTo(currentCenter) >= NAVIGATION_REFRESH_DISTANCE_METERS
    }

    /**
     * Produces overlapping query centers along the complete route so the pre-drive
     * briefing can see cameras at route km 20, 30, 100... before navigation starts.
     */
    fun routePrefetchCenters(
        route: List<GeoPoint>,
        spacingMeters: Double = ROUTE_PREFETCH_SPACING_METERS
    ): List<GeoPoint> {
        if (route.isEmpty()) return emptyList()
        if (route.size == 1) return route

        val safeSpacing = spacingMeters.coerceIn(5_000.0, 30_000.0)
        val centers = mutableListOf(route.first())
        var accumulatedSinceLast = 0.0

        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            val segmentLength = a.distanceTo(b)
            if (segmentLength <= 0.0) continue

            var remainingOnSegment = segmentLength
            var segmentStart = a

            while (accumulatedSinceLast + remainingOnSegment >= safeSpacing) {
                val needed = safeSpacing - accumulatedSinceLast
                val fraction = (needed / remainingOnSegment).coerceIn(0.0, 1.0)
                val point = GeoPoint(
                    latitude = segmentStart.latitude + (b.latitude - segmentStart.latitude) * fraction,
                    longitude = segmentStart.longitude + (b.longitude - segmentStart.longitude) * fraction
                )
                centers += point
                remainingOnSegment = point.distanceTo(b)
                segmentStart = point
                accumulatedSinceLast = 0.0
                if (remainingOnSegment <= 1.0) break
            }

            accumulatedSinceLast += remainingOnSegment
        }

        if (centers.last().distanceTo(route.last()) > safeSpacing * 0.35) {
            centers += route.last()
        }

        return centers.distinctBy {
            Pair(
                (it.latitude * 1_000_000).toLong(),
                (it.longitude * 1_000_000).toLong()
            )
        }
    }
}
