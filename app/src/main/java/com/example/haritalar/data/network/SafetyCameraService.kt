package com.example.haritalar.data.network

import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.model.SafetyCameraFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Reads fixed speed cameras from OpenStreetMap.
 *
 * Resilience: primary Overpass endpoint -> alternative mirrors -> explicit error.
 * Cache fallback is deliberately kept in the repository layer.
 */
class SafetyCameraService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val endpoints: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://z.overpass-api.de/api/interpreter"
    )
) {
    companion object {
        private const val TAG = "SafetyCameraService"
    }

    suspend fun fetchSpeedCamerasInBoundingBox(
        bbox: SafetyCameraBoundingBox,
        maxCameras: Int = 100
    ): SafetyCameraFetchResult = withContext(Dispatchers.IO) {
        if (!bbox.isValid()) {
            return@withContext SafetyCameraFetchResult.Error(
                message = "Geçersiz radar arama alanı koordinatları",
                isNetworkError = false
            )
        }

        val safeLimit = maxCameras.coerceIn(1, 500)
        val overpassQuery = """
            [out:json][timeout:12];
            (
              node["highway"="speed_camera"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
            );
            out body $safeLimit;
        """.trimIndent()

        var lastError = "Bilinmeyen hata"
        var networkError = false

        for (endpoint in endpoints) {
            try {
                Log.d(TAG, "Fetching speed cameras from: $endpoint")
                val request = Request.Builder()
                    .url(endpoint)
                    .post(overpassQuery.toRequestBody("text/plain".toMediaType()))
                    .header("User-Agent", "LANUHaritaAndroid/2.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        networkError = true
                        Log.w(TAG, "Endpoint $endpoint returned ${response.code}; trying next mirror")
                        return@use
                    }

                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        lastError = "Boş yanıt"
                        return@use
                    }

                    val cameras = parseOsmResponse(body)
                    Log.i(TAG, "Parsed ${cameras.size} speed cameras from $endpoint")
                    return@withContext SafetyCameraFetchResult.Success(
                        cameras = cameras,
                        endpointUsed = endpoint
                    )
                }
            } catch (e: Exception) {
                networkError = true
                lastError = e.message ?: "Bağlantı hatası"
                Log.w(TAG, "Exception querying $endpoint: $lastError")
            }
        }

        SafetyCameraFetchResult.Error(
            message = "Radar servisine ulaşılamadı: $lastError",
            isNetworkError = networkError
        )
    }

    fun parseOsmResponse(jsonString: String): List<SafetyCamera> {
        val results = mutableListOf<SafetyCamera>()
        val seenIds = mutableSetOf<Long>()

        try {
            val root = JSONObject(jsonString)
            val elements = root.optJSONArray("elements") ?: return emptyList()

            for (i in 0 until elements.length()) {
                val element = elements.optJSONObject(i) ?: continue
                if (element.optString("type") != "node") continue

                val id = element.optLong("id", -1L)
                if (id <= 0L || !seenIds.add(id)) continue

                val lat = element.optDouble("lat", Double.NaN)
                val lon = element.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    continue
                }

                val tagsObject = element.optJSONObject("tags")
                val tags = mutableMapOf<String, String>()
                if (tagsObject != null) {
                    val keys = tagsObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        tags[key] = tagsObject.optString(key, "")
                    }
                }

                results += SafetyCamera(
                    id = id,
                    point = GeoPoint(lat, lon),
                    maxSpeed = tags["maxspeed"],
                    direction = tags["direction"],
                    operator = tags["operator"],
                    reference = tags["ref"] ?: tags["reference"],
                    rawTags = tags
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing speed-camera OSM response: ${e.message}", e)
        }

        return results
    }
}
