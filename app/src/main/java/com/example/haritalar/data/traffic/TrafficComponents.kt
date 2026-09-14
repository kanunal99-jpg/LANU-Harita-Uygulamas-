package com.example.haritalar.data.traffic

import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.TrafficTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface TrafficProvider {
    val name: String
    val isAvailable: Boolean
    suspend fun fetchSegmentData(point: GeoPoint): TrafficSegment?
}

class TomTomTrafficProvider(
    var apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : TrafficProvider {
    override val name: String = "TomTom Traffic Flow API v4"
    override val isAvailable: Boolean
        get() = apiKey.isNotBlank() && apiKey != "\"\"" && apiKey != "\"null\"" && apiKey != "null" && apiKey != "MY_TOMTOM_API_KEY"

    var lastResponseCode: Int? = null
        private set
    var lastLatencyMs: Long = 0L
        private set
    var lastRequestTimestamp: Long = 0L
        private set
    var lastErrorMessage: String? = null
        private set
    var lastRawSampleSnippet: String? = null
        private set

    suspend fun testLiveConnection(point: GeoPoint): TrafficTestResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().removeSurrounding("\"")
        val url = "https://api.tomtom.com/traffic/services/4/flowSegmentData/relative0/10/json?" +
                "point=${point.latitude},${point.longitude}&key=$cleanKey"
        val start = System.currentTimeMillis()

        if (cleanKey.isBlank()) {
            return@withContext TrafficTestResult(
                sourceUrl = "https://api.tomtom.com/traffic/services/4/flowSegmentData/relative0/10/json",
                httpStatusCode = 0,
                isSuccess = false,
                latencyMs = 0,
                errorMessage = "TomTom API Anahtarı eksik! Canlı veri için lütfen geçerli bir anahtar tanımlayın."
            )
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LanuHaritaAndroid/1.0")
                .build()

            val callStart = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - callStart
                val code = response.code
                val body = response.body?.string() ?: ""

                lastResponseCode = code
                lastLatencyMs = latency
                lastRequestTimestamp = System.currentTimeMillis()

                if (!response.isSuccessful) {
                    val errorMsg = when (code) {
                        401 -> "HTTP 401 Unauthorized: Geçersiz veya yetkisiz API Anahtarı"
                        403 -> "HTTP 403 Forbidden: API Anahtarının Traffic Flow izni yok veya kota doldu"
                        429 -> "HTTP 429 Too Many Requests: Hız limiti aşıldı"
                        else -> "HTTP $code: TomTom sunucu hatası"
                    }
                    lastErrorMessage = errorMsg
                    return@withContext TrafficTestResult(
                        sourceUrl = url.replace(cleanKey, "••••••••"),
                        httpStatusCode = code,
                        isSuccess = false,
                        latencyMs = latency,
                        rawJsonSnippet = body.take(300),
                        errorMessage = errorMsg
                    )
                }

                val root = JSONObject(body)
                val flowData = root.optJSONObject("flowSegmentData")
                if (flowData == null) {
                    return@withContext TrafficTestResult(
                        sourceUrl = url.replace(cleanKey, "••••••••"),
                        httpStatusCode = code,
                        isSuccess = false,
                        latencyMs = latency,
                        rawJsonSnippet = body.take(300),
                        errorMessage = "Beklenen 'flowSegmentData' JSON gövdesi bulunamadı."
                    )
                }

                val currentSpeed = flowData.optDouble("currentSpeed", 0.0)
                val freeFlowSpeed = flowData.optDouble("freeFlowSpeed", 0.0)
                val currentTravelTime = flowData.optLong("currentTravelTime", 0)
                val freeFlowTravelTime = flowData.optLong("freeFlowTravelTime", 0)
                val confidence = flowData.optDouble("confidence", 0.0)
                val roadClosure = flowData.optBoolean("roadClosure", false)

                val coordsObj = flowData.optJSONObject("coordinates")
                val coordArray = coordsObj?.optJSONArray("coordinate")
                val coordCount = coordArray?.length() ?: 0
                val delay = Math.max(0L, currentTravelTime - freeFlowTravelTime)

                lastRawSampleSnippet = "Anlık: ${currentSpeed.toInt()} km/h, Serbest: ${freeFlowSpeed.toInt()} km/h, Güvenilirlik: $confidence"

                TrafficTestResult(
                    sourceUrl = url.replace(cleanKey, "••••••••"),
                    httpStatusCode = code,
                    isSuccess = true,
                    latencyMs = latency,
                    currentSpeedKmh = currentSpeed,
                    freeFlowSpeedKmh = freeFlowSpeed,
                    currentTravelTimeSec = currentTravelTime,
                    freeFlowTravelTimeSec = freeFlowTravelTime,
                    delaySeconds = delay,
                    confidence = confidence,
                    roadClosure = roadClosure,
                    coordinateCount = coordCount,
                    rawJsonSnippet = body.take(400)
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            TrafficTestResult(
                sourceUrl = url.replace(cleanKey, "••••••••"),
                httpStatusCode = 0,
                isSuccess = false,
                latencyMs = latency,
                errorMessage = "Canlı ağ soket hatası: ${e.message}"
            )
        }
    }

    override suspend fun fetchSegmentData(point: GeoPoint): TrafficSegment? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null

        try {
            val cleanKey = apiKey.trim().removeSurrounding("\"")
            val url = "https://api.tomtom.com/traffic/services/4/flowSegmentData/relative0/10/json?" +
                    "point=${point.latitude},${point.longitude}&key=$cleanKey"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HaritalarAndroidNav/1.0")
                .build()

            val callStart = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                lastLatencyMs = System.currentTimeMillis() - callStart
                lastResponseCode = response.code
                lastRequestTimestamp = System.currentTimeMillis()

                if (!response.isSuccessful) {
                    lastErrorMessage = "HTTP ${response.code}"
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val flowData = root.optJSONObject("flowSegmentData") ?: return@withContext null

                val currentSpeed = flowData.optDouble("currentSpeed", 0.0)
                val freeFlowSpeed = flowData.optDouble("freeFlowSpeed", 0.0)
                val currentTravelTime = flowData.optLong("currentTravelTime", 0)
                val freeFlowTravelTime = flowData.optLong("freeFlowTravelTime", 0)
                val confidence = flowData.optDouble("confidence", 0.0)
                val roadClosure = flowData.optBoolean("roadClosure", false)

                val coordinates = mutableListOf<GeoPoint>()
                val coordsObj = flowData.optJSONObject("coordinates")
                val coordArray = coordsObj?.optJSONArray("coordinate")
                if (coordArray != null) {
                    for (i in 0 until coordArray.length()) {
                        val c = coordArray.getJSONObject(i)
                        coordinates.add(GeoPoint(c.getDouble("latitude"), c.getDouble("longitude")))
                    }
                }
                if (coordinates.isEmpty()) {
                    coordinates.add(point)
                }

                val delay = Math.max(0L, currentTravelTime - freeFlowTravelTime)

                TrafficSegment(
                    coordinates = coordinates,
                    currentSpeed = currentSpeed,
                    freeFlowSpeed = freeFlowSpeed,
                    delaySeconds = delay,
                    confidence = confidence,
                    roadClosure = roadClosure
                )
            }
        } catch (e: Exception) {
            Log.d("TomTomTrafficProvider", "Traffic segment query error: ${e.message}")
            null
        }
    }
}

