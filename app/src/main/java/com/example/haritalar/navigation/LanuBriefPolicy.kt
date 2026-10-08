package com.example.haritalar.navigation

import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.RouteCriticalPoiDataState
import com.example.haritalar.model.RouteCriticalPoiMatch
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.RoadFeature
import com.example.haritalar.model.RoadFeatureDataState
import com.example.haritalar.model.RoadFeatureType
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherDataMode
import com.example.haritalar.model.WeatherType

enum class LanuBriefStatus { VERIFIED, PARTIAL, UNAVAILABLE }
enum class LanuBriefSeverity { INFO, NOTICE, WARNING, CRITICAL }
enum class LanuBriefItemType { ROAD_CLOSURE, TRAFFIC, CAMERA, ROAD_FEATURE, CRITICAL_SERVICES, WEATHER, TOLL, FERRY, DATA_QUALITY }

data class LanuBriefItem(
    val type: LanuBriefItemType,
    val title: String,
    val detail: String,
    val source: String,
    val status: LanuBriefStatus,
    val severity: LanuBriefSeverity = LanuBriefSeverity.INFO
)

data class LanuDriveBrief(
    val routeId: String,
    val distanceMeters: Double,
    val totalDurationSeconds: Long,
    val items: List<LanuBriefItem>,
    val verifiedItemCount: Int,
    val partialItemCount: Int,
    val unavailableItemCount: Int
)

/**
 * Pure pre-drive briefing policy.
 * It summarizes only source-backed facts already loaded by LANU.
 */
object LanuBriefPolicy {
    fun build(
        route: RouteOption,
        traffic: TrafficStatus?,
        routeWeather: List<WeatherCondition>,
        loadedSafetyCameras: List<SafetyCamera>,
        trafficSegments: List<TrafficSegment> = emptyList(),
        roadFeatures: List<RoadFeature> = emptyList(),
        roadFeatureDataState: RoadFeatureDataState = RoadFeatureDataState.IDLE,
        cameraRouteScanComplete: Boolean = false,
        routeCriticalPois: List<RouteCriticalPoiMatch> = emptyList(),
        routeCriticalPoiDataState: RouteCriticalPoiDataState = RouteCriticalPoiDataState.IDLE
    ): LanuDriveBrief {
        val items = mutableListOf<LanuBriefItem>()

        closureItem(traffic, trafficSegments)?.let { items += it }
        items += trafficItem(route, traffic)
        items += weatherItem(routeWeather)
        items += cameraItem(route, loadedSafetyCameras, cameraRouteScanComplete)
        items += roadFeatureItem(roadFeatures, roadFeatureDataState)
        items += criticalServicesItem(routeCriticalPois, routeCriticalPoiDataState)

        items += if (route.hasTolls) {
            LanuBriefItem(
                type = LanuBriefItemType.TOLL,
                title = "Ücretli geçiş var",
                detail = "Seçili rota ücretli yol veya ücretli geçiş içeriyor.",
                source = "Rota sağlayıcısı",
                status = LanuBriefStatus.VERIFIED,
                severity = LanuBriefSeverity.NOTICE
            )
        } else {
            LanuBriefItem(
                type = LanuBriefItemType.TOLL,
                title = "Ücretli geçiş işareti yok",
                detail = "Rota sağlayıcısı seçili rotada ücretli geçiş bildirmedi.",
                source = "Rota sağlayıcısı",
                status = LanuBriefStatus.VERIFIED
            )
        }

        if (route.hasFerry) {
            items += LanuBriefItem(
                type = LanuBriefItemType.FERRY,
                title = "Feribot geçişi var",
                detail = "Seçili rota feribot bölümü içeriyor.",
                source = "Rota sağlayıcısı",
                status = LanuBriefStatus.VERIFIED,
                severity = LanuBriefSeverity.NOTICE
            )
        }

        val partial = items.count { it.status == LanuBriefStatus.PARTIAL }
        val unavailable = items.count { it.status == LanuBriefStatus.UNAVAILABLE }
        val verified = items.count { it.status == LanuBriefStatus.VERIFIED }

        if (partial > 0 || unavailable > 0) {
            items += LanuBriefItem(
                type = LanuBriefItemType.DATA_QUALITY,
                title = "Veri kapsamı",
                detail = buildString {
                    append("$verified doğrulanmış")
                    if (partial > 0) append(" • $partial kısmi")
                    if (unavailable > 0) append(" • $unavailable doğrulanamadı")
                },
                source = "LANU veri kalite motoru",
                status = if (unavailable > 0) LanuBriefStatus.PARTIAL else LanuBriefStatus.VERIFIED,
                severity = if (unavailable > 0) LanuBriefSeverity.NOTICE else LanuBriefSeverity.INFO
            )
        }

        return LanuDriveBrief(
            routeId = route.routeId,
            distanceMeters = route.distanceMeters,
            totalDurationSeconds = route.totalDurationSeconds,
            items = items,
            verifiedItemCount = verified,
            partialItemCount = partial,
            unavailableItemCount = unavailable
        )
    }

