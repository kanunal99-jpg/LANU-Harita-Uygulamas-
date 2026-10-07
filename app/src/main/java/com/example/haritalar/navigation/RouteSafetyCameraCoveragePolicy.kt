package com.example.haritalar.navigation

import com.example.haritalar.data.network.SafetyCameraAreaPolicy
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera

data class RouteSafetyCameraCoverage(
    val status: RouteDataCoverage,
    val cameras: List<SafetyCamera>,
    val source: String,
    val fetchedAtMillis: Long,
    val sampleCount: Int,
    val successfulSampleCount: Int,
    val maxSampleGapMeters: Double,
    val note: String
)

/** Route-wide camera sampling plan for pre-drive LANU Brief. */
object RouteSafetyCameraCoveragePolicy {
    const val MAX_FULL_SAMPLE_GAP_METERS = 20_000.0
    const val MAX_SAMPLE_POINTS = 10

    fun plan(route: List<GeoPoint>): RouteSamplingPolicy.Plan? =
        RouteSamplingPolicy.plan(
            route = route,
            maxFullSampleGapMeters = MAX_FULL_SAMPLE_GAP_METERS,
            maxSamplePoints = MAX_SAMPLE_POINTS
        )

    fun filterToRoute(
        route: List<GeoPoint>,
        candidates: List<SafetyCamera>
    ): List<SafetyCamera> {
        val start = route.firstOrNull() ?: return emptyList()
        return SafetyCameraRouteFilterPolicy.relevantForRoute(
            cameras = candidates,
            route = route,
            userPoint = start,
            corridorMeters = SafetyCameraRouteFilterPolicy.DEFAULT_ROUTE_CORRIDOR_METERS
        ).distinctBy { it.id }
    }

    fun boundingBox(center: GeoPoint) =
        SafetyCameraAreaPolicy.boundingBoxAround(
            center = center,
            radiusMeters = SafetyCameraAreaPolicy.NAVIGATION_PREFETCH_RADIUS_METERS
        )
}
