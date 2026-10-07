package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResponse
import com.example.haritalar.model.SearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessSearchBehaviorTest {
    private class HandlerProvider(
        override val name: String,
        private val handler: (String) -> List<SearchResult>
    ) : SearchProvider {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String, focusPoint: GeoPoint?): List<SearchResult> {
            queries += query
            return handler(query)
        }

        override suspend fun reverseGeocode(point: GeoPoint): String? = null
    }

    private class EmptyProvider(override val name: String) : SearchProvider {
        override suspend fun search(query: String, focusPoint: GeoPoint?) = emptyList<SearchResult>()
        override suspend fun reverseGeocode(point: GeoPoint): String? = null
    }

    @Test
    fun globalDonukAliasMatchesVerifiedDirectory() {
        val matches = VerifiedPlaceDirectory.findMatches("global donuk gida")
        assertTrue(matches.any { it.name == "Global Donuk Gıda" })
        assertTrue(matches.any { it.district == "Sancaktepe" })
    }

    @Test
    fun frozenWholesaleQueriesGenerateBusinessVariants() {
        val generated = TurkishAddressHelper.generateSearchQueries("toptan donuk")
            .map(TurkishAddressHelper::normalizeTurkish)

        assertTrue(generated.any { "donuk gida" in it })
        assertTrue(generated.any { "toptan gida" in it || "gida toptancisi" in it })
        assertTrue(BusinessIntentClassifier.isBusinessIntent("toptan donuk"))
    }

    @Test
    fun verifiedGlobalDonukResultWinsOverWrongNameSearchHit() = runBlocking {
        val correctPoint = GeoPoint(41.005, 29.232)
        val wrongPoint = GeoPoint(41.020, 29.130)
        val primary = HandlerProvider("Fake Nominatim") { query ->
            if (query.contains("Melikşah", ignoreCase = true) ||
                query.contains("Sancaktepe", ignoreCase = true)
            ) {
                listOf(
                    SearchResult(
                        id = "address_candidate",
                        name = "Melikşah Sokak",
                        displayName = "Melikşah Sokak, Osmangazi, Sancaktepe, İstanbul",
                        shortAddress = "Osmangazi, Sancaktepe / İstanbul",
                        point = correctPoint,
                        resultType = AddressResultType.STREET,
                        provider = "Fake Nominatim"
                    )
                )
            } else {
                listOf(
                    SearchResult(
                        id = "wrong_dudullu",
                        name = "Donuk Gıda",
                        displayName = "Dudullu OSB, Ümraniye, İstanbul",
                        shortAddress = "Ümraniye / İstanbul",
                        point = wrongPoint,
                        resultType = AddressResultType.POI,
                        provider = "Fake Nominatim"
                    )
                )
            }
        }

        val chain = SearchProviderChain(
            primaryProvider = primary,
            alternativeProvider = EmptyProvider("Fake Photon"),
            cacheProvider = CacheSearchProvider(searchHistoryDao = null),
            businessProvider = EmptyProvider("Fake Business")
        )

        val response = chain.executeSearch("global donuk gida", GeoPoint(41.0, 29.2))
        assertTrue(response is SearchResponse.Success)
        val top = (response as SearchResponse.Success).results.first()
        assertEquals("Global Donuk Gıda", top.name)
        assertEquals(correctPoint, top.point)
        assertEquals("LANU Doğrulanmış", top.provider)
    }
}
