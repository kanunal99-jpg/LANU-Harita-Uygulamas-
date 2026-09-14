package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TurnManeuver
import com.example.haritalar.voice.NavigationVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationEngineTest {
    private lateinit var voice: FakeNavigationVoice
    private var offRouteCount = 0
    private var arrivalCount = 0
    private var now = 1_000_000L
    private lateinit var engine: NavigationEngine

    private val start = GeoPoint(41.0000, 29.0000)
    private val middle = GeoPoint(41.0000, 29.0010)
    private val destination = GeoPoint(41.0000, 29.0020)

    @Before
    fun setUp() {
        voice = FakeNavigationVoice()
        offRouteCount = 0
        arrivalCount = 0
        now = 1_000_000L
        engine = NavigationEngine(
            voice = voice,
            onOffRouteDetected = { offRouteCount++ },
            onArrivalDetected = { arrivalCount++ },
            nowMs = { now }
        )
    }

    @Test
    fun noActiveRoute_returnsSafeIdleProgress() {
        val progress = engine.processLocationUpdate(location(start))
        assertNull(progress.currentManeuver)
        assertFalse(progress.hasArrived)
        assertEquals(0.0, progress.totalRemainingDistanceMeters, 0.001)
    }

    @Test
    fun startNavigation_announcesStartOnlyOnceUntilStop() {
        engine.startNavigation(route())
        engine.startNavigation(route())
        assertEquals(1, voice.startSequenceCount)

        engine.stop()
        engine.startNavigation(route())
        assertEquals(2, voice.startSequenceCount)
    }

    @Test
    fun maneuverAdvancesWhenVehicleReachesManeuverPoint() {
        val first = TurnManeuver("Sağa dön", 0.0, ManeuverType.RIGHT, middle, "Atatürk Caddesi")
        val second = TurnManeuver("Hedefe doğru ilerle", 0.0, ManeuverType.STRAIGHT, destination, "İnönü Caddesi")
        engine.startNavigation(route(maneuvers = listOf(first, second)))

        val progress = engine.processLocationUpdate(location(middle))
        assertEquals(second, progress.currentManeuver)
    }

    @Test
    fun offRoute_requiresTwoConsecutiveUpdates() {
        engine.startNavigation(route())
        val farAway = GeoPoint(41.0020, 29.0000)

        val first = engine.processLocationUpdate(location(farAway))
        assertFalse(first.isOffRoute)

        val second = engine.processLocationUpdate(location(farAway))
        assertTrue(second.isOffRoute)
        assertEquals(0, offRouteCount)
    }

    @Test
    fun offRoute_afterCooldown_triggersRerouteCallbackAndVoice() {
        engine.startNavigation(route())
        val farAway = GeoPoint(41.0020, 29.0000)

        engine.processLocationUpdate(location(farAway))
        engine.processLocationUpdate(location(farAway))
        assertEquals(0, offRouteCount)
        assertFalse(voice.rerouteAnnounced)

        now += 12_001L
        engine.processLocationUpdate(location(farAway))

        assertEquals(1, offRouteCount)
        assertTrue(voice.rerouteAnnounced)
    }

    @Test
    fun arrival_requiresAccurateConsecutiveLocationUpdates() {
        engine.startNavigation(route())
        val nearDestination = GeoPoint(41.0000, 29.0017)
        val accurateNearDestination = location(nearDestination, accuracy = 5f)

        val first = engine.processLocationUpdate(accurateNearDestination)
        assertFalse(first.hasArrived)
        assertEquals(0, arrivalCount)

        val second = engine.processLocationUpdate(accurateNearDestination)
        assertTrue(second.hasArrived)
        assertEquals(1, arrivalCount)
        assertEquals(1, voice.arrivalCount)

        val third = engine.processLocationUpdate(accurateNearDestination)
        assertTrue(third.hasArrived)
        assertEquals(1, arrivalCount)
        assertEquals(1, voice.arrivalCount)
    }

    @Test
    fun inaccurateGpsAtDestination_doesNotConfirmArrival() {
        engine.startNavigation(route())
        val inaccurateAtDestination = location(destination, accuracy = 80f)
        repeat(3) { engine.processLocationUpdate(inaccurateAtDestination) }

        assertEquals(0, arrivalCount)
        assertFalse(engine.processLocationUpdate(location(start)).hasArrived)
    }

    @Test
    fun snapToRoute_exposesSnappedLocationAndBearing() {
        engine.startNavigation(route())
        val slightlyOffRoute = GeoPoint(41.0001, 29.0010)

        val progress = engine.processLocationUpdate(location(slightlyOffRoute))
        assertTrue(progress.snappedLocation != null)
        assertTrue(progress.snappedBearing != null)
        assertTrue(progress.snappedLocation!!.distanceTo(middle) < 20.0)
    }

    @Test
    fun headingAwareSnap_prefersForwardAlignedRouteSegment() {
        val loopPoint = GeoPoint(41.0005, 29.0010)
        val candidateRoute = RouteOption(
            routeId = "heading-route",
            title = "Heading",
            summary = "Road",
            durationSeconds = 500,
            distanceMeters = 250.0,
            geometry = listOf(start, loopPoint, middle, destination),
            maneuvers = emptyList()
        )
        engine.startNavigation(candidateRoute)

        val progress = engine.processLocationUpdate(
            UserLocationData(
                point = GeoPoint(41.0005, 29.0008),
                accuracyMeters = 5f,
                speedKmh = 40f,
                bearing = 90f,
                isGpsWeak = false
            )
        )

        assertTrue(progress.snappedLocation != null)
        assertTrue(progress.snappedBearing != null)
        assertTrue(progress.snappedBearing!! in 45f..135f)
    }

    @Test
    fun backtrackingIsBoundedAfterForwardProgress() {
        engine.startNavigation(route())
        val forward = location(GeoPoint(41.0000, 29.0015))
        val backward = location(GeoPoint(41.0000, 29.0005))

        val first = engine.processLocationUpdate(forward)
        val second = engine.processLocationUpdate(backward)

        assertTrue(first.totalRemainingDistanceMeters < second.totalRemainingDistanceMeters + 600.0)
        assertTrue(second.totalRemainingDistanceMeters <= first.totalRemainingDistanceMeters + 75.0)
    }

    @Test
    fun remainingDistanceUsesRouteGeometryInsteadOfDirectDestinationDistance() {
        val detourPoint = GeoPoint(41.0008, 29.0010)
        val detourRoute = RouteOption(
            routeId = "detour",
            title = "Detour",
            summary = "Road",
            durationSeconds = 600,
            distanceMeters = 450.0,
            geometry = listOf(start, detourPoint, middle, destination),
            maneuvers = emptyList()
        )
        engine.startNavigation(detourRoute)

        val progress = engine.processLocationUpdate(location(start))
        val directDistance = start.distanceTo(destination)

        assertTrue(progress.totalRemainingDistanceMeters > directDistance + 50.0)
        assertTrue(progress.totalRemainingSeconds > 0L)
    }

    @Test
    fun voiceDistanceGate_doesNotRepeatSameBand() {
        val maneuver = TurnManeuver("sağa dön", 0.0, ManeuverType.RIGHT, start)
        engine.startNavigation(route(maneuvers = listOf(maneuver)))

        engine.processLocationUpdate(location(GeoPoint(41.0000, 29.0050)))
        val firstAnnouncementCount = voice.spoken.size
        engine.processLocationUpdate(location(GeoPoint(41.0000, 29.0050)))

        assertEquals(firstAnnouncementCount, voice.spoken.size)
    }

    private fun route(maneuvers: List<TurnManeuver> = emptyList()) = RouteOption(
        routeId = "test-route",
        title = "Test Hedef",
        summary = "Test Başlangıç",
        durationSeconds = 600,
        distanceMeters = start.distanceTo(destination),
        geometry = listOf(start, middle, destination),
        maneuvers = maneuvers
    )

    private fun location(point: GeoPoint, accuracy: Float = 5f) = UserLocationData(
        point = point,
        accuracyMeters = accuracy,
        speedKmh = 30f,
        bearing = 90f,
        isGpsWeak = false
    )

    private class FakeNavigationVoice : NavigationVoice {
        var startSequenceCount = 0
        var arrivalCount = 0
        var rerouteAnnounced = false
        val spoken = mutableListOf<String>()

        override fun speak(text: String, isPriority: Boolean) { spoken += text }
        override fun playNavigationStartSequence() { startSequenceCount++ }
        override fun announceArrival() { arrivalCount++ }
        override fun announceReroute() { rerouteAnnounced = true }
        override fun stop() = Unit
    }
}
