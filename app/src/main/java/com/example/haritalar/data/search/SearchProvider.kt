package com.example.haritalar.data.search

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResult

/**
 * Common contract for geocoding and address search providers.
 */
interface SearchProvider {
    val name: String

    /**
     * Executes a search query with an optional geographic focus point.
     * Throws an exception on network/HTTP failures so the chain can invoke fallback providers.
     */
    suspend fun search(query: String, focusPoint: GeoPoint? = null): List<SearchResult>

    /**
     * Reverse geocodes a coordinate point into a readable Turkish address string.
     */
    suspend fun reverseGeocode(point: GeoPoint): String?
}
