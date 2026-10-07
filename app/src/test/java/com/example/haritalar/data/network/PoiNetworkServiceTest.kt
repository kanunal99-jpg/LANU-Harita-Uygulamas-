package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PoiNetworkServiceTest {
    private val service = PoiNetworkService()
    private val center = GeoPoint(40.765, 29.940)

    @Test
    fun pharmacyQueryIncludesNodesWaysAndRelations() {
        val query = service.buildQuery(center, 8000, PoiCategory.PHARMACY)
        assertTrue(query.contains("""nwr["amenity"="pharmacy"]"""))
        assertTrue(query.contains("""nwr["shop"="chemist"]"""))
        assertTrue(query.contains("out center 120"))
    }

    @Test
    fun parserAcceptsWayCenterForPoi() {
        val json = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 42,
                  "center": {"lat": 40.766, "lon": 29.941},
                  "tags": {
                    "amenity": "pharmacy",
                    "name": "Örnek Eczane",
                    "addr:street": "Örnek Sokak",
                    "addr:housenumber": "7",
                    "addr:city": "Kocaeli"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = service.parseOverpassResponse(json, PoiCategory.PHARMACY, center)
        assertEquals(1, result.size)
        assertEquals("Örnek Eczane", result.first().name)
        assertEquals(PoiCategory.PHARMACY, result.first().category)
        assertTrue(result.first().address?.contains("Örnek Sokak 7") == true)
    }

    @Test
    fun validEmptyOverpassPayloadIsDistinguishedFromMalformedPayload() {
        assertTrue(service.isValidOverpassPayload("""{"elements":[]}"""))
        assertTrue(!service.isValidOverpassPayload("""{"remark":"temporary failure"}"""))
        assertTrue(!service.isValidOverpassPayload("not-json"))
    }

}
