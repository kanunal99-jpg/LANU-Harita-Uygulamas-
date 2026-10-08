package com.example.haritalar.data.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.SafetyCameraBoundingBox
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real SharedPreferences persistence tests run on the Android emulator.
 * JVM-only index tests cannot prove JSON roundtrips or migration behavior.
 */
class SafetyCameraMultiRegionCacheInstrumentedTest {
    private fun box(lat: Double, lon: Double) =
        SafetyCameraBoundingBox(lat - 0.08, lon - 0.08, lat + 0.08, lon + 0.08)

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun clear(context: Context) =
        context.getSharedPreferences("lanu_safety_camera_cache", Context.MODE_PRIVATE)
            .edit().clear().commit()

    @Test
    fun separateRegionsSurviveCacheRecreationAndDoNotCrossContaminate() {
        val context = context()
        clear(context)
        val cache = SafetyCameraCache(context)
        val istanbul = box(41.0, 29.0)
        val ankara = box(39.9, 32.8)
        cache.save(istanbul, listOf(SafetyCamera(101L, GeoPoint(41.0, 29.0))))
        cache.save(ankara, listOf(SafetyCamera(202L, GeoPoint(39.9, 32.8))))

        val recreated = SafetyCameraCache(context)
        assertEquals(listOf(101L), recreated.loadFor(istanbul).map { it.id })
        assertEquals(listOf(202L), recreated.loadFor(ankara).map { it.id })
        assertTrue(recreated.loadFor(box(38.0, 27.0)).isEmpty())
    }

    @Test
    fun malformedIndexEntryDoesNotEraseHealthyArea() {
        val context = context()
        clear(context)
        val bbox = box(41.0, 29.0)
        SafetyCameraCache(context).save(
            bbox,
            listOf(SafetyCamera(303L, GeoPoint(41.0, 29.0)))
        )
        val prefs = context.getSharedPreferences("lanu_safety_camera_cache", Context.MODE_PRIVATE)
        val entries = JSONArray(prefs.getString("area_index_v2", "[]"))
        entries.put(JSONObject().put("key", "broken").put("savedAt", 123L).put(
            "bbox", JSONObject().put("south", "not-a-number")
        ))
        prefs.edit().putString("area_index_v2", entries.toString()).commit()

        assertEquals(listOf(303L), SafetyCameraCache(context).loadFor(bbox).map { it.id })
    }

    @Test
    fun previousSingleRegionPayloadRemainsReadableAfterMigration() {
        val context = context()
        clear(context)
        val bbox = box(41.0, 29.0)
        val payload = JSONObject()
            .put("savedAt", System.currentTimeMillis())
            .put("bbox", JSONObject()
                .put("south", bbox.south).put("west", bbox.west)
                .put("north", bbox.north).put("east", bbox.east))
            .put("cameras", JSONArray().put(JSONObject()
                .put("id", 404L)
                .put("lat", 41.0)
                .put("lon", 29.0)
                .put("type", "FIXED_SPEED")))
        context.getSharedPreferences("lanu_safety_camera_cache", Context.MODE_PRIVATE)
            .edit().putString("last_known_good", payload.toString()).commit()

        assertEquals(listOf(404L), SafetyCameraCache(context).loadFor(bbox).map { it.id })
    }
}
