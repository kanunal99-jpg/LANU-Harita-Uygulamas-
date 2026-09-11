package com.example.haritalar.data.search

import androidx.collection.LruCache
import com.example.haritalar.data.db.SearchHistoryDao
import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * In-memory and local database cache provider for instant and offline fallback search.
 */
class CacheSearchProvider(
    private val searchHistoryDao: SearchHistoryDao? = null
) : SearchProvider {

    override val name: String = "Önbellek & Geçmiş"

    private val memoryCache = LruCache<String, List<SearchResult>>(50)
    private val reverseCache = LruCache<String, String>(50)

    fun put(query: String, results: List<SearchResult>) {
        if (query.isNotBlank() && results.isNotEmpty()) {
            val key = TurkishAddressHelper.normalizeTurkish(query)
            memoryCache.put(key, results)
        }
    }

    fun putReverse(point: GeoPoint, address: String) {
        val key = "${String.format("%.4f", point.latitude)}_${String.format("%.4f", point.longitude)}"
        reverseCache.put(key, address)
    }

    override suspend fun search(query: String, focusPoint: GeoPoint?): List<SearchResult> = withContext(Dispatchers.IO) {
        val normQuery = TurkishAddressHelper.normalizeTurkish(query)
        if (normQuery.length < 2) return@withContext emptyList()

        // 1. Check in-memory LRU cache
        val memHit = memoryCache.get(normQuery)
        if (!memHit.isNullOrEmpty()) {
            return@withContext memHit
        }

        // 2. Fallback to local Room search history
        if (searchHistoryDao != null) {
            try {
                val history = searchHistoryDao.getRecentSearches().firstOrNull() ?: emptyList()
                val matched = history.filter { item ->
                    TurkishAddressHelper.matchesQuery(normQuery, item.query) ||
                            TurkishAddressHelper.matchesQuery(normQuery, item.displayName)
                }

                if (matched.isNotEmpty()) {
                    return@withContext matched.map { item ->
                        SearchResult(
                            id = "hist_${item.id}",
                            name = item.query,
                            displayName = item.displayName,
                            shortAddress = item.displayName.split(",").take(2).joinToString(", "),
                            point = GeoPoint(item.latitude, item.longitude),
                            type = "history",
                            resultType = AddressResultType.PLACE,
                            provider = name,
                            confidence = 0.9f
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore DB read failure in cache layer
            }
        }

        emptyList()
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? {
        val key = "${String.format("%.4f", point.latitude)}_${String.format("%.4f", point.longitude)}"
        return reverseCache.get(key)
    }

    fun clear() {
        memoryCache.evictAll()
        reverseCache.evictAll()
    }
}
