package com.example.haritalar.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishDistrictDirectoryTest {
    @Test
    fun nationwideSnapshotHas81ParentProvincesAnd973Districts() {
        val provinces = TurkishDistrictDirectory.districtsByProvince
        assertEquals(81, provinces.size)
        assertEquals(973, provinces.values.sumOf { it.size })
        assertEquals(
            TurkishAddressHelper.TURKISH_PROVINCES.map(TurkishAddressHelper::normalizeTurkish).toSet(),
            provinces.keys.map(TurkishAddressHelper::normalizeTurkish).toSet()
        )
        provinces.values.forEach { districts ->
            assertEquals(districts.size, districts.map(TurkishAddressHelper::normalizeTurkish).distinct().size)
        }
    }

    @Test
    fun istanbulSideSplitIsCompleteAndDisjoint() {
        val europe = TurkishDistrictDirectory.istanbulEuropeanDistricts
        val anatolia = TurkishDistrictDirectory.istanbulAnatolianDistricts
        val allIstanbul = TurkishDistrictDirectory.districtsByProvince.getValue("İstanbul").toSet()
        assertEquals(25, europe.size)
        assertEquals(14, anatolia.size)
        assertTrue(europe.intersect(anatolia).isEmpty())
        assertEquals(allIstanbul, europe + anatolia)
        assertEquals(
            TurkishDistrictDirectory.IstanbulSide.EUROPEAN,
            TurkishDistrictDirectory.istanbulSideOf("Şişli")
        )
        assertEquals(
            TurkishDistrictDirectory.IstanbulSide.ANATOLIAN,
            TurkishDistrictDirectory.istanbulSideOf("Üsküdar")
        )
        assertNull(TurkishDistrictDirectory.istanbulSideOf("Çankaya"))
    }

    @Test
    fun districtMustBelongToSpecifiedProvince() {
        assertTrue(TurkishDistrictDirectory.isDistrictOfProvince("Kadıköy", "İstanbul"))
        assertFalse(TurkishDistrictDirectory.isDistrictOfProvince("Kadıköy", "Ankara"))
        assertEquals("Kadıköy", TurkishDistrictDirectory.findDistrict("Kadıköy Rıhtım", "İstanbul"))
        assertNull(TurkishDistrictDirectory.findDistrict("Kadıköy Rıhtım", "Ankara"))
    }

    @Test
    fun ambiguousMerkezRequiresProvince() {
        assertNull(TurkishDistrictDirectory.findDistrict("Merkez", null))
        assertEquals("Merkez", TurkishDistrictDirectory.findDistrict("Merkez", "Ağrı"))
    }

    @Test
    fun addressParserDoesNotInventUnknownDistrict() {
        assertEquals("Kadıköy", TurkishAddressHelper.parseAddressQuery("Kadıköy, İstanbul").district)
        assertNull(TurkishAddressHelper.parseAddressQuery("Ankara, Kadıköy").district)
        assertNull(TurkishAddressHelper.parseAddressQuery("İstanbul, Bilinmeyenilçe").district)
        assertEquals("Merkez", TurkishAddressHelper.parseAddressQuery("Ağrı Merkez").district)
        assertEquals("Tuzla", TurkishAddressHelper.parseAddressQuery("İstanbul Tuzla").district)
    }
}
