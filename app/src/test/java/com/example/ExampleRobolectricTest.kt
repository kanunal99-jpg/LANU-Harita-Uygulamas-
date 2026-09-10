package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.haritalar.data.db.AppDatabase
import com.example.haritalar.data.db.FavoritePlace
import com.example.haritalar.data.network.PolylineDecoder
import com.example.haritalar.data.traffic.TrafficRouteCostModel
import com.example.haritalar.data.traffic.TrafficRouteMatcher
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficSegment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lanu Harita", appName)
    }

    @Test
    fun `test polyline decoder`() {
        // Valhalla polyline6 encoding test
        val polyline = PolylineDecoder.decodePolyline5("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertTrue(polyline.isNotEmpty())
        assertEquals(3, polyline.size)
    }

    @Test
    fun `test geometry aware traffic route matching`() {
        val route = listOf(
            GeoPoint(41.0082, 28.9784),
            GeoPoint(41.0100, 28.9800),
            GeoPoint(41.0150, 28.9850)
        )

        // Point near route (< 40m)
        val nearPoint = GeoPoint(41.00825, 28.97845)
        val isNear = TrafficRouteMatcher.isPointNearPolyline(nearPoint, route, 40.0)
        assertTrue(isNear)

        // Distant point (> 1km away)
        val farPoint = GeoPoint(41.0500, 29.0200)
        val isFarNear = TrafficRouteMatcher.isPointNearPolyline(farPoint, route, 40.0)
        assertFalse(isFarNear)
    }

    @Test
    fun `test traffic cost model`() {
        // When no provider is configured
        val fallbackStatus = TrafficRouteCostModel.calculateTrafficStatus(emptyList(), hasProvider = false)
        assertFalse(fallbackStatus.verified)
        assertEquals(0L, fallbackStatus.delaySeconds)
        assertEquals(TrafficLevel.UNKNOWN, fallbackStatus.trafficLevel)

        // When provider data is present
        val segment = TrafficSegment(
            coordinates = listOf(GeoPoint(41.0, 29.0), GeoPoint(41.01, 29.01)),
            currentSpeed = 20.0,
            freeFlowSpeed = 60.0,
            delaySeconds = 180L
        )
        val activeStatus = TrafficRouteCostModel.calculateTrafficStatus(listOf(segment), hasProvider = true)
        assertTrue(activeStatus.verified)
        assertEquals(180L, activeStatus.delaySeconds)
        assertEquals(TrafficLevel.SEVERE, activeStatus.trafficLevel)
    }

    @Test
    fun `test room database favorites persistence`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.favoriteDao()

        val fav = FavoritePlace(
            title = "Evim",
            address = "Karaköy, Beyoğlu, İstanbul",
            latitude = 41.025,
            longitude = 28.974,
            category = "HOME"
        )
        val id = dao.insertFavorite(fav)
        assertTrue(id > 0)

        val list = dao.getAllFavorites().first()
        assertEquals(1, list.size)
        assertEquals("Evim", list[0].title)

        db.close()
    }

    @Test
    fun `test lane guidance helper and speed limits`() {
        // Highway speed limit
        val otoyolLimit = com.example.haritalar.navigation.LaneGuidanceHelper.determineSpeedLimit("Kuzey Marmara Otoyolu")
        assertEquals(130, otoyolLimit)

        // Boulevard speed limit
        val bulvarLimit = com.example.haritalar.navigation.LaneGuidanceHelper.determineSpeedLimit("Barbaros Bulvarı")
        assertEquals(70, bulvarLimit)

        // Street speed limit
        val sokakLimit = com.example.haritalar.navigation.LaneGuidanceHelper.determineSpeedLimit("Karanfil Sokak")
        assertEquals(50, sokakLimit)

        // Lane generation for right turn
        val lanesRight = com.example.haritalar.navigation.LaneGuidanceHelper.generateLanesForManeuver(
            com.example.haritalar.model.ManeuverType.RIGHT,
            "Barbaros Bulvarı"
        )
        assertTrue(lanesRight.isNotEmpty())
        assertTrue(lanesRight.last().isActive)

        // Voice hint generation
        val voiceHint = com.example.haritalar.navigation.LaneGuidanceHelper.buildLaneVoiceHint(lanesRight)
        assertNotNull(voiceHint)
        assertTrue(voiceHint!!.contains("şerit"))
    }
}
