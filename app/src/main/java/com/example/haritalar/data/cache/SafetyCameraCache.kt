package com.example.haritalar.data.cache

import android.content.Context
import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.model.SafetyCameraType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded multi-region last-known-good OSM radar cache.
 *
 * One old last_known_good entry is still readable for backwards compatibility.
 * A saved region is never presented as fresh provider data, nor as proof that
 * the full requested bbox has been verified.
 */
class SafetyCameraCache(context: Context) {
    companion object {
        private const val TAG = "SafetyCameraCache"
        private const val PREFS = "lanu_safety_camera_cache"
        private const val KEY_LEGACY_PAYLOAD = "last_known_good"
        private const val KEY_AREA_INDEX = "area_index_v2"
        const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
        private const val MAX_CAMERAS_PER_AREA = 500
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun save(bbox: SafetyCameraBoundingBox, cameras: List<SafetyCamera>) {
        if (!bbox.isValid()) return
        runCatching {
            val now = System.currentTimeMillis()
            val newArea = SafetyCameraCacheIndexPolicy.Area(
                key = SafetyCameraCacheIndexPolicy.keyFor(bbox),
                bbox = bbox,
                savedAtMillis = now
            )
            val previous = readIndex()
            val validPrevious = previous.filter { entry ->
                val age = now - entry.savedAtMillis
                age >= 0L && age <= MAX_AGE_MILLIS
            }
            val retained = SafetyCameraCacheIndexPolicy.retain(validPrevious, newArea)
            val currentKeys = retained.map { it.key }.toSet()

            val data = JSONObject()
                .put("savedAt", now)
                .put("bbox", writeBox(bbox))
                .put("cameras", writeCameras(cameras.take(MAX_CAMERAS_PER_AREA)))

            val index = JSONArray()
            retained.forEach { entry ->
                index.put(
                    JSONObject()
                        .put("key", entry.key)
                        .put("savedAt", entry.savedAtMillis)
                        .put("bbox", writeBox(entry.bbox))
                )
            }

            val editor = prefs.edit()
            (previous.map { it.key }.toSet() - currentKeys).forEach { editor.remove(it) }
            editor.putString(newArea.key, data.toString())
            editor.putString(KEY_AREA_INDEX, index.toString())
            editor.apply()
        }.onFailure { error ->
            Log.w(TAG, "Unable to persist multi-area safety camera cache: ${error.message}")
        }
    }

    @Synchronized
    fun loadFor(
        requested: SafetyCameraBoundingBox,
        maxAgeMillis: Long = MAX_AGE_MILLIS
    ): List<SafetyCamera> {
        if (!requested.isValid() || maxAgeMillis <= 0L) return emptyList()
        val now = System.currentTimeMillis()
        val candidates = SafetyCameraCacheIndexPolicy.candidates(
            areas = readIndex(),
            requested = requested,
            nowMillis = now,
            maxAgeMillis = maxAgeMillis
        )

        for (area in candidates) {
            val payload = prefs.getString(area.key, null) ?: continue
            val cached = readPayload(payload, requested, now, maxAgeMillis)
            if (cached.isNotEmpty()) return cached
        }

        // Do not drop old data during the one-time migration from the single-area cache.
        val legacy = prefs.getString(KEY_LEGACY_PAYLOAD, null)
        return legacy?.let { readPayload(it, requested, now, maxAgeMillis) }.orEmpty()
    }

    private fun readIndex(): List<SafetyCameraCacheIndexPolicy.Area> = runCatching {
        val json = JSONArray(prefs.getString(KEY_AREA_INDEX, "[]") ?: "[]")
        buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                val key = item.optString("key")
                val savedAt = item.optLong("savedAt", 0L)
                val box = item.optJSONObject("bbox")?.let(::readBox) ?: continue
                if (!box.isValid() || key.isBlank() || savedAt <= 0L) continue
                add(SafetyCameraCacheIndexPolicy.Area(key, box, savedAt))
            }
        }
    }.getOrElse { error ->
        Log.w(TAG, "Radar cache index invalid; preserved legacy fallback: ${error.message}")
        emptyList()
    }

    private fun readPayload(
        payload: String,
        requested: SafetyCameraBoundingBox,
        nowMillis: Long,
        maxAgeMillis: Long
    ): List<SafetyCamera> = runCatching {
        val root = JSONObject(payload)
        val savedAt = root.optLong("savedAt", 0L)
        val age = nowMillis - savedAt
        if (savedAt <= 0L || age < 0L || age > maxAgeMillis) {
            return@runCatching emptyList()
        }

        val cachedBox = readBox(root.getJSONObject("bbox"))
        val center = GeoPoint(
            latitude = (requested.south + requested.north) / 2.0,
            longitude = (requested.west + requested.east) / 2.0
        )
        if (!cachedBox.isValid() || !cachedBox.contains(center)) {
            return@runCatching emptyList()
        }

        val cameras = root.getJSONArray("cameras")
        buildList {
            for (i in 0 until cameras.length()) {
                val cameraJson = cameras.optJSONObject(i) ?: continue
                val point = GeoPoint(
                    cameraJson.getDouble("lat"),
                    cameraJson.getDouble("lon")
                )
                if (!requested.contains(point)) continue
                add(
                    SafetyCamera(
                        id = cameraJson.getLong("id"),
                        point = point,
                        type = runCatching {
                            SafetyCameraType.valueOf(cameraJson.optString("type"))
                        }.getOrDefault(SafetyCameraType.FIXED_SPEED),
                        maxSpeed = nonBlankField(cameraJson, "maxSpeed"),
                        direction = nonBlankField(cameraJson, "direction"),
                        operator = nonBlankField(cameraJson, "operator"),
                        reference = nonBlankField(cameraJson, "reference"),
                        source = "OpenStreetMap (önbellek)"
                    )
                )
            }
        }
    }.getOrElse { error ->
        Log.w(TAG, "Ignoring unreadable radar cache area: ${error.message}")
        emptyList()
    }

    private fun nonBlankField(json: JSONObject, key: String): String? =
        json.optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun writeCameras(cameras: List<SafetyCamera>): JSONArray =
        JSONArray().also { array ->
            cameras.forEach { camera ->
                array.put(
                    JSONObject()
                        .put("id", camera.id)
                        .put("lat", camera.point.latitude)
                        .put("lon", camera.point.longitude)
                        .put("type", camera.type.name)
                        .put("maxSpeed", camera.maxSpeed)
                        .put("direction", camera.direction)
                        .put("operator", camera.operator)
                        .put("reference", camera.reference)
                )
            }
        }

    private fun writeBox(bbox: SafetyCameraBoundingBox): JSONObject =
        JSONObject()
            .put("south", bbox.south)
            .put("west", bbox.west)
            .put("north", bbox.north)
            .put("east", bbox.east)

    private fun readBox(json: JSONObject): SafetyCameraBoundingBox =
        SafetyCameraBoundingBox(
            south = json.getDouble("south"),
            west = json.getDouble("west"),
            north = json.getDouble("north"),
            east = json.getDouble("east")
        )
}
