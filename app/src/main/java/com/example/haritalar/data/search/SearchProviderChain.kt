package com.example.haritalar.data.search

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.HouseNumberStatus
import com.example.haritalar.model.SearchResponse
import com.example.haritalar.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Resilient multi-provider search chain:
 * Primary Provider (Nominatim OSM)
 * -> Alternative Provider (Komoot Photon)
 * -> Cache / Recent Searches
 * -> Safe Fallback / Error State
 *
 * Provides house-number verification, intelligent fallback to alternative
 * providers if building level isn't found in primary, deduplication, and
 * relevance ranking.
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

        val parsedQuery = TurkishAddressHelper.parseAddressQuery(trimmed)
        val queryVariations = TurkishAddressHelper.generateSearchQueries(trimmed)

        val collectedResults = mutableListOf<SearchResult>()
        var activeProviderName = primaryProvider.name
        var primaryExceptionOccurred = false
        var alternativeExceptionOccurred = false
        var hasVerifiedBuildingInPrimary = false

        // 1. Try Primary Provider (Nominatim) with generated query variations
        for (q in queryVariations) {
            try {
                val results = primaryProvider.search(q, focusPoint)
                if (results.isNotEmpty()) {
                    collectedResults.addAll(results)
                    if (parsedQuery.isBuildingLevelRequested) {
                        hasVerifiedBuildingInPrimary = results.any { res ->
                            !res.addressDetails?.houseNumber.isNullOrBlank() &&
                            TurkishAddressHelper.isHouseNumberMatch(parsedQuery.houseNumber, res.addressDetails?.houseNumber)
                        }
                    }
                    break
                }
            } catch (e: Exception) {
                primaryExceptionOccurred = true
                break
            }
        }

        // 2. Query Alternative Provider (Photon) if:
        // - Primary failed/errored, OR
        // - Primary returned no results, OR
        // - User requested a building number AND primary did not return a verified building
        val shouldQueryAlternative = primaryExceptionOccurred ||
                collectedResults.isEmpty() ||
                (parsedQuery.isBuildingLevelRequested && !hasVerifiedBuildingInPrimary)

        if (shouldQueryAlternative) {
            for (q in queryVariations) {
                try {
                    val altResults = alternativeProvider.search(q, focusPoint)
                    if (altResults.isNotEmpty()) {
                        val hasVerifiedInAlt = parsedQuery.isBuildingLevelRequested && altResults.any { res ->
                            !res.addressDetails?.houseNumber.isNullOrBlank() &&
                            TurkishAddressHelper.isHouseNumberMatch(parsedQuery.houseNumber, res.addressDetails?.houseNumber)
                        }
                        if (hasVerifiedInAlt || collectedResults.isEmpty()) {
                            activeProviderName = alternativeProvider.name
                        }
                        collectedResults.addAll(altResults)
                        break
                    }
                } catch (e: Exception) {
                    alternativeExceptionOccurred = true
                    break
                }
            }
        }

        // 3. If online results were found, deduplicate, rank, and cache
        if (collectedResults.isNotEmpty()) {
            val deduplicated = deduplicateResults(collectedResults)
            val ranked = SearchRankingEvaluator.rankAndEvaluateResults(deduplicated, parsedQuery, focusPoint)
            cacheProvider.put(trimmed, ranked)
            return@withContext SearchResponse.Success(ranked, activeProviderName)
        }

        // 4. Fallback to Local Cache / Recent Search results
        try {
            val cachedResults = cacheProvider.search(trimmed, focusPoint)
            if (cachedResults.isNotEmpty()) {
                val ranked = SearchRankingEvaluator.rankAndEvaluateResults(cachedResults, parsedQuery, focusPoint)
                return@withContext SearchResponse.Success(ranked, cacheProvider.name)
            }
        } catch (e: Exception) {
            // Ignore cache read failures
        }

        // 5. Determine final response state: Error vs Empty
        // Clear separation between network/provider failure and "no results found"
        if (primaryExceptionOccurred && (alternativeExceptionOccurred || collectedResults.isEmpty())) {
            SearchResponse.Error(
                message = "Arama servisine şu anda ulaşılamıyor. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
                canRetry = true
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

    private fun deduplicateResults(results: List<SearchResult>): List<SearchResult> {
        val unique = mutableListOf<SearchResult>()
        for (item in results) {
            val isDuplicate = unique.any { existing ->
                val closeCoordinates = abs(existing.point.latitude - item.point.latitude) < 0.0002 &&
                        abs(existing.point.longitude - item.point.longitude) < 0.0002
                val sameName = existing.name.equals(item.name, ignoreCase = true)
                closeCoordinates || sameName
            }
            if (!isDuplicate) {
                unique.add(item)
            }
        }
        return unique
    }
}
