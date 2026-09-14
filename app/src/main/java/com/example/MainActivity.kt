package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.startLocationUpdates(fineGranted || coarseGranted)
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            viewModel.startLocationUpdates(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Observe status messages to display in Snackbar
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
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

    LaunchedEffect(uiState.userLocation, safetyCameras) {
        viewModel.checkSafetyCameraProximity(safetyCameras)
        viewModel.checkWeatherProximity()
    }

    val selectedTrafficPair = uiState.selectedRoute?.let { uiState.trafficStatusMap[it.routeId] }
    val currentTrafficStatus = selectedTrafficPair?.first
    val currentTrafficSegments = selectedTrafficPair?.second ?: emptyList()

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
                    .padding(bottom = if (uiState.navigationState == NavigationState.NAVIGATING) 120.dp else 16.dp)
            )
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Base Map Layer (MapLibre Native with 2D/3D, Lines, Traffic, POIs, Safety Cameras)
            MapLibreContainer(
                userLocation = uiState.userLocation,
                activeRoute = uiState.selectedRoute,
                alternativeRoutes = uiState.routeOptions,
                trafficStatus = currentTrafficStatus,
                trafficSegments = currentTrafficSegments,
                isTrafficLayerVisible = uiState.isTrafficLayerVisible,
                isTrafficSignalsLayerVisible = uiState.isTrafficSignalsLayerVisible,
                trafficSignals = uiState.trafficSignals,
                isPoiLayerVisible = uiState.isPoiLayerVisible,
                poiList = uiState.poiList,
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
                modifier = Modifier.fillMaxSize()
            )

            // 2. Search Bar and POI category chips (when not in turn-by-turn navigation)
            if (uiState.navigationState != NavigationState.NAVIGATING) {
                SearchHeader(
                    searchQuery = uiState.searchQuery,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onClearQuery = { viewModel.clearSearchQuery() },
                    isSearching = uiState.isSearching,
                    searchStatus = uiState.searchStatus,
                    searchErrorMessage = uiState.searchErrorMessage,
                    searchActiveProvider = uiState.searchActiveProvider,
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

            // 3. Navigation HUD: Top Driving Instruction Banner
            if (uiState.navigationState == NavigationState.NAVIGATING || uiState.navigationState == NavigationState.OFF_ROUTE_REROUTING) {
                DrivingTopInstructionBanner(
                    progress = uiState.navigationProgress,
                    isOffRoute = uiState.navigationState == NavigationState.OFF_ROUTE_REROUTING ||
                            (uiState.navigationProgress?.isOffRoute == true),
                    isGpsWeak = uiState.userLocation?.isGpsWeak == true,
                    departureGuidance = uiState.departureGuidance,
                    isWrongWay = uiState.isWrongWay,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                )
            }

            // Radar Warning Card
            RadarWarningCard(
                camera = uiState.approachingCamera,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (uiState.navigationState == NavigationState.NAVIGATING) 140.dp else 100.dp)
                    .statusBarsPadding()
            )

            // Weather Warning Card
            com.example.haritalar.ui.WeatherWarningCard(
                weather = uiState.approachingWeather,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (uiState.navigationState == NavigationState.NAVIGATING) 220.dp else 180.dp)
                    .statusBarsPadding()
            )

            // 4. Floating Map Controls (2D/3D, Layers, Mute, Recenter)
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

            // 5. Selected Destination Preview Card (shown upon selecting a result before or during route selection)
            AnimatedVisibility(
                visible = uiState.isDestinationCardVisible && uiState.selectedDestination != null &&
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
                            viewModel.calculateRoutes(dest.point)
                            viewModel.startNavigation()
                        },
                        onDismiss = { viewModel.dismissDestinationCard() }
                    )
                }
            }

            // 6. Route Selection Carousel (Showing the calculated route alternatives)
            AnimatedVisibility(
                visible = uiState.navigationState == NavigationState.ROUTE_SELECTION && uiState.routeOptions.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                RouteOptionsCarousel(
                    routes = uiState.routeOptions,
                    selectedRoute = uiState.selectedRoute,
                    trafficMap = uiState.trafficStatusMap,
                    onSelectRoute = { viewModel.selectRoute(it) },
                    onStartNavigation = { viewModel.startNavigation() },
                    onCancel = { viewModel.stopNavigation() }
                )
            }

            // 6. Navigation HUD: Bottom Driving Dashboard (Speed, ETA, Remaining distance/time, Finish button)
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

            // 7. Search Along Route Dialog
            if (uiState.isSearchAlongRouteOpen) {
                SearchAlongRouteDialog(
                    alongRoutePois = uiState.alongRoutePois,
                    isLoading = uiState.isLoadingAlongRoute,
                    onSelectCategory = { cat -> viewModel.searchAlongRouteCategory(cat) },
                    onSelectPoi = { poi -> viewModel.selectAlongRoutePoi(poi) },
                    onDismiss = { viewModel.closeSearchAlongRoute() }
                )
            }

            // 8. Trip Summary Dialog on Arrival
            val tripSummary = uiState.tripSummary
            if (uiState.navigationState == NavigationState.ARRIVED && tripSummary != null) {
                TripSummaryDialog(
                    summary = tripSummary,
                    onDismiss = { viewModel.dismissTripSummary() }
                )
            }

            // 9. Traffic Inspector & Live Proof Dialog
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

            // 10. Layers Modal Bottom Sheet
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

            // 11. Real Traffic Signal Detail Sheet
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
