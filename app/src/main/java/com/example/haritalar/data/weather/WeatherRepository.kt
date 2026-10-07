package com.example.haritalar.data.weather

import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.WeatherCondition
import com.example.haritalar.model.WeatherDataMode
import com.example.haritalar.model.WeatherType
import com.example.haritalar.navigation.RouteWeatherForecastPolicy
import com.example.haritalar.navigation.RouteWeatherSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    suspend fun getRouteWeather(
        route: RouteOption,
        departureEpochMillis: Long = System.currentTimeMillis()
    ): List<WeatherCondition> = withContext(Dispatchers.IO) {
        val samples = RouteWeatherForecastPolicy.sampleRoute(route)
        if (samples.isEmpty()) return@withContext emptyList()

        try {
            val latitudes = samples.joinToString(",") { it.point.latitude.toString() }
            val longitudes = samples.joinToString(",") { it.point.longitude.toString() }
            val forecastHours = RouteWeatherForecastPolicy.forecastHours(route.totalDurationSeconds)
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitudes" +
                "&longitude=$longitudes" +
                "&hourly=weather_code,precipitation,rain,showers,snowfall" +
                "&current=weather_code,precipitation,rain,showers,snowfall" +
                "&forecast_hours=$forecastHours" +
                "&timeformat=unixtime" +
                "&timezone=GMT"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LanuHaritaAndroid/1.1.11")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val locations = parseLocations(body, samples.size)

                samples.mapIndexedNotNull { index, sample ->
                    val location = locations.getOrNull(index) ?: return@mapIndexedNotNull null
                    val targetEpochSeconds =
                        (departureEpochMillis / 1000L) + sample.etaSecondsFromStart

                    parseArrivalForecast(
                        location = location,
                        sample = sample,
                        targetEpochSeconds = targetEpochSeconds
                    ) ?: parseCurrentFallback(location, sample)
                }
            }
        } catch (_: Exception) {
            // Weather is non-critical. Never synthesize weather when the provider is unavailable.
            emptyList()
        }
    }

    private fun parseLocations(body: String, expectedCount: Int): List<JSONObject> {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            return List(array.length()) { index -> array.getJSONObject(index) }
        }
        if (expectedCount == 1 && trimmed.startsWith("{")) {
            return listOf(JSONObject(trimmed))
        }
        return emptyList()
    }

    private fun parseArrivalForecast(
        location: JSONObject,
        sample: RouteWeatherSample,
        targetEpochSeconds: Long
    ): WeatherCondition? {
        val hourly = location.optJSONObject("hourly") ?: return null
        val timesArray = hourly.optJSONArray("time") ?: return null
        val weatherCodes = hourly.optJSONArray("weather_code") ?: return null
        if (timesArray.length() == 0 || weatherCodes.length() == 0) return null

        val times = List(timesArray.length()) { index -> timesArray.optLong(index, Long.MIN_VALUE) }
            .filter { it != Long.MIN_VALUE }
        val selectedIndex = RouteWeatherForecastPolicy.nearestForecastIndex(
            timesEpochSeconds = times,
            targetEpochSeconds = targetEpochSeconds
        ) ?: return null
        if (selectedIndex >= weatherCodes.length()) return null

        val code = weatherCodes.optInt(selectedIndex, -1)
        if (code < 0) return null

        val precipitation = hourly.optJSONArray("precipitation")
            ?.optDouble(selectedIndex, 0.0) ?: 0.0
        val rain = hourly.optJSONArray("rain")
            ?.optDouble(selectedIndex, 0.0) ?: 0.0
        val showers = hourly.optJSONArray("showers")
            ?.optDouble(selectedIndex, 0.0) ?: 0.0
        val snowfall = hourly.optJSONArray("snowfall")
            ?.optDouble(selectedIndex, 0.0) ?: 0.0

        return buildCondition(
            sample = sample,
            code = code,
            precipitation = precipitation,
            rain = rain,
            showers = showers,
            snowfall = snowfall,
            forecastEpochMillis = times.getOrNull(selectedIndex)?.times(1000L),
            mode = WeatherDataMode.ARRIVAL_FORECAST
        )
    }

    private fun parseCurrentFallback(
        location: JSONObject,
        sample: RouteWeatherSample
    ): WeatherCondition? {
        val current = location.optJSONObject("current") ?: return null
        val code = current.optInt("weather_code", -1)
        if (code < 0) return null

        return buildCondition(
            sample = sample,
            code = code,
            precipitation = current.optDouble("precipitation", 0.0),
            rain = current.optDouble("rain", 0.0),
            showers = current.optDouble("showers", 0.0),
            snowfall = current.optDouble("snowfall", 0.0),
            forecastEpochMillis = null,
            mode = WeatherDataMode.CURRENT_FALLBACK
        )
    }

    private fun buildCondition(
        sample: RouteWeatherSample,
        code: Int,
        precipitation: Double,
        rain: Double,
        showers: Double,
        snowfall: Double,
        forecastEpochMillis: Long?,
        mode: WeatherDataMode
    ): WeatherCondition {
        val type = weatherTypeForCode(code)
        val intensity = when (type) {
            WeatherType.CLEAR -> 0.0
            WeatherType.FOG -> 0.25
            WeatherType.SNOW -> snowfall.coerceAtLeast(0.2)
            WeatherType.RAIN -> maxOf(precipitation, rain, showers).coerceAtLeast(0.2)
            WeatherType.STORM -> maxOf(precipitation, rain, showers).coerceAtLeast(0.5)
        }.coerceIn(0.0, 1.0).toFloat()

        return WeatherCondition(
            point = sample.point,
            type = type,
            description = descriptionFor(type, code),
            intensity = intensity,
            routeDistanceMeters = sample.distanceMeters,
            etaSecondsFromStart = sample.etaSecondsFromStart,
            forecastEpochMillis = forecastEpochMillis,
            dataMode = mode
        )
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
}
