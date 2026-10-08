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
import com.example.haritalar.model.AverageSpeedZoneRouteMatch
import com.example.haritalar.model.CameraMode
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.NavigationState
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.navigation.PoiViewportPolicy
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

private const val STYLE_URL = MapStyleFallbackPolicy.PRIMARY_STYLE_URL
private const val BACKUP_STYLE_URL = MapStyleFallbackPolicy.BACKUP_STYLE_URL
private const val STYLE_FALLBACK_TAG = "MapLibreContainer"
private const val SRC_ACTIVE_ROUTE = "src_active_route"
private const val LAYER_ACTIVE_ROUTE_CASING = "layer_active_route_casing"
private const val LAYER_ACTIVE_ROUTE = "layer_active_route"
private const val SRC_ALT_ROUTES = "src_alt_routes"
private const val LAYER_ALT_ROUTES = "layer_alt_routes"
private const val SRC_TRAFFIC = "src_traffic"
private const val LAYER_TRAFFIC = "layer_traffic"
private const val SRC_AVERAGE_SPEED_ZONES = "src_average_speed_zones"
private const val LAYER_AVERAGE_SPEED_ZONES = "layer_average_speed_zones"
private const val SRC_AVERAGE_SPEED_START = "src_average_speed_start"
private const val LAYER_AVERAGE_SPEED_START = "layer_average_speed_start"
private const val SRC_AVERAGE_SPEED_END = "src_average_speed_end"
private const val LAYER_AVERAGE_SPEED_END = "layer_average_speed_end"
private const val SRC_TRAFFIC_SIGNALS = "src_traffic_signals"
private const val LAYER_TRAFFIC_SIGNALS = "layer_traffic_signals"
private const val ICON_TRAFFIC_SIGNAL = "icon_traffic_signal"
private const val SRC_POIS = "src_pois"
private const val LAYER_POIS = "layer_pois"
private const val ICON_POI_RESTAURANT = "icon_poi_restaurant"
private const val ICON_POI_FUEL = "icon_poi_fuel"
private const val ICON_POI_HOSPITAL = "icon_poi_hospital"
private const val ICON_POI_PHARMACY = "icon_poi_pharmacy"
private const val ICON_POI_MARKET = "icon_poi_market"
private const val ICON_POI_PARKING = "icon_poi_parking"
private const val ICON_POI_ATM = "icon_poi_atm"
private const val ICON_POI_CAFE = "icon_poi_cafe"
private const val ICON_POI_CHARGING = "icon_poi_charging"
private const val SRC_WEATHER = "src_weather"
private const val LAYER_WEATHER = "layer_weather"
private const val ICON_WEATHER_RAIN = "icon_weather_rain"
private const val ICON_WEATHER_FOG = "icon_weather_fog"
private const val ICON_WEATHER_SNOW = "icon_weather_snow"
private const val ICON_WEATHER_STORM = "icon_weather_storm"

private const val SRC_SAFETY_CAMERAS = "src_safety_cameras"
private const val LAYER_SAFETY_CAMERAS = "layer_safety_cameras"
private const val ICON_SAFETY_CAMERA = "icon_safety_camera"
private const val SRC_USER_LOC = "src_user_loc"
private const val LAYER_USER_LOC_PULSE = "layer_user_loc_pulse"
private const val LAYER_USER_LOC = "layer_user_loc"
private const val LAYER_VEHICLE_ARROW = "layer_vehicle_arrow"
private const val ICON_VEHICLE_ARROW = "icon_vehicle_arrow"
private const val SRC_DEST_MARKER = "src_dest_marker"
private const val LAYER_DEST_MARKER = "layer_dest_marker"
private const val ICON_DEST_MARKER = "icon_dest_marker"

