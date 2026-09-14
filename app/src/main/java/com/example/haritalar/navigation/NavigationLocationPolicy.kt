package com.example.haritalar.navigation

/**
 * Central safety policy for operations that depend on a real user location.
 * Simulated locations are explicitly allowed for emulator/test navigation.
 */
object NavigationLocationPolicy {
    const val MAX_LOCATION_AGE_MILLIS = 30_000L
    const val MAX_ACCURACY_METERS = 100f

    fun isUsableForRouting(
        location: UserLocationData?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (location == null) return false
        if (location.isSimulated) return true

        val ageMillis = nowMillis - location.timestamp
        if (ageMillis < 0L || ageMillis > MAX_LOCATION_AGE_MILLIS) return false

        val accuracy = location.accuracyMeters
        if (!accuracy.isFinite() || accuracy <= 0f) return false
        return accuracy <= MAX_ACCURACY_METERS
    }
}
