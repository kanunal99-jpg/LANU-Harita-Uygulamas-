package com.example.haritalar.model

enum class RoadFeatureDataState {
    IDLE,
    VERIFIED,
    CACHED,
    UNAVAILABLE
}

enum class RoadFeatureType {
    SPEED_CALMING,
    SCHOOL_ZONE,
    LEVEL_CROSSING,
    ROAD_HAZARD
}

data class RoadFeature(
    val id: String,
    val point: GeoPoint,
    val type: RoadFeatureType,
    val title: String,
    val detail: String,
    val source: String = "OpenStreetMap",
    val rawTagValue: String? = null
)

sealed class RoadFeatureFetchResult {
    data class Success(
        val features: List<RoadFeature>,
        val fromCache: Boolean = false,
        val endpointUsed: String
    ) : RoadFeatureFetchResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean,
        val fallbackFeatures: List<RoadFeature> = emptyList()
    ) : RoadFeatureFetchResult()
}
