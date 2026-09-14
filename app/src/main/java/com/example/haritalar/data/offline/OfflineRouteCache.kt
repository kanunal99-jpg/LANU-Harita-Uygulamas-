package com.example.haritalar.data.offline

import android.content.Context
import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.LaneDirection
import com.example.haritalar.model.LaneInfo
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.RouteType
import com.example.haritalar.model.TurnManeuver
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the last server-validated route set so an already calculated trip can
 * survive a later network outage. This is a cache, not a synthetic routing engine.
 */
class OfflineRouteCache(context: Context) {
    companion object {
        private const val TAG = "OfflineRouteCache"
        private const val PREFS = "lanu_offline_routes"
        private const val KEY = "last_validated_routes"
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val MAX_ENDPOINT_DISTANCE_METERS = 250.0
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(start: GeoPoint, end: GeoPoint, routes: List<RouteOption>) {
        if (!isValidPoint(start) || !isValidPoint(end) || routes.isEmpty()) return
        val validRoutes = routes.filter { isValidRoute(it) }
        if (validRoutes.isEmpty()) return
        try {
            val root = JSONObject()
                .put("timestamp", System.currentTimeMillis())
                .put("startLat", start.latitude)
                .put("startLon", start.longitude)
                .put("endLat", end.latitude)
                .put("endLon", end.longitude)
                .put("routes", JSONArray().also { array -> validRoutes.forEach { array.put(serializeRoute(it)) } })
            prefs.edit().putString(KEY, root.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to persist offline route cache: ${e.message}")
        }
    }

    fun load(start: GeoPoint, end: GeoPoint, generationId: Long): List<RouteOption> {
        if (!isValidPoint(start) || !isValidPoint(end)) return emptyList()
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val root = JSONObject(raw)
            val timestamp = root.optLong("timestamp", 0L)
            if (timestamp <= 0L || System.currentTimeMillis() - timestamp > MAX_AGE_MS) return emptyList()

            val cachedStart = GeoPoint(root.optDouble("startLat"), root.optDouble("startLon"))
            val cachedEnd = GeoPoint(root.optDouble("endLat"), root.optDouble("endLon"))
            if (!isValidPoint(cachedStart) || !isValidPoint(cachedEnd)) return emptyList()
            if (cachedStart.distanceTo(start) > MAX_ENDPOINT_DISTANCE_METERS || cachedEnd.distanceTo(end) > MAX_ENDPOINT_DISTANCE_METERS) {
                return emptyList()
            }

            val routes = mutableListOf<RouteOption>()
            val array = root.optJSONArray("routes") ?: return emptyList()
            for (i in 0 until array.length()) {
                runCatching { deserializeRoute(array.getJSONObject(i), generationId) }
                    .getOrNull()
                    ?.takeIf(::isValidRoute)
                    ?.let(routes::add)
            }
            routes
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read offline route cache: ${e.message}")
            emptyList()
        }
    }

    private fun isValidPoint(point: GeoPoint): Boolean =
        point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

    private fun isValidRoute(route: RouteOption): Boolean =
        route.geometry.size >= 2 && route.geometry.all(::isValidPoint) &&
            route.distanceMeters.isFinite() && route.distanceMeters >= 0.0 &&
            route.durationSeconds >= 0L

    private fun serializeRoute(route: RouteOption): JSONObject = JSONObject()
        .put("routeId", route.routeId)
        .put("title", route.title)
        .put("summary", route.summary)
        .put("durationSeconds", route.durationSeconds)
        .put("distanceMeters", route.distanceMeters)
        .put("hasTolls", route.hasTolls)
        .put("hasFerry", route.hasFerry)
        .put("routeType", route.routeType.name)
        .put("trafficDelaySeconds", route.trafficDelaySeconds)
        .put("geometry", JSONArray().also { array -> route.geometry.forEach { array.put(pointJson(it)) } })
        .put("maneuvers", JSONArray().also { array -> route.maneuvers.forEach { array.put(maneuverJson(it)) } })

    private fun deserializeRoute(json: JSONObject, generationId: Long): RouteOption? {
        val geometry = json.optJSONArray("geometry") ?: return null
        val points = mutableListOf<GeoPoint>()
        for (i in 0 until geometry.length()) {
            val pointJson = geometry.optJSONObject(i) ?: return null
            val point = runCatching { pointFromJson(pointJson) }.getOrNull() ?: return null
            if (!isValidPoint(point)) return null
            points += point
        }
        if (points.size < 2) return null

        val maneuversJson = json.optJSONArray("maneuvers")
        val maneuvers = mutableListOf<TurnManeuver>()
        if (maneuversJson != null) {
            for (i in 0 until maneuversJson.length()) {
                val m = maneuversJson.getJSONObject(i)
                val lanesJson = m.optJSONArray("lanes")
                val lanes = mutableListOf<LaneInfo>()
                if (lanesJson != null) {
                    for (j in 0 until lanesJson.length()) {
                        val lane = lanesJson.getJSONObject(j)
                        val dirs = mutableListOf<LaneDirection>()
                        val dirsJson = lane.optJSONArray("directions")
                        if (dirsJson != null) {
                            for (k in 0 until dirsJson.length()) {
                                runCatching { dirs += LaneDirection.valueOf(dirsJson.getString(k)) }
                            }
                        }
                        lanes += LaneInfo(dirs, lane.optBoolean("isActive"), lane.optBoolean("isRecommended", true))
                    }
                }
                val maneuverPoint = m.optJSONObject("point") ?: return null
                val maneuverGeoPoint = runCatching { pointFromJson(maneuverPoint) }.getOrNull() ?: return null
                if (!isValidPoint(maneuverGeoPoint)) return null
                val type = runCatching { ManeuverType.valueOf(m.optString("type")) }.getOrNull() ?: return null
                maneuvers += TurnManeuver(
                    instruction = m.optString("instruction"),
                    distanceMeters = m.optDouble("distanceMeters", 0.0),
                    type = type,
                    point = maneuverGeoPoint,
                    roadName = m.optString("roadName"),
                    lanes = lanes,
                    speedLimitKmh = if (m.has("speedLimitKmh") && !m.isNull("speedLimitKmh")) m.optInt("speedLimitKmh") else null
                )
            }
        }

        val routeType = runCatching { RouteType.valueOf(json.optString("routeType")) }.getOrDefault(RouteType.FASTEST)
        return RouteOption(
            routeId = "offline_${json.optString("routeId")}",
            title = "Çevrimdışı • ${json.optString("title")}",
            summary = json.optString("summary"),
            durationSeconds = json.optLong("durationSeconds"),
            distanceMeters = json.optDouble("distanceMeters"),
            geometry = points,
            maneuvers = maneuvers,
            hasTolls = json.optBoolean("hasTolls"),
            hasFerry = json.optBoolean("hasFerry"),
            routeType = routeType,
            trafficDelaySeconds = 0L,
            generationId = generationId
        )
    }

    private fun maneuverJson(m: TurnManeuver): JSONObject = JSONObject()
        .put("instruction", m.instruction)
        .put("distanceMeters", m.distanceMeters)
        .put("type", m.type.name)
        .put("point", pointJson(m.point))
        .put("roadName", m.roadName)
        .put("speedLimitKmh", m.speedLimitKmh)
        .put("lanes", JSONArray().also { array ->
            m.lanes.forEach { lane ->
                array.put(JSONObject()
                    .put("isActive", lane.isActive)
                    .put("isRecommended", lane.isRecommended)
                    .put("directions", JSONArray(lane.directions.map { it.name })))
            }
        })

    private fun pointJson(point: GeoPoint): JSONObject = JSONObject()
        .put("lat", point.latitude)
        .put("lon", point.longitude)

    private fun pointFromJson(json: JSONObject): GeoPoint = GeoPoint(json.getDouble("lat"), json.getDouble("lon"))
}
