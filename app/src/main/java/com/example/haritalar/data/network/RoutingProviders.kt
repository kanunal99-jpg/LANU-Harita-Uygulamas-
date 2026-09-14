package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.RouteType
import com.example.haritalar.model.TurnManeuver
import com.example.haritalar.navigation.LaneGuidanceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

interface RoutingProvider {
    suspend fun calculateRoutes(
        start: GeoPoint,
        end: GeoPoint,
        generationId: Long
    ): List<RouteOption>
}

class ValhallaRoutingProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : RoutingProvider {

    override suspend fun calculateRoutes(
        start: GeoPoint,
        end: GeoPoint,
        generationId: Long
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val routes = mutableListOf<RouteOption>()

        // 1. Primary Request (En Hızlı)
        val primaryRoute = queryValhalla(start, end, costingOptions = null, routeType = RouteType.FASTEST, generationId = generationId)
        if (primaryRoute != null) {
            routes.add(primaryRoute)
        }

        // 2. Shortest (En Kısa)
        val shortestOptions = JSONObject().apply {
            put("auto", JSONObject().apply { put("shortest", true) })
        }
        val shortestRoute = queryValhalla(start, end, costingOptions = shortestOptions, routeType = RouteType.SHORTEST, generationId = generationId)
        if (shortestRoute != null && !isDuplicateGeometry(routes, shortestRoute)) {
            routes.add(shortestRoute)
        }

        // 3. Toll-free (Ücretsiz Yol Öncelikli)
        val tollFreeOptions = JSONObject().apply {
            put("auto", JSONObject().apply {
                put("use_tolls", 0.0)
                put("toll_booth_cost", 1000)
            })
        }
        val tollFreeRoute = queryValhalla(start, end, costingOptions = tollFreeOptions, routeType = RouteType.TOLL_FREE, generationId = generationId)
        if (tollFreeRoute != null && !isDuplicateGeometry(routes, tollFreeRoute)) {
            routes.add(tollFreeRoute)
        }

        // 4. Ferry avoidance (Feribotsuz)
        val noFerryOptions = JSONObject().apply {
            put("auto", JSONObject().apply {
                put("use_ferry", 0.0)
                put("ferry_cost", 1000)
            })
        }
        val noFerryRoute = queryValhalla(start, end, costingOptions = noFerryOptions, routeType = RouteType.NO_FERRY, generationId = generationId)
        if (noFerryRoute != null && !isDuplicateGeometry(routes, noFerryRoute)) {
            routes.add(noFerryRoute)
        }

        // 5. Toll & Ferry Free (Ücretsiz & Feribotsuz)
        val freeAndNoFerryOptions = JSONObject().apply {
            put("auto", JSONObject().apply {
                put("use_tolls", 0.0)
                put("use_ferry", 0.0)
            })
        }
        val freeNoFerryRoute = queryValhalla(start, end, costingOptions = freeAndNoFerryOptions, routeType = RouteType.TOLL_AND_FERRY_FREE, generationId = generationId)
        if (freeNoFerryRoute != null && !isDuplicateGeometry(routes, freeNoFerryRoute)) {
            routes.add(freeNoFerryRoute)
        }

        routes
    }

    private fun isDuplicateGeometry(existing: List<RouteOption>, candidate: RouteOption): Boolean {
        return existing.any {
            Math.abs(it.distanceMeters - candidate.distanceMeters) < 100.0 &&
                    Math.abs(it.durationSeconds - candidate.durationSeconds) < 60
        }
    }

