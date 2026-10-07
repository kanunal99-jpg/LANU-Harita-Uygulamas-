package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.haritalar.model.NavigationState
import com.example.haritalar.data.repository.TrafficSignalRepository
import com.example.haritalar.navigation.LanuBriefPolicy
import com.example.haritalar.navigation.NavigationLocationPolicy
import com.example.haritalar.navigation.RoadIntelligencePolicy
import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HaritalarNavigationApp()
            }
        }
    }
}

@Composable
fun HaritalarNavigationApp(
    viewModel: MainViewModel = viewModel(),
    safetyCameraViewModel: SafetyCameraLayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val safetyCameras by safetyCameraViewModel.cameras.collectAsState()
    val routeSafetyCameras by safetyCameraViewModel.routeCameras.collectAsState()
    val isRouteSafetyCameraPrefetching by safetyCameraViewModel.isRoutePrefetching.collectAsState()
    
    val offlineDownloadProgress by viewModel.offlineMapManager.downloadProgress.collectAsState()
    val offlineDownloadMessage by viewModel.offlineMapManager.downloadMessage.collectAsState()

    var showLayersSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Keep screen on during active driving navigation
    val activity = context as? Activity
    DisposableEffect(uiState.navigationState) {
        if (uiState.navigationState == NavigationState.NAVIGATING) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Location permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        viewModel.startLocationUpdates(fineGranted || coarseGranted)
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val missingPermissions = mutableListOf<String>()
        if (fine || coarse) {
            viewModel.startLocationUpdates(true)
        } else {
            missingPermissions += Manifest.permission.ACCESS_FINE_LOCATION
            missingPermissions += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    // Observe status messages to display in Snackbar.
    // GPS failures get an explicit retry action instead of a passive dead-end message.
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            val isGpsMessage = msg.contains("GPS", ignoreCase = true) ||
                msg.contains("son bilinen konum", ignoreCase = true) ||
                msg.contains("konum henüz alınmadı", ignoreCase = true) ||
                msg.contains("konum alınmadan", ignoreCase = true) ||
                msg.contains("doğruluğu navigasyon için yetersiz", ignoreCase = true)
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (isGpsMessage) "Konumu Yenile" else null,
                duration = if (isGpsMessage) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (isGpsMessage && result == SnackbarResult.ActionPerformed) {
                val fine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.startLocationUpdates(fine || coarse)
            }
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(uiState.liveShareUrl) {
        val url = uiState.liveShareUrl
        if (url != null) {
            val etaMins = uiState.navigationProgress?.totalRemainingSeconds?.let { it / 60 }
            val etaText = if (etaMins != null) " ETA: $etaMins dk." else ""
            val shareText = "Canlı konumumu ve varış tahminimi buradan takip et:$etaText $url"
            
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Canlı Takibi Paylaş")
            context.startActivity(shareIntent)
        }
    }

    LaunchedEffect(
        uiState.selectedRoute?.routeId,
        uiState.isSafetyCamerasLayerVisible
    ) {
        val route = uiState.selectedRoute
        if (route != null && uiState.isSafetyCamerasLayerVisible) {
            safetyCameraViewModel.prefetchForRoute(
                routeId = route.routeId,
                route = route.geometry
            )
        } else {
            safetyCameraViewModel.clearRoutePrefetch()
        }
    }

    LaunchedEffect(
        uiState.selectedRoute?.routeId,
        routeSafetyCameras,
        isRouteSafetyCameraPrefetching,
        uiState.navigationState
    ) {
        val routeReady = uiState.selectedRoute != null &&
            !isRouteSafetyCameraPrefetching &&
            uiState.navigationState == NavigationState.ROUTE_SELECTION
        if (routeReady) {
            viewModel.updatePreDriveSafetyCameraData(routeSafetyCameras)
        }
    }

    LaunchedEffect(
        uiState.navigationState,
        uiState.userLocation?.point,
        uiState.isSafetyCamerasLayerVisible
    ) {
        val isDriving = uiState.navigationState == NavigationState.NAVIGATING ||
            uiState.navigationState == NavigationState.OFF_ROUTE_REROUTING
        val point = uiState.userLocation?.point
        if (isDriving && point != null && uiState.isSafetyCamerasLayerVisible) {
            safetyCameraViewModel.prefetchForNavigation(point)
        } else {
            safetyCameraViewModel.clearNavigationPrefetch()
        }
    }

    LaunchedEffect(
        uiState.userLocation,
        safetyCameras,
        uiState.selectedRoute,
        uiState.navigationState,
        uiState.routeRoadFeatures
    ) {
        viewModel.checkSafetyCameraProximity(safetyCameras)
        viewModel.checkWeatherProximity()
        viewModel.checkRoadFeatureProximity()
    }

    val selectedTrafficPair = uiState.selectedRoute?.let { uiState.trafficStatusMap[it.routeId] }
    val currentTrafficStatus = selectedTrafficPair?.first
    val currentTrafficSegments = selectedTrafficPair?.second ?: emptyList()
    val roadIntelligenceEvents = remember(
        uiState.approachingCameraWarning,
        uiState.approachingRoadFeatureWarning,
        uiState.approachingWeather,
        currentTrafficStatus,
        currentTrafficSegments,
        uiState.userLocation?.point
    ) {
        RoadIntelligencePolicy.build(
            cameraWarning = uiState.approachingCameraWarning,
            weather = uiState.approachingWeather,
            roadFeatureWarning = uiState.approachingRoadFeatureWarning,
            traffic = currentTrafficStatus,
            trafficSegments = currentTrafficSegments,
            userPoint = uiState.userLocation?.point
        )
    }
    val preDriveBrief = remember(
        uiState.selectedRoute,
        currentTrafficStatus,
        currentTrafficSegments,
        uiState.routeWeather,
        uiState.routeRoadFeatures,
        uiState.roadFeatureDataState,
        uiState.routeCriticalPois,
        uiState.routeCriticalPoiDataState,
        safetyCameras
    ) {
        uiState.selectedRoute?.let { route ->
            LanuBriefPolicy.build(
                route = route,
                traffic = currentTrafficStatus,
                routeWeather = uiState.routeWeather,
                loadedSafetyCameras = safetyCameras,
                trafficSegments = currentTrafficSegments,
                roadFeatures = uiState.routeRoadFeatures,
                roadFeatureDataState = uiState.roadFeatureDataState,
                routeCriticalPois = uiState.routeCriticalPois,
                routeCriticalPoiDataState = uiState.routeCriticalPoiDataState
            )
        }
    }

    LaunchedEffect(uiState.navigationProgress, uiState.navigationState, currentTrafficStatus) {
        val prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        val isNavigating = uiState.navigationState == NavigationState.NAVIGATING
        
        val etaMins = uiState.navigationProgress?.totalRemainingSeconds?.let { it / 60 }
        val etaStr = if (etaMins != null) "$etaMins dk" else "--"
        val destName = uiState.selectedDestination?.displayName ?: "Hedef"
        val trafficLabel = when (currentTrafficStatus?.trafficLevel) {
            com.example.haritalar.model.TrafficLevel.LOW -> "Akıcı"
            com.example.haritalar.model.TrafficLevel.MODERATE -> "Yoğun"
            com.example.haritalar.model.TrafficLevel.HEAVY, com.example.haritalar.model.TrafficLevel.SEVERE -> "Sıkışık"
            else -> "--"
        }

        prefs.edit().apply {
            putBoolean("is_navigating", isNavigating)
            putString("eta", etaStr)
            putString("destination", destName)
            putString("traffic", trafficLabel)
            apply()
        }

        val updateIntent = Intent(context, com.example.haritalar.widget.RouteWidgetProvider::class.java).apply {
            action = com.example.haritalar.widget.RouteWidgetProvider.ACTION_UPDATE_WIDGET
        }
        context.sendBroadcast(updateIntent)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                        bottom = when {
                            uiState.navigationState == NavigationState.NAVIGATING -> 120.dp
                            uiState.navigationState == NavigationState.ROUTE_SELECTION -> 330.dp
                            uiState.isDestinationCardVisible -> 250.dp
                            else -> 12.dp
                        }
                    )
            )
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            MapLibreContainer(
                userLocation = uiState.userLocation,
                isUserLocationReliable = NavigationLocationPolicy.isUsableForRouting(uiState.userLocation),
                activeRoute = uiState.selectedRoute,
                alternativeRoutes = uiState.routeOptions,
                trafficStatus = currentTrafficStatus,
                trafficSegments = currentTrafficSegments,
                isTrafficLayerVisible = uiState.isTrafficLayerVisible,
                isTrafficSignalsLayerVisible = uiState.isTrafficSignalsLayerVisible &&
                        uiState.currentZoomLevel >= TrafficSignalRepository.MIN_ZOOM_FOR_SIGNALS,
                trafficSignals = uiState.trafficSignals,
                isPoiLayerVisible = uiState.isPoiLayerVisible,
                poiList = uiState.poiList,
                selectedPoiCategory = uiState.selectedPoiCategory,
                currentViewportBbox = uiState.currentViewportBbox,
                currentZoomLevel = uiState.currentZoomLevel,
                safetyCameras = safetyCameras,
                isSafetyCamerasLayerVisible = uiState.isSafetyCamerasLayerVisible,
                destinationPoint = uiState.selectedDestination?.point,
                cameraMode = uiState.cameraMode,
                mapTrackingMode = uiState.mapTrackingMode,
                navigationState = uiState.navigationState,
                vehicleHeading = uiState.vehicleHeadingState.heading,
                onMapClick = { point ->
                    if (uiState.navigationState != NavigationState.NAVIGATING) {
                        viewModel.selectDestinationPoint(point)
                    }
                },
                onMapDrag = {
                    viewModel.setFreeTrackingMode()
                },
                onViewportChanged = { bbox, zoom ->
                    viewModel.onViewportChanged(bbox, zoom)
                    safetyCameraViewModel.onViewportChanged(
                        SafetyCameraBoundingBox(
                            south = bbox.south,
                            west = bbox.west,
                            north = bbox.north,
                            east = bbox.east
                        ),
                        zoom
                    )
                },
                onTrafficSignalClick = { signal ->
                    viewModel.selectTrafficSignal(signal)
                },
                onPoiClick = { poi ->
                    viewModel.selectPoi(poi)
                },
                modifier = Modifier.fillMaxSize()
            )

            if (uiState.navigationState != NavigationState.NAVIGATING) {
                SearchHeader(
                    searchQuery = uiState.searchQuery,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onClearQuery = { viewModel.clearSearchQuery() },
                    isSearching = uiState.isSearching,
                    searchStatus = uiState.searchStatus,
                    searchErrorMessage = uiState.searchErrorMessage,
                    searchActiveProvider = uiState.searchActiveProvider,
                    isSearchFocused = uiState.isSearchFocused,
                    onSearchFocusChanged = { viewModel.onSearchFocusChanged(it) },
                    onRetrySearch = { viewModel.retrySearch() },
                    searchResults = uiState.searchResults,
                    onSelectResult = { viewModel.selectSearchResult(it) },
                    selectedCategory = uiState.selectedPoiCategory,
                    onSelectCategory = { viewModel.selectPoiCategory(it) },
                    favorites = favorites,
                    onSelectFavorite = { fav ->
                        viewModel.selectDestinationPoint(
                            com.example.haritalar.model.GeoPoint(fav.latitude, fav.longitude),
                            fav.title
                        )
                    },
                    onDeleteFavorite = { fav -> viewModel.removeFavorite(fav) },
                    recentSearches = recentSearches,
                    onSelectRecentSearch = { item ->
                        viewModel.selectDestinationPoint(
                            com.example.haritalar.model.GeoPoint(item.latitude, item.longitude),
                            item.query
                        )
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            if (uiState.navigationState == NavigationState.NAVIGATING ||
                uiState.navigationState == NavigationState.OFF_ROUTE_REROUTING
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DrivingTopInstructionBanner(
                        progress = uiState.navigationProgress,
                        isOffRoute = uiState.navigationState == NavigationState.OFF_ROUTE_REROUTING ||
                                (uiState.navigationProgress?.isOffRoute == true),
                        isGpsWeak = uiState.userLocation?.isGpsWeak == true,
                        departureGuidance = uiState.departureGuidance,
                        isWrongWay = uiState.isWrongWay,
                        modifier = Modifier.fillMaxWidth()
                    )

                    RoadIntelligenceStrip(
                        events = roadIntelligenceEvents,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            FloatingMapControls(
                cameraMode = uiState.cameraMode,
                isMuted = uiState.isMuted,
                isSimulationActive = uiState.isSimulationActive,
                isLiveSharingActive = uiState.isLiveSharingActive,
                onToggle2D3D = { viewModel.toggle2D3D() },
                onOpenLayers = { showLayersSheet = true },
                onToggleMute = { viewModel.toggleMute() },
                onRecenter = { viewModel.recenterMap() },
                onToggleLiveSharing = {
                    viewModel.toggleLiveSharing()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = when (uiState.navigationState) {
                            NavigationState.NAVIGATING -> 140.dp
                            NavigationState.ROUTE_SELECTION -> 220.dp
                            else -> 30.dp
                        }
                    )
            )

            AnimatedVisibility(
                visible = !uiState.isSearchFocused &&
                        uiState.isDestinationCardVisible && uiState.selectedDestination != null &&
                        uiState.navigationState != NavigationState.NAVIGATING && uiState.routeOptions.isEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                uiState.selectedDestination?.let { dest ->
                    DestinationPreviewCard(
                        destination = dest,
                        onCalculateRoutes = { viewModel.calculateRoutes(dest.point) },
                        onStartNavigation = {
                            viewModel.startNavigationTo(dest.point)
                        },
                        onSavePlace = { title, category ->
                            viewModel.saveSelectedPlace(title, category)
                        },
                        onDismiss = { viewModel.dismissDestinationCard() }
                    )
                }
            }

            AnimatedVisibility(
                visible = !uiState.isSearchFocused &&
                        uiState.navigationState == NavigationState.ROUTE_SELECTION && uiState.routeOptions.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                RouteOptionsCarousel(
                    routes = uiState.routeOptions,
                    selectedRoute = uiState.selectedRoute,
                    trafficMap = uiState.trafficStatusMap,
                    brief = preDriveBrief,
                    onSelectRoute = { viewModel.selectRoute(it) },
                    onStartNavigation = { viewModel.startNavigation() },
                    onCancel = { viewModel.stopNavigation() }
                )
            }

            AnimatedVisibility(
                visible = uiState.navigationState == NavigationState.NAVIGATING,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                DrivingBottomDashboard(
                    progress = uiState.navigationProgress,
                    speedKmh = uiState.userLocation?.speedKmh ?: 0f,
                    trafficStatus = currentTrafficStatus,
                    isMuted = uiState.isMuted,
                    onToggleMute = { viewModel.toggleMute() },
                    onOpenSearchAlongRoute = { viewModel.openSearchAlongRoute() },
                    onOpenTrafficInspector = { viewModel.openTrafficInspector() },
                    onStopNavigation = { viewModel.stopNavigation() }
                )
            }

            if (uiState.isSearchAlongRouteOpen) {
                SearchAlongRouteDialog(
                    alongRoutePois = uiState.alongRoutePois,
                    isLoading = uiState.isLoadingAlongRoute,
                    onSelectCategory = { cat -> viewModel.searchAlongRouteCategory(cat) },
                    onSelectPoi = { poi -> viewModel.selectAlongRoutePoi(poi) },
                    onDismiss = { viewModel.closeSearchAlongRoute() }
                )
            }

            val tripSummary = uiState.tripSummary
            if (uiState.navigationState == NavigationState.ARRIVED && tripSummary != null) {
                TripSummaryDialog(
                    summary = tripSummary,
                    onDismiss = { viewModel.dismissTripSummary() }
                )
            }

            if (uiState.isTrafficInspectorOpen) {
                TrafficInspectorDialog(
                    trafficStatus = currentTrafficStatus,
                    currentApiKey = uiState.customTomTomKey,
                    testResult = uiState.trafficTestResult,
                    isTesting = uiState.isTestingTraffic,
                    onSaveApiKey = { key -> viewModel.saveCustomTomTomKey(key) },
                    onTestConnection = { viewModel.testTrafficConnection() },
                    onDismiss = { viewModel.closeTrafficInspector() }
                )
            }

            if (showLayersSheet) {
                LayersBottomSheet(
                    cameraMode = uiState.cameraMode,
                    isTrafficLayerVisible = uiState.isTrafficLayerVisible,
                    isTrafficSignalsLayerVisible = uiState.isTrafficSignalsLayerVisible,
                    isSafetyCamerasLayerVisible = uiState.isSafetyCamerasLayerVisible,
                    isPoiLayerVisible = uiState.isPoiLayerVisible,
                    isSimulationActive = uiState.isSimulationActive,
                    hasActiveRoute = uiState.selectedRoute != null,
                    offlineDownloadProgress = offlineDownloadProgress,
                    offlineDownloadMessage = offlineDownloadMessage,
                    onSet2DMode = {
                        viewModel.set2DMode()
                        showLayersSheet = false
                    },
                    onSet3DMode = {
                        viewModel.set3DMode()
                        showLayersSheet = false
                    },
                    onToggleTrafficLayer = { viewModel.toggleTrafficLayer() },
                    onToggleTrafficSignalsLayer = { viewModel.toggleTrafficSignalsLayer() },
                    onToggleSafetyCamerasLayer = { viewModel.toggleSafetyCamerasLayer() },
                    onTogglePoiLayer = { viewModel.togglePoiLayer() },
                    onToggleSimulation = { viewModel.toggleSimulation() },
                    onOpenTrafficInspector = { viewModel.openTrafficInspector() },
                    onDownloadOfflineMap = { viewModel.downloadOfflineMap() },
                    onDismiss = { showLayersSheet = false }
                )
            }

            uiState.selectedTrafficSignal?.let { signal ->
                TrafficSignalDetailSheet(
                    signal = signal,
                    onNavigateTo = { viewModel.navigateToTrafficSignal(it) },
                    onDismiss = { viewModel.dismissTrafficSignalDetail() }
                )
            }
        }
    }
}
