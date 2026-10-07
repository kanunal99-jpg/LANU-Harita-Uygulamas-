package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.RouteCriticalPoiMatch
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Matches source-backed critical services to the selected route.
 *
 * corridorDistanceMeters is a straight-line distance to the nearest route segment.
 * It is deliberately NOT presented as road-driving detour distance.
 */
object RouteCriticalPoiPolicy {
    val CRITICAL_CATEGORIES = setOf(
        PoiCategory.FUEL,
        PoiCategory.HOSPITAL,
        PoiCategory.PHARMACY,
        PoiCategory.CHARGING_STATION
    )

    const val ROUTE_CORRIDOR_METERS = 2_500.0
    const val ROUTE_QUERY_RADIUS_METERS = 9_000
    const val ROUTE_SAMPLE_SPACING_METERS = 16_000.0

    fun matchToRoute(
        pois: List<PoiItem>,
        route: List<GeoPoint>,
        corridorMeters: Double = ROUTE_CORRIDOR_METERS
    ): List<RouteCriticalPoiMatch> {
        if (route.size < 2) return emptyList()
        val safeCorridor = corridorMeters.coerceIn(100.0, 5_000.0)

        return pois
            .asSequence()
            .filter { it.category in CRITICAL_CATEGORIES }
            .distinctBy { "${it.category}:${it.id}" }
            .mapNotNull { poi ->
                val projection = projectToRoute(poi.point, route) ?: return@mapNotNull null
                if (projection.distanceMeters > safeCorridor) return@mapNotNull null
                RouteCriticalPoiMatch(
                    poi = poi,
                    routeDistanceMeters = projection.progressMeters.coerceAtLeast(0.0),
                    corridorDistanceMeters = projection.distanceMeters.coerceAtLeast(0.0)
                )
            }
            .sortedWith(
                compareBy<RouteCriticalPoiMatch> { it.routeDistanceMeters }
                    .thenBy { it.corridorDistanceMeters }
                    .thenBy { it.poi.name }
            )
            .toList()
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

            val segmentProjection = projectOnSegment(point, a, b)
            val candidate = RouteProjection(
                progressMeters = cumulative + (segmentLength * segmentProjection.t),
                distanceMeters = segmentProjection.distanceMeters
            )
            if (best == null || candidate.distanceMeters < best!!.distanceMeters) {
                best = candidate
            }
            cumulative += segmentLength
        }
        return best
    }

    private data class SegmentProjection(
        val t: Double,
        val distanceMeters: Double
    )

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
        val dx = px - (t * bx)
        val dy = py - (t * by)
        return SegmentProjection(
            t = t,
            distanceMeters = sqrt(dx * dx + dy * dy)
        )
    }
}