    private fun closureItem(
        traffic: TrafficStatus?,
        trafficSegments: List<TrafficSegment>
    ): LanuBriefItem? {
        if (traffic?.verified != true) return null
        val closures = trafficSegments.filter { it.roadClosure && !it.fromCache }
        if (closures.isEmpty()) return null

        return LanuBriefItem(
            type = LanuBriefItemType.ROAD_CLOSURE,
            title = if (closures.size == 1) "Rota üzerinde yol kapanışı" else "${closures.size} yol kapanışı rota üzerinde",
            detail = "Canlı trafik sağlayıcısı seçili rota üzerinde doğrulanmış kapanış bildirdi. Alternatif rotayı değerlendirin.",
            source = traffic.sourceName,
            status = LanuBriefStatus.VERIFIED,
            severity = LanuBriefSeverity.CRITICAL
        )
    }

    private fun trafficItem(route: RouteOption, traffic: TrafficStatus?): LanuBriefItem {
        if (traffic?.verified != true) {
            return LanuBriefItem(
                type = LanuBriefItemType.TRAFFIC,
                title = "Canlı trafik doğrulanamadı",
                detail = "Temel rota süresi korunuyor; doğrulanmamış trafik gecikmesi eklenmiyor.",
                source = traffic?.sourceName ?: "Trafik sağlayıcısı",
                status = LanuBriefStatus.UNAVAILABLE,
                severity = LanuBriefSeverity.NOTICE
            )
        }

        val delaySeconds = maxOf(route.trafficDelaySeconds, traffic.delaySeconds)
        val delayMinutes = ((delaySeconds + 30L) / 60L).coerceAtLeast(0L)
        return if (delayMinutes >= 1L) {
            LanuBriefItem(
                type = LanuBriefItemType.TRAFFIC,
                title = "+$delayMinutes dk trafik gecikmesi",
                detail = traffic.message.ifBlank { "Canlı trafik sağlayıcısı rota gecikmesi bildirdi." },
                source = traffic.sourceName,
                status = LanuBriefStatus.VERIFIED,
                severity = when {
                    delayMinutes >= 20 -> LanuBriefSeverity.CRITICAL
                    delayMinutes >= 10 -> LanuBriefSeverity.WARNING
                    else -> LanuBriefSeverity.NOTICE
                }
            )
        } else {
            LanuBriefItem(
                type = LanuBriefItemType.TRAFFIC,
                title = "Belirgin trafik gecikmesi yok",
                detail = traffic.message.ifBlank { "Canlı trafik kontrolü rota üzerinde önemli gecikme göstermiyor." },
                source = traffic.sourceName,
                status = LanuBriefStatus.VERIFIED
            )
        }
    }

