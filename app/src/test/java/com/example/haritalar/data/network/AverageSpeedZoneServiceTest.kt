package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AverageSpeedZoneServiceTest {
    private val service = AverageSpeedZoneService()

    @Test
    fun queryRequestsOnlyGeometryBackedAverageSpeedWaysAndRelations() {
        val route = listOf(
            GeoPoint(40.90, 29.20),
            GeoPoint(40.91, 29.25)
        )

        val query = service.buildQuery(route)

        assertTrue(query.contains("""way["enforcement"~"average_speed|section_control",i]"""))
        assertTrue(query.contains("""relation["enforcement"~"average_speed|section_control",i]"""))
        assertTrue(query.contains("out geom"))
        assertFalse(query.contains("""node["enforcement""""))
    }

    @Test
    fun parserReadsWayGeometryAndKeepsUnknownLimitUnknown() {
        val json = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 700,
                  "tags": {
                    "enforcement": "average_speed",
                    "name": "D100 Koridoru"
                  },
                  "geometry": [
                    {"lat": 40.9000, "lon": 29.2000},
                    {"lat": 40.9050, "lon": 29.2050},
                    {"lat": 40.9100, "lon": 29.2100}
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = service.parseOsmResponse(json)

        assertEquals(1, result.size)
        assertEquals("way:700", result.single().id)
        assertEquals(3, result.single().geometry.size)
        assertEquals("D100 Koridoru", result.single().name)
        assertNull(result.single().maxSpeed)
    }

    @Test
    fun parserReadsExplicitEnforcedSpeedAndRelationGeometry() {
        val json = """
            {
              "elements": [
                {
                  "type": "relation",
                  "id": 701,
                  "tags": {
                    "type": "enforcement",
                    "enforcement": "section_control",
                    "maxspeed:enforced": "90"
                  },
                  "members": [
                    {
                      "type": "way",
                      "ref": 1,
                      "geometry": [
                        {"lat": 40.9000, "lon": 29.2000},
                        {"lat": 40.9050, "lon": 29.2050}
                      ]
                    },
                    {
                      "type": "way",
                      "ref": 2,
                      "geometry": [
                        {"lat": 40.9050, "lon": 29.2050},
                        {"lat": 40.9100, "lon": 29.2100}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = service.parseOsmResponse(json)

        assertEquals(1, result.size)
        assertEquals("relation:701", result.single().id)
        assertEquals("90", result.single().maxSpeed)
        assertEquals(3, result.single().geometry.size)
    }

    @Test
    fun parserRejectsSinglePointAndNonAverageSpeedObjects() {
        val json = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 1,
                  "tags": {"enforcement": "average_speed"},
                  "geometry": [{"lat": 40.9, "lon": 29.2}]
                },
                {
                  "type": "way",
                  "id": 2,
                  "tags": {"enforcement": "traffic_signals"},
                  "geometry": [
                    {"lat": 40.9, "lon": 29.2},
                    {"lat": 40.91, "lon": 29.21}
                  ]
                }
              ]
            }
        """.trimIndent()

        assertTrue(service.parseOsmResponse(json).isEmpty())
    }
}
