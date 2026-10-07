package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.RouteCriticalPoiDataState
import com.example.haritalar.model.RouteCriticalPoiMatch
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.RoadFeature
import com.example.haritalar.model.RoadFeatureDataState
import com.example.haritalar.model.RoadFeatureType
import com.example.haritalar.model.RouteType
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherDataMode
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
                intensity = 0.8f,
                routeDistanceMeters = 22_000.0,
                etaSecondsFromStart = 1_560L,
                forecastEpochMillis = 1_800_000L,
                dataMode = WeatherDataMode.ARRIVAL_FORECAST
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
    fun arrivalForecastShowsRoutePositionEtaAndSource() {
        val forecast = WeatherCondition(
            point = route.geometry[1],
            type = WeatherType.FOG,
            description = "Yoğun sis",
            routeDistanceMeters = 22_400.0,
            etaSecondsFromStart = 26 * 60L,
            forecastEpochMillis = 1_800_000L,
            dataMode = WeatherDataMode.ARRIVAL_FORECAST
        )

        val brief = LanuBriefPolicy.build(route, null, listOf(forecast), emptyList())
        val item = brief.items.first { it.type == LanuBriefItemType.WEATHER }

        assertEquals(LanuBriefStatus.VERIFIED, item.status)
        assertEquals("Open-Meteo saatlik tahmin", item.source)
        assertTrue(item.detail.contains("22.4 km"))
        assertTrue(item.detail.contains("26 dk sonra"))
    }

    @Test
    fun currentWeatherFallbackIsExplicitlyPartial() {
        val fallback = WeatherCondition(
            point = route.geometry[1],
            type = WeatherType.RAIN,
            description = "Yağış",
            routeDistanceMeters = 10_000.0,
            etaSecondsFromStart = 15 * 60L,
            dataMode = WeatherDataMode.CURRENT_FALLBACK
        )

        val brief = LanuBriefPolicy.build(route, null, listOf(fallback), emptyList())
        val item = brief.items.first { it.type == LanuBriefItemType.WEATHER }

        assertEquals(LanuBriefStatus.PARTIAL, item.status)
        assertEquals("Open-Meteo mevcut hava (fallback)", item.source)
    }

    @Test
    fun verifiedRoadClosureAppearsAsCriticalPreDriveWarning() {
        val traffic = TrafficStatus(
            verified = true,
            message = "Canlı trafik",
            trafficLevel = TrafficLevel.MODERATE,
            sourceName = "Verified Traffic",
            isLiveApi = true
        )
        val closure = TrafficSegment(
            coordinates = listOf(route.geometry[1]),
            currentSpeed = 0.0,
            freeFlowSpeed = 50.0,
            delaySeconds = 300,
            roadClosure = true
        )

        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = traffic,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            trafficSegments = listOf(closure)
        )

        val closureItem = brief.items.first { it.type == LanuBriefItemType.ROAD_CLOSURE }
        assertEquals(LanuBriefStatus.VERIFIED, closureItem.status)
        assertEquals(LanuBriefSeverity.CRITICAL, closureItem.severity)
        assertEquals("Verified Traffic", closureItem.source)
    }

    @Test
    fun cachedRoadClosureIsNotPresentedAsVerifiedPreDriveClosure() {
        val traffic = TrafficStatus(
            verified = true,
            message = "Canlı trafik",
            trafficLevel = TrafficLevel.MODERATE,
            sourceName = "Verified Traffic",
            isLiveApi = true
        )
        val cachedClosure = TrafficSegment(
            coordinates = listOf(route.geometry[1]),
            currentSpeed = 0.0,
            freeFlowSpeed = 50.0,
            delaySeconds = 300,
            roadClosure = true,
            fromCache = true
        )

        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = traffic,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            trafficSegments = listOf(cachedClosure)
        )

        assertTrue(brief.items.none { it.type == LanuBriefItemType.ROAD_CLOSURE })
    }

    @Test
    fun cameraItemShowsScanningUntilSelectedRouteScanCompletes() {
        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            cameraRouteScanComplete = false
        )

        val cameraItem = brief.items.first { it.type == LanuBriefItemType.CAMERA }
        assertEquals(LanuBriefStatus.PARTIAL, cameraItem.status)
        assertEquals("Rota radar/kamera taraması sürüyor", cameraItem.title)
    }

    @Test
    fun completedEmptyScanDoesNotClaimRealWorldHasNoCameras() {
        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            cameraRouteScanComplete = true
        )

        val cameraItem = brief.items.first { it.type == LanuBriefItemType.CAMERA }
        assertTrue(cameraItem.title.contains("OSM tam rota taramasında"))
        assertTrue(cameraItem.detail.contains("kesinlikle kamera olmadığı anlamına gelmez"))
    }

    @Test
    fun longRouteCameraCountIsExplicitlyPartial() {
        val camera = SafetyCamera(id = 42L, point = GeoPoint(40.91, 29.25))
        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = listOf(camera),
            cameraRouteScanComplete = true
        )
        val cameraItem = brief.items.first { it.type == LanuBriefItemType.CAMERA }

        assertEquals(LanuBriefStatus.PARTIAL, cameraItem.status)
        assertTrue(cameraItem.title.contains("1 sabit kamera"))
    }

    @Test
    fun verifiedRoadFeaturesAppearInPreDriveBrief() {
        val features = listOf(
            RoadFeature(
                id = "school",
                point = route.geometry[1],
                type = RoadFeatureType.SCHOOL_ZONE,
                title = "Okul bölgesi",
                detail = "Örnek Okul"
            ),
            RoadFeature(
                id = "crossing",
                point = route.geometry[1],
                type = RoadFeatureType.LEVEL_CROSSING,
                title = "Hemzemin geçit",
                detail = "OSM demiryolu geçişi"
            )
        )

        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            roadFeatures = features,
            roadFeatureDataState = RoadFeatureDataState.VERIFIED
        )

        val item = brief.items.first { it.type == LanuBriefItemType.ROAD_FEATURE }
        assertEquals(LanuBriefStatus.VERIFIED, item.status)
        assertEquals(LanuBriefSeverity.WARNING, item.severity)
        assertTrue(item.title.contains("2 yol özelliği"))
        assertTrue(item.detail.contains("okul"))
        assertTrue(item.detail.contains("hemzemin geçit"))
    }

    @Test
    fun cachedRoadFeaturesAreExplicitlyPartial() {
        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            roadFeatures = emptyList(),
            roadFeatureDataState = RoadFeatureDataState.CACHED
        )

        val item = brief.items.first { it.type == LanuBriefItemType.ROAD_FEATURE }
        assertEquals(LanuBriefStatus.PARTIAL, item.status)
        assertTrue(item.source.contains("önbelle"))
    }


    @Test
    fun verifiedCriticalServicesShowCountsRouteKmAndCorridorDistance() {
        val matches = listOf(
            RouteCriticalPoiMatch(
                poi = PoiItem(
                    id = "fuel_1",
                    name = "Rota Benzinlik",
                    category = PoiCategory.FUEL,
                    point = route.geometry[1]
                ),
                routeDistanceMeters = 7_200.0,
                corridorDistanceMeters = 180.0
            ),
            RouteCriticalPoiMatch(
                poi = PoiItem(
                    id = "pharmacy_1",
                    name = "Rota Eczane",
                    category = PoiCategory.PHARMACY,
                    point = route.geometry[1]
                ),
                routeDistanceMeters = 12_500.0,
                corridorDistanceMeters = 420.0
            )
        )

        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            cameraRouteScanComplete = true,
            routeCriticalPois = matches,
            routeCriticalPoiDataState = RouteCriticalPoiDataState.VERIFIED
        )

        val item = brief.items.first { it.type == LanuBriefItemType.CRITICAL_SERVICES }
        assertEquals(LanuBriefStatus.VERIFIED, item.status)
        assertTrue(item.title.contains("2 kritik hizmet"))
        assertTrue(item.detail.contains("Benzinlik 1"))
        assertTrue(item.detail.contains("ilk 7.2 km"))
        assertTrue(item.detail.contains("rotadan ~180 m"))
        assertTrue(item.detail.contains("Eczane 1"))
    }

    @Test
    fun unavailableCriticalServiceProviderNeverPretendsZeroServicesIsVerified() {
        val brief = LanuBriefPolicy.build(
            route = route,
            traffic = null,
            routeWeather = emptyList(),
            loadedSafetyCameras = emptyList(),
            cameraRouteScanComplete = true,
            routeCriticalPois = emptyList(),
            routeCriticalPoiDataState = RouteCriticalPoiDataState.UNAVAILABLE
        )

        val item = brief.items.first { it.type == LanuBriefItemType.CRITICAL_SERVICES }
        assertEquals(LanuBriefStatus.UNAVAILABLE, item.status)
        assertTrue(item.title.contains("doğrulanamadı"))
    }

}
