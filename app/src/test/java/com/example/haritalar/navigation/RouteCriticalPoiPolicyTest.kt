package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCriticalPoiPolicyTest {
    @Test
    fun typical64KmRouteGetsFullSamplingCoverage() {
        val route = listOf(
            GeoPoint(40.0000, 29.0000),
            GeoPoint(40.5760, 29.0000)
        )

        val plan = RouteCriticalPoiPolicy.plan(route)

        assertNotNull(plan)
        assertTrue(plan!!.fullCoverage)
        assertTrue(plan.points.size in 8..10)
        assertTrue(plan.maxSampleGapMeters <= RouteCriticalPoiPolicy.MAX_FULL_SAMPLE_GAP_METERS + 1.0)
    }

    @Test
    fun veryLongRouteIsExplicitlyPartialInsteadOfPretendingFullCoverage() {
        val route = listOf(
            GeoPoint(39.0000, 29.0000),
            GeoPoint(40.8000, 29.0000)
        )

        val plan = RouteCriticalPoiPolicy.plan(route)

        assertNotNull(plan)
        assertEquals(RouteCriticalPoiPolicy.MAX_SAMPLE_POINTS, plan!!.points.size)
        assertFalse(plan.fullCoverage)
        assertTrue(plan.maxSampleGapMeters > RouteCriticalPoiPolicy.MAX_FULL_SAMPLE_GAP_METERS)
    }

    @Test
    fun filtersNonCriticalAndOffRoutePois() {
        val route = listOf(
            GeoPoint(40.0000, 29.0000),
            GeoPoint(40.1000, 29.0000)
        )
        val candidates = listOf(
            PoiItem("fuel", "Yakıt", PoiCategory.FUEL, GeoPoint(40.0500, 29.0050)),
            PoiItem("pharmacy", "Eczane", PoiCategory.PHARMACY, GeoPoint(40.0600, 29.0040)),
            PoiItem("cafe", "Kafe", PoiCategory.CAFE, GeoPoint(40.0550, 29.0030)),
            PoiItem("far_hospital", "Uzak Hastane", PoiCategory.HOSPITAL, GeoPoint(40.0500, 29.0800))
        )

        val filtered = RouteCriticalPoiPolicy.filterToCriticalRouteCorridor(route, candidates)

        assertEquals(setOf("fuel", "pharmacy"), filtered.map { it.id }.toSet())
    }
}
