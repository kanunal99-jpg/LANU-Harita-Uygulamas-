package com.example.haritalar.data.search

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultDeduplicatorTest {
    private fun result(id: String, name: String, lat: Double, lon: Double) =
        SearchResult(id = id, name = name, displayName = name, point = GeoPoint(lat, lon))

    @Test
    fun sameNameFarApartAreDifferentBusinesses() {
        val kadikoy = result("a", "Özlem Market", 40.9900, 29.0300)
        val tuzla = result("b", "Özlem Market", 40.8200, 29.3000)

        val deduped = SearchResultDeduplicator.deduplicate(listOf(kadikoy, tuzla))

        assertEquals(2, deduped.size)
        assertFalse(SearchResultDeduplicator.isSamePlace(kadikoy, tuzla))
    }

    @Test
    fun sameNameVeryCloseAcrossProvidersIsOnePlace() {
        val osm = result("osm-1", "Global Donuk Gıda", 40.986000, 29.227000)
        val photon = result("photon-2", "global donuk gida", 40.986180, 29.227150)

        assertTrue(SearchResultDeduplicator.isSamePlace(osm, photon))
        assertEquals(1, SearchResultDeduplicator.deduplicate(listOf(osm, photon)).size)
    }

    @Test
    fun sameCoordinatesDifferentBusinessNamesRemainDistinct() {
        val a = result("a", "Market A", 41.0000, 29.0000)
        val b = result("b", "Eczane B", 41.0000, 29.0000)
        assertEquals(2, SearchResultDeduplicator.deduplicate(listOf(a, b)).size)
    }
}
