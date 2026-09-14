package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.haritalar.data.offline.OfflineRouteCache
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field

class OfflineRouteCacheValidationTest {
    @Test
    fun cacheRejectsExpiredEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("lanu_offline_routes", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val start = GeoPoint(41.0, 29.0)
        val end = GeoPoint(41.0, 29.002)
        OfflineRouteCache(context).save(start, end, listOf(RouteOption(
            routeId = "server-route", title = "Test", summary = "Test", durationSeconds = 120,
            distanceMeters = 222.0, geometry = listOf(start, end), maneuvers = emptyList()
        )))

        val raw = prefs.getString("last_validated_routes", null) ?: error("cache missing")
        val root = org.json.JSONObject(raw).put("timestamp", System.currentTimeMillis() - 25L * 60L * 60L * 1000L)
        prefs.edit().putString("last_validated_routes", root.toString()).commit()

        assertTrue(OfflineRouteCache(context).load(start, end, 1L).isEmpty())
    }
}
