package com.example.haritalar.navigation

import com.example.haritalar.model.AverageSpeedZone
import com.example.haritalar.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AverageSpeedZoneRoutePolicyTest {
    private val route = listOf(
        GeoPoint(40.9000, 29.2000),
        GeoPoint(40.9050, 29.2050),
        GeoPoint(40.9100, 29.2100),
        GeoPoint(40.9150, 29.2150)
    )

    @Test
    fun matchesRealCorridorAndCalculatesStartEndLengthAndLimit() {
        val zone = AverageSpeedZone(
            id = "way:1",
            geometry = listOf(route[1], route[2]),
            maxSpeed = "90"
        )

        val match = AverageSpeedZoneRoutePolicy.matchToRoute(listOf(zone), route).single()

        assertTrue(match.endRouteMeters > match.startRouteMeters)
        assertTrue(match.routeLengthMeters >= AverageSpeedZoneRoutePolicy.MIN_ZONE_GEOMETRY_METERS)
        assertEquals(90, match.speedLimitKmh)
    }

    @Test
    fun reverseSourceGeometryStillProducesForwardRouteStartAndEnd() {
        val zone = AverageSpeedZone(
            id = "way:2",
            geometry = listOf(route[2], route[1])
        )

        val match = AverageSpeedZoneRoutePolicy.matchToRoute(listOf(zone), route).single()

        assertTrue(match.startRouteMeters < match.endRouteMeters)
        assertNull(match.speedLimitKmh)
    }

    @Test
    fun rejectsCorridorFarAwayFromRoute() {
        val zone = AverageSpeedZone(
            id = "way:3",
            geometry = listOf(
                GeoPoint(41.2, 29.5),
                GeoPoint(41.21, 29.51)
            ),
            maxSpeed = "90"
        )

        assertTrue(AverageSpeedZoneRoutePolicy.matchToRoute(listOf(zone), route).isEmpty())
    }

    @Test
    fun rejectsTinyGeometryEvenWhenItIsOnRoute() {
        val a = route.first()
        val tiny = GeoPoint(a.latitude + 0.00001, a.longitude + 0.00001)
        val zone = AverageSpeedZone(
            id = "way:4",
            geometry = listOf(a, tiny)
        )

        assertTrue(AverageSpeedZoneRoutePolicy.matchToRoute(listOf(zone), route).isEmpty())
    }
}
