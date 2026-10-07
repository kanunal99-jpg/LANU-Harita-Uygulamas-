package com.example.haritalar.data.repository

import android.content.Context
import android.util.Log
import com.example.haritalar.data.cache.RoadFeatureCache
import com.example.haritalar.data.network.RoadFeatureService
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RoadFeatureFetchResult
import com.example.haritalar.navigation.RoadFeatureRoutePolicy

class RoadFeatureRepository(
    context: Context,
    private val service: RoadFeatureService = RoadFeatureService(),
    private val cache: RoadFeatureCache = RoadFeatureCache(context)
) {
    companion object {
        private const val TAG = "RoadFeatureRepository"
    }

    suspend fun getForRoute(route: List<GeoPoint>): RoadFeatureFetchResult {
        val signature = RoadFeatureRoutePolicy.routeSignature(route)
        if (signature.isBlank()) {
            return RoadFeatureFetchResult.Error("Geçerli rota imzası üretilemedi", false)
        }

        return when (val result = service.fetchForRoute(route)) {
            is RoadFeatureFetchResult.Success -> {
                val filtered = RoadFeatureRoutePolicy.filterNearRoute(result.features, route)
                cache.save(signature, filtered)
                result.copy(features = filtered)
            }
            is RoadFeatureFetchResult.Error -> {
                val cached = RoadFeatureRoutePolicy.filterNearRoute(cache.load(signature), route)
                if (cached.isNotEmpty()) {
                    Log.w(TAG, "Road feature mirrors failed; using ${cached.size} cached features")
                    RoadFeatureFetchResult.Success(
                        features = cached,
                        fromCache = true,
                        endpointUsed = "persistent-last-known-good"
                    )
                } else {
                    result.copy(fallbackFeatures = emptyList())
                }
            }
        }
    }
}
