package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
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

    @Test
    fun summarizesVerifiedCriticalPoisWithoutInventingCoverage() {
        val coverage = RouteCriticalPoiCoverage(
            status = RouteDataCoverage.VERIFIED,
            pois = listOf(
                PoiItem("fuel", "Yakıt", PoiCategory.FUEL, GeoPoint(40.91, 29.25)),
                PoiItem("hospital", "Hastane", PoiCategory.HOSPITAL, GeoPoint(40.91, 29.25)),
                PoiItem("pharmacy", "Eczane", PoiCategory.PHARMACY, GeoPoint(40.91, 29.25))
            ),
            source = "OpenStreetMap / Overpass",
            fetchedAtMillis = 1_000L,
            sampleCount = 6,
            maxSampleGapMeters = 8_000.0,
            note = "Tam rota koridoru örneklendi."
        )

        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            criticalPoiCoverage = coverage
        )
        val item = brief.items.first { it.type == LanuBriefItemType.CRITICAL_POI }

        assertEquals(LanuBriefStatus.VERIFIED, item.status)
        assertTrue(item.title.contains("3 kritik nokta"))
        assertTrue(item.detail.contains("Yakıt 1"))
        assertTrue(item.detail.contains("Şarj 0"))
    }


    @Test
    fun verifiedRouteCameraCoverageIncludesNearestRouteDistance() {
        val camera = SafetyCamera(id = 99L, point = GeoPoint(40.91, 29.25))
        val coverage = RouteSafetyCameraCoverage(
            status = RouteDataCoverage.VERIFIED,
            cameras = listOf(camera),
            source = "OpenStreetMap / Overpass",
            fetchedAtMillis = 2_000L,
            sampleCount = 3,
            successfulSampleCount = 3,
            maxSampleGapMeters = 8_000.0,
            note = "Rota kamera örneklerinin tamamı doğrulandı."
        )

        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            routeCameraCoverage = coverage
        )
        val item = brief.items.first { it.type == LanuBriefItemType.CAMERA }

        assertEquals(LanuBriefStatus.VERIFIED, item.status)
        assertTrue(item.title.contains("1 sabit kamera"))
        assertTrue(item.detail.contains("En yakın kamera rota boyunca yaklaşık"))
        assertTrue(item.detail.contains("3/3 kamera örneği"))
    }


    @Test
    fun everyVerifiedBriefRowRetainsSourceFreshness() {
        val timestamp = 1_700_000_000_000L
        val traffic = TrafficStatus(
            verified = true,
            message = "Akıcı",
            delaySeconds = 0,
            trafficLevel = TrafficLevel.LOW,
            sourceName = "Test Traffic",
            isLiveApi = true,
            lastCheckTimestamp = timestamp
        )
        val weather = listOf(
            WeatherCondition(
                point = route.geometry[1],
                type = WeatherType.CLEAR,
                description = "Açık"
            )
        )
        val poiCoverage = RouteCriticalPoiCoverage(
            status = RouteDataCoverage.VERIFIED,
            pois = emptyList(),
            source = "OpenStreetMap / Overpass",
            fetchedAtMillis = timestamp,
            sampleCount = 6,
            maxSampleGapMeters = 8_000.0,
            note = "Tam rota koridoru örneklendi."
        )
        val cameraCoverage = RouteSafetyCameraCoverage(
            status = RouteDataCoverage.VERIFIED,
            cameras = emptyList(),
            source = "OpenStreetMap / Overpass",
            fetchedAtMillis = timestamp,
            sampleCount = 3,
            successfulSampleCount = 3,
            maxSampleGapMeters = 8_000.0,
            note = "Rota kamera örneklerinin tamamı doğrulandı."
        )

        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = traffic,
            routeWeather = weather,
            loadedSafetyCameras = emptyList(),
            criticalPoiCoverage = poiCoverage,
            routeCameraCoverage = cameraCoverage,
            routeDataUpdatedAtMillis = timestamp,
            routeWeatherUpdatedAtMillis = timestamp
        )

        assertTrue(brief.items.isNotEmpty())
        assertTrue(brief.items.all { it.source.isNotBlank() })
        assertTrue(brief.items.all { (it.updatedAtMillis ?: 0L) > 0L })
    }

}
