package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.TrafficSignalBoundingBox
import kotlin.math.roundToInt

/**
 * Premium map-density policy.
 *
 * It keeps browsing useful at city scale without flooding the map with hundreds
 * of individual markers. No fake cluster/POI is created; the policy only chooses
 * which real source-backed POIs are rendered at a given zoom.
 */
object PoiViewportPolicy {
    const val MIN_ZOOM_FOR_CATEGORY_POIS = 10.5f
    const val MIN_ZOOM_FOR_ALL_POIS = 13.0f

    fun minimumZoom(category: PoiCategory?): Float =
        if (category == null) MIN_ZOOM_FOR_ALL_POIS else MIN_ZOOM_FOR_CATEGORY_POIS

    fun canQuery(zoomLevel: Float, category: PoiCategory?): Boolean =
        zoomLevel >= minimumZoom(category)

    fun searchRadiusMeters(
        bbox: TrafficSignalBoundingBox?,
        zoomLevel: Float
    ): Int {
        if (bbox == null || !bbox.isValid()) {
            return defaultRadiusForZoom(zoomLevel)
        }

        val center = bbox.centerPoint()
        val corner = GeoPoint(bbox.north, bbox.east)
        val halfDiagonalMeters = center.distanceTo(corner)
        return halfDiagonalMeters
            .coerceIn(4_000.0, 20_000.0)
            .roundToInt()
    }

    fun refreshDistanceMeters(zoomLevel: Float): Double = when {
        zoomLevel >= 16f -> 800.0
        zoomLevel >= 15f -> 1_200.0
        zoomLevel >= 14f -> 1_800.0
        zoomLevel >= 13f -> 2_600.0
        zoomLevel >= 12f -> 3_800.0
        else -> 5_500.0
    }

    fun visiblePois(
        pois: List<PoiItem>,
        bbox: TrafficSignalBoundingBox?,
        zoomLevel: Float
    ): List<PoiItem> {
        if (pois.isEmpty()) return emptyList()

        val inViewport = if (bbox != null && bbox.isValid()) {
            pois.filter { bbox.contains(it.point) }
        } else {
            pois
        }
        if (inViewport.isEmpty()) return emptyList()

        val center = bbox?.takeIf { it.isValid() }?.centerPoint()
            ?: inViewport.first().point
        val sorted = inViewport.sortedBy { it.point.distanceTo(center) }
        val separationMeters = minimumSeparationMeters(zoomLevel)
        val maxVisible = maxVisiblePois(zoomLevel)

        if (separationMeters <= 0.0) return sorted.take(maxVisible)

        val accepted = ArrayList<PoiItem>(maxVisible)
        for (poi in sorted) {
            if (accepted.none { it.point.distanceTo(poi.point) < separationMeters }) {
                accepted += poi
                if (accepted.size >= maxVisible) break
            }
        }
        return accepted
    }

    fun maxVisiblePois(zoomLevel: Float): Int = when {
        zoomLevel >= 17f -> 120
        zoomLevel >= 16f -> 90
        zoomLevel >= 15f -> 70
        zoomLevel >= 14f -> 50
        zoomLevel >= 13f -> 36
        zoomLevel >= 12f -> 26
        else -> 18
    }

    fun minimumSeparationMeters(zoomLevel: Float): Double = when {
        zoomLevel >= 17f -> 0.0
        zoomLevel >= 16f -> 120.0
        zoomLevel >= 15f -> 250.0
        zoomLevel >= 14f -> 500.0
        zoomLevel >= 13f -> 900.0
        zoomLevel >= 12f -> 1_500.0
        else -> 2_600.0
    }

    private fun defaultRadiusForZoom(zoomLevel: Float): Int = when {
        zoomLevel >= 16f -> 4_000
        zoomLevel >= 15f -> 5_000
        zoomLevel >= 14f -> 7_000
        zoomLevel >= 13f -> 10_000
        zoomLevel >= 12f -> 14_000
        else -> 20_000
    }

    private fun TrafficSignalBoundingBox.centerPoint(): GeoPoint =
        GeoPoint(
            latitude = (south + north) / 2.0,
            longitude = (west + east) / 2.0
        )
}
