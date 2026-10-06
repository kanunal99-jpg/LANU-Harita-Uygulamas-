package com.example.haritalar.navigation

/**
 * Deterministic GPS sampling policy. Keeps frequent updates while driving and
 * relaxes the request while stationary/slow to reduce unnecessary work.
 */
object LocationSamplingPolicy {
    enum class Mode { IDLE, MOVING }

    data class Config(
        val intervalMillis: Long,
        val minUpdateIntervalMillis: Long,
        val minUpdateDistanceMeters: Float
    )

    const val MOVING_INTERVAL_MS = 1_000L
    const val MOVING_MIN_INTERVAL_MS = 750L
    const val MOVING_MIN_DISTANCE_METERS = 2.0f

    const val IDLE_INTERVAL_MS = 4_000L
    const val IDLE_MIN_INTERVAL_MS = 2_500L
    const val IDLE_MIN_DISTANCE_METERS = 8.0f

    const val ENTER_MOVING_SPEED_KMH = 8.0f
    const val EXIT_MOVING_SPEED_KMH = 3.0f

    val MOVING_CONFIG = Config(
        intervalMillis = MOVING_INTERVAL_MS,
        minUpdateIntervalMillis = MOVING_MIN_INTERVAL_MS,
        minUpdateDistanceMeters = MOVING_MIN_DISTANCE_METERS
    )

    val IDLE_CONFIG = Config(
        intervalMillis = IDLE_INTERVAL_MS,
        minUpdateIntervalMillis = IDLE_MIN_INTERVAL_MS,
        minUpdateDistanceMeters = IDLE_MIN_DISTANCE_METERS
    )

    fun nextMode(current: Mode, speedKmh: Float): Mode = when (current) {
        Mode.IDLE -> if (speedKmh >= ENTER_MOVING_SPEED_KMH) Mode.MOVING else Mode.IDLE
        Mode.MOVING -> if (speedKmh <= EXIT_MOVING_SPEED_KMH) Mode.IDLE else Mode.MOVING
    }

    fun config(mode: Mode): Config = when (mode) {
        Mode.IDLE -> IDLE_CONFIG
        Mode.MOVING -> MOVING_CONFIG
    }
}
