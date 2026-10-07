package com.example.haritalar.model

enum class RouteCriticalPoiDataState {
    IDLE,
    LOADING,
    VERIFIED,
    PARTIAL,
    UNAVAILABLE
}

data class RouteCriticalPoiMatch(
    val poi: PoiItem,
    val routeDistanceMeters: Double,
    val corridorDistanceMeters: Double
)

data class RouteCriticalPoiFetchSummary(
    val matches: List<RouteCriticalPoiMatch>,
    val dataState: RouteCriticalPoiDataState,
    val queriedCenters: Int,
    val primaryCenterCount: Int,
    val fallbackCenterCount: Int,
    val unavailableCenterCount: Int
)
