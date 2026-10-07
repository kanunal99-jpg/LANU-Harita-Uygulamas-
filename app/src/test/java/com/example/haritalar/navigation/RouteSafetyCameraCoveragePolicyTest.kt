package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSafetyCameraCoveragePolicyTest {
    @Test
    fun typicalRouteUsesBoundedFullCameraSampling() {
        val route = listOf(
            GeoPoint(40.0000, 29.0000),
            GeoPoint(40.5760, 29.0000)
        )

        val plan = RouteSafetyCameraCoveragePolicy.plan(route)

        assertNotNull(plan)
        assertTrue(plan!!.fullCoverage)
        assertTrue(plan.points.size <= RouteSafetyCameraCoveragePolicy.MAX_SAMPLE_POINTS)
        assertTrue(plan.maxSampleGapMeters <= RouteSafetyCameraCoveragePolicy.MAX_FULL_SAMPLE_GAP_METERS + 1.0)
    }

    @Test
    fun veryLongRouteCannotSilentlyClaimFullCameraCoverage() {
        val route = listOf(
            GeoPoint(38.5000, 29.0000),
            GeoPoint(41.2000, 29.0000)
        )

        val plan = RouteSafetyCameraCoveragePolicy.plan(route)

        assertNotNull(plan)
        assertEquals(RouteSafetyCameraCoveragePolicy.MAX_SAMPLE_POINTS, plan!!.points.size)
        assertFalse(plan.fullCoverage)
    }

    @Test
    fun routeFilterRejectsFarAwayCamera() {
        val route = listOf(
            GeoPoint(40.0000, 29.0000),
            GeoPoint(40.1000, 29.0000)
        )
        val cameras = listOf(
            SafetyCamera(1L, GeoPoint(40.0500, 29.0005)),
            SafetyCamera(2L, GeoPoint(40.0500, 29.0500))
        )

        val filtered = RouteSafetyCameraCoveragePolicy.filterToRoute(route, cameras)

        assertEquals(listOf(1L), filtered.map { it.id })
    }
}
