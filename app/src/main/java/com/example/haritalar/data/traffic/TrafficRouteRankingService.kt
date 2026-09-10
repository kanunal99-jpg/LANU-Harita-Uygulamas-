package com.example.haritalar.data.traffic

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TrafficRouteRankingService(
    val providerChain: TrafficProviderChain
) {
    suspend fun rankAndApplyTraffic(
        routes: List<RouteOption>,
        generationId: Long
    ): Pair<List<RouteOption>, Map<String, Pair<TrafficStatus, List<TrafficSegment>>>> = withContext(Dispatchers.IO) {
        if (routes.isEmpty()) return@withContext Pair(emptyList(), emptyMap())

        val statusMap = mutableMapOf<String, Pair<TrafficStatus, List<TrafficSegment>>>()
        val updatedRoutes = mutableListOf<RouteOption>()

        for (route in routes) {
            // Guard: discard if generation mismatch
            if (route.generationId != generationId) continue

            val samplePoints = sampleRoutePoints(route.geometry, maxSamples = 8)
            val rawSegments = mutableListOf<TrafficSegment>()

            for (pt in samplePoints) {
                val seg = providerChain.getTrafficSegment(pt)
                if (seg != null) {
                    rawSegments.add(seg)
                }
            }

            val matchedSegments = TrafficRouteMatcher.matchSegmentsToRoute(route.geometry, rawSegments)
            val trafficStatus = TrafficRouteCostModel.calculateTrafficStatus(
                matchedSegments,
                hasProvider = providerChain.hasAvailableProvider()
            )

            val updatedRoute = route.copy(
                trafficDelaySeconds = trafficStatus.delaySeconds
            )
            updatedRoutes.add(updatedRoute)
            statusMap[route.routeId] = Pair(trafficStatus, matchedSegments)
        }

        // Rank routes: primary by total duration (durationSeconds + trafficDelaySeconds)
        val ranked = updatedRoutes.sortedBy { it.totalDurationSeconds }
        Pair(ranked, statusMap)
    }

    private fun sampleRoutePoints(geometry: List<GeoPoint>, maxSamples: Int): List<GeoPoint> {
        if (geometry.size <= maxSamples) return geometry
        val step = geometry.size / maxSamples
        val samples = mutableListOf<GeoPoint>()
        for (i in 0 until maxSamples) {
            val idx = Math.min(i * step, geometry.size - 1)
            samples.add(geometry[idx])
        }
        return samples
    }
}

class TrafficRefreshCoordinator(
    private val rankingService: TrafficRouteRankingService,
    private val minRefreshIntervalMs: Long = 60_000L // 60 seconds
) {
    private var lastRefreshTimestamp: Long = 0L
    private val mutex = Mutex()
    private var activeGenerationId: Long = 0L

    fun resetGeneration(newGenerationId: Long) {
        activeGenerationId = newGenerationId
        lastRefreshTimestamp = 0L
    }

    suspend fun requestRefresh(
        routes: List<RouteOption>,
        generationId: Long,
        force: Boolean = false
    ): Pair<List<RouteOption>, Map<String, Pair<TrafficStatus, List<TrafficSegment>>>>? {
        val now = System.currentTimeMillis()
        if (!force && (now - lastRefreshTimestamp < minRefreshIntervalMs)) {
            // Refresh throttled
            return null
        }

        return mutex.withLock {
            // Check stale generation before processing
            if (generationId != activeGenerationId) {
                return@withLock null
            }

            val result = rankingService.rankAndApplyTraffic(routes, generationId)

            // Verify generation is STILL matching after async network
            if (generationId != activeGenerationId) {
                return@withLock null
            }

            lastRefreshTimestamp = System.currentTimeMillis()
            result
        }
    }
}
