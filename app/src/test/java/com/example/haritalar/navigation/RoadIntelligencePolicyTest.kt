package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadIntelligencePolicyTest {
    private val camera = SafetyCamera(
        id = 7L,
        point = GeoPoint(40.90, 29.20),
        maxSpeed = "80"
    )

    private val warning = SafetyCameraWarningPolicy.ProximityWarning(
        camera = camera,
        distanceMeters = 1200.0,
        distanceBucketMeters = 1500,
        announcementMilestoneMeters = 2000,
        warningRadiusMeters = 6500,
        estimatedSecondsToCamera = 45,
        speedLimitKmh = 80,
        overspeed = true
    )

    @Test
    fun stormOutranksCameraAndTraffic() {
        val weather = WeatherCondition(
            point = GeoPoint(40.91, 29.21),
            type = WeatherType.STORM,
            description = "Şiddetli fırtına"
        )
        val traffic = TrafficStatus(
            verified = true,
            message = "Ağır trafik",
            delaySeconds = 900,
            trafficLevel = TrafficLevel.SEVERE,
            sourceName = "Test Traffic",
            isLiveApi = true,
            lastCheckTimestamp = 1234L
        )

        val events = RoadIntelligencePolicy.build(warning, weather, traffic)

        assertEquals(3, events.size)
        assertEquals(RoadIntelligenceType.WEATHER, events.first().type)
        assertEquals(RoadIntelligencePriority.P0, events.first().priority)
        assertEquals(RoadIntelligenceType.CAMERA, events[1].type)
        assertEquals(RoadIntelligencePriority.P1, events[1].priority)
    }

    @Test
    fun unverifiedTrafficNeverBecomesLiveWarning() {
        val traffic = TrafficStatus(
            verified = false,
            message = "Sağlayıcı erişilemedi",
            delaySeconds = 1800,
            trafficLevel = TrafficLevel.SEVERE,
            sourceName = "Unavailable Traffic"
        )

        val events = RoadIntelligencePolicy.build(
            cameraWarning = null,
            weather = null,
            traffic = traffic
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun clearWeatherDoesNotCreateNoise() {
        val weather = WeatherCondition(
            point = GeoPoint(40.91, 29.21),
            type = WeatherType.CLEAR,
            description = "Açık"
        )

        val events = RoadIntelligencePolicy.build(
            cameraWarning = warning.copy(overspeed = false),
            weather = weather,
            traffic = null
        )

        assertEquals(1, events.size)
        assertEquals(RoadIntelligenceType.CAMERA, events.single().type)
        assertTrue(events.single().detail.contains("1.2 km"))
    }
}
