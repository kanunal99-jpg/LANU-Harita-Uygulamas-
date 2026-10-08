package com.example.haritalar.model

enum class SafetyCameraType(
    val displayName: String,
    val spokenName: String
) {
    FIXED_SPEED("Sabit hız kamerası", "sabit hız kamerası"),
    RED_LIGHT("Kırmızı ışık kamerası", "kırmızı ışık kamerası"),
    SPEED_AND_RED_LIGHT("Hız + kırmızı ışık kamerası", "hız ve kırmızı ışık kamerası"),
    AVERAGE_SPEED_CONTROL_POINT("Ortalama hız denetimi noktası", "ortalama hız denetimi noktası")
}

/**
 * Source-backed traffic-enforcement point mapped from OpenStreetMap.
 *
 * Speed limits are optional and are never inferred when the source does not provide one.
 * Average-speed corridors are represented separately; an individual OSM enforcement node
 * can only be labelled as an average-speed control point here.
 */
data class SafetyCamera(
    val id: Long,
    val point: GeoPoint,
    val type: SafetyCameraType = SafetyCameraType.FIXED_SPEED,
    val maxSpeed: String? = null,
    val direction: String? = null,
    val operator: String? = null,
    val reference: String? = null,
    val rawTags: Map<String, String> = emptyMap(),
    val source: String = "OpenStreetMap"
) {
    val displayTitle: String
        get() = type.displayName

    val displaySubtitle: String
        get() = buildString {
            append(type.displayName)
            append(" • OSM #$id")
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
