package com.example.haritalar.model

import java.util.UUID

enum class WeatherType {
    CLEAR, RAIN, FOG, SNOW, STORM
}

data class WeatherCondition(
    val id: String = UUID.randomUUID().toString(),
    val point: GeoPoint,
    val type: WeatherType,
    val description: String,
    val intensity: Float = 1.0f 
)
