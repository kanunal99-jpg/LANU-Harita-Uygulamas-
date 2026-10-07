package com.example.haritalar.model

import java.util.UUID

enum class WeatherType {
    CLEAR, RAIN, FOG, SNOW, STORM
}

enum class WeatherDataMode {
    ARRIVAL_FORECAST,
    CURRENT_FALLBACK
}

data class WeatherCondition(
    val id: String = UUID.randomUUID().toString(),
    val point: GeoPoint,
    val type: WeatherType,
    val description: String,
    val intensity: Float = 1.0f,
    val routeDistanceMeters: Double? = null,
    val etaSecondsFromStart: Long? = null,
    val forecastEpochMillis: Long? = null,
    val dataMode: WeatherDataMode = WeatherDataMode.CURRENT_FALLBACK
)
