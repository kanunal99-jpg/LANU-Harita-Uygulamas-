package com.example.haritalar.navigation

import com.example.haritalar.model.AverageSpeedZone
import com.example.haritalar.model.AverageSpeedZoneRouteMatch
import com.example.haritalar.model.GeoPoint
import kotlin.math.cos
import kotlin.math.sqrt

object AverageSpeedZoneRoutePolicy {
    const val ROUTE_CORRIDOR_METERS = 320.0
    const val MIN_ZONE_GEOMETRY_METERS = 100.0
    const val QUERY_RADIUS_METERS = 3_000

    fun routeSignature(route: List<GeoPoint>): String =
        RoadFeatureRoutePolicy.routeSignature(route)

    fun matchToRoute(
        zones: List<AverageSpeedZone>,
        route: List<GeoPoint>,
        corridorMeters: Double = ROUTE_CORRIDOR_METERS
    ): List<AverageSpeedZoneRouteMatch> {
        if (route.size < 2) return emptyList()
        val safeCorridor = corridorMeters.coerceIn(100.0, 1_000.0)

        return zones.mapNotNull { zone ->
            if (zone.geometry.size < 2) return@mapNotNull null
            if (geometryLengthMeters(zone.geometry) < MIN_ZONE_GEOMETRY_METERS) return@mapNotNull null

            val a = projectToRoute(zone.geometry.first(), route) ?: return@mapNotNull null
            val b = projectToRoute(zone.geometry.last(), route) ?: return@mapNotNull null
            if (a.distanceMeters > safeCorridor || b.distanceMeters > safeCorridor) return@mapNotNull null

            val start = minOf(a.progressMeters, b.progressMeters)
            val end = maxOf(a.progressMeters, b.progressMeters)
            val length = end - start
            if (length < MIN_ZONE_GEOMETRY_METERS) return@mapNotNull null

            AverageSpeedZoneRouteMatch(
                zone = zone,
                startRouteMeters = start,
                endRouteMeters = end,
                routeLengthMeters = length,
                speedLimitKmh = SafetyCameraWarningPolicy.parseSpeedLimitKmh(zone.maxSpeed)
            )
        }
            .distinctBy { it.zone.id }
            .sortedBy { it.startRouteMeters }
    }

    fun geometryLengthMeters(geometry: List<GeoPoint>): Double {
        if (geometry.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until geometry.lastIndex) {
            total += geometry[i].distanceTo(geometry[i + 1])
        }
        return total
    }

    private data class RouteProjection(
        val progressMeters: Double,
        val distanceMeters: Double
    )

    private fun projectToRoute(point: GeoPoint, route: List<GeoPoint>): RouteProjection? {
        var cumulative = 0.0
        var best: RouteProjection? = null

        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            val segmentLength = a.distanceTo(b)
            if (segmentLength <= 0.0) continue

            val p = projectOnSegment(point, a, b)
            val candidate = RouteProjection(
                progressMeters = cumulative + (segmentLength * p.t),
                distanceMeters = p.distanceMeters
            )
            if (best == null || candidate.distanceMeters < best!!.distanceMeters) best = candidate
            cumulative += segmentLength
        }
        return best
    }

    private data class SegmentProjection(val t: Double, val distanceMeters: Double)

    private fun projectOnSegment(point: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val latRef = Math.toRadians((a.latitude + b.latitude + point.latitude) / 3.0)
        val metersPerLat = 111_132.92 - 559.82 * cos(2 * latRef)
        val metersPerLon = 111_412.84 * cos(latRef)

        val px = (point.longitude - a.longitude) * metersPerLon
        val py = (point.latitude - a.latitude) * metersPerLat
        val bx = (b.longitude - a.longitude) * metersPerLon
        val by = (b.latitude - a.latitude) * metersPerLat
        val denom = bx * bx + by * by
        if (denom <= 0.0) return SegmentProjection(0.0, point.distanceTo(a))

        val t = ((px * bx + py * by) / denom).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return SegmentProjection(t, sqrt(dx * dx + dy * dy))
    }
}
