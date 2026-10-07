package com.example.haritalar.navigation

import com.example.haritalar.data.traffic.TrafficRouteMatcher
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem

enum class RouteDataCoverage {
    VERIFIED,
    PARTIAL,
    UNAVAILABLE
}

data class RouteCriticalPoiCoverage(
    val status: RouteDataCoverage,
    val pois: List<PoiItem>,
    val source: String,
    val fetchedAtMillis: Long,
    val sampleCount: Int,
    val maxSampleGapMeters: Double,
    val note: String
)

/**
 * Plans bounded, source-backed POI sampling along a route and filters provider
 * results to an actual route corridor before they may appear in LANU Brief.
 */
object RouteCriticalPoiPolicy {
    const val FETCH_RADIUS_METERS = 5_000
    const val ROUTE_CORRIDOR_METERS = 1_500.0
    const val MAX_FULL_SAMPLE_GAP_METERS = 9_000.0
    const val MAX_SAMPLE_POINTS = 12

    val CRITICAL_CATEGORIES = setOf(
        PoiCategory.FUEL,
        PoiCategory.HOSPITAL,
        PoiCategory.PHARMACY,
        PoiCategory.CHARGING_STATION
    )

    data class SamplingPlan(
        val points: List<GeoPoint>,
        val routeLengthMeters: Double,
        val maxSampleGapMeters: Double,
        val fullCoverage: Boolean
    )

    fun plan(route: List<GeoPoint>): SamplingPlan? {
        val generic = RouteSamplingPolicy.plan(
            route = route,
            maxFullSampleGapMeters = MAX_FULL_SAMPLE_GAP_METERS,
            maxSamplePoints = MAX_SAMPLE_POINTS
        ) ?: return null
        return SamplingPlan(
            points = generic.points,
            routeLengthMeters = generic.routeLengthMeters,
            maxSampleGapMeters = generic.maxSampleGapMeters,
            fullCoverage = generic.fullCoverage
        )
    }

    fun filterToCriticalRouteCorridor(
        route: List<GeoPoint>,
        candidates: List<PoiItem>
    ): List<PoiItem> {
        if (route.size < 2 || candidates.isEmpty()) return emptyList()
        return candidates
            .asSequence()
            .filter { it.category in CRITICAL_CATEGORIES }
            .filter {
                TrafficRouteMatcher.isPointNearPolyline(
                    point = it.point,
                    polyline = route,
                    maxMeters = ROUTE_CORRIDOR_METERS
                )
            }
            .distinctBy { it.id }
            .toList()
    }

    fun countsByCategory(pois: List<PoiItem>): Map<PoiCategory, Int> =
        CRITICAL_CATEGORIES.associateWith { category ->
            pois.count { it.category == category }
        }


}