@Composable
fun MapLibreContainer(
    userLocation: UserLocationData?,
    isUserLocationReliable: Boolean = true,
    activeRoute: RouteOption?,
    alternativeRoutes: List<RouteOption>,
    trafficStatus: TrafficStatus?,
    trafficSegments: List<TrafficSegment>,
    isTrafficLayerVisible: Boolean,
    isTrafficSignalsLayerVisible: Boolean = true,
    trafficSignals: List<TrafficSignal> = emptyList(),
    isPoiLayerVisible: Boolean,
    poiList: List<PoiItem>,
    selectedPoiCategory: PoiCategory? = null,
    currentViewportBbox: TrafficSignalBoundingBox? = null,
    currentZoomLevel: Float = 0f,
    safetyCameras: List<SafetyCamera> = emptyList(),
    isSafetyCamerasLayerVisible: Boolean = true,
    routeWeather: List<com.example.haritalar.model.WeatherCondition> = emptyList(),
    isWeatherLayerVisible: Boolean = true,
    averageSpeedZones: List<AverageSpeedZoneRouteMatch> = emptyList(),
    destinationPoint: GeoPoint?,
    cameraMode: CameraMode,
    mapTrackingMode: MapTrackingMode,
    navigationState: NavigationState,
    vehicleHeading: Float = userLocation?.bearing ?: 0f,
    onMapClick: (GeoPoint) -> Unit,
    onMapDrag: () -> Unit,
    onViewportChanged: (TrafficSignalBoundingBox, Float) -> Unit = { _, _ -> },
    onTrafficSignalClick: (TrafficSignal) -> Unit = {},
    onPoiClick: (PoiItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val currentOnTrafficSignalClick by rememberUpdatedState(onTrafficSignalClick)
    val currentOnPoiClick by rememberUpdatedState(onPoiClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnMapDrag by rememberUpdatedState(onMapDrag)
    val visiblePois = remember(
        poiList,
        currentViewportBbox,
        currentZoomLevel,
        selectedPoiCategory,
        isPoiLayerVisible
    ) {
        if (!isPoiLayerVisible ||
            !PoiViewportPolicy.canQuery(currentZoomLevel, selectedPoiCategory)
        ) {
            emptyList()
        } else {
            PoiViewportPolicy.visiblePois(
                pois = poiList,
                bbox = currentViewportBbox,
                zoomLevel = currentZoomLevel
            )
        }
    }

    val currentTrafficSignals by rememberUpdatedState(trafficSignals)
    val currentSignalsVisible by rememberUpdatedState(isTrafficSignalsLayerVisible)
    val currentPois by rememberUpdatedState(visiblePois)
    val currentPoisVisible by rememberUpdatedState(isPoiLayerVisible && visiblePois.isNotEmpty())

    remember { MapLibre.getInstance(context) }
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
                var styleAttempt = 0
                fun loadStyleCandidate() {
                    val candidate = MapStyleFallbackPolicy.candidates.getOrNull(styleAttempt)
                    if (candidate == null) {
                        android.util.Log.e(STYLE_FALLBACK_TAG, "All map styles failed; keeping safe blank map state")
                        return
                    }
                    android.util.Log.i(STYLE_FALLBACK_TAG, "Loading map style candidate #${styleAttempt + 1}: $candidate")
                    map.setStyle(Style.Builder().fromUri(candidate)) { style ->
                        mapStyle = style
                        setupLayers(style, context)
                        android.util.Log.i(STYLE_FALLBACK_TAG, "Map style loaded: $candidate")
                    }
                }
                val styleFailureListener = object : MapView.OnDidFailLoadingMapListener {
                    override fun onDidFailLoadingMap(errorMessage: String) {
                        if (styleAttempt < MapStyleFallbackPolicy.candidates.lastIndex) {
                            styleAttempt += 1
                            android.util.Log.w(STYLE_FALLBACK_TAG, "Map style candidate failed; trying fallback #${styleAttempt + 1}: $errorMessage")
                            loadStyleCandidate()
                        } else {
                            android.util.Log.e(STYLE_FALLBACK_TAG, "Map style fallback exhausted: $errorMessage")
                        }
                    }
                }
                addOnDidFailLoadingMapListener(styleFailureListener)
                loadStyleCandidate()
                map.addOnCameraIdleListener {
                    try {
                        val b = map.projection.visibleRegion.latLngBounds
                        currentOnViewportChanged(
                            TrafficSignalBoundingBox(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast),
                            map.cameraPosition.zoom.toFloat()
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("MapLibreContainer", "viewport: ${e.message}")
                    }
                }
                map.addOnMapClickListener { latLng ->
                    val point = GeoPoint(latLng.latitude, latLng.longitude)
                    if (currentPoisVisible && currentPois.isNotEmpty()) {
                        val closestPoi = currentPois.minByOrNull { it.point.distanceTo(point) }
                        if (closestPoi != null && closestPoi.point.distanceTo(point) <= 80.0) {
                            currentOnPoiClick(closestPoi)
                            return@addOnMapClickListener true
                        }
                    }
                    if (currentSignalsVisible && currentTrafficSignals.isNotEmpty()) {
                        val closest = currentTrafficSignals.minByOrNull { it.point.distanceTo(point) }
                        if (closest != null && closest.point.distanceTo(point) <= 35.0) {
                            currentOnTrafficSignalClick(closest)
                            return@addOnMapClickListener true
                        }
                    }
                    currentOnMapClick(point)
                    true
                }
                map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) = currentOnMapDrag()
                    override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
                    override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
                })
            }
        }
    }

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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onDestroy() }
    }

    LaunchedEffect(userLocation, cameraMode, mapTrackingMode, navigationState, mapInstance, mapStyle) {
        val map = mapInstance ?: return@LaunchedEffect
        mapStyle ?: return@LaunchedEffect
        val pitch = if (cameraMode == CameraMode.THREE_D) 55.0 else 0.0
        if (mapTrackingMode == MapTrackingMode.FOLLOW_USER && userLocation != null) {
            val target = LatLng(userLocation.point.latitude, userLocation.point.longitude)
            map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(16.0).tilt(pitch).bearing(if (navigationState == NavigationState.NAVIGATING) userLocation.bearing.toDouble() else map.cameraPosition.bearing).build()), 600)
        } else if (mapTrackingMode == MapTrackingMode.FOLLOW_BEARING && userLocation != null) {
            val target = LatLng(userLocation.point.latitude, userLocation.point.longitude)
            map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(17.5).tilt(55.0).bearing(userLocation.bearing.toDouble()).build()), 400)
        } else if (Math.abs(map.cameraPosition.tilt - pitch) > 5.0) {
            map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(map.cameraPosition).tilt(pitch).build()), 400)
        }
    }

    LaunchedEffect(userLocation, isUserLocationReliable, vehicleHeading, navigationState, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_USER_LOC) ?: return@LaunchedEffect
        val navigating = navigationState == NavigationState.NAVIGATING
        val locationColor = if (isUserLocationReliable) Color.parseColor("#007AFF") else Color.parseColor("#64748B")
        val pulseOpacity = if (isUserLocationReliable) 0.20f else 0.08f
        style.getLayer(LAYER_USER_LOC_PULSE)?.setProperties(
            visibility(if (navigating || userLocation == null) Property.NONE else Property.VISIBLE),
            circleColor(locationColor),
            circleOpacity(pulseOpacity)
        )
        style.getLayer(LAYER_USER_LOC)?.setProperties(
            visibility(if (navigating || userLocation == null) Property.NONE else Property.VISIBLE),
            circleColor(locationColor)
        )
        style.getLayer(LAYER_VEHICLE_ARROW)?.setProperties(visibility(if (navigating && userLocation != null) Property.VISIBLE else Property.NONE))
        src.setGeoJson(if (userLocation != null) createUserLocationGeoJson(userLocation.point, vehicleHeading, navigating) else createEmptyFeatureCollection())
    }

    LaunchedEffect(destinationPoint, mapStyle, mapInstance) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_DEST_MARKER) ?: return@LaunchedEffect
        if (destinationPoint != null) {
            src.setGeoJson(createPointGeoJson(destinationPoint))
            if (navigationState != NavigationState.NAVIGATING) {
                val target = LatLng(destinationPoint.latitude, destinationPoint.longitude)
                mapInstance?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(16.5).tilt(0.0).build()), 700)
            }
        } else src.setGeoJson(createEmptyFeatureCollection())
    }

    LaunchedEffect(activeRoute, alternativeRoutes, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(SRC_ACTIVE_ROUTE)?.setGeoJson(if (activeRoute != null) createLineStringGeoJson(activeRoute.geometry) else createEmptyFeatureCollection())
        val alternatives = alternativeRoutes.filter { it.routeId != activeRoute?.routeId }
        style.getSourceAs<GeoJsonSource>(SRC_ALT_ROUTES)?.setGeoJson(if (alternatives.isNotEmpty()) createMultiLineStringGeoJson(alternatives.map { it.geometry }) else createEmptyFeatureCollection())
    }

    LaunchedEffect(isTrafficLayerVisible, trafficSegments, activeRoute, trafficStatus, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_TRAFFIC) ?: return@LaunchedEffect
        val layer = style.getLayer(LAYER_TRAFFIC)
        if (!isTrafficLayerVisible || activeRoute == null) {
            src.setGeoJson(createEmptyFeatureCollection())
            layer?.setProperties(visibility(Property.NONE))
        } else {
            if (trafficSegments.isEmpty()) {
                src.setGeoJson(createEmptyFeatureCollection())
                layer?.setProperties(visibility(Property.NONE))
            } else {
                layer?.setProperties(visibility(Property.VISIBLE))
                src.setGeoJson(createTrafficGeoJson(trafficSegments))
            }
        }
    }

    LaunchedEffect(isPoiLayerVisible, visiblePois, currentZoomLevel, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_POIS) ?: return@LaunchedEffect
        val layer = style.getLayer(LAYER_POIS)
        if (!isPoiLayerVisible || visiblePois.isEmpty()) {
            src.setGeoJson(createEmptyFeatureCollection())
            layer?.setProperties(visibility(Property.NONE))
        } else {
            val scale = when {
                currentZoomLevel >= 16f -> 1.0f
                currentZoomLevel >= 14f -> 0.9f
                currentZoomLevel >= 12f -> 0.78f
                else -> 0.68f
            }
            layer?.setProperties(
                visibility(Property.VISIBLE),
                iconSize(scale)
            )
            src.setGeoJson(createPoisGeoJson(visiblePois))
        }
    }

    LaunchedEffect(isSafetyCamerasLayerVisible, safetyCameras, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_SAFETY_CAMERAS) ?: return@LaunchedEffect
        val layer = style.getLayer(LAYER_SAFETY_CAMERAS)
        if (!isSafetyCamerasLayerVisible || safetyCameras.isEmpty()) {
            src.setGeoJson(createEmptyFeatureCollection()); layer?.setProperties(visibility(Property.NONE))
        } else {
            layer?.setProperties(visibility(Property.VISIBLE)); src.setGeoJson(createSafetyCamerasGeoJson(safetyCameras))
        }
    }

    LaunchedEffect(isWeatherLayerVisible, routeWeather, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_WEATHER) ?: return@LaunchedEffect
        val layer = style.getLayer(LAYER_WEATHER)
        if (!isWeatherLayerVisible || routeWeather.isEmpty()) {
            src.setGeoJson(createEmptyFeatureCollection())
            layer?.setProperties(visibility(Property.NONE))
        } else {
            layer?.setProperties(visibility(Property.VISIBLE))
            src.setGeoJson(createWeatherGeoJson(routeWeather))
        }
    }

    LaunchedEffect(averageSpeedZones, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val lineSource = style.getSourceAs<GeoJsonSource>(SRC_AVERAGE_SPEED_ZONES)
            ?: return@LaunchedEffect
        val startSource = style.getSourceAs<GeoJsonSource>(SRC_AVERAGE_SPEED_START)
            ?: return@LaunchedEffect
        val endSource = style.getSourceAs<GeoJsonSource>(SRC_AVERAGE_SPEED_END)
            ?: return@LaunchedEffect

        if (averageSpeedZones.isEmpty()) {
            lineSource.setGeoJson(createEmptyFeatureCollection())
            startSource.setGeoJson(createEmptyFeatureCollection())
            endSource.setGeoJson(createEmptyFeatureCollection())
            style.getLayer(LAYER_AVERAGE_SPEED_ZONES)?.setProperties(visibility(Property.NONE))
            style.getLayer(LAYER_AVERAGE_SPEED_START)?.setProperties(visibility(Property.NONE))
            style.getLayer(LAYER_AVERAGE_SPEED_END)?.setProperties(visibility(Property.NONE))
        } else {
            lineSource.setGeoJson(
                createMultiLineStringGeoJson(
                    averageSpeedZones.map { it.zone.geometry }.filter { it.size >= 2 }
                )
            )
            startSource.setGeoJson(createAverageSpeedEndpointGeoJson(averageSpeedZones, start = true))
            endSource.setGeoJson(createAverageSpeedEndpointGeoJson(averageSpeedZones, start = false))
            style.getLayer(LAYER_AVERAGE_SPEED_ZONES)?.setProperties(visibility(Property.VISIBLE))
            style.getLayer(LAYER_AVERAGE_SPEED_START)?.setProperties(visibility(Property.VISIBLE))
            style.getLayer(LAYER_AVERAGE_SPEED_END)?.setProperties(visibility(Property.VISIBLE))
        }
    }

    LaunchedEffect(isTrafficSignalsLayerVisible, trafficSignals, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SRC_TRAFFIC_SIGNALS) ?: return@LaunchedEffect
        val layer = style.getLayer(LAYER_TRAFFIC_SIGNALS)
        if (!isTrafficSignalsLayerVisible || trafficSignals.isEmpty()) {
            src.setGeoJson(createEmptyFeatureCollection()); layer?.setProperties(visibility(Property.NONE))
        } else {
            layer?.setProperties(visibility(Property.VISIBLE)); src.setGeoJson(createTrafficSignalsGeoJson(trafficSignals))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.fillMaxSize())
}

