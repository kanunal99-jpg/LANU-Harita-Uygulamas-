package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RoadFeature
import com.example.haritalar.model.RoadFeatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadFeatureRoutePolicyTest {
    private val route = listOf(
        GeoPoint(41.0000, 29.0000),
        GeoPoint(41.0000, 29.0100),
        GeoPoint(41.0000, 29.0200)
    )

    @Test
    fun routeSamplingIsBoundedAndKeepsEnds() {
        val dense = (0..100).map { i -> GeoPoint(41.0, 29.0 + i * 0.0002) }
        val sampled = RoadFeatureRoutePolicy.sampleQueryPoints(dense)

        assertTrue(sampled.size <= 12)
        assertEquals(dense.first(), sampled.first())
        assertEquals(dense.last(), sampled.last())
    }

    @Test
    fun behindFeatureIsRemovedButAheadFeatureRemains() {
        val user = GeoPoint(41.0, 29.012)
        val behind = RoadFeature(
            id = "behind",
            point = GeoPoint(41.0, 29.005),
            type = RoadFeatureType.SPEED_CALMING,
            title = "Tümsek",
            detail = "OSM"
        )
        val ahead = RoadFeature(
            id = "ahead",
            point = GeoPoint(41.0, 29.016),
            type = RoadFeatureType.LEVEL_CROSSING,
            title = "Geçit",
            detail = "OSM"
        )

        val relevant = RoadFeatureRoutePolicy.relevantAhead(listOf(behind, ahead), route, user)

        assertEquals(listOf("ahead"), relevant.map { it.id })
    }

    @Test
    fun warningRadiusGrowsAtRoadSpeed() {
        assertEquals(
            650,
            RoadFeatureRoutePolicy.warningRadiusMeters(30f, RoadFeatureType.LEVEL_CROSSING)
        )
        assertEquals(
            1000,
            RoadFeatureRoutePolicy.warningRadiusMeters(70f, RoadFeatureType.SPEED_CALMING)
        )
        assertEquals(
            1500,
            RoadFeatureRoutePolicy.warningRadiusMeters(110f, RoadFeatureType.LEVEL_CROSSING)
        )
    }

    @Test
    fun nearestWarningUsesOnlyRouteCorridor() {
        val onRoute = RoadFeature(
            id = "on",
            point = GeoPoint(41.0, 29.015),
            type = RoadFeatureType.ROAD_HAZARD,
            title = "Tehlike",
            detail = "OSM"
        )
        val farSide = RoadFeature(
            id = "side",
            point = GeoPoint(41.01, 29.015),
            type = RoadFeatureType.ROAD_HAZARD,
            title = "Yan yol",
            detail = "OSM"
        )

        val warning = RoadFeatureRoutePolicy.nearestWarning(
            features = listOf(farSide, onRoute),
            route = route,
            userPoint = GeoPoint(41.0, 29.012),
            speedKmh = 90f
        )

        assertEquals("on", warning?.feature?.id)
    }
}
