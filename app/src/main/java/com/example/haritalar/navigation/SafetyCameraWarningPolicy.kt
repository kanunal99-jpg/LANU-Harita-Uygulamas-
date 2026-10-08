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
        if (!distanceMeters.isFinite() || distanceMeters < 0.0 || distanceMeters > warningRadius) return null

        val limit = parseSpeedLimitKmh(camera.maxSpeed)
        return ProximityWarning(
            camera = camera,
            distanceMeters = distanceMeters,
            distanceBucketMeters = warningBucket(distanceMeters),
            announcementMilestoneMeters = announcementMilestone(distanceMeters),
            warningRadiusMeters = warningRadius,
            estimatedSecondsToCamera = estimateSecondsToCamera(distanceMeters, speedKmh),
            speedLimitKmh = limit,
            overspeed = limit != null && speedKmh.isFinite() && speedKmh >= 0f && speedKmh > limit
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
        if (!distanceMeters.isFinite() || distanceMeters < 0.0 || !speedKmh.isFinite() || speedKmh < 5f) return null
        val metersPerSecond = speedKmh / 3.6
        return (distanceMeters / metersPerSecond).toInt().coerceAtLeast(0)
    }

    /**
     * OSM maxspeed is not necessarily one unconditional, legally applicable number:
     * it can be conditional (50 @ (Mo-Fr 07:00-19:00)), multiple (50;80),
     * vehicle-specific, country-code (TR:urban), or nonnumeric (signals).
     * Only a single plain numeric value, optionally with units, may be spoken.
     * Do not infer the current legal limit from a conditional/multiple value.
     */
    fun parseSpeedLimitKmh(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().lowercase(java.util.Locale.ROOT)
        val match = Regex("""^(\\d{1,3})(?:[.,]0+)?\\s*(?:(km\\s*/\\s*h|kmh|kph|mph))?$""").matchEntire(normalized)
            ?: return null
        val numeric = match.groupValues[1].toIntOrNull() ?: return null
        val kmh = if (match.groupValues[2] == "mph") numeric * 1.609344 else numeric.toDouble()
        return kmh.takeIf { it in 5.0..250.0 }?.toInt()
    }
}
