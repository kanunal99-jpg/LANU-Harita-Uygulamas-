package com.example.haritalar.navigation

import com.example.haritalar.data.traffic.TrafficRouteMatcher
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RoadFeature
import com.example.haritalar.model.RoadFeatureType
import kotlin.math.cos
import kotlin.math.roundToInt

data class RoadFeatureWarning(
    val feature: RoadFeature,
    val distanceMeters: Double,
    val warningRadiusMeters: Int
)

object RoadFeatureRoutePolicy {
    const val QUERY_RADIUS_METERS = 1_200
    const val DEFAULT_ROUTE_CORRIDOR_METERS = 220.0
    private const val BACKTRACK_TOLERANCE_METERS = 100.0
    private const val MAX_QUERY_POINTS = 12

    fun sampleQueryPoints(route: List<GeoPoint>): List<GeoPoint> {
        if (route.isEmpty()) return emptyList()
        if (route.size <= MAX_QUERY_POINTS) return route.distinct()

        val cumulative = DoubleArray(route.size)
        var total = 0.0
        for (i in 1 until route.size) {
            total += route[i - 1].distanceTo(route[i])
            cumulative[i] = total
        }
        if (total <= 0.0) return listOf(route.first())

        return List(MAX_QUERY_POINTS) { idx ->
            val target = total * idx / (MAX_QUERY_POINTS - 1).toDouble()
            val found = cumulative.indexOfFirst { it >= target }.takeIf { it >= 0 } ?: route.lastIndex
            route[found]
        }.distinct()
    }

    fun routeSignature(route: List<GeoPoint>): String {
        val samples = sampleQueryPoints(route)
        return samples.joinToString("|") {
            val lat = (it.latitude * 10_000).roundToInt()
            val lon = (it.longitude * 10_000).roundToInt()
            "${lat}:${lon}"
        }
    }

    fun filterNearRoute(
        features: List<RoadFeature>,
        route: List<GeoPoint>,
        corridorMeters: Double = DEFAULT_ROUTE_CORRIDOR_METERS
    ): List<RoadFeature> {
        if (route.size < 2) return features
        return features.filter { feature ->
            val featureCorridor = when (feature.type) {
                RoadFeatureType.SCHOOL_ZONE -> 300.0
                else -> corridorMeters
            }
            TrafficRouteMatcher.isPointNearPolyline(feature.point, route, featureCorridor)
        }
    }

    fun relevantAhead(
        features: List<RoadFeature>,
        route: List<GeoPoint>,
        userPoint: GeoPoint,
        corridorMeters: Double = DEFAULT_ROUTE_CORRIDOR_METERS
    ): List<RoadFeature> {
        if (route.size < 2) return features
        val userProgress = routeProgressMeters(userPoint, route) ?: return features

        return filterNearRoute(features, route, corridorMeters).filter { feature ->
            val featureProgress = routeProgressMeters(feature.point, route) ?: return@filter false
            featureProgress + BACKTRACK_TOLERANCE_METERS >= userProgress
        }
    }

    fun nearestWarning(
        features: List<RoadFeature>,
        route: List<GeoPoint>,
        userPoint: GeoPoint,
        speedKmh: Float
    ): RoadFeatureWarning? {
        val candidates = relevantAhead(features, route, userPoint)
        val nearest = candidates.minByOrNull { it.point.distanceTo(userPoint) } ?: return null
        val distance = nearest.point.distanceTo(userPoint)
        val radius = warningRadiusMeters(speedKmh, nearest.type)
        if (distance > radius) return null
        return RoadFeatureWarning(nearest, distance, radius)
    }

    fun warningRadiusMeters(speedKmh: Float, type: RoadFeatureType): Int {
        val base = when {
            speedKmh >= 90f -> 1_500
            speedKmh >= 60f -> 1_000
            else -> 650
        }
        return when (type) {
            RoadFeatureType.SCHOOL_ZONE -> minOf(base, 1_000)
            RoadFeatureType.SPEED_CALMING -> minOf(base, 1_000)
            RoadFeatureType.LEVEL_CROSSING -> base
            RoadFeatureType.ROAD_HAZARD -> base
        }
    }

    fun voiceText(warning: RoadFeatureWarning): String {
        val distance = when {
            warning.distanceMeters >= 1000.0 ->
                "${String.format(java.util.Locale.US, "%.1f", warning.distanceMeters / 1000.0).replace('.', ',')} kilometre"
            else -> "${warning.distanceMeters.toInt().coerceAtLeast(0)} metre"
        }
        return when (warning.feature.type) {
            RoadFeatureType.SPEED_CALMING -> "$distance ileride hız tümseği veya trafik yavaşlatma noktası var."
            RoadFeatureType.SCHOOL_ZONE -> "$distance ileride okul bölgesi var. Dikkatli sürün."
            RoadFeatureType.LEVEL_CROSSING -> "$distance ileride hemzemin geçit var."
            RoadFeatureType.ROAD_HAZARD -> "$distance ileride yol tehlikesi bildirimi var."
        }
    }

    private fun routeProgressMeters(point: GeoPoint, route: List<GeoPoint>): Double? {
        if (route.size < 2) return null
        var cumulative = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        var bestProgress: Double? = null
        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            val segmentLength = a.distanceTo(b)
            if (segmentLength <= 0.0) continue
            val projection = projectOnSegment(point, a, b)
            if (projection.distanceMeters < bestDistance) {
                bestDistance = projection.distanceMeters
                bestProgress = cumulative + (segmentLength * projection.t)
            }
            cumulative += segmentLength
        }
        return bestProgress
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
        return SegmentProjection(t, kotlin.math.sqrt(dx * dx + dy * dy))
    }
}
