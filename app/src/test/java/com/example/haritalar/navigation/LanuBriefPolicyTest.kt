package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.RouteType
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanuBriefPolicyTest {
    private val route = RouteOption(
        routeId = "r1",
        title = "En hızlı",
        summary = "D100",
        durationSeconds = 3600,
        distanceMeters = 40_000.0,
        geometry = listOf(
            GeoPoint(40.90, 29.20),
            GeoPoint(40.91, 29.25),
            GeoPoint(40.92, 29.30)
        ),
        maneuvers = emptyList(),
        hasTolls = true,
        routeType = RouteType.FASTEST,
        trafficDelaySeconds = 600
    )

    @Test
    fun neverPretendsMissingTrafficOrWeatherIsKnown() {
        val brief = LanuBriefPolicy.build(route, null, emptyList(), emptyList())
        assertEquals(
            LanuBriefStatus.UNAVAILABLE,
            brief.items.first { it.type == LanuBriefItemType.TRAFFIC }.status
        )
        assertEquals(
            LanuBriefStatus.UNAVAILABLE,
            brief.items.first { it.type == LanuBriefItemType.WEATHER }.status
        )
        assertTrue(brief.unavailableItemCount >= 2)
    }

    @Test
    fun summarizesVerifiedTrafficAndWeatherRisk() {
        val traffic = TrafficStatus(
            verified = true,
            message = "Yoğun trafik",
            delaySeconds = 900,
            trafficLevel = TrafficLevel.HEAVY,
            sourceName = "Test Traffic",
            isLiveApi = true
        )
        val weather = listOf(
            WeatherCondition(
                point = route.geometry[1],
                type = WeatherType.RAIN,
                description = "Yoğun yağış",
                intensity = 0.8f
            )
        )

        val brief = LanuBriefPolicy.build(route, traffic, weather, emptyList())
        val trafficItem = brief.items.first { it.type == LanuBriefItemType.TRAFFIC }
        val weatherItem = brief.items.first { it.type == LanuBriefItemType.WEATHER }

        assertTrue(trafficItem.title.contains("dk trafik gecikmesi"))
        assertEquals(LanuBriefStatus.VERIFIED, trafficItem.status)
        assertEquals(LanuBriefStatus.VERIFIED, weatherItem.status)
        assertEquals(LanuBriefSeverity.NOTICE, weatherItem.severity)
    }

    @Test
    fun longRouteCameraCountIsExplicitlyPartial() {
        val camera = SafetyCamera(id = 42L, point = GeoPoint(40.91, 29.25))
        val brief = LanuBriefPolicy.build(route, null, emptyList(), listOf(camera))
        val cameraItem = brief.items.first { it.type == LanuBriefItemType.CAMERA }

        assertEquals(LanuBriefStatus.PARTIAL, cameraItem.status)
        assertTrue(cameraItem.title.contains("1 sabit kamera"))
    }
}
