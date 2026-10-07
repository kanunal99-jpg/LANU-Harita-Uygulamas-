package com.example.haritalar.navigation

import com.example.haritalar.data.traffic.TrafficRouteMatcher
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import kotlin.math.ceil

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
        if (route.size < 2) return null
        val total = routeLength(route)
        if (total <= 1.0) return null

        val desiredIntervals = ceil(total / MAX_FULL_SAMPLE_GAP_METERS).toInt().coerceAtLeast(1)
        val sampleCount = (desiredIntervals + 1).coerceIn(2, MAX_SAMPLE_POINTS)
        val gap = total / (sampleCount - 1)
        val points = (0 until sampleCount).map { index ->
            pointAtDistance(
                route = route,
                targetMeters = if (index == sampleCount - 1) total else gap * index
            )
        }

        return SamplingPlan(
            points = points,
            routeLengthMeters = total,
            maxSampleGapMeters = gap,
            fullCoverage = gap <= MAX_FULL_SAMPLE_GAP_METERS + 1.0
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

    private fun routeLength(route: List<GeoPoint>): Double {
        var total = 0.0
        for (index in 0 until route.lastIndex) {
            total += route[index].distanceTo(route[index + 1])
        }
        return total
    }

    private fun pointAtDistance(route: List<GeoPoint>, targetMeters: Double): GeoPoint {
        if (targetMeters <= 0.0) return route.first()
        var traversed = 0.0

        for (index in 0 until route.lastIndex) {
            val start = route[index]
            val end = route[index + 1]
            val segment = start.distanceTo(end)
            if (segment <= 0.0) continue

            if (traversed + segment >= targetMeters) {
                val fraction = ((targetMeters - traversed) / segment).coerceIn(0.0, 1.0)
                return GeoPoint(
                    latitude = start.latitude + ((end.latitude - start.latitude) * fraction),
                    longitude = start.longitude + ((end.longitude - start.longitude) * fraction)
                )
            }
            traversed += segment
        }
        return route.last()
    }
}
