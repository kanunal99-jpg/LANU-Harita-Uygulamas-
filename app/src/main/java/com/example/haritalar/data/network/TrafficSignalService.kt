package com.example.haritalar.data.network

import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox
import com.example.haritalar.model.TrafficSignalFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TrafficSignalService(
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
        private const val TAG = "TrafficSignalService"
    }

    /**
     * Fetches real traffic signals from OpenStreetMap for the specified bounding box.
     * Executes failover across primary and alternative endpoints.
     */
    suspend fun fetchTrafficSignalsInBoundingBox(
        bbox: TrafficSignalBoundingBox,
        maxSignals: Int = 100
    ): TrafficSignalFetchResult = withContext(Dispatchers.IO) {
        if (!bbox.isValid()) {
            return@withContext TrafficSignalFetchResult.Error(
                message = "Geçersiz arama alanı koordinatları",
                isNetworkError = false
            )
        }

        val overpassQuery = """
            [out:json][timeout:12];
            (
              node["highway"="traffic_signals"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
            );
            out body $maxSignals;
        """.trimIndent()

        var lastErrorMessage = "Bilinmeyen hata"
        var isNetworkError = false

        for (endpoint in endpoints) {
            try {
                Log.d(TAG, "Fetching traffic signals from: $endpoint")
                val requestBody = overpassQuery.toRequestBody("text/plain".toMediaType())
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .header("User-Agent", "LANUHaritaAndroid/2.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    Log.w(TAG, "Endpoint $endpoint returned HTTP $code, trying next mirror...")
                    lastErrorMessage = "HTTP $code"
                    isNetworkError = true
                    continue
                }

                val bodyString = response.body?.string()
                if (bodyString.isNullOrBlank()) {
                    Log.w(TAG, "Empty response body from $endpoint")
                    lastErrorMessage = "Boş yanıt"
                    continue
                }

                val parsedSignals = parseOsmResponse(bodyString)
                Log.i(TAG, "Successfully parsed ${parsedSignals.size} traffic signals from $endpoint")
                return@withContext TrafficSignalFetchResult.Success(
                    signals = parsedSignals,
                    fromCache = false,
                    endpointUsed = endpoint
                )
            } catch (e: Exception) {
                Log.w(TAG, "Exception querying $endpoint: ${e.message}")
                lastErrorMessage = e.message ?: "Bağlantı hatası"
                isNetworkError = true
            }
        }

        TrafficSignalFetchResult.Error(
            message = "Trafik ışığı servisine ulaşılamadı: $lastErrorMessage",
            isNetworkError = isNetworkError
        )
    }

    /**
     * Parses OSM JSON string into typed TrafficSignal objects.
     * Guaranteed to never throw runtime exceptions on malformed payloads.
     */
    fun parseOsmResponse(jsonString: String): List<TrafficSignal> {
        val results = mutableListOf<TrafficSignal>()
        val seenIds = mutableSetOf<Long>()

        try {
            val root = JSONObject(jsonString)
            val elements = root.optJSONArray("elements") ?: return emptyList()

            for (i in 0 until elements.length()) {
                val elem = elements.optJSONObject(i) ?: continue
                if (elem.optString("type") != "node") continue

                val id = elem.optLong("id", -1L)
                if (id <= 0L || seenIds.contains(id)) continue

                val lat = elem.optDouble("lat", Double.NaN)
                val lon = elem.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN() || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) continue

                val tagsObj = elem.optJSONObject("tags")
                val rawTags = mutableMapOf<String, String>()
                var crossing: String? = null
                var direction: String? = null
                var hasSound = false
                var hasVibration = false
                var hasArrow = false
                var reference: String? = null

                if (tagsObj != null) {
                    val keys = tagsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = tagsObj.optString(k, "")
                        rawTags[k] = v
                    }

                    crossing = rawTags["crossing"] ?: rawTags["traffic_signals:crossing"]
                    direction = rawTags["traffic_signals:direction"] ?: rawTags["direction"]

                    val soundVal = rawTags["traffic_signals:sound"] ?: rawTags["sound"]
                    hasSound = soundVal.equals("yes", ignoreCase = true) || soundVal.equals("acoustic", ignoreCase = true)

                    val vibVal = rawTags["traffic_signals:vibration"] ?: rawTags["vibration"]
                    hasVibration = vibVal.equals("yes", ignoreCase = true) || vibVal.equals("tactile_paving", ignoreCase = true)

                    val arrowVal = rawTags["traffic_signals:arrow"]
                    hasArrow = arrowVal.equals("yes", ignoreCase = true)

                    reference = rawTags["ref"] ?: rawTags["reference"]
                }

                val signal = TrafficSignal(
                    id = id,
                    point = GeoPoint(lat, lon),
                    crossing = crossing,
                    direction = direction,
                    hasSound = hasSound,
                    hasVibration = hasVibration,
                    hasArrow = hasArrow,
                    reference = reference,
                    rawTags = rawTags,
                    source = "OpenStreetMap"
                )

                seenIds.add(id)
                results.add(signal)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OSM response: ${e.message}", e)
        }

        return results
    }
}
