package com.example.haritalar.data.cache

import android.content.Context
import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RoadFeature
import com.example.haritalar.model.RoadFeatureType
import org.json.JSONArray
import org.json.JSONObject

class RoadFeatureCache(context: Context) {
    companion object {
        private const val TAG = "RoadFeatureCache"
        private const val PREFS = "lanu_road_feature_cache"
        private const val KEY_PAYLOAD = "last_known_good"
        const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(routeSignature: String, features: List<RoadFeature>) {
        runCatching {
            val items = JSONArray()
            features.forEach { feature ->
                items.put(
                    JSONObject()
                        .put("id", feature.id)
                        .put("lat", feature.point.latitude)
                        .put("lon", feature.point.longitude)
                        .put("type", feature.type.name)
                        .put("title", feature.title)
                        .put("detail", feature.detail)
                        .put("rawTagValue", feature.rawTagValue)
                )
            }
            val root = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put("routeSignature", routeSignature)
                .put("features", items)
            prefs.edit().putString(KEY_PAYLOAD, root.toString()).apply()
        }.onFailure {
            Log.w(TAG, "Unable to persist road-feature cache: ${it.message}")
        }
    }

    fun load(routeSignature: String, maxAgeMillis: Long = MAX_AGE_MILLIS): List<RoadFeature> {
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

            val items = root.optJSONArray("features") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val type = runCatching {
                        RoadFeatureType.valueOf(item.getString("type"))
                    }.getOrNull() ?: continue
                    add(
                        RoadFeature(
                            id = item.getString("id"),
                            point = GeoPoint(item.getDouble("lat"), item.getDouble("lon")),
                            type = type,
                            title = item.getString("title"),
                            detail = item.getString("detail"),
                            source = "OpenStreetMap (önbellek)",
                            rawTagValue = item.optString("rawTagValue")
                                .takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Unable to read road-feature cache: ${it.message}")
            emptyList()
        }
    }
}