private fun setupLayers(style: Style, context: Context) {
    style.addSource(GeoJsonSource(SRC_ALT_ROUTES, createEmptyFeatureCollection()))
    style.addLayer(LineLayer(LAYER_ALT_ROUTES, SRC_ALT_ROUTES).apply { setProperties(lineColor(Color.parseColor("#8E8E93")), lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND), lineOpacity(0.75f)) })
    style.addSource(GeoJsonSource(SRC_ACTIVE_ROUTE, createEmptyFeatureCollection()))
    style.addLayer(LineLayer(LAYER_ACTIVE_ROUTE_CASING, SRC_ACTIVE_ROUTE).apply { setProperties(lineColor(Color.parseColor("#0A2540")), lineWidth(9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)) })
    style.addLayer(LineLayer(LAYER_ACTIVE_ROUTE, SRC_ACTIVE_ROUTE).apply { setProperties(lineColor(Color.parseColor("#007AFF")), lineWidth(6f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)) })
    style.addSource(GeoJsonSource(SRC_TRAFFIC, createEmptyFeatureCollection()))
    style.addLayer(LineLayer(LAYER_TRAFFIC, SRC_TRAFFIC).apply { setProperties(lineColor(get("color")), lineWidth(6f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND), visibility(Property.VISIBLE)) })

    style.addSource(GeoJsonSource(SRC_AVERAGE_SPEED_ZONES, createEmptyFeatureCollection()))
    style.addLayer(LineLayer(LAYER_AVERAGE_SPEED_ZONES, SRC_AVERAGE_SPEED_ZONES).apply {
        setProperties(
            lineColor(Color.parseColor("#F59E0B")),
            lineWidth(8f),
            lineOpacity(0.62f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
            visibility(Property.NONE)
        )
    })
    style.addSource(GeoJsonSource(SRC_AVERAGE_SPEED_START, createEmptyFeatureCollection()))
    style.addLayer(CircleLayer(LAYER_AVERAGE_SPEED_START, SRC_AVERAGE_SPEED_START).apply {
        setProperties(
            circleRadius(7f),
            circleColor(Color.parseColor("#10B981")),
            circleStrokeWidth(3f),
            circleStrokeColor(Color.WHITE),
            visibility(Property.NONE)
        )
    })
    style.addSource(GeoJsonSource(SRC_AVERAGE_SPEED_END, createEmptyFeatureCollection()))
    style.addLayer(CircleLayer(LAYER_AVERAGE_SPEED_END, SRC_AVERAGE_SPEED_END).apply {
        setProperties(
            circleRadius(7f),
            circleColor(Color.parseColor("#EF4444")),
            circleStrokeWidth(3f),
            circleStrokeColor(Color.WHITE),
            visibility(Property.NONE)
        )
    })
    style.addImage(ICON_DEST_MARKER, createDestinationPinBitmap(context))
    style.addSource(GeoJsonSource(SRC_DEST_MARKER, createEmptyFeatureCollection()))
    style.addLayer(SymbolLayer(LAYER_DEST_MARKER, SRC_DEST_MARKER).apply {
        setProperties(
            iconImage(ICON_DEST_MARKER),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            iconSize(1.0f)
        )
    })

    val poiSrc = GeoJsonSource(SRC_POIS, createEmptyFeatureCollection())
    style.addSource(poiSrc)
    style.addImage(ICON_POI_RESTAURANT, createPoiBitmap(context, Color.parseColor("#EA580C"), "R", 52))
    style.addImage(ICON_POI_FUEL, createPoiBitmap(context, Color.parseColor("#2563EB"), "B", 52))
    style.addImage(ICON_POI_HOSPITAL, createPoiBitmap(context, Color.parseColor("#DC2626"), "H", 52))
    style.addImage(ICON_POI_PHARMACY, createPoiBitmap(context, Color.parseColor("#059669"), "+", 52))
    style.addImage(ICON_POI_MARKET, createPoiBitmap(context, Color.parseColor("#7C3AED"), "M", 52))
    style.addImage(ICON_POI_PARKING, createPoiBitmap(context, Color.parseColor("#0369A1"), "P", 52))
    style.addImage(ICON_POI_ATM, createPoiBitmap(context, Color.parseColor("#334155"), "ATM", 52))
    style.addImage(ICON_POI_CAFE, createPoiBitmap(context, Color.parseColor("#92400E"), "K", 52))
    style.addImage(ICON_POI_CHARGING, createPoiBitmap(context, Color.parseColor("#0F766E"), "Ş", 52))
    style.addLayer(SymbolLayer(LAYER_POIS, SRC_POIS).apply {
        setProperties(
            iconImage(get("icon")),
            iconAllowOverlap(false),
            iconIgnorePlacement(false),
            iconSize(0.9f),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
            visibility(Property.VISIBLE)
        )
    })

    style.addImage(ICON_SAFETY_CAMERA, createSafetyCameraBitmap(context))
    style.addSource(GeoJsonSource(SRC_SAFETY_CAMERAS, createEmptyFeatureCollection()))
    style.addLayer(SymbolLayer(LAYER_SAFETY_CAMERAS, SRC_SAFETY_CAMERAS).apply { setProperties(iconImage(ICON_SAFETY_CAMERA), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(1.0f), iconAnchor(Property.ICON_ANCHOR_CENTER), visibility(Property.VISIBLE)) })

    style.addImage(ICON_WEATHER_RAIN, createWeatherBitmap(context, "🌧️"))
    style.addImage(ICON_WEATHER_FOG, createWeatherBitmap(context, "🌫️"))
    style.addImage(ICON_WEATHER_SNOW, createWeatherBitmap(context, "❄️"))
    style.addImage(ICON_WEATHER_STORM, createWeatherBitmap(context, "⛈️"))
    style.addSource(GeoJsonSource(SRC_WEATHER, createEmptyFeatureCollection()))
    style.addLayer(SymbolLayer(LAYER_WEATHER, SRC_WEATHER).apply { setProperties(iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(1.2f), iconAnchor(Property.ICON_ANCHOR_CENTER), visibility(Property.VISIBLE)) })

    style.addImage(ICON_TRAFFIC_SIGNAL, createTrafficSignalBitmap(context))
    style.addSource(GeoJsonSource(SRC_TRAFFIC_SIGNALS, createEmptyFeatureCollection()))
    style.addLayer(SymbolLayer(LAYER_TRAFFIC_SIGNALS, SRC_TRAFFIC_SIGNALS).apply {
        setProperties(
            iconImage(ICON_TRAFFIC_SIGNAL),
            iconAllowOverlap(false),
            iconIgnorePlacement(false),
            iconSize(0.7f),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
            visibility(Property.VISIBLE)
        )
    })

    style.addSource(GeoJsonSource(SRC_USER_LOC, createEmptyFeatureCollection()))
    style.addLayer(CircleLayer(LAYER_USER_LOC_PULSE, SRC_USER_LOC).apply { setProperties(circleRadius(18f), circleColor(Color.parseColor("#007AFF")), circleOpacity(0.2f)) })
    style.addLayer(CircleLayer(LAYER_USER_LOC, SRC_USER_LOC).apply { setProperties(circleRadius(8f), circleColor(Color.parseColor("#007AFF")), circleStrokeWidth(3f), circleStrokeColor(Color.WHITE)) })
    style.addImage(ICON_VEHICLE_ARROW, createVehicleArrowBitmap(context))
    style.addLayer(SymbolLayer(LAYER_VEHICLE_ARROW, SRC_USER_LOC).apply { setProperties(iconImage(ICON_VEHICLE_ARROW), iconRotate(get("bearing")), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER), visibility(Property.NONE)) })
}