class TrafficCache(
    private val ttlMillis: Long = 5 * 60 * 1000L // 5 minutes TTL
) {
    private data class CacheEntry(val segment: TrafficSegment, val timestamp: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private fun makeKey(point: GeoPoint): String {
        // Round to ~100m grid to avoid redundant queries for nearby points
        val latKey = Math.round(point.latitude * 1000.0)
        val lonKey = Math.round(point.longitude * 1000.0)
        return "$latKey,$lonKey"
    }

    fun get(point: GeoPoint): TrafficSegment? {
        val entry = cache[makeKey(point)] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttlMillis) {
            cache.remove(makeKey(point))
            return null
        }
        return entry.segment
    }

    fun put(point: GeoPoint, segment: TrafficSegment) {
        cache[makeKey(point)] = CacheEntry(segment, System.currentTimeMillis())
    }

    fun clear() {
        cache.clear()
    }
}

class TrafficProviderChain(
    val primaryProvider: TrafficProvider,
    val alternativeProvider: TrafficProvider? = null,
    val cache: TrafficCache = TrafficCache()
) {
    fun clearCache() {
        cache.clear()
    }
    suspend fun getTrafficSegment(point: GeoPoint): TrafficSegment? {
        // 1. Check verified cache
        cache.get(point)?.let { return it }

        // 2. Query primary provider
        if (primaryProvider.isAvailable) {
            val result = primaryProvider.fetchSegmentData(point)
            if (result != null) {
                cache.put(point, result)
                return result
            }
        }

        // 3. Alternative provider fallback
        if (alternativeProvider?.isAvailable == true) {
            val altResult = alternativeProvider.fetchSegmentData(point)
            if (altResult != null) {
                cache.put(point, altResult)
                return altResult
            }
        }

        return null
    }

    fun hasAvailableProvider(): Boolean {
        return primaryProvider.isAvailable || (alternativeProvider?.isAvailable == true)
    }
}

