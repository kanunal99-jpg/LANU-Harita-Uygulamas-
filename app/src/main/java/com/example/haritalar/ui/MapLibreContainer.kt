package com.example.haritalar.ui

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.haritalar.model.CameraMode
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.NavigationState
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.navigation.UserLocationData
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val BACKUP_STYLE_URL = "https://demotiles.maplibre.org/style.json"

private const val SRC_ACTIVE_ROUTE = "src_active_route"
private const val LAYER_ACTIVE_ROUTE_CASING = "layer_active_route_casing"
private const val LAYER_ACTIVE_ROUTE = "layer_active_route"

private const val SRC_ALT_ROUTES = "src_alt_routes"
private const val LAYER_ALT_ROUTES = "layer_alt_routes"

private const val SRC_TRAFFIC = "src_traffic"
private const val LAYER_TRAFFIC = "layer_traffic"

private const val SRC_POIS = "src_pois"
private const val LAYER_POIS = "layer_pois"

private const val SRC_USER_LOC = "src_user_loc"
private const val LAYER_USER_LOC_PULSE = "layer_user_loc_pulse"
private const val LAYER_USER_LOC = "layer_user_loc"

private const val SRC_DEST_MARKER = "src_dest_marker"
private const val LAYER_DEST_MARKER = "layer_dest_marker"