private fun createUserLocationGeoJson(point: GeoPoint, bearing: Float, isNavigating: Boolean): String = featureCollection(feature(JSONObject().apply { put("properties", JSONObject().apply { put("bearing", bearing.toDouble()); put("isNavigating", isNavigating) }); put("geometry", pointGeometry(point)) }))

private fun createPointGeoJson(point: GeoPoint): String = featureCollection(feature(JSONObject().apply { put("geometry", pointGeometry(point)) }))

private fun createLineStringGeoJson(points: List<GeoPoint>): String {
    val coords = JSONArray(); points.forEach { coords.put(JSONArray().apply { put(it.longitude); put(it.latitude) }) }
    return featureCollection(feature(JSONObject().apply { put("geometry", JSONObject().apply { put("type", "LineString"); put("coordinates", coords) }) }))
}

private fun createMultiLineStringGeoJson(lines: List<List<GeoPoint>>): String {
    val features = JSONArray()
    lines.forEach { line ->
        val coords = JSONArray(); line.forEach { coords.put(JSONArray().apply { put(it.longitude); put(it.latitude) }) }
        features.put(feature(JSONObject().apply { put("geometry", JSONObject().apply { put("type", "LineString"); put("coordinates", coords) }) }))
    }
    return featureCollection(features)
}

