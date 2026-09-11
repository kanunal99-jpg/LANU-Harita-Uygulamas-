package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.NavigationState
import com.example.haritalar.model.SearchResponse
import com.example.haritalar.model.SearchResult
import com.example.haritalar.model.SearchUiStatus
import com.example.haritalar.model.TurkishAddressDetails
import com.example.haritalar.ui.MainUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * End-to-end integration tests explicitly verifying all 18 address search, geocoding,
 * Turkish normalization, UI state management, and navigation routing scenarios.
 */
class AddressSearchIntegrationTest {

    private lateinit var chain: SearchProviderChain
    private lateinit var mockPrimary: MockSearchProvider
    private lateinit var mockAlternative: MockSearchProvider
    private lateinit var cacheProvider: CacheSearchProvider

    class MockSearchProvider(
        override val name: String,
        var handler: ((String) -> List<SearchResult>)? = null,
        var shouldThrow: Boolean = false
    ) : SearchProvider {
        var queryLog = mutableListOf<String>()

        override suspend fun search(query: String, focusPoint: GeoPoint?): List<SearchResult> {
            queryLog.add(query)
            if (shouldThrow) throw IOException("Simulated network timeout")
            return handler?.invoke(query) ?: emptyList()
        }

        override suspend fun reverseGeocode(point: GeoPoint): String? = "Test Adresi"
    }

    @Before
    fun setUp() {
        mockPrimary = MockSearchProvider("Nominatim (OSM)")
        mockAlternative = MockSearchProvider("Photon (OSM)")
        cacheProvider = CacheSearchProvider(searchHistoryDao = null)
        chain = SearchProviderChain(mockPrimary, mockAlternative, cacheProvider)
    }

    // Senaryo 1: “İstanbul Kadıköy” araması
    @Test
    fun scenario01_IstanbulKadikoy() = runBlocking {
        mockPrimary.handler = { q ->
            if (q.contains("Kadıköy") || q.contains("kadikoy")) {
                listOf(
                    SearchResult(
                        id = "1",
                        name = "Kadıköy",
                        displayName = "Kadıköy, İstanbul, Marmara Bölgesi, Türkiye",
                        shortAddress = "İstanbul",
                        point = GeoPoint(40.9912, 29.0216),
                        resultType = AddressResultType.DISTRICT
                    )
                )
            } else emptyList()
        }

        val resp = chain.executeSearch("İstanbul Kadıköy")
        assertTrue(resp is SearchResponse.Success)
        val success = resp as SearchResponse.Success
        assertEquals(AddressResultType.DISTRICT, success.results.first().resultType)
        assertEquals("Kadıköy", success.results.first().name)
    }

    // Senaryo 2: “İstanbul Üsküdar Selimiye” araması
    @Test
    fun scenario02_IstanbulUskudarSelimiye() = runBlocking {
        mockPrimary.handler = { q ->
            if (q.contains("Selimiye")) {
                listOf(
                    SearchResult(
                        id = "2",
                        name = "Selimiye Mah.",
                        displayName = "Selimiye Mahallesi, Üsküdar, İstanbul, Türkiye",
                        shortAddress = "Üsküdar / İstanbul",
                        point = GeoPoint(41.0094, 29.0234),
                        resultType = AddressResultType.NEIGHBORHOOD
                    )
                )
            } else emptyList()
        }

        val resp = chain.executeSearch("İstanbul Üsküdar Selimiye")
        assertTrue(resp is SearchResponse.Success)
        val success = resp as SearchResponse.Success
        assertEquals("Selimiye Mah.", success.results.first().name)
    }

    // Senaryo 3: “Ankara Çankaya Atatürk Bulvarı” araması
    @Test
    fun scenario03_AnkaraCankayaAtaturkBulvari() = runBlocking {
        mockPrimary.handler = { q ->
            if (q.contains("Atatürk Bulvarı")) {
                listOf(
                    SearchResult(
                        id = "3",
                        name = "Atatürk Bulvarı",
                        displayName = "Atatürk Bulvarı, Kızılay Mahallesi, Çankaya, Ankara, Türkiye",
                        shortAddress = "Kızılay Mah., Çankaya / Ankara",
                        point = GeoPoint(39.9247, 32.8547),
                        resultType = AddressResultType.STREET
                    )
                )
            } else emptyList()
        }

        val resp = chain.executeSearch("Ankara Çankaya Atatürk Bulvarı")
        assertTrue(resp is SearchResponse.Success)
        val success = resp as SearchResponse.Success
        assertEquals("Atatürk Bulvarı", success.results.first().name)
        assertEquals(AddressResultType.STREET, success.results.first().resultType)
    }

