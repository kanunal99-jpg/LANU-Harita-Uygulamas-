package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import kotlin.math.ceil

/** Pure distance-based route sampler shared by route-scoped data providers. */
object RouteSamplingPolicy {
    data class Plan(
        val points: List<GeoPoint>,
        val routeLengthMeters: Double,
        val maxSampleGapMeters: Double,
        val fullCoverage: Boolean
    )

    fun plan(
        route: List<GeoPoint>,
        maxFullSampleGapMeters: Double,
        maxSamplePoints: Int
    ): Plan? {
        if (route.size < 2 || maxFullSampleGapMeters <= 0.0 || maxSamplePoints < 2) return null
        val total = routeLength(route)
        if (total <= 1.0) return null

        val desiredIntervals = ceil(total / maxFullSampleGapMeters).toInt().coerceAtLeast(1)
        val sampleCount = (desiredIntervals + 1).coerceIn(2, maxSamplePoints)
        val gap = total / (sampleCount - 1)
        val points = (0 until sampleCount).map { index ->
            pointAtDistance(
                route = route,
                targetMeters = if (index == sampleCount - 1) total else gap * index
            )
        }

        return Plan(
            points = points,
            routeLengthMeters = total,
            maxSampleGapMeters = gap,
            fullCoverage = gap <= maxFullSampleGapMeters + 1.0
        )
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
