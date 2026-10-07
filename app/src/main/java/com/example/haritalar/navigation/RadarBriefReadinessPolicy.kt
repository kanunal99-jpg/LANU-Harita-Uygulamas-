package com.example.haritalar.navigation

import com.example.haritalar.model.NavigationState

object RadarBriefReadinessPolicy {
    fun isSelectedRouteScanReady(
        selectedRouteId: String?,
        completedRouteId: String?,
        isPrefetching: Boolean
    ): Boolean {
        return selectedRouteId != null &&
            !isPrefetching &&
            completedRouteId == selectedRouteId
    }

    fun canDeliverInState(state: NavigationState): Boolean =
        state == NavigationState.ROUTE_SELECTION ||
            state == NavigationState.NAVIGATING
}
