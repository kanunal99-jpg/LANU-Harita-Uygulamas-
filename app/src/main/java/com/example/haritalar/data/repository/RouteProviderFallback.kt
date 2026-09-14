package com.example.haritalar.data.repository

import com.example.haritalar.model.RouteOption

/**
 * Sequential routing fallback: only invoke the alternative provider when the
 * primary provider fails or yields no internally valid routes.
 */
object RouteProviderFallback {
    suspend fun resolve(
        primary: suspend () -> List<RouteOption>,
        alternative: suspend () -> List<RouteOption>
    ): List<RouteOption> {
        val primaryRoutes = runCatching { primary() }.getOrDefault(emptyList())
        val validatedPrimary = RouteSelectionPolicy.select(primaryRoutes, emptyList())
        if (validatedPrimary.isNotEmpty()) return validatedPrimary

        val alternativeRoutes = runCatching { alternative() }.getOrDefault(emptyList())
        return RouteSelectionPolicy.select(emptyList(), alternativeRoutes)
    }
}
