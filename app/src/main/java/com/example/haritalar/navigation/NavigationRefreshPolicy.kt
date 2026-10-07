package com.example.haritalar.navigation

import com.example.haritalar.model.NavigationState

object NavigationRefreshPolicy {
    fun shouldKeepTrafficLoopAlive(state: NavigationState): Boolean =
        state == NavigationState.NAVIGATING || state == NavigationState.OFF_ROUTE_REROUTING

    fun shouldRefreshTrafficNow(state: NavigationState): Boolean =
        state == NavigationState.NAVIGATING
}
