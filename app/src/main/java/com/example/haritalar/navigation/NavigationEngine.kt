package com.example.haritalar.navigation

import com.example.haritalar.data.traffic.TrafficRouteMatcher
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TurnManeuver
import com.example.haritalar.voice.TurkishTtsManager

data class NavigationProgress(
    val currentManeuver: TurnManeuver?,
    val nextManeuver: TurnManeuver?,
    val distanceToManeuverMeters: Double,
    val totalRemainingDistanceMeters: Double,
    val totalRemainingSeconds: Long,
    val currentRoadName: String,
    val isOffRoute: Boolean = false,
    val hasArrived: Boolean = false
)

class NavigationEngine(
    private val ttsManager: TurkishTtsManager,
    private val onOffRouteDetected: (currentLocation: GeoPoint) -> Unit,
    private val onArrivalDetected: () -> Unit
) {
    private var activeRoute: RouteOption? = null
    private var currentManeuverIndex = 0

    private var offRouteConsecutiveCount = 0
    private var lastRerouteTimestamp = 0L
    private val rerouteCooldownMs = 15_000L // 15 seconds
    private val offRouteThresholdMeters = 60.0
    private val arrivalThresholdMeters = 35.0

    // Voice announcement distance gates to prevent spam
    private var announced500m = false
    private var announced200m = false
    private var announced50m = false

    fun startNavigation(route: RouteOption) {
        activeRoute = route
        currentManeuverIndex = 0
        offRouteConsecutiveCount = 0
        lastRerouteTimestamp = System.currentTimeMillis()
        resetVoiceGates()

        // Announce route start
        val firstInstruction = route.maneuvers.firstOrNull()?.instruction ?: "Rotaya doğru hareket edin"
        ttsManager.speak("Navigasyon başlatıldı. $firstInstruction", isPriority = true)
    }

    fun updateRoute(newRoute: RouteOption) {
        activeRoute = newRoute
        currentManeuverIndex = 0
        offRouteConsecutiveCount = 0
        resetVoiceGates()
    }

    private fun resetVoiceGates() {
        announced500m = false
        announced200m = false
        announced50m = false
    }

    fun processLocationUpdate(location: UserLocationData): NavigationProgress {
        val route = activeRoute ?: return NavigationProgress(
            currentManeuver = null,
            nextManeuver = null,
            distanceToManeuverMeters = 0.0,
            totalRemainingDistanceMeters = 0.0,
            totalRemainingSeconds = 0,
            currentRoadName = ""
        )

        val destination = route.geometry.lastOrNull() ?: route.maneuvers.lastOrNull()?.point ?: location.point
        val distToDest = location.point.distanceTo(destination)

        // 1. Arrival detection
        if (distToDest <= arrivalThresholdMeters) {
            ttsManager.announceArrival()
            onArrivalDetected()
            return NavigationProgress(
                currentManeuver = TurnManeuver("Hedefe ulaştınız", 0.0, ManeuverType.REACH_DESTINATION, destination),
                nextManeuver = null,
                distanceToManeuverMeters = 0.0,
                totalRemainingDistanceMeters = 0.0,
                totalRemainingSeconds = 0,
                currentRoadName = "",
                hasArrived = true
            )
        }

        // 2. Off-route detection
        val isNearRoute = TrafficRouteMatcher.isPointNearPolyline(
            location.point,
            route.geometry,
            offRouteThresholdMeters
        )

        if (!isNearRoute) {
            offRouteConsecutiveCount++
            val now = System.currentTimeMillis()
            if (offRouteConsecutiveCount >= 2 && (now - lastRerouteTimestamp > rerouteCooldownMs)) {
                lastRerouteTimestamp = now
                ttsManager.announceReroute()
                onOffRouteDetected(location.point)
            }
        } else {
            offRouteConsecutiveCount = 0
        }

        // 3. Maneuver advancement
        val maneuvers = route.maneuvers
        if (maneuvers.isNotEmpty() && currentManeuverIndex in maneuvers.indices) {
            val currMan = maneuvers[currentManeuverIndex]
            val distToMan = location.point.distanceTo(currMan.point)

            if (distToMan < 25.0 && currentManeuverIndex < maneuvers.size - 1) {
                currentManeuverIndex++
                resetVoiceGates()
            }
        }

        val activeManeuver = maneuvers.getOrNull(currentManeuverIndex)
        val upcomingManeuver = maneuvers.getOrNull(currentManeuverIndex + 1)
        val distToNextManeuver = activeManeuver?.point?.let { location.point.distanceTo(it) } ?: distToDest

        // 4. Voice guidance triggers
        handleVoiceTriggers(distToNextManeuver, activeManeuver?.instruction ?: "Yola devam edin")

        // 5. Remaining distance & duration
        val remainingDist = Math.max(0.0, distToDest)
        val avgSpeedMs = if (location.speedKmh > 10) (location.speedKmh / 3.6) else 11.1 // fallback ~40 km/h
        val remainingSec = Math.round(remainingDist / avgSpeedMs)

        return NavigationProgress(
            currentManeuver = activeManeuver,
            nextManeuver = upcomingManeuver,
            distanceToManeuverMeters = distToNextManeuver,
            totalRemainingDistanceMeters = remainingDist,
            totalRemainingSeconds = remainingSec,
            currentRoadName = activeManeuver?.roadName ?: "",
            isOffRoute = offRouteConsecutiveCount >= 2
        )
    }

    private fun handleVoiceTriggers(distanceMeters: Double, instruction: String) {
        when {
            distanceMeters in 400.0..600.0 && !announced500m -> {
                announced500m = true
                ttsManager.speak("500 metre sonra $instruction")
            }
            distanceMeters in 150.0..250.0 && !announced200m -> {
                announced200m = true
                ttsManager.speak("200 metre sonra $instruction")
            }
            distanceMeters in 20.0..60.0 && !announced50m -> {
                announced50m = true
                ttsManager.speak("Şimdi $instruction")
            }
        }
    }

    fun stop() {
        ttsManager.stop()
        activeRoute = null
        currentManeuverIndex = 0
        offRouteConsecutiveCount = 0
    }
}