private fun createAverageSpeedEndpointGeoJson(
    matches: List<AverageSpeedZoneRouteMatch>,
    start: Boolean
): String {
    val features = JSONArray()
    matches.forEach { match ->
        val point = if (start) match.zone.geometry.firstOrNull() else match.zone.geometry.lastOrNull()
        if (point != null) {
            features.put(
                feature(
                    JSONObject().apply {
                        put(
                            "properties",
                            JSONObject().apply {
                                put("zoneId", match.zone.id)
                                put("role", if (start) "start" else "end")
                                match.speedLimitKmh?.let { put("maxSpeed", it) }
                            }
                        )
                        put("geometry", pointGeometry(point))
                    }
                )
            )
        }
    }
    return featureCollection(features)
}

private fun createTrafficGeoJson(segments: List<TrafficSegment>): String {
    val features = JSONArray()
    segments.filter { it.coordinates.size >= 2 }.forEach { seg ->
        val coords = JSONArray(); seg.coordinates.forEach { coords.put(JSONArray().apply { put(it.longitude); put(it.latitude) }) }
        val ratio = if (seg.freeFlowSpeed > 0) seg.currentSpeed / seg.freeFlowSpeed else 1.0
        val colorHex = when {
            seg.roadClosure -> "#7F0000" // Dark Red / Closure
            ratio < 0.4 -> "#EF4444"    // Kırmızı / Heavy
            ratio < 0.8 -> "#F59E0B"    // Sarı / Moderate
            else -> "#10B981"           // Yeşil / Fluent
        }
        features.put(feature(JSONObject().apply {
            put("properties", JSONObject().apply {
                put("color", colorHex)
            })
            put("geometry", JSONObject().apply { put("type", "LineString"); put("coordinates", coords) })
        }))
    }
    return featureCollection(features)
}

