package com.example.haritalar.data.network

import android.util.Log
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

/**
 * Real OSM POI chain.
 *
 * Primary Overpass -> mirrors -> repository search fallback.
 * Queries nodes, ways and relations; ways/relations use their returned center.
 */
class PoiNetworkService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .build(),
    private val endpoints: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://z.overpass-api.de/api/interpreter"
    )
) {
    companion object {
        private const val TAG = "PoiNetworkService"
    }

    suspend fun fetchPoisAround(
        center: GeoPoint,
        radiusMeters: Int = 2_500,
        selectedCategory: PoiCategory? = null
    ): List<PoiItem> = when (
        val result = fetchPoisAroundResult(center, radiusMeters, selectedCategory)
    ) {
        is PoiFetchResult.Success -> result.pois
        is PoiFetchResult.Error -> emptyList()
    }

    suspend fun fetchPoisAroundResult(
        center: GeoPoint,
        radiusMeters: Int = 2_500,
        selectedCategory: PoiCategory? = null
    ): PoiFetchResult = withContext(Dispatchers.IO) {
        val safeRadius = radiusMeters.coerceIn(500, 20_000)
        val query = buildQuery(center, safeRadius, selectedCategory)
        var lastError = "Bilinmeyen hata"
        var networkError = false

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(query.toRequestBody("text/plain".toMediaType()))
                    .header("User-Agent", "LANUHaritaAndroidNav/1.1.14")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        networkError = true
                        Log.w(TAG, "POI endpoint failed: $endpoint -> ${response.code}")
                        return@use
                    }

                    val body = response.body?.string().orEmpty()
                    val parsed = parseOverpassResponse(body, selectedCategory, center)
                    Log.i(TAG, "POI endpoint $endpoint returned ${parsed.size} usable places")
                    return@withContext PoiFetchResult.Success(
                        pois = parsed,
                        endpointUsed = endpoint
                    )
                }
            } catch (e: Exception) {
                networkError = true
                lastError = e.message ?: "Bağlantı hatası"
                Log.w(TAG, "POI endpoint exception at $endpoint: $lastError")
            }
        }

        Log.e(TAG, "POI provider chain exhausted: $lastError")
        PoiFetchResult.Error(
            message = lastError,
            isNetworkError = networkError
        )
    }

    internal fun buildQuery(center: GeoPoint, radiusMeters: Int, selectedCategory: PoiCategory?): String {
        val clauses = when (selectedCategory) {
            PoiCategory.RESTAURANT -> listOf("""nwr["amenity"="restaurant"]""")
            PoiCategory.FUEL -> listOf("""nwr["amenity"="fuel"]""")
            PoiCategory.HOSPITAL -> listOf(
                """nwr["amenity"="hospital"]""",
                """nwr["healthcare"="hospital"]"""
            )
            PoiCategory.PHARMACY -> listOf(
                """nwr["amenity"="pharmacy"]""",
                """nwr["shop"="chemist"]"""
            )
            PoiCategory.MARKET -> listOf(
                """nwr["shop"~"^(supermarket|convenience|wholesale|food)$"]"""
            )
            PoiCategory.PARKING -> listOf("""nwr["amenity"="parking"]""")
            PoiCategory.ATM -> listOf("""nwr["amenity"="atm"]""")
            PoiCategory.CAFE -> listOf("""nwr["amenity"="cafe"]""")
            PoiCategory.CHARGING_STATION -> listOf("""nwr["amenity"="charging_station"]""")
            null -> listOf(
                """nwr["amenity"~"^(restaurant|fuel|hospital|pharmacy|parking|atm|cafe|charging_station)$"]""",
                """nwr["shop"~"^(supermarket|convenience|chemist|wholesale|food)$"]"""
            )
        }

        val body = clauses.joinToString("\n") { clause ->
            "$clause(around:$radiusMeters,${center.latitude},${center.longitude});"
        }

        return """
            [out:json][timeout:14];
            (
              $body
            );
            out center 120;
        """.trimIndent()
    }

    internal fun parseOverpassResponse(
        jsonString: String,
        selectedCategory: PoiCategory?,
        center: GeoPoint
    ): List<PoiItem> {
        val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return emptyList()
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val results = mutableListOf<PoiItem>()

        for (i in 0 until elements.length()) {
            val elem = elements.optJSONObject(i) ?: continue
            val tags = elem.optJSONObject("tags") ?: continue
            val type = elem.optString("type", "node")
            val osmId = elem.optLong("id", 0L)
            if (osmId <= 0L) continue

            val lat = if (elem.has("lat")) {
                elem.optDouble("lat", Double.NaN)
            } else {
                elem.optJSONObject("center")?.optDouble("lat", Double.NaN) ?: Double.NaN
            }
            val lon = if (elem.has("lon")) {
                elem.optDouble("lon", Double.NaN)
            } else {
                elem.optJSONObject("center")?.optDouble("lon", Double.NaN) ?: Double.NaN
            }
            if (lat.isNaN() || lon.isNaN()) continue

            val category = mapTagsToCategory(tags) ?: continue
            if (selectedCategory != null && category != selectedCategory) continue

            val brand = tags.optString("brand").ifBlank { null }
            val operator = tags.optString("operator").ifBlank { null }
            val name = tags.optString("name").ifBlank { null }
                ?: brand
                ?: operator
                ?: getDefaultNameForCategory(category)

            val street = tags.optString("addr:street").ifBlank { null }
            val housenumber = tags.optString("addr:housenumber").ifBlank { null }
            val neighborhood = tags.optString("addr:neighbourhood").ifBlank {
                tags.optString("addr:suburb").ifBlank { null }
            }
            val city = tags.optString("addr:city").ifBlank { null }
            val address = listOfNotNull(
                listOfNotNull(street, housenumber).joinToString(" ").ifBlank { null },
                neighborhood,
                city
            ).joinToString(", ").ifBlank { null }

            results += PoiItem(
                id = "${type}_$osmId",
                name = name,
                category = category,
                point = GeoPoint(lat, lon),
                address = address,
                brand = brand,
                operator = operator
            )
        }

        return results
            .distinctBy { it.id }
            .sortedBy { it.point.distanceTo(center) }
            .take(120)
    }

    private fun mapTagsToCategory(tags: JSONObject): PoiCategory? {
        val amenity = tags.optString("amenity")
        val shop = tags.optString("shop")
        val healthcare = tags.optString("healthcare")
        return when {
            amenity == "restaurant" -> PoiCategory.RESTAURANT
            amenity == "fuel" -> PoiCategory.FUEL
            amenity == "hospital" || healthcare == "hospital" -> PoiCategory.HOSPITAL
            amenity == "pharmacy" || shop == "chemist" -> PoiCategory.PHARMACY
            amenity == "parking" -> PoiCategory.PARKING
            amenity == "atm" -> PoiCategory.ATM
            amenity == "cafe" -> PoiCategory.CAFE
            amenity == "charging_station" -> PoiCategory.CHARGING_STATION
            shop in setOf("supermarket", "convenience", "wholesale", "food") -> PoiCategory.MARKET
            else -> null
        }
    }

    private fun getDefaultNameForCategory(category: PoiCategory): String = category.displayName
}