@Composable
fun MapLibreContainer(
    userLocation: UserLocationData?,
    activeRoute: RouteOption?,
    alternativeRoutes: List<RouteOption>,
    trafficStatus: TrafficStatus?,
    trafficSegments: List<TrafficSegment>,
    isTrafficLayerVisible: Boolean,
    isPoiLayerVisible: Boolean,
    poiList: List<PoiItem>,
    destinationPoint: GeoPoint?,
    cameraMode: CameraMode,
    mapTrackingMode: MapTrackingMode,
    navigationState: NavigationState,
    onMapClick: (GeoPoint) -> Unit,
    onMapDrag: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre singleton
    remember {
        MapLibre.getInstance(context)
    }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapStyle by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                mapInstance = map
                map.uiSettings.isAttributionEnabled = true
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isCompassEnabled = true

                map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                    mapStyle = style
                    setupLayers(style)
                }

                map.addOnMapClickListener { latLng ->
                    onMapClick(GeoPoint(latLng.latitude, latLng.longitude))
                    true
                }

                map.addOnMoveListener(object : org.maplibre.android.maps.MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                        onMapDrag()
                    }
                    override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
                    override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
                })
            }
        }
    }

    // Lifecycle integration
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Update Camera position / 2D / 3D / Tracking
    LaunchedEffect(userLocation, cameraMode, mapTrackingMode, navigationState, mapInstance, mapStyle) {
        val map = mapInstance ?: return@LaunchedEffect
        val style = mapStyle ?: return@LaunchedEffect

        val pitch = if (cameraMode == CameraMode.THREE_D) 55.0 else 0.0

        if (mapTrackingMode == MapTrackingMode.FOLLOW_USER && userLocation != null) {
            val target = LatLng(userLocation.point.latitude, userLocation.point.longitude)
            val cam = CameraPosition.Builder()
                .target(target)
                .zoom(16.0)
                .tilt(pitch)
                .bearing(if (navigationState == NavigationState.NAVIGATING) userLocation.bearing.toDouble() else map.cameraPosition.bearing)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 600)
        } else if (mapTrackingMode == MapTrackingMode.FOLLOW_BEARING && userLocation != null) {
            val target = LatLng(userLocation.point.latitude, userLocation.point.longitude)
            val cam = CameraPosition.Builder()
                .target(target)
                .zoom(17.5)
                .tilt(55.0) // 3D driving view
                .bearing(userLocation.bearing.toDouble())
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 400)
        } else {
            // Apply camera pitch change if in free mode
            if (Math.abs(map.cameraPosition.tilt - pitch) > 5.0) {
                val cam = CameraPosition.Builder(map.cameraPosition)
                    .tilt(pitch)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 400)
            }
        }
    }

    // Update User Location layer
    LaunchedEffect(userLocation, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_USER_LOC) ?: return@LaunchedEffect
        if (userLocation != null) {
            val geoJson = createPointGeoJson(userLocation.point)
            src.setGeoJson(geoJson)
        } else {
            src.setGeoJson(createEmptyFeatureCollection())
        }
    }

    // Update Destination marker
    LaunchedEffect(destinationPoint, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_DEST_MARKER) ?: return@LaunchedEffect
        if (destinationPoint != null) {
            src.setGeoJson(createPointGeoJson(destinationPoint))
        } else {
            src.setGeoJson(createEmptyFeatureCollection())
        }
    }

    // Update Active Route & Alternatives
    LaunchedEffect(activeRoute, alternativeRoutes, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val activeSrc = style.getSourceAs<GeoJsonSource>(SRC_ACTIVE_ROUTE)
        val altSrc = style.getSourceAs<GeoJsonSource>(SRC_ALT_ROUTES)

        if (activeRoute != null) {
            activeSrc?.setGeoJson(createLineStringGeoJson(activeRoute.geometry))
        } else {
            activeSrc?.setGeoJson(createEmptyFeatureCollection())
        }

        val altsWithoutActive = alternativeRoutes.filter { it.routeId != activeRoute?.routeId }
        if (altsWithoutActive.isNotEmpty()) {
            altSrc?.setGeoJson(createMultiLineStringGeoJson(altsWithoutActive.map { it.geometry }))
        } else {
            altSrc?.setGeoJson(createEmptyFeatureCollection())
        }
    }

    // Update Traffic layer
    LaunchedEffect(isTrafficLayerVisible, trafficSegments, activeRoute, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val trafficSrc = style.getSourceAs<GeoJsonSource>(SRC_TRAFFIC) ?: return@LaunchedEffect
        val trafficLayer = style.getLayer(LAYER_TRAFFIC)

        if (!isTrafficLayerVisible || trafficSegments.isEmpty() || activeRoute == null) {
            trafficSrc.setGeoJson(createEmptyFeatureCollection())
            trafficLayer?.setProperties(visibility(Property.NONE))
        } else {
            trafficLayer?.setProperties(visibility(Property.VISIBLE))
            trafficSrc.setGeoJson(createTrafficGeoJson(trafficSegments))
        }
    }

    // Update POI layer
    LaunchedEffect(isPoiLayerVisible, poiList, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val poiSrc = style.getSourceAs<GeoJsonSource>(SRC_POIS) ?: return@LaunchedEffect
        val poiLayer = style.getLayer(LAYER_POIS)

        if (!isPoiLayerVisible || poiList.isEmpty()) {
            poiSrc.setGeoJson(createEmptyFeatureCollection())
            poiLayer?.setProperties(visibility(Property.NONE))
        } else {
            poiLayer?.setProperties(visibility(Property.VISIBLE))
            poiSrc.setGeoJson(createPoisGeoJson(poiList))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}

private fun setupLayers(style: Style) {
    // 1. Alternative routes source & layer (Gray)
    val altSrc = GeoJsonSource(SRC_ALT_ROUTES, createEmptyFeatureCollection())
    style.addSource(altSrc)
    val altLayer = LineLayer(LAYER_ALT_ROUTES, SRC_ALT_ROUTES).apply {
        setProperties(
            lineColor(Color.parseColor("#8E8E93")),
            lineWidth(5f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
            lineOpacity(0.75f)
        )
    }
    style.addLayer(altLayer)

    // 2. Active route source & layers (Electric Blue with navy casing)
    val activeSrc = GeoJsonSource(SRC_ACTIVE_ROUTE, createEmptyFeatureCollection())
    style.addSource(activeSrc)

    val casingLayer = LineLayer(LAYER_ACTIVE_ROUTE_CASING, SRC_ACTIVE_ROUTE).apply {
        setProperties(
            lineColor(Color.parseColor("#0A2540")),
            lineWidth(9f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        )
    }
    style.addLayer(casingLayer)

    val activeLayer = LineLayer(LAYER_ACTIVE_ROUTE, SRC_ACTIVE_ROUTE).apply {
        setProperties(
            lineColor(Color.parseColor("#007AFF")),
            lineWidth(6f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        )
    }
    style.addLayer(activeLayer)

    // 3. Traffic layer
    val trafficSrc = GeoJsonSource(SRC_TRAFFIC, createEmptyFeatureCollection())
    style.addSource(trafficSrc)
    val trafficLayer = LineLayer(LAYER_TRAFFIC, SRC_TRAFFIC).apply {
        setProperties(
            lineColor(Color.parseColor("#FF9500")), // default fallback orange
            lineWidth(6f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
            visibility(Property.VISIBLE)
        )
    }
    style.addLayer(trafficLayer)

    // 4. Destination marker
    val destSrc = GeoJsonSource(SRC_DEST_MARKER, createEmptyFeatureCollection())
    style.addSource(destSrc)
    val destLayer = CircleLayer(LAYER_DEST_MARKER, SRC_DEST_MARKER).apply {
        setProperties(
            circleRadius(9f),
            circleColor(Color.parseColor("#FF3B30")),
            circleStrokeWidth(3f),
            circleStrokeColor(Color.WHITE)
        )
    }
    style.addLayer(destLayer)

    // 5. POIs source & layer
    val poiSrc = GeoJsonSource(SRC_POIS, createEmptyFeatureCollection())
    style.addSource(poiSrc)
    val poiLayer = CircleLayer(LAYER_POIS, SRC_POIS).apply {
        setProperties(
            circleRadius(7f),
            circleColor(Color.parseColor("#5856D6")),
            circleStrokeWidth(2f),
            circleStrokeColor(Color.WHITE)
        )
    }
    style.addLayer(poiLayer)

    // 6. User Location Puck
    val userSrc = GeoJsonSource(SRC_USER_LOC, createEmptyFeatureCollection())
    style.addSource(userSrc)

    val pulseLayer = CircleLayer(LAYER_USER_LOC_PULSE, SRC_USER_LOC).apply {
        setProperties(
            circleRadius(18f),
            circleColor(Color.parseColor("#007AFF")),
            circleOpacity(0.2f)
        )
    }
    style.addLayer(pulseLayer)

    val userLayer = CircleLayer(LAYER_USER_LOC, SRC_USER_LOC).apply {
        setProperties(
            circleRadius(8f),
            circleColor(Color.parseColor("#007AFF")),
            circleStrokeWidth(3f),
            circleStrokeColor(Color.WHITE)
        )
    }
    style.addLayer(userLayer)
}

private fun createEmptyFeatureCollection(): String = "{\"type\":\"FeatureCollection\",\"features\":[]}"

private fun createPointGeoJson(point: GeoPoint): String {
    val feature = JSONObject().apply {
        put("type", "Feature")
        put("geometry", JSONObject().apply {
            put("type", "Point")
            put("coordinates", JSONArray().apply {
                put(point.longitude)
                put(point.latitude)
            })
        })
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", JSONArray().apply { put(feature) })
    }.toString()
}

private fun createLineStringGeoJson(points: List<GeoPoint>): String {
    val coords = JSONArray()
    for (p in points) {
        val pt = JSONArray().apply {
            put(p.longitude)
            put(p.latitude)
        }
        coords.put(pt)
    }
    val feature = JSONObject().apply {
        put("type", "Feature")
        put("geometry", JSONObject().apply {
            put("type", "LineString")
            put("coordinates", coords)
        })
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", JSONArray().apply { put(feature) })
    }.toString()
}

private fun createMultiLineStringGeoJson(lines: List<List<GeoPoint>>): String {
    val features = JSONArray()
    for (line in lines) {
        val coords = JSONArray()
        for (p in line) {
            coords.put(JSONArray().apply {
                put(p.longitude)
                put(p.latitude)
            })
        }
        val feature = JSONObject().apply {
            put("type", "Feature")
            put("geometry", JSONObject().apply {
                put("type", "LineString")
                put("coordinates", coords)
            })
        }
        features.put(feature)
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
}

private fun createTrafficGeoJson(segments: List<TrafficSegment>): String {
    val features = JSONArray()
    for (seg in segments) {
        if (seg.coordinates.size < 2) continue
        val coords = JSONArray()
        for (p in seg.coordinates) {
            coords.put(JSONArray().apply {
                put(p.longitude)
                put(p.latitude)
            })
        }
        val feature = JSONObject().apply {
            put("type", "Feature")
            put("geometry", JSONObject().apply {
                put("type", "LineString")
                put("coordinates", coords)
            })
        }
        features.put(feature)
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
}

private fun createPoisGeoJson(pois: List<PoiItem>): String {
    val features = JSONArray()
    for (poi in pois) {
        val feature = JSONObject().apply {
            put("type", "Feature")
            put("properties", JSONObject().apply {
                put("name", poi.name)
                put("category", poi.category.displayName)
            })
            put("geometry", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(poi.point.longitude)
                    put(poi.point.latitude)
                })
            })
        }
        features.put(feature)
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
}
