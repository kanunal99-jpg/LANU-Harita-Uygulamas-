package com.example.haritalar.navigation

import com.example.haritalar.model.NavigationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarBriefReadinessPolicyTest {
    @Test
    fun staleCompletedRouteNeverMarksNewAlternativeReady() {
        assertFalse(
            RadarBriefReadinessPolicy.isSelectedRouteScanReady(
                selectedRouteId = "route-b",
                completedRouteId = "route-a",
                isPrefetching = false
            )
        )
    }

    @Test
    fun selectedRouteIsReadyOnlyAfterItsOwnScanCompletes() {
        assertFalse(
            RadarBriefReadinessPolicy.isSelectedRouteScanReady(
                selectedRouteId = "route-b",
                completedRouteId = "route-b",
                isPrefetching = true
            )
        )
        assertTrue(
            RadarBriefReadinessPolicy.isSelectedRouteScanReady(
                selectedRouteId = "route-b",
                completedRouteId = "route-b",
                isPrefetching = false
            )
        )
    }

    @Test
    fun briefingCanFinishBeforeOrJustAfterNavigationStarts() {
        assertTrue(RadarBriefReadinessPolicy.canDeliverInState(NavigationState.ROUTE_SELECTION))
        assertTrue(RadarBriefReadinessPolicy.canDeliverInState(NavigationState.NAVIGATING))
        assertFalse(RadarBriefReadinessPolicy.canDeliverInState(NavigationState.IDLE))
    }
}
