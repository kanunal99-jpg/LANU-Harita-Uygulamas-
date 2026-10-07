package com.example.haritalar.navigation

/**
 * Central safety policy for operations that depend on a real user location.
 * Simulated locations are explicitly allowed for emulator/test navigation.
 */
object NavigationLocationPolicy {
    const val MAX_LOCATION_AGE_MILLIS = 30_000L
    const val MAX_ACCURACY_METERS = 100f

    enum class Readiness {
        READY,
        MISSING,
        STALE,
        INACCURATE
    }

    fun readiness(
        location: UserLocationData?,
        nowMillis: Long = System.currentTimeMillis()
    ): Readiness {
        if (location == null) return Readiness.MISSING
        if (location.isSimulated) return Readiness.READY

        val ageMillis = nowMillis - location.timestamp
        if (ageMillis < 0L || ageMillis > MAX_LOCATION_AGE_MILLIS) return Readiness.STALE

        val accuracy = location.accuracyMeters
        if (!accuracy.isFinite() || accuracy <= 0f || accuracy > MAX_ACCURACY_METERS) {
            return Readiness.INACCURATE
        }
        return Readiness.READY
    }

    fun isUsableForRouting(
        location: UserLocationData?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = readiness(location, nowMillis) == Readiness.READY
}
