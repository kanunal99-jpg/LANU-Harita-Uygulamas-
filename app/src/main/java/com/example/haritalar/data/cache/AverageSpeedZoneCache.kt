package com.example.haritalar.data.cache

import android.content.Context
import android.util.Log
import com.example.haritalar.model.AverageSpeedZone
import com.example.haritalar.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * Route-signature scoped last-known-good cache.
 * Cached corridor data is never labelled as live/verified fresh data.
 */
class AverageSpeedZoneCache(context: Context) {
    companion object {
        private const val TAG = "AverageSpeedZoneCache"
        private const val PREFS = "lanu_average_speed_zone_cache"
        private const val KEY_PAYLOAD = "last_known_good"
        const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(routeSignature: String, zones: List<AverageSpeedZone>) {
        runCatching {
            val items = JSONArray()
            zones.forEach { zone ->
                val geometry = JSONArray()
                zone.geometry.forEach { point ->
                    geometry.put(
                        JSONObject()
                            .put("lat", point.latitude)
                            .put("lon", point.longitude)
                    )
                }

                items.put(
                    JSONObject()
                        .put("id", zone.id)
                        .put("geometry", geometry)
                        .put("maxSpeed", zone.maxSpeed)
                        .put("direction", zone.direction)
                        .put("operator", zone.operator)
                        .put("name", zone.name)
                )
            }

            val root = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put("routeSignature", routeSignature)
                .put("zones", items)

            prefs.edit().putString(KEY_PAYLOAD, root.toString()).apply()
        }.onFailure {
            Log.w(TAG, "Unable to persist average-speed cache: ${it.message}")
        }
    }

    fun load(
        routeSignature: String,
        maxAgeMillis: Long = MAX_AGE_MILLIS
    ): List<AverageSpeedZone> {
        val payload = prefs.getString(KEY_PAYLOAD, null) ?: return emptyList()
        return runCatching {
            val root = JSONObject(payload)
            val savedAt = root.optLong("savedAt", 0L)
            if (savedAt <= 0L || System.currentTimeMillis() - savedAt > maxAgeMillis) {
                return@runCatching emptyList()
            }
            if (root.optString("routeSignature") != routeSignature) {
                return@runCatching emptyList()
            }

            val items = root.optJSONArray("zones") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val rawGeometry = item.optJSONArray("geometry") ?: continue
                    val geometry = buildList {
                        for (j in 0 until rawGeometry.length()) {
                            val p = rawGeometry.getJSONObject(j)
                            val lat = p.optDouble("lat", Double.NaN)
                            val lon = p.optDouble("lon", Double.NaN)
                            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                                add(GeoPoint(lat, lon))
                            }
                        }
                    }
                    if (geometry.size < 2) continue

                    add(
                        AverageSpeedZone(
                            id = item.getString("id"),
                            geometry = geometry,
                            maxSpeed = item.optString("maxSpeed")
                                .takeIf { it.isNotBlank() && it != "null" },
                            direction = item.optString("direction")
                                .takeIf { it.isNotBlank() && it != "null" },
                            operator = item.optString("operator")
                                .takeIf { it.isNotBlank() && it != "null" },
                            name = item.optString("name")
                                .takeIf { it.isNotBlank() && it != "null" },
                            source = "OpenStreetMap (önbellek)"
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Unable to read average-speed cache: ${it.message}")
            emptyList()
        }
    }
}
