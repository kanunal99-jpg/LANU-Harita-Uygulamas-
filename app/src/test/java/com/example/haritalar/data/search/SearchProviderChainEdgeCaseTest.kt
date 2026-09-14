package com.example.haritalar.data.search

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SearchProviderChainEdgeCaseTest {
    private class EmptyProvider(override val name: String) : SearchProvider {
        override suspend fun search(query: String, focusPoint: GeoPoint?) = emptyList<com.example.haritalar.model.SearchResult>()
        override suspend fun reverseGeocode(point: GeoPoint): String? = null
    }

    private class ThrowingProvider(override val name: String) : SearchProvider {
        override suspend fun search(query: String, focusPoint: GeoPoint?): List<com.example.haritalar.model.SearchResult> = throw IOException("boom")
        override suspend fun reverseGeocode(point: GeoPoint): String? = throw IOException("boom")
    }

    @Test
    fun alternativeExceptionAfterPrimaryEmptyIsReported() = runBlocking {
        val chain = SearchProviderChain(
            primaryProvider = EmptyProvider("primary"),
            alternativeProvider = ThrowingProvider("alternative"),
            cacheProvider = CacheSearchProvider(searchHistoryDao = null)
        )

        assertTrue(chain.executeSearch("istanbul").let { it is SearchResponse.Error })
    }
}
