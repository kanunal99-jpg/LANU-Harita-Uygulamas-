package com.example.haritalar.model

/**
 * Domain model representing a verified real-world traffic signal from OpenStreetMap.
 * Physical infrastructure: highway=traffic_signals
 *
 * NOTE: This is road infrastructure data, completely distinct and independent from
 * real-time traffic congestion data (TrafficSegment / TomTom / TrafficLevel).
 */
data class TrafficSignal(
    val id: Long, // OpenStreetMap Node ID (e.g. 31678629)
    val point: GeoPoint,
    val crossing: String? = null, // e.g. "traffic_signals", "marked", "unmarked", "island", "zebra"
    val direction: String? = null, // e.g. "forward", "backward", "both"
    val hasSound: Boolean = false, // traffic_signals:sound == "yes" / "acoustic"
    val hasVibration: Boolean = false, // traffic_signals:vibration == "yes" / "tactile_paving"
    val hasArrow: Boolean = false, // traffic_signals:arrow == "yes"
    val reference: String? = null, // ref or reference tag
    val rawTags: Map<String, String> = emptyMap(),
    val source: String = "OpenStreetMap"
) {
    val displayTitle: String
        get() = when {
            crossing != null && crossing.isNotBlank() -> "Yaya & Kavşak Sinyalizasyonu"
            hasArrow -> "Dönüş Oklu Trafik Işığı"
            else -> "Trafik Sinyalizasyon Işığı"
        }

    val displaySubtitle: String
        get() = buildString {
            append("OSM #$id")
            if (!crossing.isNullOrBlank()) append(" • Geçit: $crossing")
            if (hasSound) append(" • Sesli")
            if (hasVibration) append(" • Titreşimli")
        }
}

/**
 * Geographic bounding box for viewport querying.
 */
data class TrafficSignalBoundingBox(
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

    /**
     * Approximate width and height in degrees.
     */
    val latSpan: Double get() = north - south
    val lonSpan: Double get() = east - west

    /**
     * Checks if this bounding box contains a given point.
     */
    fun contains(point: GeoPoint): Boolean {
        return point.latitude in south..north && point.longitude in west..east
    }
}

/**
 * Result wrapper representing the state of a traffic signal query.
 */
sealed class TrafficSignalFetchResult {
    data class Success(
        val signals: List<TrafficSignal>,
        val fromCache: Boolean = false,
        val endpointUsed: String? = null
    ) : TrafficSignalFetchResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean,
        val fallbackSignals: List<TrafficSignal> = emptyList()
    ) : TrafficSignalFetchResult()
}