    private fun weatherItem(weather: List<WeatherCondition>): LanuBriefItem {
        if (weather.isEmpty()) {
            return LanuBriefItem(
                type = LanuBriefItemType.WEATHER,
                title = "Rota havası doğrulanamadı",
                detail = "Hava sağlayıcısından rota örnekleri alınamadı; hava koşulu uydurulmuyor.",
                source = "Open-Meteo",
                status = LanuBriefStatus.UNAVAILABLE,
                severity = LanuBriefSeverity.NOTICE
            )
        }

        val fallbackCount = weather.count { it.dataMode == WeatherDataMode.CURRENT_FALLBACK }
        val forecastCount = weather.size - fallbackCount
        val weatherStatus = if (fallbackCount > 0) LanuBriefStatus.PARTIAL else LanuBriefStatus.VERIFIED
        val weatherSource = when {
            fallbackCount == 0 -> "Open-Meteo saatlik tahmin"
            forecastCount == 0 -> "Open-Meteo mevcut hava (fallback)"
            else -> "Open-Meteo saatlik tahmin + mevcut hava fallback"
        }

        val risky = weather.filter { it.type != WeatherType.CLEAR }
        if (risky.isEmpty()) {
            return LanuBriefItem(
                type = LanuBriefItemType.WEATHER,
                title = "Önemli hava riski görünmüyor",
                detail = "Rota üzerindeki örnek noktalar varış zamanlarına göre kontrol edildi.",
                source = weatherSource,
                status = weatherStatus,
                severity = if (weatherStatus == LanuBriefStatus.PARTIAL) {
                    LanuBriefSeverity.NOTICE
                } else {
                    LanuBriefSeverity.INFO
                }
            )
        }

        val worst = risky.sortedWith(
            compareByDescending<WeatherCondition> { weatherRiskRank(it.type) }
                .thenBy { it.routeDistanceMeters ?: Double.MAX_VALUE }
        ).first()
        val counts = risky.groupingBy { it.type }.eachCount()
        val riskSummary = counts.entries
            .sortedByDescending { weatherRiskRank(it.key) }
            .joinToString(" • ") { (type, count) -> "$count× ${weatherLabel(type)}" }
        val position = formatWeatherPosition(worst)
        val detail = listOfNotNull(
            position.takeIf { it.isNotBlank() },
            riskSummary.takeIf { it.isNotBlank() }
        ).joinToString(" • ")

        return LanuBriefItem(
            type = LanuBriefItemType.WEATHER,
            title = worst.description,
            detail = detail,
            source = weatherSource,
            status = weatherStatus,
            severity = when (worst.type) {
                WeatherType.STORM -> LanuBriefSeverity.CRITICAL
                WeatherType.SNOW -> LanuBriefSeverity.WARNING
                WeatherType.FOG, WeatherType.RAIN -> LanuBriefSeverity.NOTICE
                WeatherType.CLEAR -> LanuBriefSeverity.INFO
            }
        )
    }

    private fun formatWeatherPosition(condition: WeatherCondition): String {
        val parts = mutableListOf<String>()
        condition.routeDistanceMeters?.let { distance ->
            parts += if (distance >= 1000.0) {
                String.format(java.util.Locale.US, "%.1f km", distance / 1000.0)
            } else {
                "${distance.coerceAtLeast(0.0).toInt()} m"
            }
        }
        condition.etaSecondsFromStart?.let { seconds ->
            val minutes = ((seconds + 30L) / 60L).coerceAtLeast(0L)
            parts += if (minutes == 0L) "başlangıçta" else "yaklaşık $minutes dk sonra"
        }
        return parts.joinToString(" • ")
    }

