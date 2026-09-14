package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import kotlin.math.ceil

/** Pure safety-warning policy; never invents a speed limit. */
object SafetyCameraWarningPolicy {
    const val MAX_WARNING_DISTANCE_METERS = 5_000.0
    const val WARNING_STEP_METERS = 500.0

    data class ProximityWarning(
        val camera: SafetyCamera,
        val distanceMeters: Double,
        val distanceBucketMeters: Int,
        val speedLimitKmh: Int?,
        val overspeed: Boolean
    )

    fun nearest(
        cameras: List<SafetyCamera>,
        point: GeoPoint,
        speedKmh: Float
    ): ProximityWarning? {
        val camera = cameras.minByOrNull { it.point.distanceTo(point) } ?: return null
        return evaluate(camera, camera.point.distanceTo(point), speedKmh)
    }

    fun evaluate(
        camera: SafetyCamera,
        distanceMeters: Double,
        speedKmh: Float
    ): ProximityWarning? {
        if (distanceMeters > MAX_WARNING_DISTANCE_METERS) return null
        val limit = parseSpeedLimitKmh(camera.maxSpeed)
        return ProximityWarning(
            camera = camera,
            distanceMeters = distanceMeters,
            distanceBucketMeters = warningBucket(distanceMeters),
            speedLimitKmh = limit,
            overspeed = limit != null && speedKmh > limit
        )
    }

    fun warningBucket(distanceMeters: Double): Int {
        if (distanceMeters <= 0.0) return 0
        return (ceil(distanceMeters / WARNING_STEP_METERS) * WARNING_STEP_METERS).toInt()
            .coerceAtMost(MAX_WARNING_DISTANCE_METERS.toInt())
    }

    fun parseSpeedLimitKmh(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("\\d+(?:[.,]\\d+)?").find(raw.trim()) ?: return null
        return match.value.replace(',', '.').toDoubleOrNull()?.takeIf { it in 5.0..250.0 }?.toInt()
    }
}
