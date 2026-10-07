package com.example.haritalar.data.network

import android.util.Log
import com.example.BuildConfig
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class RouteCriticalPoiFetchResult {
    data class Success(
        val pois: List<PoiItem>,
        val endpointUsed: String,
        val fetchedAtMillis: Long = System.currentTimeMillis()
    ) : RouteCriticalPoiFetchResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean
    ) : RouteCriticalPoiFetchResult()
}

/**
 * Route-scoped OSM critical-POI provider.
 *
 * One bounded Overpass request contains all route sample circles so LANU does not
 * fire one request per POI category. Primary endpoint -> mirrors -> explicit error.
 */
class RouteCriticalPoiNetworkService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .build(),
    private val endpoints: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://z.overpass-api.de/api/interpreter"
    )
) {
    companion object {
        private const val TAG = "RouteCriticalPoi"
        private const val MAX_RESULTS = 600
        private val JSON_ADAPTER = Moshi.Builder().build().adapter(Map::class.java)
    }

    suspend fun fetch(
        samplePoints: List<GeoPoint>,
        radiusMeters: Int
    ): RouteCriticalPoiFetchResult = withContext(Dispatchers.IO) {
        if (samplePoints.size !in 2..20) {
            return@withContext RouteCriticalPoiFetchResult.Error(
                message = "Rota POI örnek noktaları geçersiz",
                isNetworkError = false
            )
        }
        val safeRadius = radiusMeters.coerceIn(1_000, 8_000)
        val query = buildQuery(samplePoints, safeRadius)
        var lastError = "Bilinmeyen hata"
        var networkError = false

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(query.toRequestBody("text/plain".toMediaType()))
                    .header("User-Agent", "LANUHaritaAndroidNav/${BuildConfig.VERSION_NAME}")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        networkError = true
                        Log.w(TAG, "Route POI endpoint failed: $endpoint -> ${response.code}")
                        return@use
                    }

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        lastError = "Boş yanıt"
                        return@use
                    }

                    val pois = parseResponse(body)
                    Log.i(TAG, "Route POI endpoint $endpoint returned ${pois.size} critical places")
                    return@withContext RouteCriticalPoiFetchResult.Success(
                        pois = pois,
                        endpointUsed = endpoint
                    )
                }
            } catch (e: Exception) {
                networkError = true
                lastError = e.message ?: "Bağlantı hatası"
                Log.w(TAG, "Route POI endpoint exception at $endpoint: $lastError")
            }
        }

        RouteCriticalPoiFetchResult.Error(
            message = "Rota POI servisine ulaşılamadı: $lastError",
            isNetworkError = networkError
        )
    }

    internal fun buildQuery(samplePoints: List<GeoPoint>, radiusMeters: Int): String {
        val clauses = buildString {
            samplePoints.forEach { point ->
                appendLine(
                    """nwr["amenity"~"^(fuel|hospital|pharmacy|charging_station)$"](around:$radiusMeters,${point.latitude},${point.longitude});"""
                )
                appendLine(
                    """nwr["healthcare"="hospital"](around:$radiusMeters,${point.latitude},${point.longitude});"""
                )
                appendLine(
                    """nwr["shop"="chemist"](around:$radiusMeters,${point.latitude},${point.longitude});"""
                )
            }
        }

        return """
            [out:json][timeout:16];
            (
              $clauses
            );
            out center $MAX_RESULTS;
        """.trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseResponse(jsonString: String): List<PoiItem> {
        val root = runCatching { JSON_ADAPTER.fromJson(jsonString) as? Map<*, *> }
            .getOrNull() ?: return emptyList()
        val elements = root["elements"] as? List<*> ?: return emptyList()
        val results = mutableListOf<PoiItem>()

        for (raw in elements) {
            val element = raw as? Map<*, *> ?: continue
            val tags = element["tags"] as? Map<*, *> ?: continue
            val category = categoryFrom(tags) ?: continue
            val id = numberAsLong(element["id"]) ?: continue
            if (id <= 0L) continue

            val point = pointFrom(element) ?: continue
            val type = element["type"]?.toString()?.ifBlank { "node" } ?: "node"
            val brand = tags["brand"]?.toString()?.takeIf { it.isNotBlank() }
            val operator = tags["operator"]?.toString()?.takeIf { it.isNotBlank() }
            val name = tags["name"]?.toString()?.takeIf { it.isNotBlank() }
                ?: brand
                ?: operator
                ?: category.displayName

            val street = tags["addr:street"]?.toString()?.takeIf { it.isNotBlank() }
            val number = tags["addr:housenumber"]?.toString()?.takeIf { it.isNotBlank() }
            val neighborhood = tags["addr:neighbourhood"]?.toString()?.takeIf { it.isNotBlank() }
                ?: tags["addr:suburb"]?.toString()?.takeIf { it.isNotBlank() }
            val city = tags["addr:city"]?.toString()?.takeIf { it.isNotBlank() }
            val address = listOfNotNull(
                listOfNotNull(street, number).joinToString(" ").ifBlank { null },
                neighborhood,
                city
            ).joinToString(", ").ifBlank { null }

            results += PoiItem(
                id = "${type}_$id",
                name = name,
                category = category,
                point = point,
                address = address,
                brand = brand,
                operator = operator
            )
        }

        return results.distinctBy { it.id }.take(MAX_RESULTS)
    }

    private fun categoryFrom(tags: Map<*, *>): PoiCategory? {
        val amenity = tags["amenity"]?.toString()
        val healthcare = tags["healthcare"]?.toString()
        val shop = tags["shop"]?.toString()
        return when {
            amenity == "fuel" -> PoiCategory.FUEL
            amenity == "hospital" || healthcare == "hospital" -> PoiCategory.HOSPITAL
            amenity == "pharmacy" || shop == "chemist" -> PoiCategory.PHARMACY
            amenity == "charging_station" -> PoiCategory.CHARGING_STATION
            else -> null
        }
    }

    private fun pointFrom(element: Map<*, *>): GeoPoint? {
        val lat = numberAsDouble(element["lat"])
            ?: numberAsDouble((element["center"] as? Map<*, *>)?.get("lat"))
        val lon = numberAsDouble(element["lon"])
            ?: numberAsDouble((element["center"] as? Map<*, *>)?.get("lon"))
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            return null
        }
        return GeoPoint(lat, lon)
    }

    private fun numberAsLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    private fun numberAsDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