    private fun cameraItem(
        route: RouteOption,
        loadedCameras: List<SafetyCamera>,
        routeScanComplete: Boolean
    ): LanuBriefItem {
        if (route.geometry.size < 2) {
            return LanuBriefItem(
                type = LanuBriefItemType.CAMERA,
                title = "Kamera rota kontrolü yapılamadı",
                detail = "Rota geometrisi kamera koridoru doğrulaması için yetersiz.",
                source = "OpenStreetMap",
                status = LanuBriefStatus.UNAVAILABLE,
                severity = LanuBriefSeverity.NOTICE
            )
        }

        if (!routeScanComplete) {
            return LanuBriefItem(
                type = LanuBriefItemType.CAMERA,
                title = "Rota radar/kamera taraması sürüyor",
                detail = "Seçili güzergâhın tamamı OpenStreetMap radar/kamera verisiyle eşleştiriliyor.",
                source = "OpenStreetMap",
                status = LanuBriefStatus.PARTIAL,
                severity = LanuBriefSeverity.INFO
            )
        }

        val onRoute = SafetyCameraRouteFilterPolicy.camerasAlongRoute(
            cameras = loadedCameras,
            route = route.geometry
        )

        val cameraTypeSummary = onRoute
            .groupingBy { it.camera.type }
            .eachCount()
            .entries
            .sortedBy { it.key.ordinal }
            .joinToString(" • ") { (type, count) -> "$count ${type.displayName.lowercase()}" }

        return LanuBriefItem(
            type = LanuBriefItemType.CAMERA,
            title = if (onRoute.isEmpty()) {
                "OSM tam rota taramasında kamera kaydı bulunamadı"
            } else {
                "${onRoute.size} kamera/denetim noktası rota üzerinde"
            },
            detail = if (onRoute.isEmpty()) {
                "Tam güzergâh taraması tamamlandı; OpenStreetMap verisinde eşleşen kamera kaydı yok. Bu, sahada kesinlikle kamera olmadığı anlamına gelmez."
            } else {
                "Tam güzergâh taraması tamamlandı: $cameraTypeSummary. Bilinen noktalar rota kilometresiyle eşleştirildi."
            },
            source = "OpenStreetMap",
            status = LanuBriefStatus.PARTIAL,
            severity = if (onRoute.isNotEmpty()) LanuBriefSeverity.NOTICE else LanuBriefSeverity.INFO
        )
    }

    private fun roadFeatureItem(
        features: List<RoadFeature>,
        dataState: RoadFeatureDataState
    ): LanuBriefItem {
        if (dataState == RoadFeatureDataState.UNAVAILABLE) {
            return LanuBriefItem(
                type = LanuBriefItemType.ROAD_FEATURE,
                title = "Yol özellikleri doğrulanamadı",
                detail = "OSM yol özelliği kaynaklarına ulaşılamadı; bilgi uydurulmuyor.",
                source = "OpenStreetMap / Overpass",
                status = LanuBriefStatus.UNAVAILABLE,
                severity = LanuBriefSeverity.NOTICE
            )
        }
        if (dataState == RoadFeatureDataState.IDLE) {
            return LanuBriefItem(
                type = LanuBriefItemType.ROAD_FEATURE,
                title = "Yol özellikleri yükleniyor",
                detail = "Rota koridorundaki okul, tümsek, hemzemin geçit ve yol tehlikesi kayıtları kontrol ediliyor.",
                source = "OpenStreetMap / Overpass",
                status = LanuBriefStatus.PARTIAL,
                severity = LanuBriefSeverity.INFO
            )
        }

        val status = if (dataState == RoadFeatureDataState.CACHED) {
            LanuBriefStatus.PARTIAL
        } else {
            LanuBriefStatus.VERIFIED
        }
        val source = if (dataState == RoadFeatureDataState.CACHED) {
            "OpenStreetMap (24 saatlik rota önbelleği)"
        } else {
            "OpenStreetMap / Overpass"
        }

        if (features.isEmpty()) {
            return LanuBriefItem(
                type = LanuBriefItemType.ROAD_FEATURE,
                title = "Rota koridorunda yol özelliği görünmüyor",
                detail = if (status == LanuBriefStatus.PARTIAL) {
                    "Aynı rota için önbellekte okul/tümsek/geçit/tehlike kaydı bulunmadı."
                } else {
                    "Sorgulanan OSM rota koridorunda desteklenen yol özelliği bulunmadı."
                },
                source = source,
                status = status
            )
        }

        val counts = features.groupingBy { it.type }.eachCount()
        val labels = listOf(
            RoadFeatureType.SPEED_CALMING to "tümsek/yavaşlatma",
            RoadFeatureType.SCHOOL_ZONE to "okul",
            RoadFeatureType.LEVEL_CROSSING to "hemzemin geçit",
            RoadFeatureType.ROAD_HAZARD to "yol tehlikesi"
        ).mapNotNull { (type, label) ->
            counts[type]?.takeIf { it > 0 }?.let { "$it× $label" }
        }

        val severity = when {
            (counts[RoadFeatureType.LEVEL_CROSSING] ?: 0) > 0 ||
                (counts[RoadFeatureType.ROAD_HAZARD] ?: 0) > 0 -> LanuBriefSeverity.WARNING
            (counts[RoadFeatureType.SCHOOL_ZONE] ?: 0) > 0 ||
                (counts[RoadFeatureType.SPEED_CALMING] ?: 0) > 0 -> LanuBriefSeverity.NOTICE
            else -> LanuBriefSeverity.INFO
        }

        return LanuBriefItem(
            type = LanuBriefItemType.ROAD_FEATURE,
            title = "${features.size} yol özelliği rota koridorunda",
            detail = labels.joinToString(" • "),
            source = source,
            status = status,
            severity = severity
        )
    }

