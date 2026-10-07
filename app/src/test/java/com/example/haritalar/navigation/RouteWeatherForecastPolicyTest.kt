package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.RouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWeatherForecastPolicyTest {
    private fun route(
        geometry: List<GeoPoint>,
        durationSeconds: Long = 3600L
    ) = RouteOption(
        routeId = "weather-route",
        title = "Test",
        summary = "Test",
        durationSeconds = durationSeconds,
        distanceMeters = geometry.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) },
        geometry = geometry,
        maneuvers = emptyList(),
        routeType = RouteType.FASTEST
    )

    @Test
    fun samplesStartAndEndWithMonotonicDistanceAndEta() {
        val r = route(
            listOf(
                GeoPoint(40.0, 29.0),
                GeoPoint(40.0, 29.1),
                GeoPoint(40.1, 29.1)
            ),
            durationSeconds = 7200L
        )

        val samples = RouteWeatherForecastPolicy.sampleRoute(r, maxSamples = 6)

        assertEquals(r.geometry.first(), samples.first().point)
        assertEquals(r.geometry.last(), samples.last().point)
        assertEquals(0L, samples.first().etaSecondsFromStart)
        assertEquals(7200L, samples.last().etaSecondsFromStart)
        assertTrue(samples.zipWithNext().all { (a, b) -> b.distanceMeters >= a.distanceMeters })
        assertTrue(samples.zipWithNext().all { (a, b) -> b.etaSecondsFromStart >= a.etaSecondsFromStart })
    }

    @Test
    fun samplingUsesPhysicalDistanceNotGeometryIndex() {
        val r = route(
            listOf(
                GeoPoint(40.0, 29.0),
                GeoPoint(40.0, 29.001),
                GeoPoint(40.0, 29.100)
            )
        )

        val samples = RouteWeatherForecastPolicy.sampleRoute(r, maxSamples = 3)
        val midpoint = samples[1].point

        assertTrue(midpoint.longitude > 29.03)
        assertTrue(midpoint.longitude < 29.07)
        assertEquals(1800L, samples[1].etaSecondsFromStart)
    }

    @Test
    fun nearestForecastIndexChoosesClosestEpoch() {
        val times = listOf(1_000L, 4_600L, 8_200L)
        assertEquals(1, RouteWeatherForecastPolicy.nearestForecastIndex(times, 5_000L))
        assertEquals(null, RouteWeatherForecastPolicy.nearestForecastIndex(emptyList(), 5_000L))
    }

    @Test
    fun forecastHorizonIncludesTripAndSafetyMargin() {
        assertEquals(6, RouteWeatherForecastPolicy.forecastHours(0L))
        assertEquals(6, RouteWeatherForecastPolicy.forecastHours(2 * 3600L))
        assertEquals(13, RouteWeatherForecastPolicy.forecastHours(10 * 3600L))
        assertEquals(168, RouteWeatherForecastPolicy.forecastHours(300 * 3600L))
    }
}
