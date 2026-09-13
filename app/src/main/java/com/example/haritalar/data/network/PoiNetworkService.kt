package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PoiNetworkService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchPoisAround(center: GeoPoint, radiusMeters: Int = 2500, selectedCategory: PoiCategory? = null): List<PoiItem> = withContext(Dispatchers.IO) {
        try {
            val amenityFilter = when (selectedCategory) {
                PoiCategory.RESTAURANT -> "\"amenity\"=\"restaurant\""
                PoiCategory.FUEL -> "\"amenity\"=\"fuel\""
                PoiCategory.HOSPITAL -> "\"amenity\"=\"hospital\""
                PoiCategory.PHARMACY -> "\"amenity\"=\"pharmacy\""
                PoiCategory.MARKET -> "\"shop\"=\"supermarket\""
                PoiCategory.PARKING -> "\"amenity\"=\"parking\""
                PoiCategory.ATM -> "\"amenity\"=\"atm\""
                PoiCategory.CAFE -> "\"amenity\"=\"cafe\""
                PoiCategory.CHARGING_STATION -> "\"amenity\"=\"charging_station\""
                null -> "\"amenity\"~\"restaurant|fuel|hospital|pharmacy|parking|atm|cafe|charging_station\""
            }
            val query = """
                [out:json][timeout:15];
                (node[$amenityFilter](around:$radiusMeters,${center.latitude},${center.longitude}););
                out 35;
            """.trimIndent()
            val request = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .post(query.toRequestBody("text/plain".toMediaType()))
                .header("User-Agent", "HaritalarAndroidNav/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val elements = JSONObject(body).optJSONArray("elements") ?: return@withContext emptyList()
            val results = mutableListOf<PoiItem>()
            for (i in 0 until elements.length()) {
                val elem = elements.getJSONObject(i)
                val id = elem.optLong("id", 0).toString()
                val lat = elem.optDouble("lat", Double.NaN)
                val lon = elem.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val tags = elem.optJSONObject("tags")
                val brand = tags?.optString("brand")?.ifBlank { null }
                val operator = tags?.optString("operator")?.ifBlank { null }
                val name = tags?.optString("name")?.ifBlank { null } ?: brand ?: operator ?: getDefaultNameForAmenity(tags)
                val cat = mapTagsToCategory(tags)
                val street = tags?.optString("addr:street")?.ifBlank { null }
                val housenumber = tags?.optString("addr:housenumber")?.ifBlank { null }
                val address = if (street != null) listOfNotNull(street, housenumber).joinToString(" ") else null
                results.add(PoiItem(id, name, cat, GeoPoint(lat, lon), address, brand, operator))
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapTagsToCategory(tags: JSONObject?): PoiCategory {
        if (tags == null) return PoiCategory.RESTAURANT
        val amenity = tags.optString("amenity")
        val shop = tags.optString("shop")
        return when {
            amenity == "restaurant" -> PoiCategory.RESTAURANT
            amenity == "fuel" -> PoiCategory.FUEL
            amenity == "hospital" -> PoiCategory.HOSPITAL
            amenity == "pharmacy" -> PoiCategory.PHARMACY
            amenity == "parking" -> PoiCategory.PARKING
            amenity == "atm" -> PoiCategory.ATM
            amenity == "cafe" -> PoiCategory.CAFE
            amenity == "charging_station" -> PoiCategory.CHARGING_STATION
            shop == "supermarket" || shop == "convenience" -> PoiCategory.MARKET
            else -> PoiCategory.RESTAURANT
        }
    }

    private fun getDefaultNameForAmenity(tags: JSONObject?): String = mapTagsToCategory(tags).displayName
}
