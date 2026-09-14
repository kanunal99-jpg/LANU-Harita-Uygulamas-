package com.example.haritalar.data.repository

import com.example.haritalar.model.GeoPoint
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
        return geometryLength >= endToEnd && geometryLength / route.distanceMeters in 0.85..1.15
    }
}
