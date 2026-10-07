package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResult
import com.example.haritalar.model.TurkishAddressDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostalCodeSearchTest {
    @Test
    fun pureFiveDigitQueryIsPostalCodeNotHouseNumber() {
        val parsed = TurkishAddressHelper.parseAddressQuery("34953")

        assertEquals("34953", parsed.postalCode)
        assertNull(parsed.houseNumber)
        assertFalse(parsed.isBuildingLevelRequested)
        assertTrue(TurkishAddressHelper.generateSearchQueries("34953").contains("34953"))
    }

    @Test
    fun nominatimUsesStructuredPostalCodeSearchWithoutLocalViewbox() {
        val provider = NominatimSearchProvider()
        val url = provider.buildSearchUrl("34953", GeoPoint(40.9, 29.3))

        assertTrue(url.contains("postalcode=34953"))
        assertTrue(url.contains("countrycodes=tr"))
        assertFalse(url.contains("q=34953"))
        assertFalse(url.contains("viewbox="))
    }

    @Test
    fun exactPostalCodeResultRanksAboveDifferentPostcode() {
        val parsed = TurkishAddressHelper.parseAddressQuery("34953")
        val matching = SearchResult(
            id = "matching",
            name = "Aydınlı Mahallesi",
            displayName = "Aydınlı Mahallesi, Tuzla, İstanbul, 34953, Türkiye",
            shortAddress = "Tuzla / İstanbul",
            point = GeoPoint(40.88, 29.32),
            resultType = AddressResultType.NEIGHBORHOOD,
            addressDetails = TurkishAddressDetails(
                province = "İstanbul",
                district = "Tuzla",
                neighborhood = "Aydınlı Mahallesi",
                postalCode = "34953"
            )
        )
        val other = matching.copy(
            id = "other",
            name = "Başka Mahalle",
            displayName = "Başka Mahalle, İstanbul, 34950, Türkiye",
            addressDetails = matching.addressDetails?.copy(postalCode = "34950")
        )

        val ranked = SearchRankingEvaluator.rankAndEvaluateResults(
            rawResults = listOf(other, matching),
            parsedQuery = parsed
        )

        assertEquals("matching", ranked.first().id)
    }
}