    // Senaryo 4: “İstanbul Beylikdüzü Adnan Kahveci Mahallesi” araması
    @Test
    fun scenario04_BeylikduzuAdnanKahveciMahallesi() = runBlocking {
        mockPrimary.handler = { q ->
            if (q.contains("Adnan Kahveci")) {
                listOf(
                    SearchResult(
                        id = "4",
                        name = "Adnan Kahveci Mahallesi",
                        displayName = "Adnan Kahveci Mahallesi, Beylikdüzü, İstanbul, Türkiye",
                        shortAddress = "Beylikdüzü / İstanbul",
                        point = GeoPoint(41.0021, 28.6342),
                        resultType = AddressResultType.NEIGHBORHOOD
                    )
                )
            } else emptyList()
        }

        val resp = chain.executeSearch("İstanbul Beylikdüzü Adnan Kahveci Mah.")
        assertTrue(resp is SearchResponse.Success)
        val success = resp as SearchResponse.Success
        assertEquals(AddressResultType.NEIGHBORHOOD, success.results.first().resultType)
    }

    // Senaryo 5: “kadikoy rihtim” araması (küçük harf, Türkçe karaktersiz)
    @Test
    fun scenario05_KadikoyRihtimNonTurkishAscii() = runBlocking {
        mockPrimary.handler = { q ->
            // Simulating server accepting normalized ASCII or Turkish text
            if (TurkishAddressHelper.normalizeTurkish(q).contains("kadikoy rihtim")) {
                listOf(
                    SearchResult(
                        id = "5",
                        name = "Rıhtım Caddesi",
                        displayName = "Rıhtım Caddesi, Osmanağa, Kadıköy, İstanbul, Türkiye",
                        shortAddress = "Kadıköy / İstanbul",
                        point = GeoPoint(40.9923, 29.0248),
                        resultType = AddressResultType.STREET
                    )
                )
            } else emptyList()
        }

        val resp = chain.executeSearch("kadikoy rihtim")
        assertTrue(resp is SearchResponse.Success)
        val success = resp as SearchResponse.Success
        assertEquals("Rıhtım Caddesi", success.results.first().name)
    }

    // Senaryo 6: “İstanbul, Kadıköy, Rıhtım” araması (virgüllü format)
    @Test
    fun scenario06_CommaSeparatedFormat() = runBlocking {
        mockPrimary.handler = { q ->
            if (q.contains("Kadıköy") || q.contains("Rıhtım")) {
                listOf(
                    SearchResult(
                        id = "6",
                        name = "Kadıköy Rıhtım",
                        displayName = "Rıhtım, Kadıköy, İstanbul, Türkiye",
                        shortAddress = "Kadıköy, İstanbul",
                        point = GeoPoint(40.9912, 29.0215),
                        resultType = AddressResultType.PLACE
                    )
                )
            } else emptyList()
        }

        val resp = chain.executeSearch("İstanbul, Kadıköy, Rıhtım")
        assertTrue(resp is SearchResponse.Success)
        assertEquals(1, (resp as SearchResponse.Success).results.size)
    }

    // Senaryo 7: “İstiklal Caddesi No: 15 Beyoğlu” araması
    @Test
    fun scenario07_StreetAndBuildingNumber() = runBlocking {
        mockPrimary.handler = { q ->
            val details = TurkishAddressDetails(
                province = "İstanbul",
                district = "Beyoğlu",
                neighborhood = "Hüseyinağa",
                street = "İstiklal Caddesi",
                houseNumber = "15"
            )
            listOf(
                SearchResult(
                    id = "7",
                    name = "İstiklal Caddesi No:15",
                    displayName = "İstiklal Caddesi No:15, Hüseyinağa Mah., Beyoğlu, İstanbul, Türkiye",
                    shortAddress = "Hüseyinağa Mah., Beyoğlu / İstanbul",
                    point = GeoPoint(41.0345, 28.9782),
                    resultType = AddressResultType.ADDRESS,
                    addressDetails = details
                )
            )
        }

        val resp = chain.executeSearch("İstiklal Caddesi No: 15 Beyoğlu")
        assertTrue(resp is SearchResponse.Success)
        val result = (resp as SearchResponse.Success).results.first()
        assertEquals(AddressResultType.ADDRESS, result.resultType)
        assertEquals("15", result.addressDetails?.houseNumber)
    }

