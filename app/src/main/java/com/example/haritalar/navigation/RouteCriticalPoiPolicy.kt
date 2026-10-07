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

    fun routeSampleCenters(
        route: List<GeoPoint>,
        spacingMeters: Double = ROUTE_SAMPLE_SPACING_METERS
    ): List<GeoPoint> {
        if (route.isEmpty()) return emptyList()
        if (route.size == 1) return route

        val safeSpacing = spacingMeters.coerceIn(8_000.0, 30_000.0)
        val centers = mutableListOf(route.first())
        var distanceSinceLast = 0.0

        for (index in 0 until route.lastIndex) {
            var segmentStart = route[index]
            val segmentEnd = route[index + 1]
            var remaining = segmentStart.distanceTo(segmentEnd)
            if (remaining <= 0.0) continue

            while (distanceSinceLast + remaining >= safeSpacing) {
                val needed = safeSpacing - distanceSinceLast
                val fraction = (needed / remaining).coerceIn(0.0, 1.0)
                val sample = GeoPoint(
                    latitude = segmentStart.latitude +
                        (segmentEnd.latitude - segmentStart.latitude) * fraction,
                    longitude = segmentStart.longitude +
                        (segmentEnd.longitude - segmentStart.longitude) * fraction
                )
                centers += sample
                segmentStart = sample
                remaining = segmentStart.distanceTo(segmentEnd)
                distanceSinceLast = 0.0
                if (remaining <= 1.0) break
            }
            distanceSinceLast += remaining
        }

        if (centers.last().distanceTo(route.last()) > safeSpacing * 0.35) {
            centers += route.last()
        }

        return centers.distinctBy {
            Pair(
                (it.latitude * 1_000_000).toLong(),
                (it.longitude * 1_000_000).toLong()
            )
        }
    }

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
