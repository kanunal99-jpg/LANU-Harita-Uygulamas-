package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResponse
import com.example.haritalar.model.SearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SearchProviderChainTest {

    private lateinit var fakePrimaryProvider: FakeSearchProvider
    private lateinit var fakeAlternativeProvider: FakeSearchProvider
    private lateinit var fakeCacheProvider: CacheSearchProvider
    private lateinit var chain: SearchProviderChain

    class FakeSearchProvider(
        override val name: String,
        var shouldThrow: Boolean = false,
        var returnResults: List<SearchResult> = emptyList(),
        var returnReverseAddress: String? = null
    ) : SearchProvider {
        var searchCallCount = 0

        override suspend fun search(query: String, focusPoint: GeoPoint?): List<SearchResult> {
            searchCallCount++
            if (shouldThrow) {
                throw IOException("Network timeout or connection error on $name")
            }
            return returnResults
        }

        override suspend fun reverseGeocode(point: GeoPoint): String? {
            if (shouldThrow) {
                throw IOException("Reverse geocode error on $name")
            }
            return returnReverseAddress
        }
    }

    @Before
    fun setUp() {
        fakePrimaryProvider = FakeSearchProvider("FakeNominatim")
        fakeAlternativeProvider = FakeSearchProvider("FakePhoton")
        fakeCacheProvider = CacheSearchProvider(searchHistoryDao = null)
        chain = SearchProviderChain(
            primaryProvider = fakePrimaryProvider,
            alternativeProvider = fakeAlternativeProvider,
            cacheProvider = fakeCacheProvider
        )
    }

    @Test
    fun testPrimaryProviderSuccess() = runBlocking {
        val sampleResult = SearchResult(
            id = "osm_1",
            name = "Kadıköy Rıhtım",
            displayName = "Rıhtım Caddesi, Kadıköy, İstanbul",
            shortAddress = "Kadıköy, İstanbul",
            point = GeoPoint(40.9912, 29.0216),
            resultType = AddressResultType.STREET,
            provider = fakePrimaryProvider.name
        )
        fakePrimaryProvider.returnResults = listOf(sampleResult)

        val response = chain.executeSearch("kadıköy rıhtım")

        assertTrue(response is SearchResponse.Success)
        val success = response as SearchResponse.Success
        assertEquals("FakeNominatim", success.provider)
        assertEquals(1, success.results.size)
        assertEquals("Kadıköy Rıhtım", success.results.first().name)
        assertEquals(0, fakeAlternativeProvider.searchCallCount)
    }

    @Test
    fun sameNameResultsFarApartAreNotDeduplicated() = runBlocking {
        fakePrimaryProvider.returnResults = listOf(
            SearchResult(
                id = "opet_istanbul",
                name = "Opet",
                displayName = "Opet, İstanbul",
                point = GeoPoint(41.01, 29.01),
                resultType = AddressResultType.POI,
                provider = fakePrimaryProvider.name
            ),
            SearchResult(
                id = "opet_kocaeli",
                name = "Opet",
                displayName = "Opet, Kocaeli",
                point = GeoPoint(40.76, 29.92),
                resultType = AddressResultType.POI,
                provider = fakePrimaryProvider.name
            )
        )

        val response = chain.executeSearch("opet")

        assertTrue(response is SearchResponse.Success)
        assertEquals(2, (response as SearchResponse.Success).results.size)
    }

    @Test
    fun liveTypeaheadDoesNotCallCommittedOnlyAlternativeProvider() = runBlocking {
        fakePrimaryProvider.returnResults = emptyList()
        fakeAlternativeProvider.returnResults = listOf(
            SearchResult(
                id = "restricted_1",
                name = "Alternative",
                displayName = "Alternative",
                point = GeoPoint(41.0, 29.0)
            )
        )

        val response = chain.executeSearch("istanbul")

        assertTrue(response is SearchResponse.Empty)
        assertEquals(0, fakeAlternativeProvider.searchCallCount)
    }

    @Test
    fun testFallbackToAlternativeProviderWhenPrimaryFails() = runBlocking {
        fakePrimaryProvider.shouldThrow = true

        val photonResult = SearchResult(
            id = "photon_1",
            name = "Atatürk Bulvarı No:14",
            displayName = "Atatürk Bulvarı No:14, Çankaya, Ankara",
            shortAddress = "Kızılay, Çankaya / Ankara",
            point = GeoPoint(39.9247, 32.8547),
            resultType = AddressResultType.ADDRESS,
            provider = fakeAlternativeProvider.name
        )
        fakeAlternativeProvider.returnResults = listOf(photonResult)

        val response = chain.executeSearch(
            "Ankara Çankaya Atatürk Bulvarı",
            allowAlternativeForwardGeocoder = true
        )

        assertTrue(response is SearchResponse.Success)
        val success = response as SearchResponse.Success
        assertEquals("FakePhoton", success.provider)
        assertEquals(1, success.results.size)
        assertEquals("Atatürk Bulvarı No:14", success.results.first().name)
    }

    @Test
    fun testFallbackToCacheWhenBothExternalProvidersFail() = runBlocking {
        val cachedResult = SearchResult(
            id = "cache_1",
            name = "Kadıköy",
            displayName = "Kadıköy, İstanbul",
            shortAddress = "İstanbul",
            point = GeoPoint(40.9912, 29.0216),
            resultType = AddressResultType.DISTRICT,
            provider = fakeCacheProvider.name
        )
        fakeCacheProvider.put("kadikoy", listOf(cachedResult))

        fakePrimaryProvider.shouldThrow = true
        fakeAlternativeProvider.shouldThrow = true

        val response = chain.executeSearch("kadıköy", allowAlternativeForwardGeocoder = true)

        assertTrue(response is SearchResponse.Success)
        val success = response as SearchResponse.Success
        assertEquals("Önbellek & Geçmiş", success.provider)
        assertEquals(1, success.results.size)
    }

    @Test
    fun testNetworkErrorStateWhenAllExternalProvidersFailAndNoCache() = runBlocking {
        fakePrimaryProvider.shouldThrow = true
        fakeAlternativeProvider.shouldThrow = true

        val response = chain.executeSearch(
            "Herhangi bir adres",
            allowAlternativeForwardGeocoder = true
        )

        assertTrue(response is SearchResponse.Error)
        val error = response as SearchResponse.Error
        assertTrue(error.message.contains("ulaşılamıyor"))
        assertTrue(error.canRetry)
    }

    @Test
    fun testEmptyStateWhenNoResultsFoundInAnyProvider() = runBlocking {
        fakePrimaryProvider.returnResults = emptyList()
        fakeAlternativeProvider.returnResults = emptyList()

        val response = chain.executeSearch("asdfxyz123nonsense")

        assertTrue(response is SearchResponse.Empty)
        val empty = response as SearchResponse.Empty
        assertEquals("asdfxyz123nonsense", empty.query)
    }

    @Test
    fun testShortOrBlankQueryReturnsEmptyDirectly() = runBlocking {
        val response = chain.executeSearch("a")
        assertTrue(response is SearchResponse.Empty)
        assertEquals(0, fakePrimaryProvider.searchCallCount)
        assertEquals(0, fakeAlternativeProvider.searchCallCount)
    }

    @Test
    fun testReverseGeocodingFallback() = runBlocking {
        val testPoint = GeoPoint(41.0082, 28.9784)
        fakePrimaryProvider.shouldThrow = true
        fakeAlternativeProvider.returnReverseAddress = "Sultanahmet Meydanı, Fatih, İstanbul"

        val address = chain.reverseGeocode(testPoint)
        assertEquals("Sultanahmet Meydanı, Fatih, İstanbul", address)

        // Second call should hit the cache without calling alternative provider again
        val cachedAddress = chain.reverseGeocode(testPoint)
        assertEquals("Sultanahmet Meydanı, Fatih, İstanbul", cachedAddress)
    }
}
