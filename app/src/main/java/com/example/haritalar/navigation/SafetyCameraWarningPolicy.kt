package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import kotlin.math.ceil

/** Pure safety-warning policy; never invents a speed limit or enforcement point. */
object SafetyCameraWarningPolicy {
    const val BASE_WARNING_DISTANCE_METERS = 5_000.0
    const val MAX_WARNING_DISTANCE_METERS = 10_000.0
    const val WARNING_STEP_METERS = 500.0

    private val ANNOUNCEMENT_MILESTONES_METERS =
        intArrayOf(250, 500, 1_000, 2_000, 3_000, 5_000, 6_500, 8_000, 10_000)

    data class ProximityWarning(
        val camera: SafetyCamera,
        val distanceMeters: Double,
        val distanceBucketMeters: Int,
        val announcementMilestoneMeters: Int?,
        val warningRadiusMeters: Int,
        val estimatedSecondsToCamera: Int?,
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
        val warningRadius = warningRadiusMeters(speedKmh)
        if (distanceMeters > warningRadius) return null

        val limit = parseSpeedLimitKmh(camera.maxSpeed)
        return ProximityWarning(
            camera = camera,
            distanceMeters = distanceMeters,
            distanceBucketMeters = warningBucket(distanceMeters),
            announcementMilestoneMeters = announcementMilestone(distanceMeters),
            warningRadiusMeters = warningRadius,
            estimatedSecondsToCamera = estimateSecondsToCamera(distanceMeters, speedKmh),
            speedLimitKmh = limit,
            overspeed = limit != null && speedKmh > limit
        )
    }

    /**
     * Earlier warning at road speeds without increasing false precision at low speed.
     * The value is a notification horizon only; it does not imply camera detection.
     */
    fun warningRadiusMeters(speedKmh: Float): Int = when {
        speedKmh >= 110f -> 10_000
        speedKmh >= 90f -> 8_000
        speedKmh >= 70f -> 6_500
        else -> BASE_WARNING_DISTANCE_METERS.toInt()
    }

    fun warningBucket(distanceMeters: Double): Int {
        if (distanceMeters <= 0.0) return 0
        return (ceil(distanceMeters / WARNING_STEP_METERS) * WARNING_STEP_METERS).toInt()
            .coerceAtMost(MAX_WARNING_DISTANCE_METERS.toInt())
    }

    /**
     * Sparse voice/haptic milestones avoid a distracting alert every 500 m.
     */
    fun announcementMilestone(distanceMeters: Double): Int? {
        if (distanceMeters < 0.0 || distanceMeters > MAX_WARNING_DISTANCE_METERS) return null
        if (distanceMeters == 0.0) return 0
        return ANNOUNCEMENT_MILESTONES_METERS.firstOrNull { distanceMeters <= it }
    }

    fun estimateSecondsToCamera(distanceMeters: Double, speedKmh: Float): Int? {
        if (distanceMeters < 0.0 || speedKmh < 5f) return null
        val metersPerSecond = speedKmh / 3.6
        return (distanceMeters / metersPerSecond).toInt().coerceAtLeast(0)
    }

    fun parseSpeedLimitKmh(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().lowercase()
        val match = Regex("\\d+(?:[.,]\\d+)?").find(normalized) ?: return null
        val numeric = match.value.replace(',', '.').toDoubleOrNull() ?: return null
        val kmh = if ("mph" in normalized) numeric * 1.609344 else numeric
        return kmh.takeIf { it in 5.0..250.0 }?.toInt()
    }
}
