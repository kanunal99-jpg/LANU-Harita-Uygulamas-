package com.example.haritalar.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLocationPolicyTest {
    private val now = 1_000_000L

    private fun location(
        timestamp: Long = now,
        accuracyMeters: Float = 10f,
        simulated: Boolean = false
    ) = UserLocationData(
        point = com.example.haritalar.model.GeoPoint(41.01, 28.97),
        accuracyMeters = accuracyMeters,
        timestamp = timestamp,
        isSimulated = simulated
    )

    @Test
    fun rejectsMissingLocation() {
        assertFalse(NavigationLocationPolicy.isUsableForRouting(null, now))
    }

    @Test
    fun rejectsStaleLocation() {
        assertFalse(
            NavigationLocationPolicy.isUsableForRouting(
                location(timestamp = now - NavigationLocationPolicy.MAX_LOCATION_AGE_MILLIS - 1L),
                now
            )
        )
    }

    @Test
    fun rejectsInaccurateLocation() {
        assertFalse(
            NavigationLocationPolicy.isUsableForRouting(
                location(accuracyMeters = NavigationLocationPolicy.MAX_ACCURACY_METERS + 0.1f),
                now
            )
        )
    }


    @Test
    fun readinessExplainsWhyRoutingIsBlocked() {
        assertEquals(
            NavigationLocationPolicy.Readiness.MISSING,
            NavigationLocationPolicy.readiness(null, now)
        )
        assertEquals(
            NavigationLocationPolicy.Readiness.STALE,
            NavigationLocationPolicy.readiness(
                location(timestamp = now - NavigationLocationPolicy.MAX_LOCATION_AGE_MILLIS - 1L),
                now
            )
        )
        assertEquals(
            NavigationLocationPolicy.Readiness.INACCURATE,
            NavigationLocationPolicy.readiness(
                location(accuracyMeters = NavigationLocationPolicy.MAX_ACCURACY_METERS + 1f),
                now
            )
        )
        assertEquals(
            NavigationLocationPolicy.Readiness.READY,
            NavigationLocationPolicy.readiness(location(), now)
        )
    }

    @Test
    fun acceptsFreshAccurateLocation() {
        assertTrue(NavigationLocationPolicy.isUsableForRouting(location(), now))
    }

    @Test
    fun acceptsExplicitSimulationRegardlessOfAge() {
        assertTrue(
            NavigationLocationPolicy.isUsableForRouting(
                location(
                    timestamp = now - NavigationLocationPolicy.MAX_LOCATION_AGE_MILLIS * 10,
                    accuracyMeters = 1_000f,
                    simulated = true
                ),
                now
            )
        )
    }
}
