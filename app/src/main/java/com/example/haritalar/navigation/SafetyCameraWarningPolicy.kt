package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import kotlin.math.ceil

/** Pure safety-warning policy; never invents a speed limit or enforcement point. */
object SafetyCameraWarningPolicy {
    const val BASE_WARNING_DISTANCE_METERS = 5_000.0
    const val MAX_WARNING_DISTANCE_METERS = 5_000.0
    const val WARNING_STEP_METERS = 500.0

    private val ANNOUNCEMENT_MILESTONES_METERS =
        (500..5_000 step 500).toList().sorted().toIntArray()

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
        if (distanceMeters < 0.0 || distanceMeters > warningRadius) return null

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

    /** Driving voice alerts start only inside the final 5 km. */
    fun warningRadiusMeters(speedKmh: Float): Int = MAX_WARNING_DISTANCE_METERS.toInt()

    fun warningBucket(distanceMeters: Double): Int {
        if (distanceMeters <= 0.0) return 0
        return (ceil(distanceMeters / WARNING_STEP_METERS) * WARNING_STEP_METERS).toInt()
            .coerceAtMost(MAX_WARNING_DISTANCE_METERS.toInt())
    }

    /**
     * During driving, announce only 5.0 -> 4.5 -> ... -> 0.5 km.
     * No 20 km / 10 km driving announcements: long-distance camera positions belong
     * to the pre-drive route briefing.
     */
    fun announcementMilestone(distanceMeters: Double): Int? {
        if (distanceMeters <= 0.0 || distanceMeters > MAX_WARNING_DISTANCE_METERS) return null
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
