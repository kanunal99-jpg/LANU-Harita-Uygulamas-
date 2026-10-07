package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.TrafficSignalBoundingBox

object PoiSearchCenterPolicy {
    const val MAX_LAST_KNOWN_AGE_MS = 15 * 60 * 1000L
    const val MAX_LAST_KNOWN_ACCURACY_METERS = 2_000f
    const val MIN_REFRESH_DISTANCE_METERS = 2_500.0

    fun viewportCenter(bbox: TrafficSignalBoundingBox?): GeoPoint? {
        if (bbox == null || !bbox.isValid()) return null
        return GeoPoint(
            latitude = (bbox.south + bbox.north) / 2.0,
            longitude = (bbox.west + bbox.east) / 2.0
        )
    }

    fun resolve(
        trackingMode: MapTrackingMode,
        freshRoutingLocation: UserLocationData?,
        lastKnownLocation: UserLocationData?,
        viewport: TrafficSignalBoundingBox?,
        nowMillis: Long = System.currentTimeMillis()
    ): GeoPoint? {
        val viewportPoint = viewportCenter(viewport)
        val lastKnownPoint = lastKnownLocation
            ?.takeIf { !it.isSimulated }
            ?.takeIf { nowMillis - it.timestamp in 0L..MAX_LAST_KNOWN_AGE_MS }
            ?.takeIf {
                it.accuracyMeters.isFinite() &&
                    it.accuracyMeters in 0f..MAX_LAST_KNOWN_ACCURACY_METERS
            }
            ?.point

        return when (trackingMode) {
            MapTrackingMode.FREE -> viewportPoint ?: freshRoutingLocation?.point ?: lastKnownPoint
            MapTrackingMode.FOLLOW_USER,
            MapTrackingMode.FOLLOW_BEARING -> freshRoutingLocation?.point ?: lastKnownPoint ?: viewportPoint
        }
    }

    fun shouldRefresh(previousCenter: GeoPoint?, nextCenter: GeoPoint): Boolean {
        if (previousCenter == null) return true
        return previousCenter.distanceTo(nextCenter) >= MIN_REFRESH_DISTANCE_METERS
    }
}
