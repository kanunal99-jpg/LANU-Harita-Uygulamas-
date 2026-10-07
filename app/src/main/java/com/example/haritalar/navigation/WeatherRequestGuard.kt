package com.example.haritalar.navigation

object WeatherRequestGuard {
    fun shouldApply(
        requestGeneration: Long,
        currentGeneration: Long,
        requestedRouteId: String,
        currentRouteId: String?
    ): Boolean {
        return requestGeneration == currentGeneration &&
            currentRouteId != null &&
            requestedRouteId == currentRouteId
    }
}
