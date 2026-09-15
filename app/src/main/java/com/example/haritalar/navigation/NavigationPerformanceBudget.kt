/** Product budgets for deterministic navigation performance validation. */
package com.example.haritalar.navigation

object NavigationPerformanceBudget {
    const val LOCATION_UPDATE_INTERVAL_MS = 1_000L
    const val TRAFFIC_REFRESH_MIN_INTERVAL_MS = 60_000L
    const val MAX_ROUTE_GENERATION_MS = 15_000L
    const val MAX_NAVIGATION_UPDATE_WORK_MS = 50L

    fun isNavigationUpdateWithinBudget(elapsedMs: Long): Boolean = elapsedMs <= MAX_NAVIGATION_UPDATE_WORK_MS
    fun isRouteGenerationWithinBudget(elapsedMs: Long): Boolean = elapsedMs <= MAX_ROUTE_GENERATION_MS
}
