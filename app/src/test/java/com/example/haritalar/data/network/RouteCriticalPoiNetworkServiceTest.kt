package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCriticalPoiNetworkServiceTest {
    private val service = RouteCriticalPoiNetworkService(
        endpoints = listOf("https://example.invalid")
    )

    @Test
    fun routeQueryContainsCriticalCategoriesForEverySample() {
        val points = listOf(
            GeoPoint(40.10, 29.10),
            GeoPoint(40.20, 29.20)
        )

        val query = service.buildQuery(points, 5_000)

        assertTrue(query.contains("""amenity"~"^(fuel|hospital|pharmacy|charging_station)$"""))
        assertTrue(query.contains("""healthcare"="hospital"""))
        assertTrue(query.contains("""shop"="chemist"""))
        assertEquals(2, Regex("""amenity"~""").findAll(query).count())
        assertTrue(query.contains("40.1,29.1"))
        assertTrue(query.contains("40.2,29.2"))
    }

    @Test
    fun parserKeepsOnlyCriticalPoisAndSupportsWayCenters() {
        val json = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 40.10,
                  "lon": 29.10,
                  "tags": {"amenity": "fuel", "name": "Yakıt A"}
                },
                {
                  "type": "way",
                  "id": 2,
                  "center": {"lat": 40.20, "lon": 29.20},
                  "tags": {"healthcare": "hospital", "name": "Hastane B"}
                },
                {
                  "type": "node",
                  "id": 3,
                  "lat": 40.30,
                  "lon": 29.30,
                  "tags": {"amenity": "cafe", "name": "Kafe C"}
                }
              ]
            }
        """.trimIndent()

        val pois = service.parseResponse(json)

        assertEquals(2, pois.size)
        assertEquals(setOf(PoiCategory.FUEL, PoiCategory.HOSPITAL), pois.map { it.category }.toSet())
        assertFalse(pois.any { it.name == "Kafe C" })
    }
}
