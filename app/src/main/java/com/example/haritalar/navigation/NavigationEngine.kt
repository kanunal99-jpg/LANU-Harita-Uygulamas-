package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.LaneInfo
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TripSummary
import com.example.haritalar.model.TurnManeuver
import com.example.haritalar.voice.NavigationVoice
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
    private val voice: NavigationVoice,
    private val onOffRouteDetected: (currentLocation: GeoPoint) -> Unit,
    private val onArrivalDetected: (tripSummary: TripSummary) -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    constructor(
        ttsManager: TurkishTtsManager,
        onOffRouteDetected: (currentLocation: GeoPoint) -> Unit,
        onArrivalDetected: (tripSummary: TripSummary) -> Unit
    ) : this(ttsManager, onOffRouteDetected, onArrivalDetected, System::currentTimeMillis)

    private var activeRoute: RouteOption? = null
    private var currentManeuverIndex = 0
    private var offRouteConsecutiveCount = 0
    private var lastRerouteTimestamp = 0L
    private val rerouteCooldownMs = 12_000L
    private val offRouteThresholdMeters = 55.0
    private val arrivalThresholdMeters = 35.0
    private val snapThresholdMeters = 35.0
    private val maxAllowedBacktrackMeters = 75.0
    private var announced500m = false
    private var announced200m = false
    private var announced50m = false
    private var announcedLane = false
    private var hasAnnouncedStartSequence = false
    private var hasAnnouncedArrival = false
    private var arrivalConsecutiveCount = 0
    private var tripStartTime: Long = 0L
    private var accumulatedDistanceMeters: Double = 0.0
    private var lastProcessedPoint: GeoPoint? = null
    private var maxSpeedKmh: Float = 0f
    private var sumSpeedKmh: Double = 0.0
    private var speedSampleCount: Int = 0
    private var lastRouteProgressMeters = 0.0

    fun startNavigation(route: RouteOption) {
        activeRoute = route
        currentManeuverIndex = 0
        offRouteConsecutiveCount = 0
        lastRerouteTimestamp = nowMs()
        tripStartTime = nowMs()
        accumulatedDistanceMeters = 0.0
        lastProcessedPoint = null
        maxSpeedKmh = 0f
        sumSpeedKmh = 0.0
        speedSampleCount = 0
        lastRouteProgressMeters = 0.0
        hasAnnouncedArrival = false
        arrivalConsecutiveCount = 0
        resetVoiceGates()
        if (!hasAnnouncedStartSequence) {
            hasAnnouncedStartSequence = true
            voice.playNavigationStartSequence()
        }
    }

    fun updateRoute(newRoute: RouteOption) {
        activeRoute = newRoute
        currentManeuverIndex = 0
        offRouteConsecutiveCount = 0
        lastProcessedPoint = null
        lastRouteProgressMeters = 0.0
        resetVoiceGates()
    }

    private fun resetVoiceGates() {
        announced500m = false
        announced200m = false
        announced50m = false
        announcedLane = false
    }

    fun processLocationUpdate(location: UserLocationData): NavigationProgress {
        val route = activeRoute ?: return NavigationProgress(null, null, 0.0, 0.0, 0, "")
        if (route.geometry.size < 2) {
            return NavigationProgress(null, null, 0.0, 0.0, 0, "", isOffRoute = true)
        }

        val destination = route.geometry.last()
        val distToDest = location.point.distanceTo(destination)
        updateTripSamples(location)

        val snapResult = snapPointToPolyline(
            point = location.point,
            polyline = route.geometry,
            bearingHint = location.bearing,
            speedKmh = location.speedKmh,
            previousProgressMeters = lastRouteProgressMeters
        )
        val isNearRoute = snapResult != null && snapResult.distanceMeters <= offRouteThresholdMeters
        if (!isNearRoute) {
            offRouteConsecutiveCount++
            val now = nowMs()
            if (offRouteConsecutiveCount >= 2 && now - lastRerouteTimestamp > rerouteCooldownMs) {
                lastRerouteTimestamp = now
                voice.announceReroute()
                onOffRouteDetected(location.point)
            }
        } else {
            offRouteConsecutiveCount = 0
        }

        val snappedLoc = snapResult?.takeIf { it.distanceMeters <= snapThresholdMeters }?.projectedPoint
        val snappedBearing = snapResult?.takeIf { it.distanceMeters <= snapThresholdMeters }?.segmentBearing

        val routeLengthMeters = routeGeometryLength(route.geometry)
        val rawProgressMeters = snapResult?.distanceAlongRouteMeters?.coerceIn(0.0, routeLengthMeters)
        val minimumAllowedProgress = (lastRouteProgressMeters - maxAllowedBacktrackMeters).coerceAtLeast(0.0)
        val routeProgressMeters = (rawProgressMeters ?: lastRouteProgressMeters)
            .coerceIn(minimumAllowedProgress, routeLengthMeters)
        if (snapResult != null) lastRouteProgressMeters = routeProgressMeters
        val remainingDist = (routeLengthMeters - routeProgressMeters).coerceAtLeast(0.0)

        val isGpsAccurate = location.accuracyMeters <= 45f
        val isNearDest = distToDest <= arrivalThresholdMeters
        if (isNearDest && isGpsAccurate) arrivalConsecutiveCount++
        else if (distToDest > arrivalThresholdMeters + 10.0) arrivalConsecutiveCount = 0

        val isArrivalConfirmed = (arrivalConsecutiveCount >= 2) || (distToDest <= 15.0 && isGpsAccurate)
        if (isArrivalConfirmed) {
            val elapsedSec = maxOf(1L, (nowMs() - tripStartTime) / 1000L)
            val avgSpeedKmh = if (speedSampleCount > 0) sumSpeedKmh / speedSampleCount
            else (accumulatedDistanceMeters / 1000.0) / (elapsedSec / 3600.0)
            val summary = TripSummary(
                totalDistanceMeters = route.distanceMeters,
                totalDurationSeconds = elapsedSec,
                averageSpeedKmh = avgSpeedKmh,
                startAddress = route.summary.ifEmpty { "Başlangıç Noktası" },
                destinationAddress = route.title
            )
            if (!hasAnnouncedArrival) {
                hasAnnouncedArrival = true
                voice.announceArrival()
                onArrivalDetected(summary)
            }
            return NavigationProgress(
                currentManeuver = TurnManeuver("Hedefe ulaştınız", 0.0, ManeuverType.REACH_DESTINATION, destination),
                nextManeuver = null,
                distanceToManeuverMeters = 0.0,
                totalRemainingDistanceMeters = 0.0,
                totalRemainingSeconds = 0,
                currentRoadName = "",
                hasArrived = true,
                snappedLocation = snappedLoc,
                snappedBearing = snappedBearing,
                tripSummary = summary
            )
        }

        val maneuvers = route.maneuvers
        while (currentManeuverIndex < maneuvers.lastIndex) {
            val maneuverProgress = progressAtPoint(maneuvers[currentManeuverIndex].point, route.geometry)
            if (maneuverProgress == null || routeProgressMeters + 8.0 < maneuverProgress) break
            currentManeuverIndex++
            resetVoiceGates()
        }

        val activeManeuver = maneuvers.getOrNull(currentManeuverIndex)
        val upcomingManeuver = maneuvers.getOrNull(currentManeuverIndex + 1)
        val distToNextManeuver = activeManeuver?.let {
            val maneuverProgress = progressAtPoint(it.point, route.geometry)
            if (maneuverProgress != null) {
                (maneuverProgress - routeProgressMeters).coerceAtLeast(0.0)
            } else {
                location.point.distanceTo(it.point)
            }
        } ?: remainingDist

        handleVoiceTriggers(distToNextManeuver, activeManeuver)
        val remainingSec = estimateRemainingSeconds(route, remainingDist, location.speedKmh)

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

    private fun updateTripSamples(location: UserLocationData) {
        if (lastProcessedPoint != null) {
            val delta = lastProcessedPoint!!.distanceTo(location.point)
            if (delta in 0.5..150.0) accumulatedDistanceMeters += delta
        }
        lastProcessedPoint = location.point
        if (location.speedKmh > 0f && location.speedKmh <= 180f) {
            maxSpeedKmh = maxOf(maxSpeedKmh, location.speedKmh)
            sumSpeedKmh += location.speedKmh
            speedSampleCount++
        }
    }

    private fun estimateRemainingSeconds(route: RouteOption, remainingDistanceMeters: Double, speedKmh: Float): Long {
        if (remainingDistanceMeters <= 0.0) return 0L
        val geometryLength = routeGeometryLength(route.geometry)
        val profileSeconds = if (geometryLength > 1.0) {
            (route.totalDurationSeconds.toDouble() * (remainingDistanceMeters / geometryLength)).roundToLongSafe()
        } else {
            0L
        }

        val measuredSeconds = if (speedSampleCount >= 3 && speedKmh in 5f..180f) {
            (remainingDistanceMeters / (speedKmh / 3.6f)).roundToLongSafe()
        } else null

        return (measuredSeconds ?: profileSeconds).coerceAtLeast(0L)
    }

    private fun handleVoiceTriggers(distanceMeters: Double, activeManeuver: TurnManeuver?) {
        if (activeManeuver == null) return
        val instruction = activeManeuver.instruction
        when {
            distanceMeters in 400.0..600.0 && !announced500m -> {
                announced500m = true
                voice.speak("500 metre sonra $instruction")
            }
            distanceMeters in 150.0..280.0 && !announced200m -> {
                announced200m = true
                val laneHint = LaneGuidanceHelper.buildLaneVoiceHint(activeManeuver.lanes)
                if (laneHint != null && !announcedLane) {
                    announcedLane = true
                    voice.speak("200 metre sonra $instruction. $laneHint")
                } else {
                    voice.speak("200 metre sonra $instruction")
                }
            }
            distanceMeters in 15.0..60.0 && !announced50m -> {
                announced50m = true
                voice.speak("Şimdi $instruction")
            }
        }
    }

    fun stop() {
        voice.stop()
        activeRoute = null
        currentManeuverIndex = 0
        offRouteConsecutiveCount = 0
        lastProcessedPoint = null
        lastRouteProgressMeters = 0.0
        hasAnnouncedStartSequence = false
        hasAnnouncedArrival = false
        arrivalConsecutiveCount = 0
    }

    private data class SnapResult(
        val projectedPoint: GeoPoint,
        val distanceMeters: Double,
        val segmentBearing: Float,
        val distanceAlongRouteMeters: Double
    )

    private fun routeGeometryLength(polyline: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 0 until polyline.lastIndex) total += polyline[i].distanceTo(polyline[i + 1])
        return total
    }

    private fun progressAtPoint(point: GeoPoint, polyline: List<GeoPoint>): Double? {
        if (polyline.size < 2) return null
        var cumulative = 0.0
        var bestDistance = Double.MAX_VALUE
        var bestProgress = 0.0
        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val projection = projectOnSegment(point, a, b)
            if (projection.distanceMeters < bestDistance) {
                bestDistance = projection.distanceMeters
                bestProgress = cumulative + projection.segmentPosition * a.distanceTo(b)
            }
            cumulative += a.distanceTo(b)
        }
        return bestProgress
    }

    private fun snapPointToPolyline(
        point: GeoPoint,
        polyline: List<GeoPoint>,
        bearingHint: Float,
        speedKmh: Float,
        previousProgressMeters: Double
    ): SnapResult? {
        if (polyline.size < 2) return null
        var cumulative = 0.0
        var best: SnapResult? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val segmentLength = a.distanceTo(b)
            val projection = projectOnSegment(point, a, b)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val y = Math.sin(dLon) * Math.cos(Math.toRadians(b.latitude))
            val x = Math.cos(Math.toRadians(a.latitude)) * Math.sin(Math.toRadians(b.latitude)) -
                    Math.sin(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude)) * Math.cos(dLon)
            val bearing = ((Math.toDegrees(Math.atan2(y, x)).toFloat() + 360f) % 360f)
            val progress = cumulative + projection.segmentPosition * segmentLength
            val headingDiff = circularHeadingDifference(bearingHint, bearing)
            val headingRelevant = speedKmh >= 8f && bearingHint.isFinite()
            val headingPenalty = if (headingRelevant) (headingDiff / 180.0) * 30.0 else 0.0
            val backtrack = (previousProgressMeters - progress).coerceAtLeast(0.0)
            val backtrackPenalty = if (backtrack > maxAllowedBacktrackMeters) {
                120.0 + (backtrack - maxAllowedBacktrackMeters)
            } else {
                0.0
            }
            val score = projection.distanceMeters + headingPenalty + backtrackPenalty
            if (score < bestScore) {
                bestScore = score
                best = SnapResult(
                    projectedPoint = projection.projectedPoint,
                    distanceMeters = projection.distanceMeters,
                    segmentBearing = bearing,
                    distanceAlongRouteMeters = progress
                )
            }
            cumulative += segmentLength
        }
        return best
    }

    private fun circularHeadingDifference(first: Float, second: Float): Double {
        val normalized = kotlin.math.abs(((first - second + 540f) % 360f) - 180f)
        return normalized.toDouble()
    }

    private data class SegmentProjection(
        val projectedPoint: GeoPoint,
        val distanceMeters: Double,
        val segmentPosition: Double
    )

    private fun projectOnSegment(point: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val latMean = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val cosLat = Math.cos(latMean)
        val dx = (b.longitude - a.longitude) * cosLat
        val dy = b.latitude - a.latitude
        val segLenSq = dx * dx + dy * dy
        val t = if (segLenSq < 1e-12) 0.0 else {
            val px = (point.longitude - a.longitude) * cosLat
            val py = point.latitude - a.latitude
            (px * dx + py * dy).div(segLenSq).coerceIn(0.0, 1.0)
        }
        val projected = GeoPoint(
            a.latitude + t * (b.latitude - a.latitude),
            a.longitude + t * (b.longitude - a.longitude)
        )
        return SegmentProjection(projected, point.distanceTo(projected), t)
    }

    private fun Double.roundToLongSafe(): Long = kotlin.math.round(this).toLong().coerceAtLeast(0L)
}