object TrafficRouteMatcher {
    /**
     * Geometry-aware matching: verifies distance from segment coordinate to route polyline
     * within threshold (e.g. 35 meters) to prevent projecting cross-street traffic.
     */
    fun matchSegmentsToRoute(
        routePoints: List<GeoPoint>,
        segments: List<TrafficSegment>,
        maxDistanceMeters: Double = 40.0
    ): List<TrafficSegment> {
        if (routePoints.size < 2 || segments.isEmpty()) return emptyList()

        return segments.filter { seg ->
            seg.coordinates.any { segPt ->
                isPointNearPolyline(segPt, routePoints, maxDistanceMeters)
            }
        }
    }

    fun isPointNearPolyline(point: GeoPoint, polyline: List<GeoPoint>, maxMeters: Double): Boolean {
        for (i in 0 until polyline.size - 1) {
            val p1 = polyline[i]
            val p2 = polyline[i + 1]
            val dist = distanceToSegmentMeters(point, p1, p2)
            if (dist <= maxMeters) return true
        }
        return false
    }

    fun distanceToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val abDist = a.distanceTo(b)
        if (abDist == 0.0) return p.distanceTo(a)

        // Equirectangular projection local flat coordinates
        val latRef = Math.toRadians((a.latitude + b.latitude + p.latitude) / 3.0)
        val mPerLat = 111132.92 - 559.82 * Math.cos(2 * latRef)
        val mPerLon = 111412.84 * Math.cos(latRef)

        val px = (p.longitude - a.longitude) * mPerLon
        val py = (p.latitude - a.latitude) * mPerLat
        val bx = (b.longitude - a.longitude) * mPerLon
        val by = (b.latitude - a.latitude) * mPerLat

        val t = Math.max(0.0, Math.min(1.0, (px * bx + py * by) / (bx * bx + by * by)))
        val projX = t * bx
        val projY = t * by
        val dx = px - projX
        val dy = py - projY
        return Math.sqrt(dx * dx + dy * dy)
    }
}

object TrafficRouteCostModel {
    fun calculateTrafficStatus(
        segments: List<TrafficSegment>,
        hasProvider: Boolean,
        providerName: String = "TomTom Traffic Flow API v4",
        httpStatusCode: Int? = null,
        lastCheckTimestamp: Long = System.currentTimeMillis()
    ): TrafficStatus {
        if (!hasProvider || segments.isEmpty()) {
            val isNoKey = !hasProvider
            return TrafficStatus(
                verified = false,
                message = if (isNoKey) "Canlı trafik doğrulanamadı • temel ETA korunuyor"
                          else "Canlı trafik doğrulanamadı • temel ETA korunuyor",
                delaySeconds = 0,
                trafficLevel = TrafficLevel.UNKNOWN,
                sourceName = if (isNoKey) "OSRM / Valhalla Statik Yol Profili" else providerName,
                isLiveApi = false,
                httpStatusCode = httpStatusCode,
                segmentCount = segments.size,
                lastCheckTimestamp = lastCheckTimestamp
            )
        }

        var totalDelay = 0L
        var totalSpeedRatio = 0.0
        var totalSpeed = 0.0
        var count = 0

        for (s in segments) {
            totalDelay += s.delaySeconds
            totalSpeed += s.currentSpeed
            if (s.freeFlowSpeed > 0) {
                totalSpeedRatio += (s.currentSpeed / s.freeFlowSpeed)
                count++
            }
        }

        val avgRatio = if (count > 0) totalSpeedRatio / count else 1.0
        val avgSpeed = if (count > 0) totalSpeed / count else null
        val level = when {
            avgRatio < 0.35 -> TrafficLevel.SEVERE
            avgRatio < 0.65 -> TrafficLevel.HEAVY
            avgRatio < 0.85 -> TrafficLevel.MODERATE
            else -> TrafficLevel.LOW
        }

        val delayMinutes = Math.round(totalDelay / 60.0)
        val message = if (delayMinutes > 0) {
            "TomTom Canlı Trafik • +$delayMinutes dk gecikme"
        } else {
            "TomTom Canlı Trafik • Akıcı trafik"
        }

        return TrafficStatus(
            verified = true,
            message = message,
            delaySeconds = totalDelay,
            trafficLevel = level,
            sourceName = providerName,
            isLiveApi = true,
            httpStatusCode = 200,
            segmentCount = segments.size,
            lastCheckTimestamp = lastCheckTimestamp,
            averageSpeedKmh = avgSpeed,
            rawSampleDetails = "Doğrulanan $count segment ortalama hızı: ${avgSpeed?.toInt() ?: 0} km/h"
        )
    }
}
