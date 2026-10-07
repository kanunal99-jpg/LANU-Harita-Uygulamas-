package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import kotlin.math.ceil

data class RouteWeatherSample(
    val point: GeoPoint,
    val distanceMeters: Double,
    val etaSecondsFromStart: Long
)

object RouteWeatherForecastPolicy {
    const val DEFAULT_MAX_SAMPLES = 6
    private const val MIN_FORECAST_HOURS = 6
    private const val MAX_FORECAST_HOURS = 168

    fun sampleRoute(
        route: RouteOption,
        maxSamples: Int = DEFAULT_MAX_SAMPLES
    ): List<RouteWeatherSample> {
        val geometry = route.geometry
        if (geometry.isEmpty() || maxSamples <= 0) return emptyList()
        if (geometry.size == 1 || maxSamples == 1) {
            return listOf(RouteWeatherSample(geometry.first(), 0.0, 0L))
        }

        val segmentLengths = DoubleArray(geometry.lastIndex) { index ->
            geometry[index].distanceTo(geometry[index + 1])
        }
        val totalGeometryMeters = segmentLengths.sum()
        if (totalGeometryMeters <= 0.0) {
            return listOf(RouteWeatherSample(geometry.first(), 0.0, 0L))
        }

        val sampleCount = maxSamples.coerceAtLeast(2)
        return (0 until sampleCount).map { index ->
            val fraction = index.toDouble() / (sampleCount - 1).toDouble()
            val targetDistance = totalGeometryMeters * fraction
            val point = interpolateAtDistance(geometry, segmentLengths, targetDistance)
            val etaSeconds = (route.totalDurationSeconds.toDouble() * fraction).toLong()
                .coerceIn(0L, route.totalDurationSeconds.coerceAtLeast(0L))
            RouteWeatherSample(
                point = point,
                distanceMeters = targetDistance,
                etaSecondsFromStart = etaSeconds
            )
        }.distinctBy { sample ->
            Pair(
                (sample.point.latitude * 1_000_000).toLong(),
                (sample.point.longitude * 1_000_000).toLong()
            )
        }
    }

    fun forecastHours(totalDurationSeconds: Long): Int {
        val tripHours = ceil(totalDurationSeconds.coerceAtLeast(0L) / 3600.0).toInt()
        return (tripHours + 3).coerceIn(MIN_FORECAST_HOURS, MAX_FORECAST_HOURS)
    }

    fun nearestForecastIndex(
        timesEpochSeconds: List<Long>,
        targetEpochSeconds: Long
    ): Int? {
        if (timesEpochSeconds.isEmpty()) return null
        return timesEpochSeconds.indices.minByOrNull { index ->
            kotlin.math.abs(timesEpochSeconds[index] - targetEpochSeconds)
        }
    }

    private fun interpolateAtDistance(
        geometry: List<GeoPoint>,
        segmentLengths: DoubleArray,
        targetDistanceMeters: Double
    ): GeoPoint {
        if (targetDistanceMeters <= 0.0) return geometry.first()
        val total = segmentLengths.sum()
        if (targetDistanceMeters >= total) return geometry.last()

        var cumulative = 0.0
        for (index in segmentLengths.indices) {
            val segmentLength = segmentLengths[index]
            val nextCumulative = cumulative + segmentLength
            if (targetDistanceMeters <= nextCumulative) {
                if (segmentLength <= 0.0) return geometry[index]
                val fraction = ((targetDistanceMeters - cumulative) / segmentLength).coerceIn(0.0, 1.0)
                val a = geometry[index]
                val b = geometry[index + 1]
                return GeoPoint(
                    latitude = a.latitude + (b.latitude - a.latitude) * fraction,
                    longitude = a.longitude + (b.longitude - a.longitude) * fraction
                )
            }
            cumulative = nextCumulative
        }
        return geometry.last()
    }
}
