package com.example.haritalar.navigation

import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherType

enum class LanuBriefStatus { VERIFIED, PARTIAL, UNAVAILABLE }
enum class LanuBriefSeverity { INFO, NOTICE, WARNING, CRITICAL }
enum class LanuBriefItemType { ROAD_CLOSURE, TRAFFIC, CAMERA, WEATHER, TOLL, FERRY, DATA_QUALITY }

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
        trafficSegments: List<TrafficSegment> = emptyList()
    ): LanuDriveBrief {
        val items = mutableListOf<LanuBriefItem>()

        closureItem(traffic, trafficSegments)?.let { items += it }
        items += trafficItem(route, traffic)
        items += weatherItem(routeWeather)
        items += cameraItem(route, loadedSafetyCameras)

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

        val risky = weather.filter { it.type != WeatherType.CLEAR }
        if (risky.isEmpty()) {
            return LanuBriefItem(
                type = LanuBriefItemType.WEATHER,
                title = "Önemli hava riski görünmüyor",
                detail = "Open-Meteo tarafından örneklenen rota noktalarında yağmur, kar, sis veya fırtına işareti yok.",
                source = "Open-Meteo",
                status = LanuBriefStatus.VERIFIED
            )
        }

        val worst = risky.maxByOrNull { weatherRiskRank(it.type) } ?: risky.first()
        val counts = risky.groupingBy { it.type }.eachCount()
        val detail = counts.entries
            .sortedByDescending { weatherRiskRank(it.key) }
            .joinToString(" • ") { (type, count) -> "$count× ${weatherLabel(type)}" }

        return LanuBriefItem(
            type = LanuBriefItemType.WEATHER,
            title = worst.description,
            detail = "$detail • rota örnek noktalarında",
            source = "Open-Meteo",
            status = LanuBriefStatus.VERIFIED,
            severity = when (worst.type) {
                WeatherType.STORM -> LanuBriefSeverity.CRITICAL
                WeatherType.SNOW -> LanuBriefSeverity.WARNING
                WeatherType.FOG, WeatherType.RAIN -> LanuBriefSeverity.NOTICE
                WeatherType.CLEAR -> LanuBriefSeverity.INFO
            }
        )
    }

    private fun cameraItem(route: RouteOption, loadedCameras: List<SafetyCamera>): LanuBriefItem {
        val start = route.geometry.firstOrNull()
        if (start == null || route.geometry.size < 2) {
            return LanuBriefItem(
                type = LanuBriefItemType.CAMERA,
                title = "Kamera rota kontrolü yapılamadı",
                detail = "Rota geometrisi kamera koridoru doğrulaması için yetersiz.",
                source = "OpenStreetMap",
                status = LanuBriefStatus.UNAVAILABLE,
                severity = LanuBriefSeverity.NOTICE
            )
        }

        val onRoute = SafetyCameraRouteFilterPolicy.relevantForRoute(
            cameras = loadedCameras,
            route = route.geometry,
            userPoint = start
        )

        val coverageStatus = if (route.distanceMeters <= 10_000.0) {
            LanuBriefStatus.VERIFIED
        } else {
            LanuBriefStatus.PARTIAL
        }
        val coverageNote = if (coverageStatus == LanuBriefStatus.VERIFIED) {
            "Yüklü OSM kamera verisi seçili kısa rota koridoruyla eşleştirildi."
        } else {
            "Yüklü OSM kamera verisi rota koridoruyla eşleştirildi; uzun rotada tam rota kapsaması henüz garanti edilmiyor."
        }

        return LanuBriefItem(
            type = LanuBriefItemType.CAMERA,
            title = if (onRoute.isEmpty()) {
                "Yüklü veride rota kamerası görünmüyor"
            } else {
                "${onRoute.size} sabit kamera rota koridorunda"
            },
            detail = coverageNote,
            source = "OpenStreetMap",
            status = coverageStatus,
            severity = if (onRoute.isNotEmpty()) LanuBriefSeverity.NOTICE else LanuBriefSeverity.INFO
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
