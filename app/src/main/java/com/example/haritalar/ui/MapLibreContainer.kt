package com.example.haritalar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox
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
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
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

private const val SRC_TRAFFIC_SIGNALS = "src_traffic_signals"
private const val LAYER_TRAFFIC_SIGNALS = "layer_traffic_signals"
private const val ICON_TRAFFIC_SIGNAL = "icon_traffic_signal"

private const val SRC_POIS = "src_pois"
private const val LAYER_POIS = "layer_pois"

private const val SRC_USER_LOC = "src_user_loc"
private const val LAYER_USER_LOC_PULSE = "layer_user_loc_pulse"
private const val LAYER_USER_LOC = "layer_user_loc"
private const val LAYER_VEHICLE_ARROW = "layer_vehicle_arrow"
private const val ICON_VEHICLE_ARROW = "icon_vehicle_arrow"

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
    isTrafficSignalsLayerVisible: Boolean = true,
    trafficSignals: List<TrafficSignal> = emptyList(),
    isPoiLayerVisible: Boolean,
    poiList: List<PoiItem>,
    destinationPoint: GeoPoint?,
    cameraMode: CameraMode,
    mapTrackingMode: MapTrackingMode,
    navigationState: NavigationState,
    vehicleHeading: Float = userLocation?.bearing ?: 0f,
    onMapClick: (GeoPoint) -> Unit,
    onMapDrag: () -> Unit,
    onViewportChanged: (TrafficSignalBoundingBox, Float) -> Unit = { _, _ -> },
    onTrafficSignalClick: (TrafficSignal) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val currentOnTrafficSignalClick by rememberUpdatedState(onTrafficSignalClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnMapDrag by rememberUpdatedState(onMapDrag)
    val currentTrafficSignals by rememberUpdatedState(trafficSignals)
    val currentIsTrafficSignalsVisible by rememberUpdatedState(isTrafficSignalsLayerVisible)

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
                    setupLayers(style, context)
                }

                map.addOnCameraIdleListener {
                    val zoom = map.cameraPosition.zoom.toFloat()
                    try {
                        val bounds = map.projection.visibleRegion.latLngBounds
                        val bbox = TrafficSignalBoundingBox(
                            south = bounds.latitudeSouth,
                            west = bounds.longitudeWest,
                            north = bounds.latitudeNorth,
                            east = bounds.longitudeEast
                        )
                        currentOnViewportChanged(bbox, zoom)
                    } catch (e: Exception) {
                        android.util.Log.w("MapLibreContainer", "Error reading visible bounds: ${e.message}")
                    }
                }

                map.addOnMapClickListener { latLng ->
                    val clickPoint = GeoPoint(latLng.latitude, latLng.longitude)
                    if (currentIsTrafficSignalsVisible && currentTrafficSignals.isNotEmpty()) {
                        val closest = currentTrafficSignals.minByOrNull { it.point.distanceTo(clickPoint) }
                        if (closest != null && closest.point.distanceTo(clickPoint) <= 35.0) {
                            currentOnTrafficSignalClick(closest)
                            return@addOnMapClickListener true
                        }
                    }
                    currentOnMapClick(clickPoint)
                    true
                }

                map.addOnMoveListener(object : org.maplibre.android.maps.MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                        currentOnMapDrag()
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

    // Update User Location layer (Dynamic Arrow Puck during Navigation, Dot when Free)
    LaunchedEffect(userLocation, vehicleHeading, navigationState, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_USER_LOC) ?: return@LaunchedEffect
        val pulseLayer = style.getLayer(LAYER_USER_LOC_PULSE)
        val userLayer = style.getLayer(LAYER_USER_LOC)
        val arrowLayer = style.getLayer(LAYER_VEHICLE_ARROW)

        val isNavigating = (navigationState == NavigationState.NAVIGATING)

        // Stale puck prevention: only show vehicle arrow during active navigation with valid location
        pulseLayer?.setProperties(visibility(if (isNavigating || userLocation == null) Property.NONE else Property.VISIBLE))
        userLayer?.setProperties(visibility(if (isNavigating || userLocation == null) Property.NONE else Property.VISIBLE))
        arrowLayer?.setProperties(visibility(if (isNavigating && userLocation != null) Property.VISIBLE else Property.NONE))

        if (userLocation != null) {
            val geoJson = createUserLocationGeoJson(
                point = userLocation.point,
                bearing = vehicleHeading,
                isNavigating = isNavigating
            )
            src.setGeoJson(geoJson)
        } else {
            src.setGeoJson(createEmptyFeatureCollection())
        }
    }

    // Update Destination marker and animate camera to target
    LaunchedEffect(destinationPoint, mapStyle, mapInstance) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_DEST_MARKER) ?: return@LaunchedEffect
        if (destinationPoint != null) {
            src.setGeoJson(createPointGeoJson(destinationPoint))
            if (navigationState != NavigationState.NAVIGATING) {
                mapInstance?.let { map ->
                    val target = LatLng(destinationPoint.latitude, destinationPoint.longitude)
                    val cam = CameraPosition.Builder()
                        .target(target)
                        .zoom(16.5)
                        .tilt(0.0)
                        .build()
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 700)
                }
            }
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

    // Update Real Traffic Signals layer
    LaunchedEffect(isTrafficSignalsLayerVisible, trafficSignals, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val signalSrc = style.getSourceAs<GeoJsonSource>(SRC_TRAFFIC_SIGNALS) ?: return@LaunchedEffect
        val signalLayer = style.getLayer(LAYER_TRAFFIC_SIGNALS)

        if (!isTrafficSignalsLayerVisible || trafficSignals.isEmpty()) {
            signalSrc.setGeoJson(createEmptyFeatureCollection())
            signalLayer?.setProperties(visibility(Property.NONE))
        } else {
            signalLayer?.setProperties(visibility(Property.VISIBLE))
            signalSrc.setGeoJson(createTrafficSignalsGeoJson(trafficSignals))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}

private fun setupLayers(style: Style, context: Context) {
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

    // 6. Traffic Signals source & layer (OSM physical infrastructure)
    val signalBitmap = createTrafficSignalBitmap(context)
    style.addImage(ICON_TRAFFIC_SIGNAL, signalBitmap)

    val signalSrc = GeoJsonSource(SRC_TRAFFIC_SIGNALS, createEmptyFeatureCollection())
    style.addSource(signalSrc)

    val signalLayer = SymbolLayer(LAYER_TRAFFIC_SIGNALS, SRC_TRAFFIC_SIGNALS).apply {
        setProperties(
            iconImage(ICON_TRAFFIC_SIGNAL),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconSize(0.7f),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
            visibility(Property.VISIBLE)
        )
    }
    style.addLayer(signalLayer)

    // 7. User Location Puck & Vehicle Arrow
    val userSrc = GeoJsonSource(SRC_USER_LOC, createEmptyFeatureCollection())
    style.addSource(userSrc)

    // Standard blue pulsing dot (Free mode / route selection)
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

    // Dynamic Vehicle Navigation Arrow (Navigating mode)
    // Register custom 3D arrow puck icon
    val arrowBitmap = createVehicleArrowBitmap(context)
    style.addImage(ICON_VEHICLE_ARROW, arrowBitmap)

    val arrowLayer = SymbolLayer(LAYER_VEHICLE_ARROW, SRC_USER_LOC).apply {
        setProperties(
            iconImage(ICON_VEHICLE_ARROW),
            iconRotate(get("bearing")),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
            visibility(Property.NONE)
        )
    }
    style.addLayer(arrowLayer)
}

private fun createUserLocationGeoJson(point: GeoPoint, bearing: Float, isNavigating: Boolean): String {
    val feature = JSONObject().apply {
        put("type", "Feature")
        put("properties", JSONObject().apply {
            put("bearing", bearing.toDouble())
            put("isNavigating", isNavigating)
        })
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

/**
 * Creates high-visibility 3D Navigation Arrow Puck Bitmap with outer puck border,
 * dual-tone 3D arrow facets, and soft drop shadow.
 */
fun createVehicleArrowBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (52 * density).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    // 1. Soft drop shadow (dark semi-transparent)
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + (2.5f * density), 19f * density, shadowPaint)

    // 2. Outer circular white puck backing for contrast on any map style
    val puckWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 19f * density, puckWhitePaint)

    // 3. Subtle blue outline ring
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E7FF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    canvas.drawCircle(cx, cy, 18.5f * density, ringPaint)

    // 4. Directional Navigation Arrow (Pointing Up/North at 0 deg)
    val pathLeft = Path().apply {
        moveTo(cx, cy - 13f * density)
        lineTo(cx - 9.5f * density, cy + 10f * density)
        lineTo(cx, cy + 5.5f * density)
        close()
    }
    val pathRight = Path().apply {
        moveTo(cx, cy - 13f * density)
        lineTo(cx, cy + 5.5f * density)
        lineTo(cx + 9.5f * density, cy + 10f * density)
        close()
    }

    // Left facet: Electric LANU Blue
    val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#007AFF")
        style = Paint.Style.FILL
    }
    canvas.drawPath(pathLeft, leftPaint)

    // Right facet: Deeper blue for 3D lighting
    val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0056B3")
        style = Paint.Style.FILL
    }
    canvas.drawPath(pathRight, rightPaint)

    // Center spine highlight
    val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx, cy - 11.5f * density, cx, cy + 4.5f * density, spinePaint)

    return bitmap
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

