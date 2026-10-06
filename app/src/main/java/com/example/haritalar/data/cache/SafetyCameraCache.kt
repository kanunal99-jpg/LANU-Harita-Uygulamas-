package com.example.haritalar.data.cache

import android.content.Context
import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.SafetyCameraBoundingBox
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent last-known-good cache for fixed safety-camera data.
 *
 * Cache data is never presented as fresh/live data. It is only used when all
 * configured network mirrors fail and the requested area's center is still
 * inside the cached coverage.
 */
class SafetyCameraCache(context: Context) {
    companion object {
        private const val TAG = "SafetyCameraCache"
        private const val PREFS = "lanu_safety_camera_cache"
        private const val KEY_PAYLOAD = "last_known_good"
        const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(bbox: SafetyCameraBoundingBox, cameras: List<SafetyCamera>) {
        runCatching {
            val items = JSONArray()
            cameras.forEach { camera ->
                items.put(
                    JSONObject()
                        .put("id", camera.id)
                        .put("lat", camera.point.latitude)
                        .put("lon", camera.point.longitude)
                        .put("maxSpeed", camera.maxSpeed)
                        .put("direction", camera.direction)
                        .put("operator", camera.operator)
                        .put("reference", camera.reference)
                )
            }

            val root = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put(
                    "bbox",
                    JSONObject()
                        .put("south", bbox.south)
                        .put("west", bbox.west)
                        .put("north", bbox.north)
                        .put("east", bbox.east)
                )
                .put("cameras", items)

            prefs.edit().putString(KEY_PAYLOAD, root.toString()).apply()
        }.onFailure {
            Log.w(TAG, "Unable to persist safety-camera cache: ${it.message}")
        }
    }

    fun loadFor(
        requested: SafetyCameraBoundingBox,
        maxAgeMillis: Long = MAX_AGE_MILLIS
    ): List<SafetyCamera> {
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return emptyList()
        return runCatching {
            val root = JSONObject(payload)
            val savedAt = root.optLong("savedAt", 0L)
            if (savedAt <= 0L || System.currentTimeMillis() - savedAt > maxAgeMillis) {
                return@runCatching emptyList()
            }

            val cachedBoxJson = root.getJSONObject("bbox")
            val cachedBox = SafetyCameraBoundingBox(
                south = cachedBoxJson.getDouble("south"),
                west = cachedBoxJson.getDouble("west"),
                north = cachedBoxJson.getDouble("north"),
                east = cachedBoxJson.getDouble("east")
            )
            val requestedCenter = GeoPoint(
                latitude = (requested.south + requested.north) / 2.0,
                longitude = (requested.west + requested.east) / 2.0
            )
            if (!cachedBox.isValid() || !cachedBox.contains(requestedCenter)) {
                return@runCatching emptyList()
            }

            val items = root.getJSONArray("cameras")
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val point = GeoPoint(item.getDouble("lat"), item.getDouble("lon"))
                    if (!requested.contains(point)) continue
                    add(
                        SafetyCamera(
                            id = item.getLong("id"),
                            point = point,
                            maxSpeed = item.optString("maxSpeed").takeIf { it.isNotBlank() && it != "null" },
                            direction = item.optString("direction").takeIf { it.isNotBlank() && it != "null" },
                            operator = item.optString("operator").takeIf { it.isNotBlank() && it != "null" },
                            reference = item.optString("reference").takeIf { it.isNotBlank() && it != "null" },
                            source = "OpenStreetMap (önbellek)"
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Unable to read safety-camera cache: ${it.message}")
            emptyList()
        }
    }
}