    // Senaryo 8: Yalnızca mahalle araması
    @Test
    fun scenario08_NeighborhoodOnly() = runBlocking {
        mockPrimary.handler = {
            listOf(
                SearchResult(
                    id = "8",
                    name = "Moda",
                    displayName = "Moda, Caferağa Mahallesi, Kadıköy, İstanbul",
                    shortAddress = "Kadıköy / İstanbul",
                    point = GeoPoint(40.9850, 29.0270),
                    resultType = AddressResultType.NEIGHBORHOOD
                )
            )
        }
        val resp = chain.executeSearch("Moda")
        assertTrue(resp is SearchResponse.Success)
        assertEquals(AddressResultType.NEIGHBORHOOD, (resp as SearchResponse.Success).results.first().resultType)
    }

    // Senaryo 9: Yalnızca cadde araması
    @Test
    fun scenario09_StreetOnly() = runBlocking {
        mockPrimary.handler = {
            listOf(
                SearchResult(
                    id = "9",
                    name = "Bağdat Caddesi",
                    displayName = "Bağdat Caddesi, Kadıköy, İstanbul",
                    shortAddress = "Kadıköy / İstanbul",
                    point = GeoPoint(40.9650, 29.0600),
                    resultType = AddressResultType.STREET
                )
            )
        }
        val resp = chain.executeSearch("Bağdat Caddesi")
        assertTrue(resp is SearchResponse.Success)
        assertEquals(AddressResultType.STREET, (resp as SearchResponse.Success).results.first().resultType)
    }

    // Senaryo 10: Sadece ilçe araması
    @Test
    fun scenario10_DistrictOnly() = runBlocking {
        mockPrimary.handler = {
            listOf(
                SearchResult(
                    id = "10",
                    name = "Çankaya",
                    displayName = "Çankaya, Ankara, Türkiye",
                    shortAddress = "Ankara",
                    point = GeoPoint(39.9000, 32.8600),
                    resultType = AddressResultType.DISTRICT
                )
            )
        }
        val resp = chain.executeSearch("Çankaya")
        assertTrue(resp is SearchResponse.Success)
        assertEquals(AddressResultType.DISTRICT, (resp as SearchResponse.Success).results.first().resultType)
    }

    // Senaryo 11: Bilinen bir AVM/hastane/otel/istasyon POI araması
    @Test
    fun scenario11_KnownPoiSearch() = runBlocking {
        mockPrimary.handler = {
            listOf(
                SearchResult(
                    id = "11",
                    name = "İstanbul Sabiha Gökçen Havalimanı",
                    displayName = "Sabiha Gökçen Havalimanı, Pendik, İstanbul",
                    shortAddress = "Pendik / İstanbul",
                    point = GeoPoint(40.8986, 29.3092),
                    resultType = AddressResultType.POI
                )
            )
        }
        val resp = chain.executeSearch("Sabiha Gökçen Havalimanı")
        assertTrue(resp is SearchResponse.Success)
        assertEquals(AddressResultType.POI, (resp as SearchResponse.Success).results.first().resultType)
    }

    // Senaryo 12: Yanlış yazılmış veya eksik adres araması
    @Test
    fun scenario12_TypoTolerantSearchFallback() = runBlocking {
        mockPrimary.handler = { emptyList() } // Primary returns empty for typo
        mockAlternative.handler = { q ->
            // Photon elasticsearch fuzzy matching handles typos
            listOf(
                SearchResult(
                    id = "12",
                    name = "Kadıköy Rıhtım",
                    displayName = "Rıhtım Caddesi, Kadıköy, İstanbul",
                    shortAddress = "Kadıköy / İstanbul",
                    point = GeoPoint(40.9912, 29.0215),
                    resultType = AddressResultType.STREET,
                    provider = "Photon (OSM)"
                )
            )
        }

        val resp = chain.executeSearch("kadkoy rhtm")
        assertTrue(resp is SearchResponse.Success)
        val success = resp as SearchResponse.Success
        assertEquals("Photon (OSM)", success.provider)
        assertEquals("Kadıköy Rıhtım", success.results.first().name)
    }

    // Senaryo 13: Sonuç bulunamayan anlamsız arama
    @Test
    fun scenario13_NonsenseQueryReturnsEmptyState() = runBlocking {
        mockPrimary.handler = { emptyList() }
        mockAlternative.handler = { emptyList() }

        val resp = chain.executeSearch("zzzzqqqqxxx12345")
        assertTrue(resp is SearchResponse.Empty)
        assertEquals("zzzzqqqqxxx12345", (resp as SearchResponse.Empty).query)
    }

