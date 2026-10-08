package com.example.haritalar.data.network

import android.util.Log
import com.example.haritalar.model.AverageSpeedZone
import com.example.haritalar.model.AverageSpeedZoneFetchResult
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.navigation.AverageSpeedZoneRoutePolicy
import com.example.haritalar.navigation.RoadFeatureRoutePolicy
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Source-backed OSM average-speed / section-control corridor geometry.
 *
 * A single enforcement node is intentionally ignored here. A corridor is emitted
 * only when OSM returns an explicit way/relation geometry with at least two valid
 * coordinates and a meaningful length.
 */
class AverageSpeedZoneService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build(),
    private val endpoints: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://z.overpass-api.de/api/interpreter"
    )
) {
    companion object {
        private const val TAG = "AverageSpeedZoneService"
        private val JSON_ADAPTER = Moshi.Builder().build().adapter(Map::class.java)
    }

    suspend fun fetchForRoute(route: List<GeoPoint>): AverageSpeedZoneFetchResult =
        withContext(Dispatchers.IO) {
            if (route.size < 2) {
                return@withContext AverageSpeedZoneFetchResult.Error(
                    message = "Ortalama hız koridoru sorgusu için geçerli rota geometrisi yok",
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
                        .header("User-Agent", "LANUHaritaAndroid/1.1.17")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            lastError = "HTTP ${response.code}"
                            networkError = true
                            Log.w(TAG, "Endpoint $endpoint returned ${response.code}; trying mirror")
                            return@use
                        }

                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) {
                            lastError = "Boş yanıt"
                            return@use
                        }

                        val zones = parseOsmResponse(body)
                        return@withContext AverageSpeedZoneFetchResult.Success(
                            zones = zones,
                            endpointUsed = endpoint
                        )
                    }
                } catch (e: Exception) {
                    networkError = true
                    lastError = e.message ?: "Bağlantı hatası"
                    Log.w(TAG, "Average-speed endpoint failed: $lastError")
                }
            }

            AverageSpeedZoneFetchResult.Error(
                message = "Ortalama hız koridoru kaynaklarına ulaşılamadı: $lastError",
                isNetworkError = networkError
            )
        }

    internal fun buildQuery(route: List<GeoPoint>): String {
        val samples = RoadFeatureRoutePolicy.sampleQueryPoints(route)
        val clauses = buildString {
            samples.forEach { point ->
                val around = "(around:${AverageSpeedZoneRoutePolicy.QUERY_RADIUS_METERS},${point.latitude},${point.longitude});"
                append("""way["enforcement"~"average_speed|section_control",i]$around""").append('\n')
                append("""relation["enforcement"~"average_speed|section_control",i]$around""").append('\n')
                append("""relation["type"="enforcement"]["enforcement"~"average_speed|section_control",i]$around""").append('\n')
            }
        }

        return """
            [out:json][timeout:20];
            (
              $clauses
            );
            out geom 300;
        """.trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseOsmResponse(jsonString: String): List<AverageSpeedZone> {
        val root = runCatching { JSON_ADAPTER.fromJson(jsonString) as? Map<*, *> }.getOrNull()
            ?: return emptyList()
        val elements = root["elements"] as? List<*> ?: return emptyList()
        val results = mutableListOf<AverageSpeedZone>()
        val seen = mutableSetOf<String>()

        for (raw in elements) {
            val element = raw as? Map<*, *> ?: continue
            val osmType = element["type"]?.toString() ?: continue
            if (osmType != "way" && osmType != "relation") continue
            val id = (element["id"] as? Number)?.toLong() ?: continue
            if (id <= 0L) continue

            val tagsRaw = element["tags"] as? Map<*, *> ?: continue
            val tags = tagsRaw.entries.mapNotNull { (key, value) ->
                val k = key?.toString() ?: return@mapNotNull null
                val v = value?.toString() ?: return@mapNotNull null
                k to v
            }.toMap()

            if (!isAverageSpeedTags(tags)) continue

            val geometry = when (osmType) {
                "way" -> parseGeometry(element["geometry"] as? List<*>)
                "relation" -> parseRelationGeometry(element["members"] as? List<*>)
                else -> emptyList()
            }
            if (geometry.size < 2) continue
            if (AverageSpeedZoneRoutePolicy.geometryLengthMeters(geometry) <
                AverageSpeedZoneRoutePolicy.MIN_ZONE_GEOMETRY_METERS
            ) continue

            val uniqueId = "$osmType:$id"
            if (!seen.add(uniqueId)) continue

            results += AverageSpeedZone(
                id = uniqueId,
                geometry = geometry,
                maxSpeed = (tags["maxspeed:enforced"] ?: tags["maxspeed"])
                    ?.takeIf { it.isNotBlank() },
                direction = tags["direction"]?.takeIf { it.isNotBlank() },
                operator = tags["operator"]?.takeIf { it.isNotBlank() },
                name = tags["name"]?.takeIf { it.isNotBlank() },
                rawTags = tags
            )
        }

        return results
    }

    internal fun isAverageSpeedTags(tags: Map<String, String>): Boolean {
        val enforcement = tags["enforcement"]?.lowercase().orEmpty()
        return "average_speed" in enforcement || "section_control" in enforcement
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRelationGeometry(members: List<*>?): List<GeoPoint> {
        if (members.isNullOrEmpty()) return emptyList()
        val points = mutableListOf<GeoPoint>()
        for (rawMember in members) {
            val member = rawMember as? Map<*, *> ?: continue
            val geometry = parseGeometry(member["geometry"] as? List<*>)
            appendGeometry(points, geometry)
        }
        return points
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseGeometry(rawGeometry: List<*>?): List<GeoPoint> {
        if (rawGeometry.isNullOrEmpty()) return emptyList()
        return rawGeometry.mapNotNull { raw ->
            val point = raw as? Map<*, *> ?: return@mapNotNull null
            val lat = (point["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
            val lon = (point["lon"] as? Number)?.toDouble() ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            GeoPoint(lat, lon)
        }.fold(mutableListOf()) { acc, point ->
            if (acc.lastOrNull() != point) acc += point
            acc
        }
    }

    private fun appendGeometry(target: MutableList<GeoPoint>, geometry: List<GeoPoint>) {
        if (geometry.isEmpty()) return
        if (target.isEmpty()) {
            target += geometry
            return
        }

        val forwardGap = target.last().distanceTo(geometry.first())
        val reverseGap = target.last().distanceTo(geometry.last())
        val ordered = if (reverseGap < forwardGap) geometry.asReversed() else geometry

        // Avoid stitching disconnected relation members into a fake corridor.
        if (target.last().distanceTo(ordered.first()) > 5_000.0) return
        ordered.forEach { point ->
            if (target.lastOrNull() != point) target += point
        }
    }
}
