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
    const val MIN_VIEWPORT_ZOOM = 12f
    /**
     * Must stay larger than SafetyCameraWarningPolicy.MAX_WARNING_DISTANCE_METERS so
     * movement/network latency does not create a blind edge at the warning horizon.
     */
    const val NAVIGATION_PREFETCH_RADIUS_METERS = 12_000.0
    const val NAVIGATION_REFRESH_DISTANCE_METERS = 1_500.0

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

    fun shouldLoadViewport(bbox: SafetyCameraBoundingBox, zoomLevel: Float): Boolean =
        bbox.isValid() && zoomLevel >= MIN_VIEWPORT_ZOOM

    fun shouldRefresh(previousCenter: GeoPoint?, currentCenter: GeoPoint): Boolean {
        if (previousCenter == null) return true
        return previousCenter.distanceTo(currentCenter) >= NAVIGATION_REFRESH_DISTANCE_METERS
    }
}