    private fun queryValhalla(
        start: GeoPoint,
        end: GeoPoint,
        costingOptions: JSONObject?,
        routeType: RouteType,
        generationId: Long
    ): RouteOption? {
        try {
            val jsonPayload = JSONObject().apply {
                val locations = JSONArray().apply {
                    put(JSONObject().apply {
                        put("lat", start.latitude)
                        put("lon", start.longitude)
                        put("type", "break")
                    })
                    put(JSONObject().apply {
                        put("lat", end.latitude)
                        put("lon", end.longitude)
                        put("type", "break")
                    })
                }
                put("locations", locations)
                put("costing", "auto")
                if (costingOptions != null) {
                    put("costing_options", costingOptions)
                }
                put("directions_options", JSONObject().apply {
                    put("units", "kilometers")
                    put("language", "tr-TR")
                })
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://valhalla1.openstreetmap.de/route")
                .post(requestBody)
                .header("User-Agent", "HaritalarAndroidNav/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null

                    val body = response.body?.string() ?: return null
                    val root = JSONObject(body)
                    val trip = root.optJSONObject("trip") ?: return null
                val summary = trip.optJSONObject("summary") ?: return null

                val totalTime = summary.optLong("time", 0)
                val totalLengthKm = summary.optDouble("length", 0.0)
                val distanceMeters = totalLengthKm * 1000.0

                val hasToll = summary.optBoolean("has_toll", false)
                val hasFerry = summary.optBoolean("has_ferry", false)

                val legs = trip.optJSONArray("legs") ?: return null
                if (legs.length() == 0) return null

                val leg0 = legs.getJSONObject(0)
                val encodedShape = leg0.optString("shape", "")
                val decodedGeometry = if (encodedShape.isNotEmpty()) {
                    PolylineDecoder.decodePolyline6(encodedShape)
                } else {
                    listOf(start, end)
                }

                val maneuvers = mutableListOf<TurnManeuver>()
                val maneuversArray = leg0.optJSONArray("maneuvers")
                if (maneuversArray != null) {
                    for (i in 0 until maneuversArray.length()) {
                        val m = maneuversArray.getJSONObject(i)
                        val instruction = m.optString("instruction", "Yola devam edin")
                        val lengthKm = m.optDouble("length", 0.0)
                        val typeCode = m.optInt("type", 0)
                        val streetNames = m.optJSONArray("street_names")
                        val road = if (streetNames != null && streetNames.length() > 0) streetNames.getString(0) else ""
                        val shapeIndex = m.optInt("begin_shape_index", 0)
                        val point = if (shapeIndex in decodedGeometry.indices) decodedGeometry[shapeIndex] else start

                        val mType = mapValhallaManeuverType(typeCode)
                        val lanes = LaneGuidanceHelper.generateLanesForManeuver(mType, road)
                        val speedLimit = LaneGuidanceHelper.determineSpeedLimit(road)

                        maneuvers.add(
                            TurnManeuver(
                                instruction = instruction,
                                distanceMeters = lengthKm * 1000.0,
                                type = mType,
                                point = point,
                                roadName = road,
                                lanes = lanes,
                                speedLimitKmh = speedLimit
                            )
                        )
                    }
                }

                val routeId = "valhalla_${routeType.name.lowercase()}_${UUID.randomUUID().toString().take(6)}"

                return RouteOption(
                    routeId = routeId,
                    title = routeType.displayName,
                    summary = maneuvers.firstOrNull { it.roadName.isNotEmpty() }?.roadName ?: "En uygun rota",
                    durationSeconds = totalTime,
                    distanceMeters = distanceMeters,
                    geometry = decodedGeometry,
                    maneuvers = maneuvers,
                    hasTolls = hasToll,
                    hasFerry = hasFerry,
                    routeType = routeType,
                    generationId = generationId
                )
        } catch (e: Exception) {
            return null
        }
    }

    private fun mapValhallaManeuverType(type: Int): ManeuverType {
        return when (type) {
            1 -> ManeuverType.START
            2, 3 -> ManeuverType.RIGHT
            4, 5 -> ManeuverType.LEFT
            6 -> ManeuverType.UTURN
            7, 8 -> ManeuverType.SLIGHT_RIGHT
            9, 10 -> ManeuverType.SLIGHT_LEFT
            11 -> ManeuverType.STRAIGHT
            14, 15 -> ManeuverType.ENTER_ROUNDABOUT
            16 -> ManeuverType.EXIT_ROUNDABOUT
            17, 18 -> ManeuverType.RAMP_RIGHT
            19, 20 -> ManeuverType.RAMP_LEFT
            24 -> ManeuverType.FERRY
            else -> ManeuverType.STRAIGHT
        }
    }
}

class OsrmRoutingProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : RoutingProvider {

    override suspend fun calculateRoutes(
        start: GeoPoint,
        end: GeoPoint,
        generationId: Long
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "${start.longitude},${start.latitude};${end.longitude},${end.latitude}" +
                    "?overview=full&geometries=geojson&steps=true&alternatives=true"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HaritalarAndroidNav/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()

                    val body = response.body?.string() ?: return@withContext emptyList()
                    val root = JSONObject(body)
                    val routesArray = root.optJSONArray("routes") ?: return@withContext emptyList()

                val results = mutableListOf<RouteOption>()
                val routeTypes = listOf(
                    RouteType.FASTEST,
                    RouteType.SHORTEST,
                    RouteType.TOLL_FREE,
                    RouteType.FASTEST_TOLL,
                    RouteType.NO_FERRY,
                    RouteType.WITH_FERRY,
                    RouteType.TOLL_AND_FERRY_FREE
                )

                for (i in 0 until routesArray.length()) {
                    val r = routesArray.getJSONObject(i)
                    val duration = r.optDouble("duration", 0.0).toLong()
                    val distance = r.optDouble("distance", 0.0)

                    val geom = r.optJSONObject("geometry")
                    val coords = geom?.optJSONArray("coordinates")
                    val geometryPoints = mutableListOf<GeoPoint>()
                    if (coords != null) {
                        for (c in 0 until coords.length()) {
                            val pt = coords.getJSONArray(c)
                            geometryPoints.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                        }
                    }

                    val legs = r.optJSONArray("legs")
                    val maneuvers = mutableListOf<TurnManeuver>()
                    var mainRoad = ""
                    var hasToll = false
                    var hasFerry = false

                    if (legs != null && legs.length() > 0) {
                        val leg = legs.getJSONObject(0)
                        val steps = leg.optJSONArray("steps")
                        if (steps != null) {
                            for (s in 0 until steps.length()) {
                                val step = steps.getJSONObject(s)
                                val stepName = step.optString("name", "")
                                if (mainRoad.isEmpty() && stepName.isNotEmpty()) {
                                    mainRoad = stepName
                                }
                                val stepDist = step.optDouble("distance", 0.0)
                                val manObj = step.optJSONObject("maneuver")
                                val manTypeStr = manObj?.optString("type", "continue")
                                val modifier = manObj?.optString("modifier", "")

                                val manLocation = manObj?.optJSONArray("location")
                                val stepPoint = if (manLocation != null && manLocation.length() >= 2) {
                                    GeoPoint(manLocation.getDouble(1), manLocation.getDouble(0))
                                } else {
                                    start
                                }

                                if (manTypeStr == "toll" || step.optString("mode") == "toll") {
                                    hasToll = true
                                }
                                if (manTypeStr == "ferry" || step.optString("mode") == "ferry") {
                                    hasFerry = true
                                }

                                val instruction = formatOsrmInstruction(manTypeStr, modifier, stepName)
                                val mType = mapOsrmManeuverType(manTypeStr, modifier)
                                val lanes = LaneGuidanceHelper.generateLanesForManeuver(mType, stepName)
                                val speedLimit = LaneGuidanceHelper.determineSpeedLimit(stepName)

                                maneuvers.add(
                                    TurnManeuver(
                                        instruction = instruction,
                                        distanceMeters = stepDist,
                                        type = mType,
                                        point = stepPoint,
                                        roadName = stepName,
                                        lanes = lanes,
                                        speedLimitKmh = speedLimit
                                    )
                                )
                            }
                        }
                    }

                    val assignedType = routeTypes.getOrElse(i) { RouteType.FASTEST }
                    val routeId = "osrm_${assignedType.name.lowercase()}_${UUID.randomUUID().toString().take(6)}"

                    results.add(
                        RouteOption(
                            routeId = routeId,
                            title = assignedType.displayName,
                            summary = if (mainRoad.isNotEmpty()) mainRoad else "Rota $i",
                            durationSeconds = duration,
                            distanceMeters = distance,
                            geometry = geometryPoints,
                            maneuvers = maneuvers,
                            hasTolls = hasToll,
                            hasFerry = hasFerry,
                            routeType = assignedType,
                            generationId = generationId
                        )
                    )
                }
                results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun mapOsrmManeuverType(type: String?, modifier: String?): ManeuverType {
        return when (type) {
            "turn" -> when (modifier) {
                "sharp right" -> ManeuverType.SHARP_RIGHT
                "right" -> ManeuverType.RIGHT
                "slight right" -> ManeuverType.SLIGHT_RIGHT
                "sharp left" -> ManeuverType.SHARP_LEFT
                "left" -> ManeuverType.LEFT
                "slight left" -> ManeuverType.SLIGHT_LEFT
                "uturn" -> ManeuverType.UTURN
                else -> ManeuverType.STRAIGHT
            }
            "depart" -> ManeuverType.START
            "arrive" -> ManeuverType.REACH_DESTINATION
            "roundabout", "rotary" -> ManeuverType.ENTER_ROUNDABOUT
            "exit roundabout", "exit rotary" -> ManeuverType.EXIT_ROUNDABOUT
            "on ramp" -> ManeuverType.RAMP_RIGHT
            "off ramp" -> ManeuverType.RAMP_RIGHT
            "fork" -> if (modifier?.contains("left") == true) ManeuverType.SLIGHT_LEFT else ManeuverType.SLIGHT_RIGHT
            "ferry" -> ManeuverType.FERRY
            else -> ManeuverType.STRAIGHT
        }
    }

    private fun formatOsrmInstruction(type: String?, modifier: String?, roadName: String): String {
        val roadPart = if (roadName.isNotEmpty()) " '$roadName' yönüne" else ""
        return when (type) {
            "depart" -> "Harekete geçin"
            "arrive" -> "Hedefinize ulaştınız"
            "turn" -> when (modifier) {
                "right" -> "$roadPart sağa dönün"
                "left" -> "$roadPart sola dönün"
                "slight right" -> "Hafifçe sağa yönelin"
                "slight left" -> "Hafifçe sola yönelin"
                "sharp right" -> "Keskin sağa dönün"
                "sharp left" -> "Keskin sola dönün"
                "uturn" -> "U dönüşü yapın"
                else -> "Dönün"
            }
            "roundabout" -> "Dönel kavşağa girin"
            "exit roundabout" -> "Kavşaktan çıkın"
            "merge" -> "Yola bağlanın"
            "on ramp", "off ramp" -> "Çıkışı kullanın"
            "ferry" -> "Feribota binin"
            else -> if (roadName.isNotEmpty()) "$roadName üzerinde devam edin" else "Düz devam edin"
        }
    }
}
