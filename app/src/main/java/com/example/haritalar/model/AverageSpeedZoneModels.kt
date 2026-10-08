package com.example.haritalar.model

enum class AverageSpeedZoneDataState {
    IDLE,
    LOADING,
    VERIFIED,
    CACHED,
    UNAVAILABLE
}

data class AverageSpeedZone(
    val id: String,
    val geometry: List<GeoPoint>,
    val maxSpeed: String? = null,
    val direction: String? = null,
    val operator: String? = null,
    val name: String? = null,
    val source: String = "OpenStreetMap",
    val rawTags: Map<String, String> = emptyMap()
)

data class AverageSpeedZoneRouteMatch(
    val zone: AverageSpeedZone,
    val startRouteMeters: Double,
    val endRouteMeters: Double,
    val routeLengthMeters: Double,
    val speedLimitKmh: Int?
)

sealed class AverageSpeedZoneFetchResult {
    data class Success(
        val zones: List<AverageSpeedZone>,
        val fromCache: Boolean = false,
        val endpointUsed: String
    ) : AverageSpeedZoneFetchResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean,
        val fallbackZones: List<AverageSpeedZone> = emptyList()
    ) : AverageSpeedZoneFetchResult()
}


data class AverageSpeedZoneRouteSummary(
    val matches: List<AverageSpeedZoneRouteMatch>,
    val dataState: AverageSpeedZoneDataState,
    val source: String
)
