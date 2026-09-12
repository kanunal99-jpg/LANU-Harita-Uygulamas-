package com.example.haritalar.model

/**
 * Fixed speed-enforcement camera mapped in OpenStreetMap.
 *
 * Speed limits are optional and are never inferred when the source does not provide one.
 */
data class SafetyCamera(
    val id: Long,
    val point: GeoPoint,
    val maxSpeed: String? = null,
    val direction: String? = null,
    val operator: String? = null,
    val reference: String? = null,
    val rawTags: Map<String, String> = emptyMap(),
    val source: String = "OpenStreetMap"
) {
    val displayTitle: String
        get() = "Hız Kamerası"

    val displaySubtitle: String
        get() = buildString {
            append("OSM #$id")
            maxSpeed?.takeIf { it.isNotBlank() }?.let { append(" • Hız: $it") }
            if (!direction.isNullOrBlank()) append(" • Yön: $direction")
        }
}

data class SafetyCameraBoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    fun isValid(): Boolean {
        return south in -90.0..90.0 && north in -90.0..90.0 &&
            west in -180.0..180.0 && east in -180.0..180.0 &&
            south <= north && west <= east
    }

    fun contains(point: GeoPoint): Boolean =
        point.latitude in south..north && point.longitude in west..east
}

sealed class SafetyCameraFetchResult {
    data class Success(
        val cameras: List<SafetyCamera>,
        val fromCache: Boolean = false,
        val endpointUsed: String? = null
    ) : SafetyCameraFetchResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean,
        val fallbackCameras: List<SafetyCamera> = emptyList()
    ) : SafetyCameraFetchResult()
}