private fun createTrafficSignalsGeoJson(signals: List<TrafficSignal>): String {
    val features = JSONArray()
    for (s in signals) {
        val feature = JSONObject().apply {
            put("type", "Feature")
            put("id", s.id)
            put("properties", JSONObject().apply {
                put("id", s.id)
                put("title", s.displayTitle)
                put("crossing", s.crossing ?: "")
                put("hasSound", s.hasSound)
                put("hasVibration", s.hasVibration)
                put("hasArrow", s.hasArrow)
            })
            put("geometry", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(s.point.longitude)
                    put(s.point.latitude)
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

/**
 * Creates high-visibility Traffic Light Icon Bitmap with dark housing,
 * crisp white border, and 3 LED lights (Red, Amber, Green).
 */
fun createTrafficSignalBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val widthPx = (24 * density).toInt().coerceAtLeast(32)
    val heightPx = (44 * density).toInt().coerceAtLeast(60)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val pad = 3f * density
    val rect = android.graphics.RectF(pad, pad, widthPx - pad, heightPx - pad)
    val cornerRadius = 8f * density

    // 1. Dark outer housing
    paint.color = Color.parseColor("#0F172A")
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

    // 2. Crisp border for contrast over any map style
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f * density
    paint.color = Color.parseColor("#F8FAFC")
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

    // 3. Three distinct signal lights: Red (top), Amber (middle), Green (bottom)
    val cx = widthPx / 2f
    val bulbRadius = 3.5f * density
    val stepY = (heightPx - (2 * pad)) / 4f

    paint.style = Paint.Style.FILL

    // Red light (top)
    paint.color = Color.parseColor("#EF4444")
    canvas.drawCircle(cx, pad + stepY, bulbRadius, paint)

    // Amber light (middle)
    paint.color = Color.parseColor("#F59E0B")
    canvas.drawCircle(cx, pad + (stepY * 2f), bulbRadius, paint)

    // Green light (bottom)
    paint.color = Color.parseColor("#10B981")
    canvas.drawCircle(cx, pad + (stepY * 3f), bulbRadius, paint)

    return bitmap
}

