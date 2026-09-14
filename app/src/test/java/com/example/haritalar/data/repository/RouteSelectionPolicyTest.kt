package com.example.haritalar.data.repository

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSelectionPolicyTest {
    private val start = GeoPoint(41.0000, 29.0000)
    private val middle = GeoPoint(41.0000, 29.0010)
    private val end = GeoPoint(41.0000, 29.0020)

    private fun route(id: String, distance: Double = 167.0, duration: Long = 120, geometry: List<GeoPoint> = listOf(start, middle, end)) =
        RouteOption(id, "Test", "Test", duration, distance, geometry, emptyList())

    @Test fun validPrimaryRoutesWin() {
        val primary = route("primary")
        val alternative = route("alternative")
        assertEquals(listOf("primary"), RouteSelectionPolicy.select(listOf(primary), listOf(alternative)).map { it.routeId })
    }

    @Test fun invalidPrimaryFallsBackToAlternative() {
        val invalid = route("invalid", distance = 0.0)
        val alternative = route("alternative")
        assertEquals(listOf("alternative"), RouteSelectionPolicy.select(listOf(invalid), listOf(alternative)).map { it.routeId })
    }

    @Test fun unusableGeometryIsRejected() {
        val invalid = route("invalid", geometry = listOf(start))
        assertFalse(RouteSelectionPolicy.isUsable(invalid))
        assertTrue(RouteSelectionPolicy.select(listOf(invalid), emptyList()).isEmpty())
    }

    @Test fun impossibleEtaIsRejected() {
        val tooFast = route("too-fast", duration = 1)
        assertFalse(RouteSelectionPolicy.isUsable(tooFast))
    }

    @Test fun verySlowButPlausibleUrbanRouteIsAccepted() {
        val slow = route("slow", duration = 1_000)
        assertTrue(RouteSelectionPolicy.isUsable(slow))
    }
}
