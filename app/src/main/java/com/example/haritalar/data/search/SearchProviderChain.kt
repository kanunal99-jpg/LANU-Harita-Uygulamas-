package com.example.haritalar.data.search

import androidx.collection.LruCache
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.HouseNumberStatus
import com.example.haritalar.model.SearchResponse
import com.example.haritalar.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Resilient multi-provider search chain:
 * Live typeahead provider (Komoot Photon)
 * -> Explicit committed fallback (Nominatim OSM)
 * -> Cache / Recent Searches
 * -> Safe Fallback / Error State
 *
 * Provides house-number verification, intelligent fallback to alternative
 * providers if building level isn't found in primary, deduplication, and
 * relevance ranking.
 */
class SearchProviderChain(
    val primaryProvider: SearchProvider = PhotonSearchProvider(),
    val alternativeProvider: SearchProvider = NominatimSearchProvider(),
    val cacheProvider: CacheSearchProvider = CacheSearchProvider(),
    val businessProvider: SearchProvider = OverpassBusinessSearchProvider()
) {
    private val committedForwardCache = LruCache<String, List<SearchResult>>(50)

    suspend fun executeSearch(
        query: String,
        focusPoint: GeoPoint? = null,
        allowAlternativeForwardGeocoder: Boolean = false
    ): SearchResponse = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return@withContext SearchResponse.Empty(query)
        }

        val committedCacheKey = TurkishAddressHelper.normalizeTurkish(trimmed)
        if (allowAlternativeForwardGeocoder) {
            committedForwardCache.get(committedCacheKey)?.let { cached ->
                return@withContext SearchResponse.Success(cached, "Önbellek • Kesin Arama")
            }
        }

        val parsedQuery = TurkishAddressHelper.parseAddressQuery(trimmed)
        val queryVariations = TurkishAddressHelper.generateSearchQueries(trimmed)
        val businessIntent = BusinessIntentClassifier.isBusinessIntent(trimmed)

        val collectedResults = mutableListOf<SearchResult>()
        var activeProviderName = primaryProvider.name
        var usedVerifiedDirectory = false
        var usedBusinessProvider = false

        val verifiedEntries = VerifiedPlaceDirectory.findMatches(trimmed)
        for (entry in verifiedEntries) {
            val resolved = resolveVerifiedPlace(entry, allowAlternativeForwardGeocoder)
            if (resolved != null) {
                collectedResults += resolved
                usedVerifiedDirectory = true
            }
        }
        var primaryExceptionOccurred = false
        var alternativeExceptionOccurred = false
        var hasVerifiedBuildingInPrimary = false

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

        val shouldQueryAlternative = allowAlternativeForwardGeocoder && (
                primaryExceptionOccurred ||
                collectedResults.isEmpty() ||
                businessIntent ||
                (parsedQuery.isBuildingLevelRequested && !hasVerifiedBuildingInPrimary)
            )

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

        if (allowAlternativeForwardGeocoder &&
            businessIntent && focusPoint != null && collectedResults.size < 12
        ) {
            try {
                val businessResults = businessProvider.search(trimmed, focusPoint)
                if (businessResults.isNotEmpty()) {
                    collectedResults.addAll(businessResults)
                    usedBusinessProvider = true
                }
            } catch (_: Exception) {
                // The address geocoders and cache remain valid fallbacks.
            }
        }

        if (usedVerifiedDirectory || usedBusinessProvider) {
            activeProviderName = when {
                usedVerifiedDirectory && !usedBusinessProvider -> "LANU Doğrulanmış"
                else -> "LANU Çoklu Kaynak"
            }
        }

        if (collectedResults.isNotEmpty()) {
            val deduplicated = deduplicateResults(collectedResults)
            val ranked = SearchRankingEvaluator.rankAndEvaluateResults(deduplicated, parsedQuery, focusPoint)
            cacheProvider.put(trimmed, ranked)
            if (allowAlternativeForwardGeocoder) {
                committedForwardCache.put(committedCacheKey, ranked)
            }
            return@withContext SearchResponse.Success(ranked, activeProviderName)
        }

        try {
            val cachedResults = cacheProvider.search(trimmed, focusPoint)
            if (cachedResults.isNotEmpty()) {
                val ranked = SearchRankingEvaluator.rankAndEvaluateResults(cachedResults, parsedQuery, focusPoint)
                return@withContext SearchResponse.Success(ranked, cacheProvider.name)
            }
        } catch (e: Exception) {
            // Ignore cache read failures; provider failures still determine the final state.
        }

        if (primaryExceptionOccurred || alternativeExceptionOccurred) {
            SearchResponse.Error(
                message = "Arama servisine şu anda ulaşılamıyor. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
                canRetry = true
            )
        } else {
            SearchResponse.Empty(query = trimmed)
        }
    }

    suspend fun reverseGeocode(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        val cached = cacheProvider.reverseGeocode(point)
        if (!cached.isNullOrBlank()) return@withContext cached

        try {
            val primaryResult = primaryProvider.reverseGeocode(point)
            if (!primaryResult.isNullOrBlank()) {
                cacheProvider.putReverse(point, primaryResult)
                return@withContext primaryResult
            }
        } catch (e: Exception) {
            // Fallback to alternative.
        }

        try {
            val altResult = alternativeProvider.reverseGeocode(point)
            if (!altResult.isNullOrBlank()) {
                cacheProvider.putReverse(point, altResult)
                return@withContext altResult
            }
        } catch (e: Exception) {
            // Keep reverse geocoding unavailable rather than fabricating an address.
        }

        null
    }

    private suspend fun resolveVerifiedPlace(
        entry: VerifiedPlaceEntry,
        allowAlternativeForwardGeocoder: Boolean
    ): SearchResult? {
        val candidates = mutableListOf<SearchResult>()
        try {
            candidates += primaryProvider.search(entry.address, null)
        } catch (_: Exception) {
            // Continue with alternative.
        }
        if (candidates.isEmpty() && allowAlternativeForwardGeocoder) {
            try {
                candidates += alternativeProvider.search(entry.address, null)
            } catch (_: Exception) {
                return null
            }
        }

        val expectedTokens = listOf(entry.street, entry.district, entry.province)
            .map { TurkishAddressHelper.normalizeTurkish(it).substringBefore(" ") }
            .filter { it.length >= 4 }
        val best = candidates
            .map { candidate ->
                val haystack = TurkishAddressHelper.normalizeTurkish(
                    candidate.displayName + " " + candidate.shortAddress
                )
                expectedTokens.count { haystack.contains(it) } to candidate
            }
            .maxByOrNull { it.first }
            ?.takeIf { it.first >= 2 }
            ?.second
            ?: return null

        return best.copy(
            id = entry.id,
            name = entry.name,
            displayName = entry.address,
            shortAddress = entry.address,
            resultType = com.example.haritalar.model.AddressResultType.POI,
            provider = "LANU Doğrulanmış",
            confidence = 1.0f
        )
    }

    private fun deduplicateResults(results: List<SearchResult>): List<SearchResult> {
        val unique = mutableListOf<SearchResult>()
        for (item in results) {
            val isDuplicate = unique.any { existing ->
                val closeCoordinates = abs(existing.point.latitude - item.point.latitude) < 0.0002 &&
                        abs(existing.point.longitude - item.point.longitude) < 0.0002
                val sameName = TurkishAddressHelper.normalizeTurkish(existing.name) ==
                    TurkishAddressHelper.normalizeTurkish(item.name)
                val sameNamedNearbyPlace = sameName && existing.point.distanceTo(item.point) <= 150.0
                closeCoordinates || sameNamedNearbyPlace
            }
            if (!isDuplicate) {
                unique.add(item)
            }
        }
        return unique
    }
}
