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
@Config(sdk = [35])
class ExampleRobolectricTest {
    @Test fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Lanu Harita", context.getString(R.string.app_name))
    }

    @Test fun `test polyline decoder`() {
        val polyline = PolylineDecoder.decodePolyline5("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertTrue(polyline.isNotEmpty())
        assertEquals(3, polyline.size)
    }

    @Test fun `test geometry aware traffic route matching`() {
        val route = listOf(GeoPoint(41.0082, 28.9784), GeoPoint(41.0100, 28.9800), GeoPoint(41.0150, 28.9850))
        assertTrue(TrafficRouteMatcher.isPointNearPolyline(GeoPoint(41.00825, 28.97845), route, 40.0))
        assertFalse(TrafficRouteMatcher.isPointNearPolyline(GeoPoint(41.0500, 29.0200), route, 40.0))
    }

    @Test fun `test traffic cost model with in-memory stub segments`() {
        val fallbackStatus = TrafficRouteCostModel.calculateTrafficStatus(emptyList(), hasProvider = false)
        assertFalse(fallbackStatus.verified)
        assertFalse(fallbackStatus.isLiveApi)
        assertEquals(0L, fallbackStatus.delaySeconds)
        assertEquals(TrafficLevel.UNKNOWN, fallbackStatus.trafficLevel)
        assertEquals("OSRM / Valhalla Statik Yol Profili", fallbackStatus.sourceName)
        val stubSegment = TrafficSegment(listOf(GeoPoint(41.0, 29.0), GeoPoint(41.01, 29.01)), 20.0, 60.0, 180L)
        val activeStatus = TrafficRouteCostModel.calculateTrafficStatus(listOf(stubSegment), true, "TomTom Traffic Flow API v4")
        assertTrue(activeStatus.verified)
        assertTrue(activeStatus.isLiveApi)
        assertEquals(180L, activeStatus.delaySeconds)
        assertEquals(TrafficLevel.SEVERE, activeStatus.trafficLevel)
        assertEquals("TomTom Traffic Flow API v4", activeStatus.sourceName)
    }

    @Test fun `test room database favorites persistence`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.favoriteDao()
        val id = dao.insertFavorite(
            FavoritePlace(
                title = "Evim",
                address = "Karaköy, Beyoğlu, İstanbul",
                latitude = 41.025,
                longitude = 28.974,
                category = "HOME"
            )
        )
        assertTrue(id > 0)
        val list = dao.getAllFavorites().first()
        assertEquals(1, list.size)
        assertEquals("Evim", list[0].title)
        db.close()
    }

    @Test fun `lane guidance without provider metadata stays empty`() {
        assertNull(com.example.haritalar.navigation.LaneGuidanceHelper.determineSpeedLimit("Kuzey Marmara Otoyolu"))
        assertNull(com.example.haritalar.navigation.LaneGuidanceHelper.determineSpeedLimit("Barbaros Bulvarı"))
        assertNull(com.example.haritalar.navigation.LaneGuidanceHelper.determineSpeedLimit("Karanfil Sokak"))
        assertTrue(com.example.haritalar.navigation.LaneGuidanceHelper.generateLanesForManeuver(com.example.haritalar.model.ManeuverType.RIGHT, "Barbaros Bulvarı").isEmpty())
        assertNull(com.example.haritalar.navigation.LaneGuidanceHelper.buildLaneVoiceHint(emptyList()))
    }
}