    private fun criticalServicesItem(
        matches: List<RouteCriticalPoiMatch>,
        dataState: RouteCriticalPoiDataState
    ): LanuBriefItem {
        if (dataState == RouteCriticalPoiDataState.UNAVAILABLE) {
            return LanuBriefItem(
                type = LanuBriefItemType.CRITICAL_SERVICES,
                title = "Kritik hizmetler doğrulanamadı",
                detail = "Benzinlik, hastane, eczane ve şarj noktası verisi alınamadı; bilgi uydurulmuyor.",
                source = "OpenStreetMap / LANU POI zinciri",
                status = LanuBriefStatus.UNAVAILABLE,
                severity = LanuBriefSeverity.NOTICE
            )
        }
        if (dataState == RouteCriticalPoiDataState.IDLE ||
            dataState == RouteCriticalPoiDataState.LOADING
        ) {
            return LanuBriefItem(
                type = LanuBriefItemType.CRITICAL_SERVICES,
                title = "Kritik hizmetler kontrol ediliyor",
                detail = "Rota çevresindeki benzinlik, hastane, eczane ve şarj noktaları taranıyor.",
                source = "OpenStreetMap / LANU POI zinciri",
                status = LanuBriefStatus.PARTIAL
            )
        }

        fun categoryLabel(category: PoiCategory): String = when (category) {
            PoiCategory.FUEL -> "Benzinlik"
            PoiCategory.HOSPITAL -> "Hastane"
            PoiCategory.PHARMACY -> "Eczane"
            PoiCategory.CHARGING_STATION -> "Şarj"
            else -> category.displayName
        }
        fun formatDistance(meters: Double): String =
            if (meters >= 1000.0) {
                String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
            } else {
                "${meters.coerceAtLeast(0.0).toInt()} m"
            }

        val parts = RouteCriticalPoiPolicy.CRITICAL_CATEGORIES.map { category ->
            val categoryMatches = matches.filter { it.poi.category == category }
            val first = categoryMatches.minByOrNull { it.routeDistanceMeters }
            buildString {
                append(categoryLabel(category))
                append(" ")
                append(categoryMatches.size)
                first?.let {
                    append(" • ilk ")
                    append(formatDistance(it.routeDistanceMeters))
                    append(" • rotadan ~")
                    append(formatDistance(it.corridorDistanceMeters))
                }
            }
        }

        return LanuBriefItem(
            type = LanuBriefItemType.CRITICAL_SERVICES,
            title = if (matches.isEmpty()) {
                "Yüklü veride kritik hizmet görünmüyor"
            } else {
                "${matches.size} kritik hizmet noktası rota çevresinde"
            },
            detail = parts.joinToString(" | "),
            source = "OpenStreetMap / LANU POI zinciri",
            status = if (dataState == RouteCriticalPoiDataState.VERIFIED) {
                LanuBriefStatus.VERIFIED
            } else {
                LanuBriefStatus.PARTIAL
            },
            severity = if (matches.isEmpty()) LanuBriefSeverity.INFO else LanuBriefSeverity.NOTICE
        )
    }

    private fun weatherRiskRank(type: WeatherType): Int = when (type) {
        WeatherType.CLEAR -> 0
        WeatherType.RAIN -> 1
        WeatherType.FOG -> 2
        WeatherType.SNOW -> 3
        WeatherType.STORM -> 4
    }

    private fun weatherLabel(type: WeatherType): String = when (type) {
        WeatherType.CLEAR -> "açık"
        WeatherType.RAIN -> "yağmur"
        WeatherType.FOG -> "sis"
        WeatherType.SNOW -> "kar"
        WeatherType.STORM -> "fırtına"
    }
}
