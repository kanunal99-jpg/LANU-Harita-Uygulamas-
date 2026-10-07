package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RoadFeatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadFeatureServiceTest {
    private val service = RoadFeatureService()

    @Test
    fun queryIncludesSupportedRoadFeatureTags() {
        val query = service.buildQuery(
            listOf(
                GeoPoint(41.0, 29.0),
                GeoPoint(41.01, 29.02)
            )
        )

        assertTrue(query.contains("""["traffic_calming"~"^(bump|hump|table|cushion)$"]"""))
        assertTrue(query.contains("""["railway"="level_crossing"]"""))
        assertTrue(query.contains("""["amenity"="school"]"""))
        assertTrue(query.contains("""["hazard"]"""))
        assertTrue(query.contains("out center 500"))
    }

    @Test
    fun parserAcceptsNodeAndWayCenterAndIgnoresIrrelevantData() {
        val json = """
            {
              "elements": [
                {
                  "type":"node","id":1,"lat":41.0,"lon":29.0,
                  "tags":{"traffic_calming":"hump"}
                },
                {
                  "type":"way","id":2,
                  "center":{"lat":41.001,"lon":29.001},
                  "tags":{"amenity":"school","name":"Örnek Okul"}
                },
                {
                  "type":"node","id":3,"lat":41.002,"lon":29.002,
                  "tags":{"railway":"level_crossing"}
                },
                {
                  "type":"node","id":4,"lat":41.003,"lon":29.003,
                  "tags":{"hazard":"slippery"}
                },
                {
                  "type":"node","id":5,"lat":41.004,"lon":29.004,
                  "tags":{"amenity":"cafe"}
                }
              ]
            }
        """.trimIndent()

        val parsed = service.parseOsmResponse(json)

        assertEquals(4, parsed.size)
        assertTrue(parsed.any { it.type == RoadFeatureType.SPEED_CALMING })
        assertTrue(parsed.any { it.type == RoadFeatureType.SCHOOL_ZONE && it.detail == "Örnek Okul" })
        assertTrue(parsed.any { it.type == RoadFeatureType.LEVEL_CROSSING })
        assertTrue(parsed.any { it.type == RoadFeatureType.ROAD_HAZARD })
    }
}