private fun createPoisGeoJson(pois: List<PoiItem>): String {
    val features = JSONArray()
    pois.forEach { poi ->
        val brand = (poi.brand ?: poi.operator)?.trim()?.takeIf { it.isNotEmpty() }
        features.put(feature(JSONObject().apply {
            put("properties", JSONObject().apply {
                put("name", poi.name)
                put("category", poi.category.displayName)
                put("brand", brand ?: "")
                put("icon", poiIconName(poi.category))
            })
            put("geometry", pointGeometry(poi.point))
        }))
    }
    return featureCollection(features)
}

private fun poiIconName(category: PoiCategory): String = when (category) {
    PoiCategory.RESTAURANT -> ICON_POI_RESTAURANT
    PoiCategory.FUEL -> ICON_POI_FUEL
    PoiCategory.HOSPITAL -> ICON_POI_HOSPITAL
    PoiCategory.PHARMACY -> ICON_POI_PHARMACY
    PoiCategory.MARKET -> ICON_POI_MARKET
    PoiCategory.PARKING -> ICON_POI_PARKING
    PoiCategory.ATM -> ICON_POI_ATM
    PoiCategory.CAFE -> ICON_POI_CAFE
    PoiCategory.CHARGING_STATION -> ICON_POI_CHARGING
}

