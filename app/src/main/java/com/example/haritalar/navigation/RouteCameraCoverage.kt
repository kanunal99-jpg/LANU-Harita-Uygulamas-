package com.example.haritalar.navigation

/**
 * Tracks source quality for EVERY segment of a route radar scan.
 * Query attempts are not proof of verified coverage: cache and provider errors
 * always remain visible, even when the scan loop has finished.
 */
data class RouteCameraCoverage(
    val routeId: String? = null,
    val totalCenters: Int = 0,
    val checkedCenters: Int = 0,
    val verifiedCenters: Int = 0,
    val cachedCenters: Int = 0,
    val failedCenters: Int = 0,
    val finished: Boolean = false
) {
    val fullyVerified: Boolean
        get() = finished && totalCenters > 0 &&
            checkedCenters == totalCenters &&
            verifiedCenters == totalCenters &&
            cachedCenters == 0 && failedCenters == 0

    val coverageDegraded: Boolean
        get() = finished && !fullyVerified

    fun withProviderSuccess(fromCache: Boolean): RouteCameraCoverage = copy(
        checkedCenters = checkedCenters + 1,
        verifiedCenters = verifiedCenters + if (fromCache) 0 else 1,
        cachedCenters = cachedCenters + if (fromCache) 1 else 0
    )

    fun withProviderFailure(): RouteCameraCoverage = copy(
        checkedCenters = checkedCenters + 1,
        failedCenters = failedCenters + 1
    )

    fun finishWithRemainingFailures(): RouteCameraCoverage {
        val outstanding = (totalCenters - checkedCenters).coerceAtLeast(0)
        return copy(
            checkedCenters = checkedCenters + outstanding,
            failedCenters = failedCenters + outstanding,
            finished = true
        )
    }
}