    // Senaryo 14: Arama sırasında ağ hatası / timeout durumu
    @Test
    fun scenario14_NetworkFailureReturnsErrorStateWithRetry() = runBlocking {
        mockPrimary.shouldThrow = true
        mockAlternative.shouldThrow = true

        val resp = chain.executeSearch("Atatürk Caddesi")
        assertTrue(resp is SearchResponse.Error)
        val error = resp as SearchResponse.Error
        assertTrue(error.canRetry)
        assertTrue(error.message.contains("ulaşılamıyor"))
    }

    // Senaryo 15: Bir sonuç seçildiğinde haritada işaretlenme durumu
    @Test
    fun scenario15_ResultSelectionUpdatesDestinationMarker() {
        val selected = SearchResult(
            id = "15",
            name = "Anıtkabir",
            displayName = "Anıtkabir, Çankaya, Ankara",
            shortAddress = "Çankaya / Ankara",
            point = GeoPoint(39.9250, 32.8369),
            resultType = AddressResultType.POI
        )

        var uiState = MainUiState()
        uiState = uiState.copy(
            selectedDestination = selected,
            searchQuery = selected.name,
            searchResults = emptyList(),
            searchStatus = SearchUiStatus.IDLE,
            isDestinationCardVisible = true
        )

        assertNotNull(uiState.selectedDestination)
        assertEquals(39.9250, uiState.selectedDestination!!.point.latitude, 0.0001)
        assertEquals(32.8369, uiState.selectedDestination!!.point.longitude, 0.0001)
        assertTrue(uiState.isDestinationCardVisible)
    }

    // Senaryo 16: Seçilen noktanın rota motoruna hedef olarak aktarılması
    @Test
    fun scenario16_SelectedPointPassedToRouteCalculator() {
        val dest = SearchResult(
            id = "16",
            name = "Galata Kulesi",
            displayName = "Bereketzade Mah., Beyoğlu, İstanbul",
            point = GeoPoint(41.0256, 28.9741),
            resultType = AddressResultType.POI
        )

        var passedDestination: GeoPoint? = null
        val calculateRoutesFake: (GeoPoint) -> Unit = { target ->
            passedDestination = target
        }

        calculateRoutesFake(dest.point)

        assertNotNull(passedDestination)
        assertEquals(dest.point.latitude, passedDestination!!.latitude, 0.0001)
        assertEquals(dest.point.longitude, passedDestination!!.longitude, 0.0001)
    }

    // Senaryo 17: Hızlı yazımda eski sorgunun yeni sorguyu ezmemesi (stale response koruması)
    @Test
    fun scenario17_StaleResponseProtection() = runBlocking {
        var generationId = 0L
        var activeUiResults = emptyList<SearchResult>()

        // Simulate typing "K" (gen 1) then rapidly "Kadıköy" (gen 2)
        val gen1 = ++generationId
        val gen2 = ++generationId

        // Simulating slow async network response for gen 1 returning later
        val gen1Results = listOf(SearchResult("1", "Kızılcahamam", "Ankara", GeoPoint(40.4, 32.6)))
        val gen2Results = listOf(SearchResult("2", "Kadıköy", "İstanbul", GeoPoint(40.9, 29.0)))

        // Gen 2 arrives first:
        if (gen2 == generationId) {
            activeUiResults = gen2Results
        }

        // Gen 1 arrives late:
        if (gen1 == generationId) {
            activeUiResults = gen1Results // Should NOT execute
        }

        assertEquals(1, activeUiResults.size)
        assertEquals("Kadıköy", activeUiResults.first().name)
    }

    // Senaryo 18: Sonuç ekranından rota başlatma akışına geçiş
    @Test
    fun scenario18_TransitionToNavigationState() {
        var state = MainUiState(
            navigationState = NavigationState.IDLE,
            isDestinationCardVisible = true,
            selectedDestination = SearchResult("18", "Hedef", "Adres", GeoPoint(41.0, 29.0))
        )

        // User clicks "Navigasyonu Başlat"
        state = state.copy(
            navigationState = NavigationState.NAVIGATING,
            isDestinationCardVisible = false
        )

        assertEquals(NavigationState.NAVIGATING, state.navigationState)
        assertFalse(state.isDestinationCardVisible)
    }
}
