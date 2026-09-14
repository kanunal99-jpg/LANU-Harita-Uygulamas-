package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.haritalar.data.offline.OfflineRouteCache
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TurnManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun useAppContext() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
  }

  @Test
  fun offlineRouteCache_roundTripsValidatedRoute() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.getSharedPreferences("lanu_offline_routes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    val cache = OfflineRouteCache(context)
    val start = GeoPoint(41.0000, 29.0000)
    val middle = GeoPoint(41.0000, 29.0010)
    val end = GeoPoint(41.0000, 29.0020)
    val route = RouteOption(
      routeId = "server-route",
      title = "En Hızlı",
      summary = "Test Caddesi",
      durationSeconds = 120,
      distanceMeters = 222.0,
      geometry = listOf(start, middle, end),
      maneuvers = listOf(TurnManeuver("Sağa dön", 80.0, ManeuverType.RIGHT, middle, "Test Caddesi"))
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
  fun offlineRouteCache_rejectsDifferentDestination() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.getSharedPreferences("lanu_offline_routes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    val cache = OfflineRouteCache(context)
    val start = GeoPoint(41.0000, 29.0000)
    val end = GeoPoint(41.0000, 29.0020)
    val route = RouteOption(
      routeId = "server-route",
      title = "En Hızlı",
      summary = "Test Caddesi",
      durationSeconds = 120,
      distanceMeters = 222.0,
      geometry = listOf(start, end),
      maneuvers = emptyList()
    )

    cache.save(start, end, listOf(route))
    assertFalse(cache.load(start, GeoPoint(41.01, 29.01), 1L).isNotEmpty())
  }
}
