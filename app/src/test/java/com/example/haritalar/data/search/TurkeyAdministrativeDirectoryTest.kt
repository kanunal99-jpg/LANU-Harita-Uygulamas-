package com.example.haritalar.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkeyAdministrativeDirectoryTest {
    @Test
    fun containsCompleteProvinceAndDistrictSnapshot() {
        assertEquals(81, TurkeyAdministrativeDirectory.provinces.size)
        assertEquals(973, TurkeyAdministrativeDirectory.districtCount())
        assertEquals(39, TurkeyAdministrativeDirectory.districtsForProvince("İstanbul").size)
        assertTrue(TurkeyAdministrativeDirectory.containsDistrict("İstanbul", "Sancaktepe"))
        assertTrue(TurkeyAdministrativeDirectory.containsDistrict("Kocaeli", "Kartepe"))
        assertTrue(TurkeyAdministrativeDirectory.containsDistrict("Samsun", "19 Mayıs"))
    }

    @Test
    fun ambiguousDistrictWithoutProvinceIsNotTreatedAsUnique() {
        assertNull(TurkeyAdministrativeDirectory.uniqueProvinceForDistrict("Merkez"))
        assertNull(TurkeyAdministrativeDirectory.uniqueProvinceForDistrict("Gölbaşı"))
    }

    @Test
    fun parserUsesProvinceScopedFullDistrictDirectory() {
        val parsed = TurkishAddressHelper.parseAddressQuery("19 Mayıs Samsun")
        assertEquals("Samsun", parsed.province)
        assertEquals("19 Mayıs", parsed.district)

        val kartepe = TurkishAddressHelper.parseAddressQuery("Kartepe Kocaeli")
        assertEquals("Kocaeli", kartepe.province)
        assertEquals("Kartepe", kartepe.district)
    }
}

class IstanbulAdministrativeDirectoryTest {
    @Test
    fun all39DistrictsHaveExactlyOneSide() {
        assertEquals(39, IstanbulAdministrativeDirectory.allDistricts.size)
        assertEquals(25, IstanbulAdministrativeDirectory.districts(IstanbulAdministrativeDirectory.Side.EUROPE).size)
        assertEquals(14, IstanbulAdministrativeDirectory.districts(IstanbulAdministrativeDirectory.Side.ASIA).size)
        IstanbulAdministrativeDirectory.allDistricts.forEach {
            assertTrue(IstanbulAdministrativeDirectory.sideOf(it) != null)
        }
    }

    @Test
    fun knownDistrictSidesAreCorrect() {
        assertEquals(IstanbulAdministrativeDirectory.Side.ASIA, IstanbulAdministrativeDirectory.sideOf("Tuzla"))
        assertEquals(IstanbulAdministrativeDirectory.Side.ASIA, IstanbulAdministrativeDirectory.sideOf("Kadıköy"))
        assertEquals(IstanbulAdministrativeDirectory.Side.EUROPE, IstanbulAdministrativeDirectory.sideOf("Beşiktaş"))
        assertEquals(IstanbulAdministrativeDirectory.Side.EUROPE, IstanbulAdministrativeDirectory.sideOf("Silivri"))
    }
}
