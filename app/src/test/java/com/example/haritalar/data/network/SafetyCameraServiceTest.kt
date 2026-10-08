package com.example.haritalar.data.network

import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.model.SafetyCameraType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCameraServiceTest {
    private val service = SafetyCameraService()

    @Test
    fun `overpass error remark is not a verified empty camera scan`() {
        val reason = service.invalidOverpassResponseReason(
            """{"remark":"runtime error: Query timed out","elements":[]}"""
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("remark"))
    }

    @Test
    fun `malformed overpass json and elements shape never verify zero cameras`() {
        assertNotNull(service.invalidOverpassResponseReason("<html>service unavailable</html>"))
        assertNotNull(service.invalidOverpassResponseReason("""{"elements":"not-an-array"}"""))
        assertNotNull(service.invalidOverpassResponseReason("""{"status":"ok"}"""))
        assertNotNull(service.invalidOverpassResponseReason("null"))
    }

    @Test
    fun `valid overpass empty elements is allowed without inventing cameras`() {
        assertNull(service.invalidOverpassResponseReason("""{"elements":[]}"""))
        assertTrue(service.parseOsmResponse("""{"elements":[]}""").isEmpty())
    }

    @Test
    fun `parser reads speed camera and source tags without inventing speed`() {
        val json = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 12345,
                  "lat": 41.0123,
                  "lon": 28.9876,
                  "tags": {
                    "highway": "speed_camera",
                    "maxspeed": "50",
                    "direction": "forward",
                    "ref": "TR-001"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = service.parseOsmResponse(json)

        assertEquals(1, result.size)
        assertEquals(12345L, result.single().id)
        assertEquals("50", result.single().maxSpeed)
        assertEquals("forward", result.single().direction)
        assertEquals("TR-001", result.single().reference)
    }

    @Test
    fun `parser enriches camera with nearby road and place context`() {
        val json = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 100,
                  "lat": 41.0000,
                  "lon": 29.0000,
                  "tags": {"highway": "speed_camera", "maxspeed": "80"}
                },
                {
                  "type": "way",
                  "id": 200,
                  "center": {"lat": 41.0002, "lon": 29.0000},
                  "tags": {"highway": "primary", "name": "D100"}
                },
                {
                  "type": "node",
                  "id": 300,
                  "lat": 41.0003,
                  "lon": 29.0000,
                  "tags": {"amenity": "fuel", "name": "Örnek Tesis"}
                }
              ]
            }
        """.trimIndent()

        val result = service.parseOsmResponse(json)
        val camera = result.single()

        assertEquals("D100", camera.rawTags["lanu:nearby_road"])
        assertEquals("Örnek Tesis", camera.rawTags["lanu:nearby_place"])
    }

    @Test
    fun `parser keeps missing speed limit unknown`() {
        val json = """
            {"elements":[
              {"type":"node","id":7,"lat":41.0,"lon":29.0,
               "tags":{"highway":"speed_camera"}}
            ]}
        """.trimIndent()

        val result = service.parseOsmResponse(json)

        assertEquals(1, result.size)
        assertEquals(null, result.single().maxSpeed)
    }

    @Test
    fun `parser ignores malformed and duplicate nodes`() {
        val json = """
            {"elements":[
              {"type":"node","id":10,"lat":91,"lon":29,
               "tags":{"highway":"speed_camera"}},
              {"type":"node","id":11,"lat":41,"lon":29,
               "tags":{"highway":"speed_camera"}},
              {"type":"node","id":11,"lat":41,"lon":29,
               "tags":{"highway":"speed_camera"}}
            ]}
        """.trimIndent()

        val result = service.parseOsmResponse(json)

        assertEquals(1, result.size)
        assertEquals(11L, result.single().id)
    }

    @Test
    fun `classifier distinguishes red light combined and average speed enforcement`() {
        assertEquals(
            SafetyCameraType.RED_LIGHT,
            service.classifyCameraType(mapOf("enforcement" to "traffic_signals"))
        )
        assertEquals(
            SafetyCameraType.SPEED_AND_RED_LIGHT,
            service.classifyCameraType(
                mapOf("highway" to "speed_camera", "enforcement" to "traffic_signals;maxspeed")
            )
        )
        assertEquals(
            SafetyCameraType.AVERAGE_SPEED_CONTROL_POINT,
            service.classifyCameraType(mapOf("enforcement" to "average_speed"))
        )
        assertEquals(
            SafetyCameraType.FIXED_SPEED,
            service.classifyCameraType(mapOf("highway" to "speed_camera"))
        )
    }

    @Test
    fun `parser accepts enforcement node without highway speed camera tag`() {
        val json = """
            {"elements":[
              {"type":"node","id":88,"lat":41.0,"lon":29.0,
               "tags":{"enforcement":"traffic_signals"}}
            ]}
        """.trimIndent()

        val result = service.parseOsmResponse(json)

        assertEquals(1, result.size)
        assertEquals(SafetyCameraType.RED_LIGHT, result.single().type)
    }

    @Test
    fun `bounding box validates coordinates and containment`() {
        val bbox = SafetyCameraBoundingBox(40.9, 28.8, 41.1, 29.1)

        assertTrue(bbox.isValid())
        assertTrue(bbox.contains(com.example.haritalar.model.GeoPoint(41.0, 29.0)))
    }
}
