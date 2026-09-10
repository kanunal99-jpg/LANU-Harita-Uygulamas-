package com.example.haritalar.navigation

import com.example.haritalar.data.traffic.TrafficRouteMatcher
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.LaneInfo
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TripSummary
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
    val hasArrived: Boolean = false,
    val lanes: List<LaneInfo> = emptyList(),
    val speedLimitKmh: Int? = null,
    val snappedLocation: GeoPoint? = null,
    val snappedBearing: Float? = null,
    val tripSummary: TripSummary? = null
)

class NavigationEngine(
    private val ttsManager: TurkishTtsManager,
    private val onOffRouteDetected: (currentLocation: GeoPoint) -> Unit,
    private val onArrivalDetected: (tripSummary: TripSummary) -> Unit
) {
    private var activeRoute: RouteOption? = null
    private var currentManeuverIndex = 0

    private var offRouteConsecutiveCount = 0
    private var lastRerouteTimestamp = 0L
    private val rerouteCooldownMs = 12_000L // 12 seconds
    private val offRouteThresholdMeters = 55.0
    private val arrivalThresholdMeters = 35.0
    private val snapThresholdMeters = 35.0

    // Voice announcement distance gates to prevent spam
    private var announced500m = false
    private var announced200m = false
    private var announced50m = false
    private var announcedLane = false

    // Trip statistics tracking
    private var tripStartTime: Long = 0L
    private var accumulatedDistanceMeters: Double = 0.0
    private var lastProcessedPoint: GeoPoint? = null
    private var maxSpeedKmh: Float = 0f
    private var sumSpeedKmh: Double = 0.0
    private var speedSampleCount: Int = 0

    fun startNavigation(route: RouteOption) {
        activeRoute = route
        currentManeuverIndex = 0
        offRouteConsecutiveCount = 0
        lastRerouteTimestamp = System.currentTimeMillis()
        tripStartTime = System.currentTimeMillis()
        accumulatedDistanceMeters = 0.0
        lastProcessedPoint = null
        maxSpeedKmh = 0f
        sumSpeedKmh = 0.0
        speedSampleCount = 0
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
        announcedLane = false
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

        // Trip statistics accumulation
        if (lastProcessedPoint != null) {
            val delta = lastProcessedPoint!!.distanceTo(location.point)
            if (delta in 0.5..150.0) {
                accumulatedDistanceMeters += delta
            }
        }
        lastProcessedPoint = location.point

        if (location.speedKmh > 0) {
            if (location.speedKmh > maxSpeedKmh) maxSpeedKmh = location.speedKmh
            sumSpeedKmh += location.speedKmh
            speedSampleCount++
        }

        // 1. Arrival detection
        if (distToDest <= arrivalThresholdMeters) {
            val elapsedSec = Math.max(1L, (System.currentTimeMillis() - tripStartTime) / 1000L)
            val avgSpeedKmh = if (speedSampleCount > 0) {
                sumSpeedKmh / speedSampleCount
            } else if (elapsedSec > 0) {
                (accumulatedDistanceMeters / 1000.0) / (elapsedSec / 3600.0)
            } else 0.0

            val summary = TripSummary(
                totalDistanceMeters = if (accumulatedDistanceMeters > 50.0) accumulatedDistanceMeters else route.distanceMeters,
                totalDurationSeconds = elapsedSec,
                averageSpeedKmh = avgSpeedKmh,
                startAddress = route.summary.ifEmpty { "Başlangıç Noktası" },
                destinationAddress = route.title
            )

            ttsManager.announceArrival()
            onArrivalDetected(summary)

            return NavigationProgress(
                currentManeuver = TurnManeuver("Hedefe ulaştınız", 0.0, ManeuverType.REACH_DESTINATION, destination),
                nextManeuver = null,
                distanceToManeuverMeters = 0.0,
                totalRemainingDistanceMeters = 0.0,
                totalRemainingSeconds = 0,
                currentRoadName = "",
                hasArrived = true,
                tripSummary = summary
            )
        }

        // 2. Off-route detection & Map Matching (Snap-to-road)
        val snapResult = snapPointToPolyline(location.point, route.geometry)
        val isNearRoute = snapResult != null && snapResult.distanceMeters <= offRouteThresholdMeters

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

        val snappedLoc = if (snapResult != null && snapResult.distanceMeters <= snapThresholdMeters) snapResult.projectedPoint else null
        val snappedBearing = if (snapResult != null && snapResult.distanceMeters <= snapThresholdMeters) snapResult.segmentBearing else null

        // 3. Maneuver advancement
        val maneuvers = route.maneuvers
        if (maneuvers.isNotEmpty() && currentManeuverIndex in maneuvers.indices) {
            val currMan = maneuvers[currentManeuverIndex]
            val distToMan = location.point.distanceTo(currMan.point)

            if (distToMan < 30.0 && currentManeuverIndex < maneuvers.size - 1) {
                currentManeuverIndex++
                resetVoiceGates()
            }
        }

        val activeManeuver = maneuvers.getOrNull(currentManeuverIndex)
        val upcomingManeuver = maneuvers.getOrNull(currentManeuverIndex + 1)
        val distToNextManeuver = activeManeuver?.point?.let { location.point.distanceTo(it) } ?: distToDest

        // 4. Voice guidance triggers & Lane announcements
        handleVoiceTriggers(distToNextManeuver, activeManeuver)

        // 5. Remaining distance & duration
        val remainingDist = Math.max(0.0, distToDest)
        val avgSpeedMs = if (location.speedKmh > 12) (location.speedKmh / 3.6) else 11.1 // ~40 km/h fallback
        val remainingSec = Math.round(remainingDist / avgSpeedMs)

        return NavigationProgress(
            currentManeuver = activeManeuver,
            nextManeuver = upcomingManeuver,
            distanceToManeuverMeters = distToNextManeuver,
            totalRemainingDistanceMeters = remainingDist,
            totalRemainingSeconds = remainingSec,
            currentRoadName = activeManeuver?.roadName ?: "",
            isOffRoute = offRouteConsecutiveCount >= 2,
            lanes = activeManeuver?.lanes ?: emptyList(),
            speedLimitKmh = activeManeuver?.speedLimitKmh,
            snappedLocation = snappedLoc,
            snappedBearing = snappedBearing
        )
    }

    private fun handleVoiceTriggers(distanceMeters: Double, activeManeuver: TurnManeuver?) {
        if (activeManeuver == null) return
        val instruction = activeManeuver.instruction

        when {
            distanceMeters in 400.0..600.0 && !announced500m -> {
                announced500m = true
                ttsManager.speak("500 metre sonra $instruction")
            }
            distanceMeters in 150.0..280.0 && !announced200m -> {
                announced200m = true
                val laneHint = LaneGuidanceHelper.buildLaneVoiceHint(activeManeuver.lanes)
                if (laneHint != null && !announcedLane) {
                    announcedLane = true
                    ttsManager.speak("200 metre sonra $instruction. $laneHint")
                } else {
                    ttsManager.speak("200 metre sonra $instruction")
                }
            }
            distanceMeters in 15.0..60.0 && !announced50m -> {
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
        lastProcessedPoint = null
    }

    private data class SnapResult(
        val projectedPoint: GeoPoint,
        val distanceMeters: Double,
        val segmentBearing: Float
    )

    private fun snapPointToPolyline(point: GeoPoint, polyline: List<GeoPoint>): SnapResult? {
        if (polyline.size < 2) return null

        var bestProj: GeoPoint? = null
        var bestDist = Double.MAX_VALUE
        var bestBearing = 0f

        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]

            val latMean = Math.toRadians((a.latitude + b.latitude) / 2.0)
            val cosLat = Math.cos(latMean)

            val dx = (b.longitude - a.longitude) * cosLat
            val dy = b.latitude - a.latitude
            val segLenSq = dx * dx + dy * dy

            val t = if (segLenSq < 1e-12) 0.0 else {
                val px = (point.longitude - a.longitude) * cosLat
                val py = point.latitude - a.latitude
                val dot = px * dx + py * dy
                (dot / segLenSq).coerceIn(0.0, 1.0)
            }

            val projLat = a.latitude + t * (b.latitude - a.latitude)
            val projLon = a.longitude + t * (b.longitude - a.longitude)
            val projPoint = GeoPoint(projLat, projLon)
            val dist = point.distanceTo(projPoint)

            if (dist < bestDist) {
                bestDist = dist
                bestProj = projPoint

                // Calculate segment bearing
                val y = Math.sin(Math.toRadians(b.longitude - a.longitude)) * Math.cos(Math.toRadians(b.latitude))
                val x = Math.cos(Math.toRadians(a.latitude)) * Math.sin(Math.toRadians(b.latitude)) -
                        Math.sin(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) * Math.cos(Math.toRadians(b.longitude - a.longitude))
                bestBearing = ((Math.toDegrees(Math.atan2(y, x)).toFloat() + 360f) % 360f)
            }
        }

        return if (bestProj != null) SnapResult(bestProj, bestDist, bestBearing) else null
    }
}
