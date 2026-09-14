package com.example.haritalar.data.offline

import androidx.test.core.app.ApplicationProvider
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TurnManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineRouteCacheTest {
    private lateinit var context: android.content.Context
    private val start = GeoPoint(41.0000, 29.0000)
    private val middle = GeoPoint(41.0000, 29.0010)
    private val end = GeoPoint(41.0000, 29.0020)
    private lateinit var cache: OfflineRouteCache

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lanu_offline_routes", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        cache = OfflineRouteCache(context)
    }

    @Test
    fun saveAndLoadRestoresValidatedRoute() {
        val route = RouteOption(
            routeId = "server-route",
            title = "En Hızlı",
            summary = "Test Caddesi",
            durationSeconds = 120,
            distanceMeters = 222.0,
            geometry = listOf(start, middle, end),
            maneuvers = listOf(
                TurnManeuver("Sağa dön", 80.0, ManeuverType.RIGHT, middle, "Test Caddesi")
            )
        )

        cache.save(start, end, listOf(route))
        val loaded = cache.load(start, end, 9L)

        assertEquals(1, loaded.size)
        assertEquals("offline_server-route", loaded.single().routeId)
        assertEquals(9L, loaded.single().generationId)
        assertEquals(3, loaded.single().geometry.size)
        assertEquals(ManeuverType.RIGHT, loaded.single().maneuvers.single().type)
    }

    @Test
    fun loadRejectsDifferentDestination() {
        val route = RouteOption(
            routeId = "server-route",
            title = "En Hızlı",
            summary = "Test Caddesi",
            durationSeconds = 120,
            distanceMeters = 222.0,
            geometry = listOf(start, middle, end),
            maneuvers = emptyList()
        )
        cache.save(start, end, listOf(route))

        val loaded = cache.load(start, GeoPoint(41.01, 29.01), 1L)
        assertFalse(loaded.isNotEmpty())
    }
}
