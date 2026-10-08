package com.example.haritalar.data.cache

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCameraBoundingBox
import kotlin.math.roundToInt

/**
 * Deterministic bounded index for geographically separate last-known-good areas.
 * All decisions are pure and can be tested without Android SharedPreferences.
 */
internal object SafetyCameraCacheIndexPolicy {
    const val MAX_AREAS = 48
    const val MAX_CACHE_BYTES = 3_000_000
    const val MAX_AREA_BYTES = 200_000
    private const val KEY_PREFIX = "lanu_safety_camera_area_v2_"

    data class Area(
        val key: String,
        val bbox: SafetyCameraBoundingBox,
        val savedAtMillis: Long,
        val payloadBytes: Int = 0
    )

    fun keyFor(bbox: SafetyCameraBoundingBox): String {
        require(bbox.isValid()) { "Invalid camera cache bbox" }
        val coordinates = listOf(bbox.south, bbox.west, bbox.north, bbox.east)
            .joinToString("_") { (it * 10_000.0).roundToInt().toString() }
        return KEY_PREFIX + coordinates
    }

    fun retain(entries: List<Area>, newArea: Area): List<Area> {
        var usedBytes = 0L
        return (listOf(newArea) + entries)
            .filter { it.key.startsWith(KEY_PREFIX) && it.bbox.isValid() && it.savedAtMillis > 0L }
            .sortedByDescending { it.savedAtMillis }
            .distinctBy { it.key }
            .filter { area ->
                val bytes = area.payloadBytes.coerceAtLeast(0).toLong()
                if (bytes > MAX_AREA_BYTES || usedBytes + bytes > MAX_CACHE_BYTES) {
                    false
                } else {
                    usedBytes += bytes
                    true
                }
            }
            .take(MAX_AREAS)
    }

    /**
     * Only source-backed historical areas that overlap the requested CENTER qualify
     * for partial fallback. This does not mean the entire bbox was verified.
     */
    fun candidates(
        areas: List<Area>,
        requested: SafetyCameraBoundingBox,
        nowMillis: Long,
        maxAgeMillis: Long
    ): List<Area> {
        if (!requested.isValid() || maxAgeMillis <= 0L) return emptyList()
        val center = GeoPoint(
            latitude = (requested.south + requested.north) / 2.0,
            longitude = (requested.west + requested.east) / 2.0
        )
        return areas.filter { area ->
            val age = nowMillis - area.savedAtMillis
            area.key.startsWith(KEY_PREFIX) &&
                area.bbox.isValid() &&
                age >= 0L &&
                age <= maxAgeMillis &&
                area.bbox.contains(center)
        }.sortedByDescending { it.savedAtMillis }
    }
}
