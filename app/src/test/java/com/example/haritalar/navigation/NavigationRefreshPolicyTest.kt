package com.example.haritalar.navigation

import com.example.haritalar.model.NavigationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRefreshPolicyTest {
    @Test
    fun reroutingKeepsTrafficWorkerAliveButDoesNotRefreshOldRoute() {
        assertTrue(NavigationRefreshPolicy.shouldKeepTrafficLoopAlive(NavigationState.NAVIGATING))
        assertTrue(NavigationRefreshPolicy.shouldKeepTrafficLoopAlive(NavigationState.OFF_ROUTE_REROUTING))
        assertTrue(NavigationRefreshPolicy.shouldRefreshTrafficNow(NavigationState.NAVIGATING))
        assertFalse(NavigationRefreshPolicy.shouldRefreshTrafficNow(NavigationState.OFF_ROUTE_REROUTING))
    }

    @Test
    fun nonNavigationStatesStopWorker() {
        assertFalse(NavigationRefreshPolicy.shouldKeepTrafficLoopAlive(NavigationState.IDLE))
        assertFalse(NavigationRefreshPolicy.shouldKeepTrafficLoopAlive(NavigationState.ROUTE_SELECTION))
        assertFalse(NavigationRefreshPolicy.shouldKeepTrafficLoopAlive(NavigationState.ARRIVED))
    }
}
