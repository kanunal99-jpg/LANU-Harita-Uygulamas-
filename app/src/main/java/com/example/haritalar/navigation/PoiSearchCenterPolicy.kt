package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.TrafficSignalBoundingBox

/**
 * Chooses a useful POI search center without making browsing depend on a fresh GPS fix.
 * Free-map browsing follows the visible viewport; follow modes prefer a live GPS point.
 */
object PoiSearchCenterPolicy {
    fun radiusMeters(
        viewport: TrafficSignalBoundingBox?,
        defaultMeters: Int = 8_000
    ): Int {
        val box = viewport?.takeIf { it.isValid() } ?: return defaultMeters
        val center = GeoPoint((box.south + box.north) / 2.0, (box.west + box.east) / 2.0)
        val corner = GeoPoint(box.north, box.east)
        return center.distanceTo(corner).toInt().coerceIn(2_500, 20_000)
    }

    fun resolve(
        trackingMode: MapTrackingMode,
        liveLocation: GeoPoint?,
        viewport: TrafficSignalBoundingBox?
    ): GeoPoint? {
        val viewportCenter = viewport
            ?.takeIf { it.isValid() }
            ?.let { GeoPoint((it.south + it.north) / 2.0, (it.west + it.east) / 2.0) }

        return when (trackingMode) {
            MapTrackingMode.FREE -> viewportCenter ?: liveLocation
            MapTrackingMode.FOLLOW_USER, MapTrackingMode.FOLLOW_BEARING -> liveLocation ?: viewportCenter
        }
    }
}
