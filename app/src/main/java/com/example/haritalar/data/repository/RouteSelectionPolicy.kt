package com.example.haritalar.data.repository

import com.example.haritalar.model.RouteOption

/** Selects only provider routes whose geometry and metrics are internally consistent. */
object RouteSelectionPolicy {
    fun select(primary: List<RouteOption>, alternative: List<RouteOption>): List<RouteOption> {
        val validatedPrimary = primary.filter(::isUsable)
        if (validatedPrimary.isNotEmpty()) return validatedPrimary
        return alternative.filter(::isUsable)
    }

    fun isUsable(route: RouteOption): Boolean {
        if (route.geometry.size < 2) return false
        if (route.distanceMeters <= 0.0 || route.durationSeconds <= 0L) return false
        val geometryLength = route.geometry.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
        if (geometryLength <= 0.0) return false
        val endToEnd = route.geometry.first().distanceTo(route.geometry.last())
        if (geometryLength / route.distanceMeters !in 0.85..1.15) return false

        // A provider must not be allowed to inject an impossible ETA into navigation.
        // Keep this deliberately broad for slow urban/ferry traffic while rejecting
        // zero-speed and implausibly fast driving profiles.
        val averageSpeedKmh = route.distanceMeters / route.durationSeconds * 3.6
        return geometryLength >= endToEnd && averageSpeedKmh in 0.5..180.0
    }
}
