package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class NominatimGeocodingService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(query: String, focusPoint: GeoPoint? = null): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlBuilder = StringBuilder("https://nominatim.openstreetmap.org/search?")
                .append("q=").append(encodedQuery)
                .append("&format=json")
                .append("&addressdetails=1")
                .append("&limit=12")
                .append("&accept-language=tr,en")
                .append("&countrycodes=tr")

            if (focusPoint != null) {
                // Viewbox around user focus point (+/- 0.5 degrees ~ 55km)
                val minLon = focusPoint.longitude - 0.5
                val maxLon = focusPoint.longitude + 0.5
                val minLat = focusPoint.latitude - 0.5
                val maxLat = focusPoint.latitude + 0.5
                urlBuilder.append("&viewbox=").append("$minLon,$maxLat,$maxLon,$minLat")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "HaritalarAndroidNav/1.0 (haritalar@example.com)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(bodyString)
            val results = mutableListOf<SearchResult>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val displayName = item.optString("display_name", "")
                val name = item.optString("name").ifEmpty {
                    displayName.split(",").firstOrNull()?.trim() ?: query
                }
                val type = item.optString("type", "place")
                val osmId = item.optString("osm_id", i.toString())

                results.add(
                    SearchResult(
                        id = osmId,
                        name = name,
                        displayName = displayName,
                        point = GeoPoint(lat, lon),
                        type = type
                    )
                )
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun reverseGeocode(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=${point.latitude}&lon=${point.longitude}&accept-language=tr"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HaritalarAndroidNav/1.0 (haritalar@example.com)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val bodyString = response.body?.string() ?: return@withContext null
            val obj = org.json.JSONObject(bodyString)
            if (obj.has("display_name")) obj.getString("display_name") else null
        } catch (e: Exception) {
            null
        }
    }
}
