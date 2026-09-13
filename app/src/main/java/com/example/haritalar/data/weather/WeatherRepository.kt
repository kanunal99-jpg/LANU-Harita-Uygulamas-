package com.example.haritalar.data.weather

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class WeatherRepository {

    suspend fun getRouteWeather(route: RouteOption): List<WeatherCondition> = withContext(Dispatchers.IO) {
        val conditions = mutableListOf<WeatherCondition>()
        val points = route.geometry
        if (points.isEmpty()) return@withContext conditions

        // We'll generate weather events occasionally along the route
        var accumulatedDistance = 0.0
        
        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]
            accumulatedDistance += p1.distanceTo(p2)
            
            // Every ~10 km (10000 meters), potentially add a weather event
            if (accumulatedDistance > 10000) {
                accumulatedDistance = 0.0
                
                // 40% chance of a severe weather event (rain, fog, storm, snow)
                if (Random.nextDouble() < 0.4) {
                    val weatherType = when (Random.nextInt(4)) {
                        0 -> WeatherType.RAIN
                        1 -> WeatherType.FOG
                        2 -> WeatherType.STORM
                        else -> WeatherType.SNOW
                    }
                    
                    val desc = when (weatherType) {
                        WeatherType.RAIN -> "Yoğun Yağış"
                        WeatherType.FOG -> "Yoğun Sis - Görüş Mesafesi Düşük"
                        WeatherType.STORM -> "Fırtına Uyarı"
                        WeatherType.SNOW -> "Kar Yağışı / Buzlanma"
                        else -> "Açık"
                    }
                    
                    conditions.add(
                        WeatherCondition(
                            point = p2,
                            type = weatherType,
                            description = desc,
                            intensity = Random.nextDouble(0.5, 1.0).toFloat()
                        )
                    )
                }
            }
        }
        
        // Ensure there is at least one weather condition for demonstration if route is long enough
        if (conditions.isEmpty() && route.distanceMeters > 5000) {
            val midPoint = points[points.size / 2]
            conditions.add(
                WeatherCondition(
                    point = midPoint,
                    type = WeatherType.RAIN,
                    description = "Lokal Sağanak Yağış",
                    intensity = 0.8f
                )
            )
        }
        
        conditions
    }
}
