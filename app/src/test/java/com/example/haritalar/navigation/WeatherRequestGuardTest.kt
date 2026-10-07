package com.example.haritalar.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRequestGuardTest {
    @Test
    fun acceptsOnlyCurrentGenerationAndSelectedRoute() {
        assertTrue(
            WeatherRequestGuard.shouldApply(
                requestGeneration = 4L,
                currentGeneration = 4L,
                requestedRouteId = "r2",
                currentRouteId = "r2"
            )
        )
        assertFalse(
            WeatherRequestGuard.shouldApply(
                requestGeneration = 3L,
                currentGeneration = 4L,
                requestedRouteId = "r2",
                currentRouteId = "r2"
            )
        )
        assertFalse(
            WeatherRequestGuard.shouldApply(
                requestGeneration = 4L,
                currentGeneration = 4L,
                requestedRouteId = "r1",
                currentRouteId = "r2"
            )
        )
        assertFalse(
            WeatherRequestGuard.shouldApply(
                requestGeneration = 4L,
                currentGeneration = 4L,
                requestedRouteId = "r2",
                currentRouteId = null
            )
        )
    }
}
