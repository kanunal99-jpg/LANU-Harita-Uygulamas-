package com.example.haritalar.data.search

import com.example.haritalar.model.TurkishAddressDetails
import org.junit.Assert.*
import org.junit.Test

class TurkishAddressHelperTest {

    @Test
    fun testNormalizeTurkishCharacters() {
        val input = "İstanbul Kadıköy Şişli Çankaya Beylikdüzü Ağrı"
        val normalized = TurkishAddressHelper.normalizeTurkish(input)
        assertEquals("istanbul kadikoy sisli cankaya beylikduzu agri", normalized)
    }

    @Test
    fun testNormalizeTurkishCapitalI() {
        val input = "İSTİKLAL CADDESİ"
        val normalized = TurkishAddressHelper.normalizeTurkish(input)
        assertEquals("istiklal caddesi", normalized)
    }

    @Test
    fun testStandardizeAddressQueryAbbreviations() {
        val query1 = "Adnan Kahveci Mah. Atatürk Cad. No: 14"
        val standardized1 = TurkishAddressHelper.standardizeAddressQuery(query1)
        assertEquals("Adnan Kahveci Mahallesi Atatürk Caddesi No:14", standardized1)

        val query2 = "Gül Sok. No 5"
        val standardized2 = TurkishAddressHelper.standardizeAddressQuery(query2)
        assertEquals("Gül Sokağı No:5", standardized2)

        val query3 = "İnönü Blv. numara 42"
        val standardized3 = TurkishAddressHelper.standardizeAddressQuery(query3)
        assertEquals("İnönü Bulvarı No:42", standardized3)
    }

    @Test
    fun testGenerateSearchQueriesVariants() {
        val query = "Selimiye Mah. Üsküdar"
        val variants = TurkishAddressHelper.generateSearchQueries(query)

        assertTrue(variants.isNotEmpty())
        assertTrue("Should include standardized query", variants.contains("Selimiye Mahallesi Üsküdar"))
        assertTrue("Should include ASCII normalized variant", variants.any { it.contains("selimiye") && it.contains("uskudar") })
    }

    @Test
    fun testFormatAddressPartsWithPoi() {
        val details = TurkishAddressDetails(
            province = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            street = "Rıhtım Caddesi",
            poiName = "Kadıköy Rıhtım Otel"
        )
        val (title, subtitle) = TurkishAddressHelper.formatAddressParts("Default Name", "Default Display", details)
        assertEquals("Kadıköy Rıhtım Otel", title)
        assertEquals("Caferağa Mah., Kadıköy / İstanbul", subtitle)
    }

    @Test
    fun testFormatAddressPartsWithStreetAndHouseNumber() {
        val details = TurkishAddressDetails(
            province = "Ankara",
            district = "Çankaya",
            neighborhood = "Kızılay",
            street = "Atatürk Bulvarı",
            houseNumber = "14"
        )
        val (title, subtitle) = TurkishAddressHelper.formatAddressParts("Default", "Default", details)
        assertEquals("Atatürk Bulvarı No:14", title)
        assertEquals("Kızılay Mah., Çankaya / Ankara", subtitle)
    }

    @Test
    fun testFormatAddressPartsWithNeighborhood() {
        val details = TurkishAddressDetails(
            province = "İstanbul",
            district = "Beylikdüzü",
            neighborhood = "Adnan Kahveci"
        )
        val (title, subtitle) = TurkishAddressHelper.formatAddressParts("Default", "Default", details)
        assertEquals("Adnan Kahveci Mah.", title)
        assertEquals("Beylikdüzü / İstanbul", subtitle)
    }

    @Test
    fun testFormatAddressPartsWithDistrictOnly() {
        val details = TurkishAddressDetails(
            province = "İstanbul",
            district = "Kadıköy"
        )
        val (title, subtitle) = TurkishAddressHelper.formatAddressParts("Default", "Default", details)
        assertEquals("Kadıköy", title)
        assertEquals("İstanbul", subtitle)
    }

    @Test
    fun testMatchesQueryWithDiacriticTolerance() {
        val query = "kadikoy rihtim"
        val target = "Rıhtım Caddesi, Osmanağa Mahallesi, Kadıköy, İstanbul"
        assertTrue(TurkishAddressHelper.matchesQuery(query, target))
    }

    @Test
    fun testMatchesQueryTokenOrderIndependence() {
        val query = "kadıköy selimiye üsküdar"
        val target = "Selimiye Mahallesi, Üsküdar, Kadıköy civarı"
        assertTrue(TurkishAddressHelper.matchesQuery(query, target))
    }
}
