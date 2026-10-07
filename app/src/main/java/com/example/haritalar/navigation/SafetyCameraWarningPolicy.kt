package com.example.haritalar.navigation

import com.example.haritalar.model.SafetyCamera
import kotlin.math.ceil

/** Pure safety-warning policy; never invents a speed limit or enforcement point. */
object SafetyCameraWarningPolicy {
    const val BASE_WARNING_DISTANCE_METERS = 20_000.0
    const val MAX_WARNING_DISTANCE_METERS = 20_000.0
    const val WARNING_STEP_METERS = 500.0
    const val CASCADE_START_DISTANCE_METERS = 5_000.0

    private val ANNOUNCEMENT_MILESTONES_METERS = buildList {
        add(250)
        var distance = 500
        while (distance <= CASCADE_START_DISTANCE_METERS.toInt()) {
            add(distance)
            distance += WARNING_STEP_METERS.toInt()
        }
        add(20_000)
    }.distinct().sorted().toIntArray()

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

    /**
     * Active navigation gets a 20 km early-warning horizon. Camera truthfulness comes
     * from route filtering and source data, not from vehicle speed.
     */
    fun warningRadiusMeters(speedKmh: Float): Int = MAX_WARNING_DISTANCE_METERS.toInt()

    fun warningBucket(distanceMeters: Double): Int {
        if (distanceMeters <= 0.0) return 0
        return (ceil(distanceMeters / WARNING_STEP_METERS) * WARNING_STEP_METERS).toInt()
            .coerceAtMost(MAX_WARNING_DISTANCE_METERS.toInt())
    }

    /**
     * Voice sequence:
     * - early heads-up at 20 km
     * - from 5 km down to 500 m, every 500 m
     * - final 250 m warning
     *
     * A milestone key is announced only once per camera by the ViewModel.
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
