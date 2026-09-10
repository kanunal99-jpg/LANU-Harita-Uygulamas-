package com.example.haritalar.model

enum class ManeuverType {
    STRAIGHT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    UTURN,
    ENTER_ROUNDABOUT,
    EXIT_ROUNDABOUT,
    REACH_DESTINATION,
    START,
    FERRY,
    RAMP_RIGHT,
    RAMP_LEFT,
    MERGE
}

enum class RouteType(val displayName: String) {
    FASTEST("En Hızlı"),
    SHORTEST("En Kısa"),
    TOLL_FREE("Ücretsiz Yol"),
    FASTEST_TOLL("Hızlı Ücretli"),
    WITH_FERRY("Hızlı Feribotlu"),
    NO_FERRY("Feribotsuz"),
    TOLL_AND_FERRY_FREE("Ücretsiz & Feribotsuz")
}

enum class TrafficLevel {
    UNKNOWN,
    LOW,
    MODERATE,
    HEAVY,
    SEVERE
}

enum class CameraMode {
    TWO_D,
    THREE_D
}

enum class MapTrackingMode {
    FREE,
    FOLLOW_USER,
    FOLLOW_BEARING
}

enum class NavigationState {
    IDLE,
    ROUTE_SELECTION,
    NAVIGATING,
    OFF_ROUTE_REROUTING,
    ARRIVED
}

enum class PoiCategory(val displayName: String, val iconName: String) {
    RESTAURANT("Restoran", "restaurant"),
    FUEL("Benzinlik", "local_gas_station"),
    HOSPITAL("Hastane", "local_hospital"),
    PHARMACY("Eczane", "local_pharmacy"),
    MARKET("Market", "shopping_cart"),
    PARKING("Otopark", "local_parking"),
    ATM("ATM", "atm"),
    CAFE("Kafe", "local_cafe"),
    CHARGING_STATION("Şarj İstasyonu", "ev_station")
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun distanceTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(other.latitude)
        val lon2 = Math.toRadians(other.longitude)
        val earthRadius = 6371000.0 // meters

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}

enum class LaneDirection {
    STRAIGHT,
    LEFT,
    SLIGHT_LEFT,
    SHARP_LEFT,
    RIGHT,
    SLIGHT_RIGHT,
    SHARP_RIGHT,
    UTURN
}

data class LaneInfo(
    val directions: List<LaneDirection>,
    val isActive: Boolean,
    val isRecommended: Boolean = isActive
)

data class TripSummary(
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Long,
    val averageSpeedKmh: Double,
    val startAddress: String,
    val destinationAddress: String,
    val completedTimestamp: Long = System.currentTimeMillis()
)

data class TurnManeuver(
    val instruction: String,
    val distanceMeters: Double,
    val type: ManeuverType,
    val point: GeoPoint,
    val roadName: String = "",
    val lanes: List<LaneInfo> = emptyList(),
    val speedLimitKmh: Int? = null
)

data class RouteOption(
    val routeId: String,
    val title: String,
    val summary: String,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val geometry: List<GeoPoint>,
    val maneuvers: List<TurnManeuver>,
    val hasTolls: Boolean = false,
    val hasFerry: Boolean = false,
    val routeType: RouteType = RouteType.FASTEST,
    val trafficDelaySeconds: Long = 0,
    val generationId: Long = 0
) {
    val totalDurationSeconds: Long
        get() = durationSeconds + trafficDelaySeconds
}

data class SearchResult(
    val id: String,
    val name: String,
    val displayName: String,
    val point: GeoPoint,
    val type: String
)

data class PoiItem(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val point: GeoPoint,
    val address: String? = null
)

data class TrafficSegment(
    val coordinates: List<GeoPoint>,
    val currentSpeed: Double,
    val freeFlowSpeed: Double,
    val delaySeconds: Long,
    val confidence: Double = 1.0,
    val roadClosure: Boolean = false
)

data class TrafficStatus(
    val verified: Boolean,
    val message: String,
    val delaySeconds: Long = 0,
    val trafficLevel: TrafficLevel = TrafficLevel.UNKNOWN
)