private fun createSafetyCamerasGeoJson(cameras: List<SafetyCamera>): String {
    val features = JSONArray()
    cameras.forEach { camera ->
        features.put(feature(JSONObject().apply {
            put("properties", JSONObject().apply {
                put("id", camera.id); put("title", camera.displayTitle); put("maxSpeed", camera.maxSpeed ?: ""); put("direction", camera.direction ?: ""); put("operator", camera.operator ?: ""); put("reference", camera.reference ?: "")
            })
            put("geometry", pointGeometry(camera.point))
        }))
    }
    return featureCollection(features)
}

private fun createWeatherGeoJson(weatherList: List<com.example.haritalar.model.WeatherCondition>): String {
    val features = JSONArray()
    weatherList.forEach { w ->
        val iconName = when (w.type) {
            com.example.haritalar.model.WeatherType.RAIN -> ICON_WEATHER_RAIN
            com.example.haritalar.model.WeatherType.FOG -> ICON_WEATHER_FOG
            com.example.haritalar.model.WeatherType.SNOW -> ICON_WEATHER_SNOW
            com.example.haritalar.model.WeatherType.STORM -> ICON_WEATHER_STORM
            else -> ICON_WEATHER_RAIN
        }
        features.put(feature(JSONObject().apply {
            put("properties", JSONObject().apply {
                put("icon", iconName)
            })
            put("geometry", pointGeometry(w.point))
        }))
    }
    return featureCollection(features)
}

private fun createTrafficSignalsGeoJson(signals: List<TrafficSignal>): String {
    val features = JSONArray()
    signals.forEach { s ->
        features.put(feature(JSONObject().apply {
            put("id", s.id)
            put("properties", JSONObject().apply { put("id", s.id); put("title", s.displayTitle); put("crossing", s.crossing ?: ""); put("hasSound", s.hasSound); put("hasVibration", s.hasVibration); put("hasArrow", s.hasArrow) })
            put("geometry", pointGeometry(s.point))
        }))
    }
    return featureCollection(features)
}

private fun pointGeometry(point: GeoPoint): JSONObject = JSONObject().apply { put("type", "Point"); put("coordinates", JSONArray().apply { put(point.longitude); put(point.latitude) }) }
private fun feature(body: JSONObject): JSONObject { body.put("type", "Feature"); return body }
private fun featureCollection(features: JSONArray): String = JSONObject().apply { put("type", "FeatureCollection"); put("features", features) }.toString()
private fun featureCollection(single: JSONObject): String = featureCollection(JSONArray().apply { put(single) })
private fun createEmptyFeatureCollection(): String = "{\"type\":\"FeatureCollection\",\"features\":[]}"

fun createDestinationPinBitmap(context: Context): Bitmap {
    val d = context.resources.displayMetrics.density
    val width = (44 * d).toInt().coerceAtLeast(52)
    val height = (56 * d).toInt().coerceAtLeast(66)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f
    val headCy = 19f * d
    val radius = 14f * d

    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.argb(55, 0, 0, 0)
        canvas.drawCircle(cx, headCy + 2f * d, radius + 3f * d, it)
    }

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7C3AED") }
    val pinPath = Path().apply {
        moveTo(cx, height - 5f * d)
        lineTo(cx - 10f * d, headCy + 8f * d)
        lineTo(cx + 10f * d, headCy + 8f * d)
        close()
    }
    canvas.drawPath(pinPath, pinPaint)
    canvas.drawCircle(cx, headCy, radius, pinPaint)

    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.WHITE
        it.style = Paint.Style.STROKE
        it.strokeWidth = 2.2f * d
        canvas.drawCircle(cx, headCy, radius, it)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.WHITE
        canvas.drawCircle(cx, headCy, 5f * d, it)
    }
    return bitmap
}

