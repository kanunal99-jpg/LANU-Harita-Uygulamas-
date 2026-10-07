package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.TrafficSignalBoundingBox

/**
 * Keeps POI coverage proportional to what the user can actually see while
 * bounding public Overpass query cost.
 */
object PoiSearchAreaPolicy {
    const val MIN_RADIUS_METERS = 4_000
    const val FOLLOW_RADIUS_METERS = 8_000
    const val MAX_RADIUS_METERS = 20_000

    fun radiusMeters(
        trackingMode: MapTrackingMode,
        center: GeoPoint,
        viewport: TrafficSignalBoundingBox?
    ): Int {
        if (trackingMode != MapTrackingMode.FREE || viewport?.isValid() != true) {
            return FOLLOW_RADIUS_METERS
        }

        val northEast = GeoPoint(viewport.north, viewport.east)
        val southWest = GeoPoint(viewport.south, viewport.west)
        val farthest = maxOf(center.distanceTo(northEast), center.distanceTo(southWest))
        return (farthest * 1.10)
            .toInt()
            .coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
    }
}
