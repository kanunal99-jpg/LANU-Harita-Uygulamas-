package com.example.haritalar.data.search

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResponse
import com.example.haritalar.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Resilient search controller executing the provider chain:
 * Primary Provider (Nominatim OSM)
 * -> Alternative Provider (Komoot Photon)
 * -> Cache / Recent Searches
 * -> Safe Empty / Error State
 */
class SearchProviderChain(
    val primaryProvider: SearchProvider = NominatimSearchProvider(),
    val alternativeProvider: SearchProvider = PhotonSearchProvider(),
    val cacheProvider: CacheSearchProvider = CacheSearchProvider()
) {

    suspend fun executeSearch(query: String, focusPoint: GeoPoint? = null): SearchResponse = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return@withContext SearchResponse.Empty(query)
        }

        val queryVariations = TurkishAddressHelper.generateSearchQueries(trimmed)
        var networkExceptionOccurred = false
        var lastErrorMessage: String? = null

        // 1. Try Primary Provider (Nominatim) with generated query variations
        for (q in queryVariations) {
            try {
                val results = primaryProvider.search(q, focusPoint)
                if (results.isNotEmpty()) {
                    cacheProvider.put(trimmed, results)
                    return@withContext SearchResponse.Success(results, primaryProvider.name)
                }
            } catch (e: Exception) {
                networkExceptionOccurred = true
                lastErrorMessage = e.message
                // Log and continue to alternative provider
                break
            }
        }

        // 2. Fallback to Alternative Provider (Photon)
        for (q in queryVariations) {
            try {
                val results = alternativeProvider.search(q, focusPoint)
                if (results.isNotEmpty()) {
                    cacheProvider.put(trimmed, results)
                    return@withContext SearchResponse.Success(results, alternativeProvider.name)
                }
            } catch (e: Exception) {
                networkExceptionOccurred = true
                lastErrorMessage = e.message
                break
            }
        }

        // 3. Fallback to Cache / Recent Search results
        try {
            val cachedResults = cacheProvider.search(trimmed, focusPoint)
            if (cachedResults.isNotEmpty()) {
                return@withContext SearchResponse.Success(cachedResults, cacheProvider.name)
            }
        } catch (e: Exception) {
            // Ignore cache read failures
        }

        // 4. Determine final response state: Error vs Empty
        if (networkExceptionOccurred) {
            SearchResponse.Error(
                message = "Arama servisine şu anda ulaşılamıyor. Lütfen internet bağlantınızı kontrol edip tekrar deneyin."
            )
        } else {
            SearchResponse.Empty(query = trimmed)
        }
    }

    suspend fun reverseGeocode(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        // 1. Check cache
        val cached = cacheProvider.reverseGeocode(point)
        if (!cached.isNullOrBlank()) return@withContext cached

        // 2. Primary provider
        try {
            val primaryResult = primaryProvider.reverseGeocode(point)
            if (!primaryResult.isNullOrBlank()) {
                cacheProvider.putReverse(point, primaryResult)
                return@withContext primaryResult
            }
        } catch (e: Exception) {
            // Fallback to alternative
        }

        // 3. Alternative provider
        try {
            val altResult = alternativeProvider.reverseGeocode(point)
            if (!altResult.isNullOrBlank()) {
                cacheProvider.putReverse(point, altResult)
                return@withContext altResult
            }
        } catch (e: Exception) {
            null
        }

        null
    }
}