fun createSafetyCameraBitmap(context: Context): Bitmap {
    val d = context.resources.displayMetrics.density
    val size = (54 * d).toInt().coerceAtLeast(60)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.argb(70, 0, 0, 0)
        canvas.drawCircle(cx, cy + 2.5f * d, 22f * d, it)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.WHITE
        canvas.drawCircle(cx, cy, 22f * d, it)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.parseColor("#DC2626")
        canvas.drawCircle(cx, cy, 18.5f * d, it)
    }

    val cameraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val body = android.graphics.RectF(
        cx - 11.5f * d,
        cy - 7f * d,
        cx + 11.5f * d,
        cy + 8.5f * d
    )
    canvas.drawRoundRect(body, 3.5f * d, 3.5f * d, cameraPaint)

    val top = android.graphics.RectF(
        cx - 7.5f * d,
        cy - 11f * d,
        cx + 1.5f * d,
        cy - 5.5f * d
    )
    canvas.drawRoundRect(top, 2f * d, 2f * d, cameraPaint)

    val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DC2626") }
    canvas.drawCircle(cx, cy + 0.5f * d, 5f * d, lens)
    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.WHITE
        it.style = Paint.Style.STROKE
        it.strokeWidth = 1.4f * d
        canvas.drawCircle(cx, cy + 0.5f * d, 5f * d, it)
    }
    return bitmap
}

private fun createPoiBitmap(context: Context, background: Int, label: String, sizeDp: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val size = (sizeDp * d).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
    canvas.drawCircle(cx, cy, size * 0.42f, bg)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2 * d }
    canvas.drawCircle(cx, cy, size * 0.42f, border)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (background == Color.WHITE) Color.parseColor("#111827") else Color.WHITE; textAlign = Paint.Align.CENTER; textSize = size * 0.23f; isFakeBoldText = true }
    canvas.drawText(label.take(4), cx, cy - (text.ascent() + text.descent()) / 2f, text)
    return bitmap
}

fun createTrafficSignalBitmap(context: Context): Bitmap {
    val d = context.resources.displayMetrics.density
    val width = (24 * d).toInt().coerceAtLeast(32)
    val height = (44 * d).toInt().coerceAtLeast(60)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val pad = 3f * d
    val rect = android.graphics.RectF(pad, pad, width - pad, height - pad)
    paint.color = Color.parseColor("#0F172A"); paint.style = Paint.Style.FILL
    canvas.drawRoundRect(rect, 8f * d, 8f * d, paint)
    paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d; paint.color = Color.WHITE
    canvas.drawRoundRect(rect, 8f * d, 8f * d, paint)
    paint.style = Paint.Style.FILL
    val cx = width / 2f; val r = 3.5f * d; val step = (height - 2 * pad) / 4f
    paint.color = Color.parseColor("#EF4444"); canvas.drawCircle(cx, pad + step, r, paint)
    paint.color = Color.parseColor("#F59E0B"); canvas.drawCircle(cx, pad + step * 2f, r, paint)
    paint.color = Color.parseColor("#10B981"); canvas.drawCircle(cx, pad + step * 3f, r, paint)
    return bitmap
}

fun createVehicleArrowBitmap(context: Context): Bitmap {
    val d = context.resources.displayMetrics.density
    val size = (52 * d).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap); val cx = size / 2f; val cy = size / 2f
    Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = Color.argb(68, 0, 0, 0); canvas.drawCircle(cx, cy + 2.5f * d, 19f * d, it) }
    Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = Color.WHITE; canvas.drawCircle(cx, cy, 19f * d, it) }
    Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = Color.parseColor("#E0E7FF"); it.style = Paint.Style.STROKE; it.strokeWidth = 1.5f * d; canvas.drawCircle(cx, cy, 18.5f * d, it) }
    val left = Path().apply { moveTo(cx, cy - 13f * d); lineTo(cx - 9.5f * d, cy + 10f * d); lineTo(cx, cy + 5.5f * d); close() }
    val right = Path().apply { moveTo(cx, cy - 13f * d); lineTo(cx, cy + 5.5f * d); lineTo(cx + 9.5f * d, cy + 10f * d); close() }
    Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = Color.parseColor("#007AFF"); canvas.drawPath(left, it) }
    Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = Color.parseColor("#0056B3"); canvas.drawPath(right, it) }
    Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = Color.WHITE; it.style = Paint.Style.STROKE; it.strokeWidth = 1.6f * d; it.strokeCap = Paint.Cap.ROUND; canvas.drawLine(cx, cy - 11.5f * d, cx, cy + 4.5f * d, it) }
    return bitmap
}
