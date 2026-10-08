package com.example.haritalar.data.cache

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.haritalar.model.AverageSpeedZone
import com.example.haritalar.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AverageSpeedZoneCacheTest {
    @Test
    fun cacheIsScopedToExactRouteSignatureAndPreservesGeometry() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cache = AverageSpeedZoneCache(app)
        val zone = AverageSpeedZone(
            id = "way:42",
            geometry = listOf(
                GeoPoint(40.90, 29.20),
                GeoPoint(40.91, 29.21)
            ),
            maxSpeed = "90",
            name = "Test Koridoru"
        )

        cache.save("route-a", listOf(zone))

        val loaded = cache.load("route-a")
        assertEquals(1, loaded.size)
        assertEquals(2, loaded.single().geometry.size)
        assertEquals("90", loaded.single().maxSpeed)
        assertTrue(cache.load("route-b").isEmpty())
    }

    @Test
    fun expiredCacheIsRejected() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cache = AverageSpeedZoneCache(app)
        cache.save(
            "route-expired",
            listOf(
                AverageSpeedZone(
                    id = "way:7",
                    geometry = listOf(
                        GeoPoint(40.90, 29.20),
                        GeoPoint(40.91, 29.21)
                    )
                )
            )
        )

        assertTrue(cache.load("route-expired", maxAgeMillis = -1L).isEmpty())
    }
}
