package com.example.haritalar.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import com.example.haritalar.voice.NavigationVoice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationScenarioInstrumentationTest {
    @Test
    fun cleanStart_thenForwardProgress_producesSnappedNavigationState() {
        val engine = NavigationEngine(
            voice = SilentVoice(),
            onOffRouteDetected = {},
            onArrivalDetected = {}
        )
        val route = RouteOption(
            routeId = "scenario-route",
            title = "Scenario",
            summary = "Test",
            durationSeconds = 300,
            distanceMeters = 167.0,
            geometry = listOf(
                GeoPoint(41.0000, 29.0000),
                GeoPoint(41.0000, 29.0010),
                GeoPoint(41.0000, 29.0020)
            ),
            maneuvers = emptyList()
        )

        engine.startNavigation(route)
        val progress = engine.processLocationUpdate(
            UserLocationData(
                point = GeoPoint(41.0000, 29.0008),
                accuracyMeters = 5f,
                speedKmh = 35f,
                bearing = 90f
            )
        )

        assertTrue(progress.snappedLocation != null)
        assertTrue(progress.totalRemainingDistanceMeters > 0.0)
    }

    @Test
    fun staleAndOffRouteSignals_doNotCreateSyntheticNavigationState() {
        val engine = NavigationEngine(
            voice = SilentVoice(),
            onOffRouteDetected = {},
            onArrivalDetected = {}
        )
        val route = RouteOption(
            routeId = "scenario-route",
            title = "Scenario",
            summary = "Test",
            durationSeconds = 300,
            distanceMeters = 167.0,
            geometry = listOf(
                GeoPoint(41.0000, 29.0000),
                GeoPoint(41.0000, 29.0010),
                GeoPoint(41.0000, 29.0020)
            ),
            maneuvers = emptyList()
        )
        engine.startNavigation(route)

        val first = engine.processLocationUpdate(
            UserLocationData(
                point = GeoPoint(41.0030, 29.0030),
                accuracyMeters = 5f,
                speedKmh = 20f,
                bearing = 90f
            )
        )
        val second = engine.processLocationUpdate(
            UserLocationData(
                point = GeoPoint(41.0030, 29.0030),
                accuracyMeters = 5f,
                speedKmh = 20f,
                bearing = 90f
            )
        )

        assertTrue(!first.hasArrived)
        assertTrue(second.isOffRoute)
    }

    private class SilentVoice : NavigationVoice {
        override fun speak(text: String, isPriority: Boolean) = Unit
        override fun playNavigationStartSequence() = Unit
        override fun announceArrival() = Unit
        override fun announceReroute() = Unit
        override fun stop() = Unit
    }
}
