package com.example.haritalar.data.weather

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    suspend fun getRouteWeather(route: RouteOption): List<WeatherCondition> = withContext(Dispatchers.IO) {
        val points = sampleRoutePoints(route.geometry)
        if (points.isEmpty()) return@withContext emptyList()

        try {
            val latitudes = points.joinToString(",") { it.latitude.toString() }
            val longitudes = points.joinToString(",") { it.longitude.toString() }
            val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitudes" +
                    "&longitude=$longitudes" +
                    "&current=weather_code,precipitation,rain,showers,snowfall" +
                    "&timezone=auto"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LanuHaritaAndroid/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = if (points.size == 1) {
                    listOf(JSONObject(body))
                } else {
                    val array = JSONArray(body)
                    List(array.length()) { index -> array.getJSONObject(index) }
                }

                parsed.mapIndexedNotNull { index, current ->
                    val code = current.optInt("current", JSONObject()).optInt("weather_code", -1)
                    if (code < 0 || index >= points.size) return@mapIndexedNotNull null

                    val precipitation = current.optJSONObject("current")
                        ?.optDouble("precipitation", 0.0) ?: 0.0
                    val rain = current.optJSONObject("current")
                        ?.optDouble("rain", 0.0) ?: 0.0
                    val showers = current.optJSONObject("current")
                        ?.optDouble("showers", 0.0) ?: 0.0
                    val snowfall = current.optJSONObject("current")
                        ?.optDouble("snowfall", 0.0) ?: 0.0

                    val type = weatherTypeForCode(code)
                    val intensity = when (type) {
                        WeatherType.CLEAR -> 0.0
                        WeatherType.FOG -> 0.25
                        WeatherType.SNOW -> snowfall.coerceAtLeast(0.2)
                        WeatherType.RAIN -> maxOf(precipitation, rain, showers).coerceAtLeast(0.2)
                        WeatherType.STORM -> maxOf(precipitation, rain, showers).coerceAtLeast(0.5)
                    }.coerceIn(0.0, 1.0).toFloat()

                    WeatherCondition(
                        point = points[index],
                        type = type,
                        description = descriptionFor(type, code),
                        intensity = intensity
                    )
                }
            }
        } catch (_: Exception) {
            // Weather is non-critical. Never synthesize weather when the provider is unavailable.
            emptyList()
        }
    }

    private fun sampleRoutePoints(geometry: List<GeoPoint>): List<GeoPoint> {
        if (geometry.isEmpty()) return emptyList()
        if (geometry.size <= MAX_ROUTE_SAMPLES) return geometry
        return List(MAX_ROUTE_SAMPLES) { index ->
            val position = index.toDouble() / (MAX_ROUTE_SAMPLES - 1)
            geometry[(position * geometry.lastIndex).roundToInt().coerceIn(0, geometry.lastIndex)]
        }.distinct()
    }

    private fun weatherTypeForCode(code: Int): WeatherType = when (code) {
        0, 1, 2, 3 -> WeatherType.CLEAR
        45, 48 -> WeatherType.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherType.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherType.SNOW
        95, 96, 99 -> WeatherType.STORM
        else -> WeatherType.CLEAR
    }

    private fun descriptionFor(type: WeatherType, code: Int): String = when (type) {
        WeatherType.CLEAR -> if (code == 0) "Açık" else "Parçalı bulutlu"
        WeatherType.RAIN -> "Yağış"
        WeatherType.FOG -> "Sis - görüş mesafesi düşük"
        WeatherType.SNOW -> "Kar yağışı / buzlanma riski"
        WeatherType.STORM -> "Fırtına / gök gürültülü hava"
    }

    private companion object {
        const val MAX_ROUTE_SAMPLES = 6
    }
}
