package com.example.haritalar.data.repository

import android.content.Context
import android.util.Log
import com.example.haritalar.data.cache.AverageSpeedZoneCache
import com.example.haritalar.data.network.AverageSpeedZoneService
import com.example.haritalar.model.AverageSpeedZoneDataState
import com.example.haritalar.model.AverageSpeedZoneFetchResult
import com.example.haritalar.model.AverageSpeedZoneRouteSummary
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.navigation.AverageSpeedZoneRoutePolicy

class AverageSpeedZoneRepository(
    context: Context,
    private val service: AverageSpeedZoneService = AverageSpeedZoneService(),
    private val cache: AverageSpeedZoneCache = AverageSpeedZoneCache(context)
) {
    companion object {
        private const val TAG = "AverageSpeedZoneRepo"
    }

    suspend fun getForRoute(route: List<GeoPoint>): AverageSpeedZoneRouteSummary {
        val signature = AverageSpeedZoneRoutePolicy.routeSignature(route)
        if (signature.isBlank()) {
            return AverageSpeedZoneRouteSummary(
                matches = emptyList(),
                dataState = AverageSpeedZoneDataState.UNAVAILABLE,
                source = "OpenStreetMap / Overpass"
            )
        }

        return when (val result = service.fetchForRoute(route)) {
            is AverageSpeedZoneFetchResult.Success -> {
                val matches = AverageSpeedZoneRoutePolicy.matchToRoute(result.zones, route)
                // Save only source-backed geometries that survived basic route matching.
                val matchedIds = matches.map { it.zone.id }.toSet()
                val matchedZones = result.zones.filter { it.id in matchedIds }
                cache.save(signature, matchedZones)

                AverageSpeedZoneRouteSummary(
                    matches = matches,
                    dataState = AverageSpeedZoneDataState.VERIFIED,
                    source = "OpenStreetMap / Overpass"
                )
            }

            is AverageSpeedZoneFetchResult.Error -> {
                val cachedZones = cache.load(signature)
                val matches = AverageSpeedZoneRoutePolicy.matchToRoute(cachedZones, route)
                if (matches.isNotEmpty()) {
                    Log.w(TAG, "Overpass mirrors failed; using ${matches.size} cached average-speed corridors")
                    AverageSpeedZoneRouteSummary(
                        matches = matches,
                        dataState = AverageSpeedZoneDataState.CACHED,
                        source = "OpenStreetMap (24 saatlik rota önbelleği)"
                    )
                } else {
                    Log.e(TAG, "Average-speed chain exhausted: ${result.message}")
                    AverageSpeedZoneRouteSummary(
                        matches = emptyList(),
                        dataState = AverageSpeedZoneDataState.UNAVAILABLE,
                        source = "OpenStreetMap / Overpass"
                    )
                }
            }
        }
    }
}
