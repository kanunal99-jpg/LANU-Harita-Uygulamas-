package com.example.haritalar.data.repository

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProviderFallbackTest {
    private fun route(id: String) = RouteOption(
        routeId = id,
        title = id,
        summary = id,
        durationSeconds = 120,
        distanceMeters = 398.0,
        geometry = listOf(
            GeoPoint(41.0082, 28.9784),
            GeoPoint(41.0092, 28.9804),
            GeoPoint(41.0102, 28.9824)
        ),
        maneuvers = emptyList()
    )

    @Test
    fun validPrimary_preventsAlternativeCall() = kotlinx.coroutines.runBlocking {
        var alternativeCalls = 0
        val result = RouteProviderFallback.resolve(
            primary = { listOf(route("valhalla")) },
            alternative = { alternativeCalls++; listOf(route("osrm")) }
        )

        assertEquals("valhalla", result.single().routeId)
        assertEquals(0, alternativeCalls)
    }

    @Test
    fun emptyPrimary_callsAlternative_once() = kotlinx.coroutines.runBlocking {
        var alternativeCalls = 0
        val result = RouteProviderFallback.resolve(
            primary = { emptyList() },
            alternative = { alternativeCalls++; listOf(route("osrm")) }
        )

        assertEquals("osrm", result.single().routeId)
        assertEquals(1, alternativeCalls)
    }

    @Test
    fun invalidPrimary_callsAlternative() = kotlinx.coroutines.runBlocking {
        var alternativeCalls = 0
        val invalid = route("invalid").copy(distanceMeters = 0.0)
        val result = RouteProviderFallback.resolve(
            primary = { listOf(invalid) },
            alternative = { alternativeCalls++; listOf(route("osrm")) }
        )

        assertEquals("osrm", result.single().routeId)
        assertEquals(1, alternativeCalls)
        assertFalse(result.any { it.routeId == "invalid" })
    }

    @Test
    fun bothProvidersEmpty_returnsNoRoutes() = kotlinx.coroutines.runBlocking {
        var alternativeCalls = 0
        val result = RouteProviderFallback.resolve(
            primary = { error("primary unavailable") },
            alternative = { alternativeCalls++; emptyList() }
        )

        assertTrue(result.isEmpty())
        assertEquals(1, alternativeCalls)
    }
}
