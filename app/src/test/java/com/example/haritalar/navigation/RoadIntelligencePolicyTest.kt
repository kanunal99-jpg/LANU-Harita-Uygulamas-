package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.RoadFeature
import com.example.haritalar.model.RoadFeatureType
import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherDataMode
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
    fun arrivalForecastWeatherCarriesDistanceEtaAndSource() {
        val weather = WeatherCondition(
            point = GeoPoint(40.91, 29.21),
            type = WeatherType.RAIN,
            description = "Yoğun yağış",
            routeDistanceMeters = 12_500.0,
            etaSecondsFromStart = 18 * 60L,
            forecastEpochMillis = 1_800_000L,
            dataMode = WeatherDataMode.ARRIVAL_FORECAST
        )

        val events = RoadIntelligencePolicy.build(
            cameraWarning = null,
            weather = weather,
            traffic = null
        )

        val event = events.single()
        assertEquals(RoadIntelligenceType.WEATHER, event.type)
        assertEquals("Open-Meteo saatlik tahmin", event.source)
        assertTrue(event.detail.contains("12.5 km"))
        assertTrue(event.detail.contains("18 dk sonra"))
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
    fun verifiedRoadClosureBecomesP0WithRealDistance() {
        val userPoint = GeoPoint(40.9000, 29.2000)
        val closurePoint = GeoPoint(40.9010, 29.2000)
        val traffic = TrafficStatus(
            verified = true,
            message = "Canlı trafik",
            trafficLevel = TrafficLevel.MODERATE,
            sourceName = "Verified Traffic",
            isLiveApi = true,
            lastCheckTimestamp = 9876L
        )
        val segments = listOf(
            TrafficSegment(
                coordinates = listOf(closurePoint),
                currentSpeed = 0.0,
                freeFlowSpeed = 50.0,
                delaySeconds = 300,
                roadClosure = true
            )
        )

        val events = RoadIntelligencePolicy.build(
            cameraWarning = null,
            weather = null,
            traffic = traffic,
            trafficSegments = segments,
            userPoint = userPoint
        )

        assertEquals(1, events.size)
        assertEquals(RoadIntelligenceType.ROAD_CLOSURE, events.single().type)
        assertEquals(RoadIntelligencePriority.P0, events.single().priority)
        assertTrue(events.single().distanceMeters != null)
        assertTrue(events.single().detail.contains("doğrulanmış kapanış"))
        assertEquals("Verified Traffic", events.single().source)
    }

    @Test
    fun unverifiedClosureSegmentNeverCreatesClosureWarning() {
        val traffic = TrafficStatus(
            verified = false,
            message = "Fallback trafik",
            trafficLevel = TrafficLevel.SEVERE,
            sourceName = "Fallback Traffic"
        )
        val segments = listOf(
            TrafficSegment(
                coordinates = listOf(GeoPoint(40.9010, 29.2000)),
                currentSpeed = 0.0,
                freeFlowSpeed = 50.0,
                delaySeconds = 600,
                roadClosure = true
            )
        )

        val events = RoadIntelligencePolicy.build(
            cameraWarning = null,
            weather = null,
            traffic = traffic,
            trafficSegments = segments,
            userPoint = GeoPoint(40.9000, 29.2000)
        )

        assertTrue(events.none { it.type == RoadIntelligenceType.ROAD_CLOSURE })
    }


    @Test
    fun cachedClosureNeverBecomesP0EvenWhenTrafficStatusIsVerified() {
        val traffic = TrafficStatus(
            verified = true,
            message = "Canlı trafik",
            trafficLevel = TrafficLevel.MODERATE,
            sourceName = "Verified Traffic",
            isLiveApi = true
        )
        val cachedClosure = TrafficSegment(
            coordinates = listOf(GeoPoint(40.9010, 29.2000)),
            currentSpeed = 0.0,
            freeFlowSpeed = 50.0,
            delaySeconds = 600,
            roadClosure = true,
            fromCache = true
        )

        val events = RoadIntelligencePolicy.build(
            cameraWarning = null,
            weather = null,
            traffic = traffic,
            trafficSegments = listOf(cachedClosure),
            userPoint = GeoPoint(40.9000, 29.2000)
        )

        assertTrue(events.none { it.type == RoadIntelligenceType.ROAD_CLOSURE })
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

    @Test
    fun roadFeatureBecomesP1SourceBackedWarning() {
        val feature = RoadFeature(
            id = "level",
            point = GeoPoint(40.901, 29.200),
            type = RoadFeatureType.LEVEL_CROSSING,
            title = "Hemzemin geçit",
            detail = "OSM demiryolu geçişi",
            source = "OpenStreetMap"
        )
        val featureWarning = RoadFeatureWarning(
            feature = feature,
            distanceMeters = 850.0,
            warningRadiusMeters = 1_500
        )

        val events = RoadIntelligencePolicy.build(
            cameraWarning = null,
            weather = null,
            traffic = null,
            roadFeatureWarning = featureWarning
        )

        assertEquals(1, events.size)
        assertEquals(RoadIntelligenceType.ROAD_FEATURE, events.single().type)
        assertEquals(RoadIntelligencePriority.P1, events.single().priority)
        assertEquals("OpenStreetMap", events.single().source)
        assertTrue(events.single().detail.contains("850 m"))
    }

}
