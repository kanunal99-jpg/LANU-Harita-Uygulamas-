package com.example.haritalar.navigation

import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherType

enum class RoadIntelligencePriority(val rank: Int) {
    P0(0),
    P1(1),
    P2(2),
    P3(3)
}

enum class RoadIntelligenceType {
    CAMERA,
    WEATHER,
    TRAFFIC
}

data class RoadIntelligenceEvent(
    val type: RoadIntelligenceType,
    val priority: RoadIntelligencePriority,
    val title: String,
    val detail: String,
    val source: String,
    val distanceMeters: Double? = null,
    val updatedAtMillis: Long? = null
)

/**
 * Source-backed live-driving event prioritization.
 *
 * No event is fabricated: unavailable or unverified providers do not become warnings.
 * The highest-priority event is always first so the UI can avoid warning-card stacking.
 */
object RoadIntelligencePolicy {
    fun build(
        cameraWarning: SafetyCameraWarningPolicy.ProximityWarning?,
        weather: WeatherCondition?,
        traffic: TrafficStatus?
    ): List<RoadIntelligenceEvent> {
        val events = mutableListOf<RoadIntelligenceEvent>()

        cameraWarning?.let { warning ->
            events += RoadIntelligenceEvent(
                type = RoadIntelligenceType.CAMERA,
                priority = RoadIntelligencePriority.P1,
                title = if (warning.overspeed) {
                    "Hız kamerası • limit aşımı"
                } else {
                    "Sabit hız kamerası"
                },
                detail = buildString {
                    append(formatDistance(warning.distanceMeters))
                    warning.speedLimitKmh?.let { append(" • limit $it km/h") }
                    warning.estimatedSecondsToCamera?.let { append(" • yaklaşık ${formatEta(it)}") }
                },
                source = warning.camera.source,
                distanceMeters = warning.distanceMeters
            )
        }

        weather?.takeIf { it.type != WeatherType.CLEAR }?.let { condition ->
            events += RoadIntelligenceEvent(
                type = RoadIntelligenceType.WEATHER,
                priority = when (condition.type) {
                    WeatherType.STORM -> RoadIntelligencePriority.P0
                    WeatherType.SNOW, WeatherType.FOG -> RoadIntelligencePriority.P1
                    WeatherType.RAIN -> RoadIntelligencePriority.P2
                    WeatherType.CLEAR -> RoadIntelligencePriority.P3
                },
                title = condition.description.ifBlank { weatherLabel(condition.type) },
                detail = "Rota üzerinde yaklaşan ${weatherLabel(condition.type)} koşulu",
                source = "Open-Meteo"
            )
        }

        traffic
            ?.takeIf {
                it.verified &&
                    (it.trafficLevel == TrafficLevel.HEAVY || it.trafficLevel == TrafficLevel.SEVERE)
            }
            ?.let { status ->
                val delayMinutes = ((status.delaySeconds + 30L) / 60L).coerceAtLeast(0L)
                events += RoadIntelligenceEvent(
                    type = RoadIntelligenceType.TRAFFIC,
                    priority = RoadIntelligencePriority.P2,
                    title = status.message.ifBlank { "Yoğun trafik" },
                    detail = if (delayMinutes > 0L) {
                        "+$delayMinutes dk doğrulanmış rota gecikmesi"
                    } else {
                        "Doğrulanmış yoğun trafik"
                    },
                    source = status.sourceName,
                    updatedAtMillis = status.lastCheckTimestamp.takeIf { it > 0L }
                )
            }

        return events.sortedWith(
            compareBy<RoadIntelligenceEvent> { it.priority.rank }
                .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
                .thenBy { it.type.ordinal }
        )
    }

    private fun formatDistance(distanceMeters: Double): String =
        if (distanceMeters >= 1000.0) {
            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.coerceAtLeast(0.0).toInt()} m"
        }

    private fun formatEta(seconds: Int): String =
        if (seconds >= 60) {
            val roundedMinutes = ((seconds + 30) / 60).coerceAtLeast(1)
            "$roundedMinutes dk"
        } else {
            "$seconds sn"
        }

    private fun weatherLabel(type: WeatherType): String = when (type) {
        WeatherType.CLEAR -> "açık hava"
        WeatherType.RAIN -> "yağmur"
        WeatherType.FOG -> "sis"
        WeatherType.SNOW -> "kar"
        WeatherType.STORM -> "fırtına"
    }
}
