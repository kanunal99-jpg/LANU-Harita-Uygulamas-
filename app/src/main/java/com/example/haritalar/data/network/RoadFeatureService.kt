package com.example.haritalar.data.network

import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RoadFeature
import com.example.haritalar.model.RoadFeatureFetchResult
import com.example.haritalar.model.RoadFeatureType
import com.example.haritalar.navigation.RoadFeatureRoutePolicy
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RoadFeatureService(
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
        private const val TAG = "RoadFeatureService"
        private val JSON_ADAPTER = Moshi.Builder().build().adapter(Map::class.java)
    }

    suspend fun fetchForRoute(route: List<GeoPoint>): RoadFeatureFetchResult = withContext(Dispatchers.IO) {
        if (route.size < 2) {
            return@withContext RoadFeatureFetchResult.Error(
                message = "Yol özelliği sorgusu için geçerli rota geometrisi yok",
                isNetworkError = false
            )
        }

        val query = buildQuery(route)
        var lastError = "Bilinmeyen hata"
        var networkError = false

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(query.toRequestBody("text/plain".toMediaType()))
                    .header("User-Agent", "LANUHaritaAndroid/1.1.13")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        networkError = true
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        lastError = "Boş yanıt"
                        return@use
                    }
                    val parsed = parseOsmResponse(body)
                    return@withContext RoadFeatureFetchResult.Success(
                        features = parsed,
                        endpointUsed = endpoint
                    )
                }
            } catch (e: Exception) {
                networkError = true
                lastError = e.message ?: "Bağlantı hatası"
                Log.w(TAG, "Road feature endpoint failed: ${e.message}")
            }
        }

        RoadFeatureFetchResult.Error(
            message = "Yol özelliği servisine ulaşılamadı: $lastError",
            isNetworkError = networkError
        )
    }

    internal fun buildQuery(route: List<GeoPoint>): String {
        val samples = RoadFeatureRoutePolicy.sampleQueryPoints(route)
        val clauses = buildString {
            samples.forEach { point ->
                val around = "(around:${RoadFeatureRoutePolicy.QUERY_RADIUS_METERS},${point.latitude},${point.longitude});"
                append("""node["traffic_calming"~"^(bump|hump|table|cushion)$"]$around""").append('\n')
                append("""node["highway"="speed_bump"]$around""").append('\n')
                append("""node["railway"="level_crossing"]$around""").append('\n')
                append("""nwr["amenity"="school"]$around""").append('\n')
                append("""nwr["hazard"]$around""").append('\n')
            }
        }
        return """
            [out:json][timeout:16];
            (
              $clauses
            );
            out center 500;
        """.trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseOsmResponse(jsonString: String): List<RoadFeature> {
        val root = runCatching { JSON_ADAPTER.fromJson(jsonString) as? Map<*, *> }.getOrNull()
            ?: return emptyList()
        val elements = root["elements"] as? List<*> ?: return emptyList()
        val results = mutableListOf<RoadFeature>()
        val seen = mutableSetOf<String>()

        for (raw in elements) {
            val element = raw as? Map<*, *> ?: continue
            val type = element["type"]?.toString() ?: continue
            val id = (element["id"] as? Number)?.toLong() ?: continue
            val tagsRaw = element["tags"] as? Map<*, *> ?: continue
            val tags = tagsRaw.entries.mapNotNull { (k, v) ->
                val key = k?.toString() ?: return@mapNotNull null
                val value = v?.toString() ?: return@mapNotNull null
                key to value
            }.toMap()

            val point = extractPoint(element) ?: continue
            val classification = classify(tags) ?: continue
            val uniqueKey = "$type:$id:${classification.first}"
            if (!seen.add(uniqueKey)) continue

            val name = tags["name"]?.takeIf { it.isNotBlank() }
            val rawTag = when (classification.first) {
                RoadFeatureType.SPEED_CALMING -> tags["traffic_calming"] ?: tags["highway"]
                RoadFeatureType.SCHOOL_ZONE -> tags["amenity"]
                RoadFeatureType.LEVEL_CROSSING -> tags["railway"]
                RoadFeatureType.ROAD_HAZARD -> tags["hazard"]
            }

            results += RoadFeature(
                id = uniqueKey,
                point = point,
                type = classification.first,
                title = classification.second,
                detail = name ?: detailFor(classification.first, rawTag),
                rawTagValue = rawTag
            )
        }
        return results
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPoint(element: Map<*, *>): GeoPoint? {
        val lat = (element["lat"] as? Number)?.toDouble()
        val lon = (element["lon"] as? Number)?.toDouble()
        if (lat != null && lon != null) return validPoint(lat, lon)

        val center = element["center"] as? Map<*, *> ?: return null
        val centerLat = (center["lat"] as? Number)?.toDouble() ?: return null
        val centerLon = (center["lon"] as? Number)?.toDouble() ?: return null
        return validPoint(centerLat, centerLon)
    }

    private fun validPoint(lat: Double, lon: Double): GeoPoint? =
        if (lat in -90.0..90.0 && lon in -180.0..180.0) GeoPoint(lat, lon) else null

    private fun classify(tags: Map<String, String>): Pair<RoadFeatureType, String>? {
        val trafficCalming = tags["traffic_calming"]
        if (trafficCalming in setOf("bump", "hump", "table", "cushion") || tags["highway"] == "speed_bump") {
            return RoadFeatureType.SPEED_CALMING to "Hız tümseği / yavaşlatma"
        }
        if (tags["railway"] == "level_crossing") {
            return RoadFeatureType.LEVEL_CROSSING to "Hemzemin geçit"
        }
        if (tags["amenity"] == "school") {
            return RoadFeatureType.SCHOOL_ZONE to "Okul bölgesi"
        }
        tags["hazard"]?.takeIf { it.isNotBlank() }?.let {
            return RoadFeatureType.ROAD_HAZARD to "Yol tehlikesi"
        }
        return null
    }

    private fun detailFor(type: RoadFeatureType, rawTag: String?): String = when (type) {
        RoadFeatureType.SPEED_CALMING -> "OSM trafik yavaşlatma noktası"
        RoadFeatureType.SCHOOL_ZONE -> "OSM okul alanı"
        RoadFeatureType.LEVEL_CROSSING -> "OSM demiryolu geçişi"
        RoadFeatureType.ROAD_HAZARD -> rawTag?.replace('_', ' ') ?: "OSM yol tehlikesi"
    }
}
