package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import kotlin.math.max

/**
 * Rejects stale or physically implausible GPS fixes before they reach navigation state.
 * It never invents a replacement point; rejected fixes simply return null.
 */
class LocationQualityFilter(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxPlausibleSpeedKmh: Double = 240.0,
    private val maxTimestampAgeMs: Long = 30_000L,
    private val maxFutureTimestampMs: Long = 5_000L
) {
    private var lastAccepted: UserLocationData? = null

    fun accept(candidate: UserLocationData): UserLocationData? {
        if (!candidate.point.latitude.isFinite() || !candidate.point.longitude.isFinite()) return null
        if (candidate.point.latitude !in -90.0..90.0 || candidate.point.longitude !in -180.0..180.0) return null

        val now = nowMs()
        val age = now - candidate.timestamp
        if (age > maxTimestampAgeMs || age < -maxFutureTimestampMs) return null
        if (candidate.accuracyMeters.isNaN() || candidate.accuracyMeters < 0f) return null

        val previous = lastAccepted
        if (previous != null && candidate.timestamp > previous.timestamp) {
            val deltaSeconds = (candidate.timestamp - previous.timestamp) / 1000.0
            val jumpMeters = previous.point.distanceTo(candidate.point)
            val reportedSpeedKmh = candidate.speedKmh.coerceAtLeast(0f).toDouble()
            val allowedSpeedKmh = max(maxPlausibleSpeedKmh, reportedSpeedKmh + 80.0)
            val allowedDistance = allowedSpeedKmh / 3.6 * deltaSeconds + max(20.0, candidate.accuracyMeters.toDouble())
            if (jumpMeters > allowedDistance) return null
        }

        lastAccepted = candidate
        return candidate
    }

    fun reset() {
        lastAccepted = null
    }
}
