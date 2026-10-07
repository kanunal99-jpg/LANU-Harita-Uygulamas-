package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

object BusinessIntentClassifier {
    private val intentTerms = setOf(
        "toptan", "donuk", "dondurulmus", "gida", "fabrika", "uretim", "uretici",
        "bayi", "distributor", "depo", "market", "restoran", "kafe", "eczane",
        "hastane", "benzinlik", "magaza", "isletme", "tedarik"
    )

    fun isBusinessIntent(query: String): Boolean {
        val tokens = TurkishAddressHelper.normalizeTurkish(query).split(" ")
        return tokens.any { it in intentTerms }
    }

    fun significantTokens(query: String): List<String> {
        val stop = setOf("gida", "ve", "san", "tic", "ltd", "sti", "as", "istanbul", "kocaeli")
        return TurkishAddressHelper.normalizeTurkish(query)
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 4 && it !in stop }
            .distinct()
            .take(4)
    }
}

/**
 * Business/POI fallback based on OSM Overpass. It is only used for clear
 * business-intent queries and when a focus point exists, preventing expensive
 * nationwide scans.
 */
class OverpassBusinessSearchProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val endpoints: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://z.overpass-api.de/api/interpreter"
    )
) : SearchProvider {
    override val name: String = "OSM İşletme Araması"

    override suspend fun search(query: String, focusPoint: GeoPoint?): List<SearchResult> = withContext(Dispatchers.IO) {
        if (focusPoint == null || !BusinessIntentClassifier.isBusinessIntent(query)) return@withContext emptyList()
        val tokens = BusinessIntentClassifier.significantTokens(query)
        if (tokens.isEmpty()) return@withContext emptyList()

        val regex = tokens.joinToString("|") { Regex.escape(it) }
        val normalized = TurkishAddressHelper.normalizeTurkish(query)
        val extraClauses = buildString {
            if ("toptan" in normalized) {
                append("""nwr["shop"="wholesale"](around:30000,${focusPoint.latitude},${focusPoint.longitude});""")
            }
            if ("donuk" in normalized || "dondurulmus" in normalized) {
                append("""nwr["industrial"~"food|food_processing",i](around:30000,${focusPoint.latitude},${focusPoint.longitude});""")
            }
        }
        val overpass = """
            [out:json][timeout:12];
            (
              nwr["name"~"$regex",i](around:30000,${focusPoint.latitude},${focusPoint.longitude});
              nwr["brand"~"$regex",i](around:30000,${focusPoint.latitude},${focusPoint.longitude});
              nwr["description"~"$regex",i](around:30000,${focusPoint.latitude},${focusPoint.longitude});
              nwr["product"~"$regex",i](around:30000,${focusPoint.latitude},${focusPoint.longitude});
              $extraClauses
            );
            out center 80;
        """.trimIndent()

        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(overpass.toRequestBody("text/plain".toMediaType()))
                    .header("User-Agent", "LANUHaritaAndroidNav/1.1.3")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = IOException("Overpass HTTP ${response.code}")
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    return@withContext parse(body, query, focusPoint)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("İşletme arama servisine ulaşılamadı")
    }

    internal fun parse(json: String, query: String, focusPoint: GeoPoint): List<SearchResult> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val queryTokens = BusinessIntentClassifier.significantTokens(query)
        val results = mutableListOf<Pair<Int, SearchResult>>()

        for (i in 0 until elements.length()) {
            val element = elements.optJSONObject(i) ?: continue
            val tags = element.optJSONObject("tags") ?: continue
            val name = tags.optString("name").ifBlank {
                tags.optString("brand").ifBlank { tags.optString("operator") }
            }.ifBlank { continue }

            val lat = if (element.has("lat")) element.optDouble("lat", Double.NaN)
                else element.optJSONObject("center")?.optDouble("lat", Double.NaN) ?: Double.NaN
            val lon = if (element.has("lon")) element.optDouble("lon", Double.NaN)
                else element.optJSONObject("center")?.optDouble("lon", Double.NaN) ?: Double.NaN
            if (lat.isNaN() || lon.isNaN()) continue

            val point = GeoPoint(lat, lon)
            val street = tags.optString("addr:street").ifBlank { null }
            val number = tags.optString("addr:housenumber").ifBlank { null }
            val district = tags.optString("addr:district").ifBlank {
                tags.optString("addr:suburb").ifBlank { null }
            }
            val city = tags.optString("addr:city").ifBlank { null }
            val address = listOfNotNull(
                listOfNotNull(street, number).joinToString(" ").ifBlank { null },
                district,
                city
            ).joinToString(", ")

            val searchable = TurkishAddressHelper.normalizeTurkish(
                listOf(
                    name,
                    tags.optString("brand"),
                    tags.optString("description"),
                    tags.optString("product"),
                    tags.optString("shop"),
                    tags.optString("industrial")
                ).joinToString(" ")
            )
            val tokenHits = queryTokens.count { searchable.contains(it) }
            val distanceKm = point.distanceTo(focusPoint) / 1000.0
            val score = tokenHits * 2000 + max(0, (1000 - distanceKm * 20).toInt())

            results += score to SearchResult(
                id = "overpass_business_${element.optString("type")}_${element.optLong("id")}",
                name = name,
                displayName = address.ifBlank { name },
                shortAddress = address,
                point = point,
                type = tags.optString("shop").ifBlank { tags.optString("industrial").ifBlank { "business" } },
                resultType = AddressResultType.POI,
                provider = this.name,
                confidence = (0.6f + tokenHits * 0.1f).coerceAtMost(0.95f)
            )
        }

        return results
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.id }
            .take(30)
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? = null
}
