package com.example.haritalar.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.haritalar.data.db.FavoritePlace
import com.example.haritalar.data.db.SearchHistoryItem
import com.example.haritalar.data.repository.NavigationRepository
import com.example.haritalar.data.repository.RoadFeatureRepository
import com.example.BuildConfig
import com.example.haritalar.data.repository.TrafficSignalRepository
import com.example.haritalar.data.network.LiveSharingClient
import com.example.haritalar.model.CameraMode
import com.example.haritalar.model.DepartureGuidance
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.NavigationState
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.RoadFeature
import com.example.haritalar.model.RoadFeatureDataState
import com.example.haritalar.model.RoadFeatureFetchResult
import com.example.haritalar.model.SearchResult
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox
import com.example.haritalar.model.TrafficSignalFetchResult
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.TrafficTestResult
import com.example.haritalar.model.TripSummary
import com.example.haritalar.navigation.AppLocationManager
import com.example.haritalar.navigation.CompassHeadingSensor
import com.example.haritalar.navigation.DestinationSnapPolicy
import com.example.haritalar.navigation.NavigationEngine
import com.example.haritalar.navigation.NavigationLocationPolicy
import com.example.haritalar.navigation.NavigationForegroundService
import com.example.haritalar.navigation.PoiSearchCenterPolicy
import com.example.haritalar.navigation.PoiViewportPolicy
import com.example.haritalar.navigation.RoadFeatureRoutePolicy
import com.example.haritalar.navigation.RoadFeatureWarning
import com.example.haritalar.navigation.NavigationProgress
import com.example.haritalar.navigation.UserLocationData
import com.example.haritalar.navigation.VehicleHeadingManager
import com.example.haritalar.navigation.VehicleHeadingState
import com.example.haritalar.navigation.WeatherRequestGuard
import com.example.haritalar.voice.TurkishTtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val userLocation: UserLocationData? = null,
    val vehicleHeadingState: VehicleHeadingState = VehicleHeadingState(),
    val departureGuidance: DepartureGuidance? = null,
    val isWrongWay: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchStatus: com.example.haritalar.model.SearchUiStatus = com.example.haritalar.model.SearchUiStatus.IDLE,
    val searchErrorMessage: String? = null,
    val searchActiveProvider: String? = null,
    val isSearchFocused: Boolean = false,
    val isDestinationCardVisible: Boolean = false,
    val selectedDestination: SearchResult? = null,
    val routeOptions: List<RouteOption> = emptyList(),
    val selectedRoute: RouteOption? = null,
    val trafficStatusMap: Map<String, Pair<TrafficStatus, List<TrafficSegment>>> = emptyMap(),
    val navigationState: NavigationState = NavigationState.IDLE,
    val navigationProgress: NavigationProgress? = null,
    val cameraMode: CameraMode = CameraMode.TWO_D,
    val mapTrackingMode: MapTrackingMode = MapTrackingMode.FOLLOW_USER,
    val isTrafficLayerVisible: Boolean = true,
    val isTrafficSignalsLayerVisible: Boolean = true,
    val trafficSignals: List<TrafficSignal> = emptyList(),
    val selectedTrafficSignal: TrafficSignal? = null,
    val isLoadingTrafficSignals: Boolean = false,
    val isPoiLayerVisible: Boolean = false,
    val selectedPoiCategory: PoiCategory? = null,
    val poiList: List<PoiItem> = emptyList(),
    val isLoadingPois: Boolean = false,
    val isMuted: Boolean = false,
    val isSimulationActive: Boolean = false,
    val activeGenerationId: Long = 0L,
    val statusMessage: String? = null,
    val isLoadingRoutes: Boolean = false,
    val tripSummary: TripSummary? = null,
    val isSearchAlongRouteOpen: Boolean = false,
    val alongRoutePois: List<PoiItem> = emptyList(),
    val isLoadingAlongRoute: Boolean = false,
    val isTrafficInspectorOpen: Boolean = false,
    val trafficTestResult: TrafficTestResult? = null,
    val isTestingTraffic: Boolean = false,
    val customTomTomKey: String = "",
    val isLiveSharingActive: Boolean = false,
    val liveShareUrl: String? = null,
    val isSafetyCamerasLayerVisible: Boolean = true,
    val approachingCamera: com.example.haritalar.model.SafetyCamera? = null,
    val approachingCameraWarning: com.example.haritalar.navigation.SafetyCameraWarningPolicy.ProximityWarning? = null,
    val currentViewportBbox: com.example.haritalar.model.TrafficSignalBoundingBox? = null,
    val currentZoomLevel: Float = 0f,
    val isWeatherLayerVisible: Boolean = true,
    val routeWeather: List<com.example.haritalar.model.WeatherCondition> = emptyList(),
    val approachingWeather: com.example.haritalar.model.WeatherCondition? = null,
    val routeRoadFeatures: List<RoadFeature> = emptyList(),
    val roadFeatureDataState: RoadFeatureDataState = RoadFeatureDataState.IDLE,
    val approachingRoadFeatureWarning: RoadFeatureWarning? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = NavigationRepository(application)
    val locationManager = AppLocationManager(application)
    val offlineMapManager = com.example.haritalar.data.offline.OfflineMapManager(application)
    val weatherRepository = com.example.haritalar.data.weather.WeatherRepository()
    val roadFeatureRepository = RoadFeatureRepository(application)
    val ttsManager = TurkishTtsManager(application)
    val compassSensor = CompassHeadingSensor(application)
    val vehicleHeadingManager = VehicleHeadingManager(compassSensor)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val favorites: StateFlow<List<FavoritePlace>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<SearchHistoryItem>> = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val navigationEngine = NavigationEngine(
        ttsManager = ttsManager,
        onOffRouteDetected = { currentPoint -> handleOffRoute(currentPoint) },
        onArrivalDetected = { summary -> handleArrival(summary) }
    )

    private var searchJob: Job? = null
    private var routeCalculationJob: Job? = null
    private var weatherJob: Job? = null
    private var roadFeatureJob: Job? = null
    private var trafficRefreshJob: Job? = null
    private var trafficSignalJob: Job? = null
    private var poiLoadJob: Job? = null
    private var poiViewportRefreshJob: Job? = null
    private var liveShareJob: Job? = null
    private var liveShareSession: LiveSharingClient.Session? = null
    private val liveSharingClient = LiveSharingClient(BuildConfig.LIVE_SHARE_BASE_URL)
    private var generationCounter = 1L
    private var weatherGeneration = 0L
    private var roadFeatureGeneration = 0L
    private var trafficSignalGeneration = 0L
    private var poiGeneration = 0L
    private var lastPoiSearchCenter: GeoPoint? = null
    private var lastPoiSearchRadiusMeters: Int = 0

    private val trafficSignalRepository = repository.trafficSignalRepository

    init {
        viewModelScope.launch {
            locationManager.userLocation.collect { loc ->
                if (loc != null) {
                    val headingState = vehicleHeadingManager.processLocation(loc)
                    if (_uiState.value.navigationState == NavigationState.NAVIGATING) {
                        val progress = navigationEngine.processLocationUpdate(loc)
                        val wrongWay = vehicleHeadingManager.evaluateWrongWay(
                            currentHeading = headingState.heading,
                            routeSegmentBearing = progress.snappedBearing,
                            speedKmh = loc.speedKmh
                        )

                        val startDist = _uiState.value.selectedRoute?.geometry?.firstOrNull()?.distanceTo(loc.point) ?: 100.0
                        val currentDep = if (startDist > 65.0) null else _uiState.value.departureGuidance

                        val displayLoc = if (progress.snappedLocation != null) {
                            loc.copy(
                                point = progress.snappedLocation,
                                bearing = headingState.heading
                            )
                        } else {
                            loc.copy(bearing = headingState.heading)
                        }
                        _uiState.value = _uiState.value.copy(
                            userLocation = displayLoc,
                            vehicleHeadingState = headingState,
                            navigationProgress = progress,
                            departureGuidance = currentDep,
                            isWrongWay = wrongWay
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            userLocation = loc.copy(bearing = headingState.heading),
                            vehicleHeadingState = headingState
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            compassSensor.sensorHeading.collect { _ ->
                val currentLoc = _uiState.value.userLocation
                if (currentLoc != null && currentLoc.speedKmh < 4.5f) {
                    val headingState = vehicleHeadingManager.processLocation(currentLoc)
                    val updatedLoc = currentLoc.copy(bearing = headingState.heading)
                    _uiState.value = _uiState.value.copy(
                        userLocation = updatedLoc,
                        vehicleHeadingState = headingState
                    )
                }
            }
        }
    }

    fun startLocationUpdates(hasFinePermission: Boolean) {
        if (!hasFinePermission) {
            locationManager.stopLocationUpdates()
            _uiState.value = _uiState.value.copy(
                isLoadingRoutes = false,
                statusMessage = "Konum izni gerekli. Gerçek GPS konumu alınmadan rota hesaplanamaz."
            )
            return
        }

        _uiState.value = _uiState.value.copy(statusMessage = "Konum alınıyor...")
        locationManager.startLocationUpdates(true)
    }

    private var searchGenerationId = 0L

    private fun currentRoutingLocation(): UserLocationData? {
        val location = _uiState.value.userLocation
        return location?.takeIf { NavigationLocationPolicy.isUsableForRouting(it) }
    }

    private fun routingLocationFailureMessage(action: String): String {
        val location = _uiState.value.userLocation
        return when (NavigationLocationPolicy.readiness(location)) {
            NavigationLocationPolicy.Readiness.MISSING ->
                "Canlı GPS konumu henüz alınmadı. Konum servislerini ve izni kontrol edin. $action"
            NavigationLocationPolicy.Readiness.STALE -> {
                val ageSeconds = location?.let {
                    ((System.currentTimeMillis() - it.timestamp).coerceAtLeast(0L) / 1000L)
                } ?: 0L
                "Haritadaki nokta son bilinen konum; canlı GPS yaklaşık ${ageSeconds} sn önce güncellendi. Yeni konum bekleniyor. $action"
            }
            NavigationLocationPolicy.Readiness.INACCURATE -> {
                val accuracy = location?.accuracyMeters?.takeIf { it.isFinite() }?.toInt()
                val accuracyText = accuracy?.let { " (±${it} m)" }.orEmpty()
                "GPS doğruluğu navigasyon için yetersiz$accuracyText. Açık alanda yeni konum bekleniyor. $action"
            }
            NavigationLocationPolicy.Readiness.READY -> action
        }
    }

    private fun destinationSnappedToRouteEndpoint(route: RouteOption?): SearchResult? {
        val selected = _uiState.value.selectedDestination ?: return null
        val routeEnd = route?.geometry?.lastOrNull()
        val displayPoint = DestinationSnapPolicy.displayPoint(
            resultType = selected.resultType,
            geocoderPoint = selected.point,
            routeEndPoint = routeEnd
        )
        return if (displayPoint == selected.point) selected else selected.copy(point = displayPoint)
    }

    private fun clearRoutePresentationForSearch() {
        if (_uiState.value.navigationState == NavigationState.NAVIGATING ||
            _uiState.value.navigationState == NavigationState.OFF_ROUTE_REROUTING
        ) return

        routeCalculationJob?.cancel()
        routeCalculationJob = null
        weatherJob?.cancel()
        weatherJob = null
        weatherGeneration++
        roadFeatureJob?.cancel()
        roadFeatureJob = null
        roadFeatureGeneration++
        val invalidateGeneration = ++generationCounter
        _uiState.value = _uiState.value.copy(
            selectedDestination = null,
            isDestinationCardVisible = false,
            routeOptions = emptyList(),
            selectedRoute = null,
            trafficStatusMap = emptyMap(),
            routeWeather = emptyList(),
            approachingWeather = null,
            routeRoadFeatures = emptyList(),
            roadFeatureDataState = RoadFeatureDataState.IDLE,
            approachingRoadFeatureWarning = null,
            isLoadingRoutes = false,
            activeGenerationId = invalidateGeneration,
            navigationState = NavigationState.IDLE,
            statusMessage = null
        )
    }

    fun onSearchFocusChanged(focused: Boolean) {
        if (focused && !_uiState.value.isSearchFocused) {
            clearRoutePresentationForSearch()
        }
        _uiState.value = _uiState.value.copy(isSearchFocused = focused)
    }

    fun clearSearchQuery() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            searchStatus = com.example.haritalar.model.SearchUiStatus.IDLE,
            isSearching = false,
            searchErrorMessage = null
        )
    }

    fun onSearchQueryChanged(query: String) {
        if (query != _uiState.value.searchQuery &&
            _uiState.value.navigationState != NavigationState.NAVIGATING &&
            _uiState.value.navigationState != NavigationState.OFF_ROUTE_REROUTING
        ) {
            clearRoutePresentationForSearch()
        }
        _uiState.value = _uiState.value.copy(searchQuery = query, isSearchFocused = true)
        searchJob?.cancel()
        val currentGen = ++searchGenerationId

        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                isSearching = false,
                searchStatus = com.example.haritalar.model.SearchUiStatus.IDLE,
                searchErrorMessage = null
            )
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                searchStatus = com.example.haritalar.model.SearchUiStatus.SEARCHING,
                searchErrorMessage = null
            )
            delay(300L)
            val focus = _uiState.value.userLocation?.point
            val response = repository.searchPlacesResponse(query, focus)

            if (currentGen == searchGenerationId) {
                when (response) {
                    is com.example.haritalar.model.SearchResponse.Success -> {
                        _uiState.value = _uiState.value.copy(
                            searchResults = response.results,
                            isSearching = false,
                            searchStatus = com.example.haritalar.model.SearchUiStatus.SUCCESS,
                            searchActiveProvider = response.provider,
                            searchErrorMessage = null
                        )
                    }
                    is com.example.haritalar.model.SearchResponse.Empty -> {
                        _uiState.value = _uiState.value.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            searchStatus = com.example.haritalar.model.SearchUiStatus.EMPTY,
                            searchErrorMessage = null
                        )
                    }
                    is com.example.haritalar.model.SearchResponse.Error -> {
                        _uiState.value = _uiState.value.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            searchStatus = com.example.haritalar.model.SearchUiStatus.ERROR,
                            searchErrorMessage = response.message
                        )
                    }
                }
            }
        }
    }

    fun retrySearch() {
        val q = _uiState.value.searchQuery
        if (q.isNotBlank()) onSearchQueryChanged(q)
    }

    fun dismissDestinationCard() {
        routeCalculationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedDestination = null,
            isDestinationCardVisible = false,
            routeOptions = emptyList(),
            selectedRoute = null,
            isLoadingRoutes = false,
            navigationState = NavigationState.IDLE
        )
    }

    fun selectSearchResult(result: SearchResult) {
        routeCalculationJob?.cancel()
        val invalidateGeneration = ++generationCounter
        _uiState.value = _uiState.value.copy(
            selectedDestination = result,
            searchQuery = result.name,
            searchResults = emptyList(),
            searchStatus = com.example.haritalar.model.SearchUiStatus.IDLE,
            searchErrorMessage = null,
            isSearching = false,
            isSearchFocused = false,
            isDestinationCardVisible = true,
            routeOptions = emptyList(),
            selectedRoute = null,
            trafficStatusMap = emptyMap(),
            navigationState = NavigationState.IDLE,
            isLoadingRoutes = false,
            activeGenerationId = invalidateGeneration,
            statusMessage = null
        )
    }

    fun selectDestinationPoint(point: GeoPoint, title: String = "Seçilen Nokta") {
        viewModelScope.launch {
            val address = repository.reverseGeocode(point) ?: "$title (${point.latitude.toString().take(6)}, ${point.longitude.toString().take(6)})"
            val result = SearchResult(
                id = "custom_${System.currentTimeMillis()}",
                name = title,
                displayName = address,
                shortAddress = address.split(",").take(2).joinToString(", "),
                point = point,
                type = "point",
                resultType = com.example.haritalar.model.AddressResultType.PLACE,
                provider = "Harita Dokunma"
            )
            routeCalculationJob?.cancel()
            val invalidateGeneration = ++generationCounter
            _uiState.value = _uiState.value.copy(
                selectedDestination = result,
                searchQuery = title,
                searchResults = emptyList(),
                searchStatus = com.example.haritalar.model.SearchUiStatus.IDLE,
                searchErrorMessage = null,
                isSearching = false,
                isSearchFocused = false,
                isDestinationCardVisible = true,
                routeOptions = emptyList(),
                selectedRoute = null,
                trafficStatusMap = emptyMap(),
                navigationState = NavigationState.IDLE,
                isLoadingRoutes = false,
                activeGenerationId = invalidateGeneration,
                statusMessage = null
            )
        }
    }

    fun calculateRoutes(destination: GeoPoint) {
        routeCalculationJob?.cancel()
        val userLocation = currentRoutingLocation()
        if (userLocation == null) {
            val genId = ++generationCounter
            _uiState.value = _uiState.value.copy(
                routeOptions = emptyList(),
                selectedRoute = null,
                isLoadingRoutes = false,
                activeGenerationId = genId,
                navigationState = NavigationState.IDLE,
                statusMessage = routingLocationFailureMessage("Konum alınmadan rota hesaplanamaz.")
            )
            return
        }

        val genId = ++generationCounter
        routeCalculationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingRoutes = true,
                activeGenerationId = genId,
                routeWeather = emptyList(),
                routeRoadFeatures = emptyList(),
                roadFeatureDataState = RoadFeatureDataState.IDLE,
                approachingRoadFeatureWarning = null,
                statusMessage = "Rotalar hesaplanıyor..."
            )

            try {
                val (routes, trafficMap) = repository.calculateRouteAlternatives(userLocation.point, destination, genId)
                if (genId != _uiState.value.activeGenerationId) return@launch

                val primaryRoute = routes.firstOrNull()
                val snappedDestination = destinationSnappedToRouteEndpoint(primaryRoute)
                _uiState.value = _uiState.value.copy(
                    routeOptions = routes,
                    selectedRoute = primaryRoute,
                    selectedDestination = snappedDestination ?: _uiState.value.selectedDestination,
                    trafficStatusMap = trafficMap,
                    navigationState = if (routes.isNotEmpty()) NavigationState.ROUTE_SELECTION else NavigationState.IDLE,
                    isLoadingRoutes = false,
                    statusMessage = if (routes.isEmpty()) "Rota bulunamadı." else null
                )
                primaryRoute?.let {
                    fetchWeatherForRoute(it)
                    fetchRoadFeaturesForRoute(it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (genId == _uiState.value.activeGenerationId) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRoutes = false,
                        statusMessage = "Rota hesaplama hatası: ${e.message}"
                    )
                }
            }
        }
    }

    fun startNavigationTo(destination: GeoPoint) {
        routeCalculationJob?.cancel()
        val initialLocation = currentRoutingLocation()
        if (initialLocation == null) {
            _uiState.value = _uiState.value.copy(
                routeOptions = emptyList(), selectedRoute = null, isLoadingRoutes = false,
                statusMessage = routingLocationFailureMessage("Navigasyon başlatılamaz.")
            )
            return
        }

        val genId = ++generationCounter
        routeCalculationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingRoutes = true, activeGenerationId = genId,
                routeOptions = emptyList(), selectedRoute = null, routeWeather = emptyList(),
                statusMessage = "Rota hesaplanıyor..."
            )
            try {
                val (routes, trafficMap) = repository.calculateRouteAlternatives(initialLocation.point, destination, genId)
                if (genId != _uiState.value.activeGenerationId) return@launch
                val route = routes.firstOrNull()
                if (route == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRoutes = false, routeOptions = emptyList(), selectedRoute = null,
                        navigationState = NavigationState.IDLE, statusMessage = "Rota bulunamadı."
                    )
                    return@launch
                }
                val snappedDestination = destinationSnappedToRouteEndpoint(route)
                val latestLocation = currentRoutingLocation()
                if (latestLocation == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRoutes = false, routeOptions = routes, selectedRoute = route,
                        selectedDestination = snappedDestination ?: _uiState.value.selectedDestination,
                        navigationState = NavigationState.ROUTE_SELECTION,
                        statusMessage = routingLocationFailureMessage("Navigasyon başlatılmadı.")
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    routeOptions = routes, selectedRoute = route, trafficStatusMap = trafficMap,
                    selectedDestination = snappedDestination ?: _uiState.value.selectedDestination,
                    isLoadingRoutes = false, navigationState = NavigationState.ROUTE_SELECTION, statusMessage = null
                )
                fetchWeatherForRoute(route)
                fetchRoadFeaturesForRoute(route)
                startNavigationInternal(route, latestLocation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (genId == _uiState.value.activeGenerationId) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRoutes = false, navigationState = NavigationState.IDLE,
                        statusMessage = "Rota hesaplama hatası: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectRoute(route: RouteOption) {
        _uiState.value = _uiState.value.copy(selectedRoute = route)
        fetchWeatherForRoute(route)
        fetchRoadFeaturesForRoute(route)
    }

    private fun fetchWeatherForRoute(route: RouteOption) {
        val requestGeneration = ++weatherGeneration
        weatherJob?.cancel()
        roadFeatureJob?.cancel()
        roadFeatureJob = null
        roadFeatureGeneration++
        _uiState.value = _uiState.value.copy(
            routeWeather = emptyList(),
            approachingWeather = null,
            routeRoadFeatures = emptyList(),
            roadFeatureDataState = RoadFeatureDataState.IDLE,
            approachingRoadFeatureWarning = null
        )
        weatherJob = viewModelScope.launch {
            val departureEpochMillis = System.currentTimeMillis()
            val weather = weatherRepository.getRouteWeather(
                route = route,
                departureEpochMillis = departureEpochMillis
            )
            if (!WeatherRequestGuard.shouldApply(
                    requestGeneration = requestGeneration,
                    currentGeneration = weatherGeneration,
                    requestedRouteId = route.routeId,
                    currentRouteId = _uiState.value.selectedRoute?.routeId
                )
            ) {
                return@launch
            }
            _uiState.value = _uiState.value.copy(routeWeather = weather)
            checkWeatherProximity()
        }
    }

    private fun fetchRoadFeaturesForRoute(route: RouteOption) {
        val requestGeneration = ++roadFeatureGeneration
        roadFeatureJob?.cancel()
        _uiState.value = _uiState.value.copy(
            routeRoadFeatures = emptyList(),
            roadFeatureDataState = RoadFeatureDataState.IDLE,
            approachingRoadFeatureWarning = null
        )
        roadFeatureJob = viewModelScope.launch {
            val result = roadFeatureRepository.getForRoute(route.geometry)
            if (requestGeneration != roadFeatureGeneration ||
                _uiState.value.selectedRoute?.routeId != route.routeId
            ) {
                return@launch
            }

            when (result) {
                is RoadFeatureFetchResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        routeRoadFeatures = result.features,
                        roadFeatureDataState = if (result.fromCache) {
                            RoadFeatureDataState.CACHED
                        } else {
                            RoadFeatureDataState.VERIFIED
                        }
                    )
                    checkRoadFeatureProximity()
                }
                is RoadFeatureFetchResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        routeRoadFeatures = emptyList(),
                        roadFeatureDataState = RoadFeatureDataState.UNAVAILABLE,
                        approachingRoadFeatureWarning = null
                    )
                }
            }
        }
    }

    fun toggleWeatherLayer() {
        _uiState.value = _uiState.value.copy(isWeatherLayerVisible = !_uiState.value.isWeatherLayerVisible)
        checkWeatherProximity()
    }

    fun startNavigation() {
        if (_uiState.value.isLoadingRoutes) {
            _uiState.value = _uiState.value.copy(statusMessage = "Rota hâlâ hesaplanıyor. Lütfen rota hazır olduğunda tekrar başlatın.")
            return
        }
        val route = _uiState.value.selectedRoute
        if (route == null) {
            _uiState.value = _uiState.value.copy(statusMessage = "Başlatılacak hazır rota bulunamadı.")
            return
        }
        val userLocation = currentRoutingLocation()
        if (userLocation == null) {
            _uiState.value = _uiState.value.copy(
                statusMessage = routingLocationFailureMessage("Navigasyon başlatılamadı.")
            )
            return
        }
        startNavigationInternal(route, userLocation)
    }

    private fun startNavigationInternal(route: RouteOption, userLocation: UserLocationData) {
        announcedCameraWarningMilestones.clear()
        announcedRoadFeatureMilestones.clear()
        lastOverspeedCameraWarningKey = null
        val currentHeading = _uiState.value.vehicleHeadingState.heading
        val depGuidance = VehicleHeadingManager.buildDepartureGuidance(
            vehicleHeading = currentHeading, route = route, userPoint = userLocation.point
        )
        vehicleHeadingManager.start()
        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.NAVIGATING,
            cameraMode = CameraMode.THREE_D,
            mapTrackingMode = MapTrackingMode.FOLLOW_BEARING,
            departureGuidance = depGuidance,
            isWrongWay = depGuidance.isWrongWay,
            statusMessage = null
        )
        navigationEngine.startNavigation(route)
        NavigationForegroundService.start(
            getApplication(),
            _uiState.value.selectedDestination?.displayName
        )
        startPeriodicTrafficRefresh()
    }

    fun stopNavigation() {
        routeCalculationJob?.cancel()
        routeCalculationJob = null
        weatherJob?.cancel()
        weatherJob = null
        weatherGeneration++
        vehicleHeadingManager.stop()
        vehicleHeadingManager.resetSession()
        navigationEngine.stop()
        NavigationForegroundService.stop(getApplication())
        locationManager.stopSimulation()
        trafficRefreshJob?.cancel()
        ttsManager.stop()
        announcedCameraWarningMilestones.clear()
        lastOverspeedCameraWarningKey = null
        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.IDLE, navigationProgress = null, selectedRoute = null,
            routeOptions = emptyList(), selectedDestination = null, searchQuery = "",
            cameraMode = CameraMode.TWO_D, mapTrackingMode = MapTrackingMode.FOLLOW_USER,
            isSimulationActive = false, statusMessage = null, isSearchAlongRouteOpen = false,
            alongRoutePois = emptyList(), isLoadingAlongRoute = false, departureGuidance = null,
            isWrongWay = false, isLoadingRoutes = false, routeWeather = emptyList(), approachingWeather = null,
            routeRoadFeatures = emptyList(), roadFeatureDataState = RoadFeatureDataState.IDLE,
            approachingRoadFeatureWarning = null
        )
    }

    private fun handleOffRoute(currentPoint: GeoPoint) {
        routeCalculationJob?.cancel()
        weatherJob?.cancel()
        weatherJob = null
        weatherGeneration++
        _uiState.value = _uiState.value.copy(
            routeWeather = emptyList(),
            approachingWeather = null
        )
        vehicleHeadingManager.onReroute()
        val dest = _uiState.value.selectedDestination?.point ?: return
        val genId = ++generationCounter
        routeCalculationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                navigationState = NavigationState.OFF_ROUTE_REROUTING,
                activeGenerationId = genId,
                statusMessage = "Rotadan çıkıldı. Yeni rota hesaplanıyor..."
            )
            try {
                val (routes, trafficMap) = repository.calculateRouteAlternatives(currentPoint, dest, genId)
                if (genId == _uiState.value.activeGenerationId && routes.isNotEmpty()) {
                    val newRoute = routes.first()
                    _uiState.value = _uiState.value.copy(
                        routeOptions = routes, selectedRoute = newRoute, trafficStatusMap = trafficMap,
                        navigationState = NavigationState.NAVIGATING, statusMessage = null, isLoadingRoutes = false
                    )
                    navigationEngine.updateRoute(newRoute)
                    fetchWeatherForRoute(newRoute)
                    fetchRoadFeaturesForRoute(newRoute)
                } else if (genId == _uiState.value.activeGenerationId) {
                    _uiState.value = _uiState.value.copy(
                        navigationState = NavigationState.NAVIGATING, isLoadingRoutes = false,
                        statusMessage = "Yeniden rota oluşturulamadı, mevcut rotaya dönün."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (genId == _uiState.value.activeGenerationId) {
                    _uiState.value = _uiState.value.copy(
                        navigationState = NavigationState.NAVIGATING,
                        isLoadingRoutes = false,
                        statusMessage = "Yeniden rota oluşturulamadı, mevcut rotaya dönün."
                    )
                }
            }
        }
    }

    private fun handleArrival(summary: TripSummary) {
        NavigationForegroundService.stop(getApplication())
        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.ARRIVED, tripSummary = summary, statusMessage = "Hedefinize ulaştınız!"
        )
    }

    fun dismissTripSummary() {
        stopNavigation()
        _uiState.value = _uiState.value.copy(tripSummary = null)
    }

    fun openSearchAlongRoute() { _uiState.value = _uiState.value.copy(isSearchAlongRouteOpen = true) }

    fun closeSearchAlongRoute() {
        _uiState.value = _uiState.value.copy(isSearchAlongRouteOpen = false, alongRoutePois = emptyList(), isLoadingAlongRoute = false)
    }

    fun searchAlongRouteCategory(category: PoiCategory) {
        val focus = currentRoutingLocation()?.point ?: run {
            _uiState.value = _uiState.value.copy(statusMessage = "İlgi noktalarını aramak için gerçek konum gerekli.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAlongRoute = true)
            val pois = repository.fetchPois(focus, category)
            _uiState.value = _uiState.value.copy(alongRoutePois = pois, isLoadingAlongRoute = false)
        }
    }

    fun selectPoi(poi: PoiItem) {
        selectSearchResult(
            SearchResult(
                id = poi.id,
                name = poi.name,
                displayName = poi.address ?: poi.name,
                shortAddress = poi.address ?: "",
                point = poi.point,
                type = "poi",
                resultType = com.example.haritalar.model.AddressResultType.POI,
                provider = "OSM POI"
            )
        )
    }

    fun selectAlongRoutePoi(poi: PoiItem) {
        closeSearchAlongRoute()
        selectPoi(poi)
    }

    fun openTrafficInspector() {
        _uiState.value = _uiState.value.copy(isTrafficInspectorOpen = true, customTomTomKey = repository.getEffectiveTomTomKey())
    }

    fun closeTrafficInspector() { _uiState.value = _uiState.value.copy(isTrafficInspectorOpen = false) }

    fun saveCustomTomTomKey(key: String) {
        repository.setCustomTomTomKey(key)
        _uiState.value = _uiState.value.copy(customTomTomKey = key.trim(), statusMessage = "TomTom API anahtarı güncellendi.")
    }

    fun testTrafficConnection() {
        val testPoint = currentRoutingLocation()?.point ?: _uiState.value.selectedDestination?.point ?: run {
            _uiState.value = _uiState.value.copy(
                isTestingTraffic = false, trafficTestResult = null,
                statusMessage = "Trafik bağlantı testi için gerçek konum veya hedef gerekli."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingTraffic = true, trafficTestResult = null)
            val result = repository.testTomTomTraffic(testPoint)
            _uiState.value = _uiState.value.copy(isTestingTraffic = false, trafficTestResult = result)
        }
    }

    private fun startPeriodicTrafficRefresh() {
        trafficRefreshJob?.cancel()
        trafficRefreshJob = viewModelScope.launch {
            while (_uiState.value.navigationState == NavigationState.NAVIGATING) {
                delay(60_000L)
                val currentRoute = _uiState.value.selectedRoute ?: break
                val genId = _uiState.value.activeGenerationId
                val result = repository.trafficCoordinator.requestRefresh(routes = listOf(currentRoute), generationId = genId)
                if (result != null && genId == _uiState.value.activeGenerationId) {
                    val (updatedRoutes, updatedMap) = result
                    val updated = updatedRoutes.firstOrNull()
                    if (updated != null) {
                        _uiState.value = _uiState.value.copy(
                            selectedRoute = updated,
                            trafficStatusMap = _uiState.value.trafficStatusMap + updatedMap
                        )
                    }
                }
            }
        }
    }

    fun toggle2D3D() {
        val newMode = if (_uiState.value.cameraMode == CameraMode.TWO_D) CameraMode.THREE_D else CameraMode.TWO_D
        _uiState.value = _uiState.value.copy(cameraMode = newMode)
    }
    fun set2DMode() { _uiState.value = _uiState.value.copy(cameraMode = CameraMode.TWO_D) }
    fun set3DMode() { _uiState.value = _uiState.value.copy(cameraMode = CameraMode.THREE_D) }
    fun toggleTrafficLayer() { _uiState.value = _uiState.value.copy(isTrafficLayerVisible = !_uiState.value.isTrafficLayerVisible) }
    fun toggleSafetyCamerasLayer() {
        val newVisible = !_uiState.value.isSafetyCamerasLayerVisible
        _uiState.value = _uiState.value.copy(
            isSafetyCamerasLayerVisible = newVisible,
            approachingCamera = if (newVisible) _uiState.value.approachingCamera else null,
            approachingCameraWarning = if (newVisible) _uiState.value.approachingCameraWarning else null
        )
        if (!newVisible) {
            announcedCameraWarningMilestones.clear()
            lastOverspeedCameraWarningKey = null
        }
    }

    private var lastAnnouncedWeatherId: String? = null

    fun checkWeatherProximity() {
        if (!_uiState.value.isWeatherLayerVisible || _uiState.value.navigationState != NavigationState.NAVIGATING) {
            _uiState.value = _uiState.value.copy(approachingWeather = null)
            return
        }
        val userLoc = currentRoutingLocation()?.point ?: return
        val weatherList = _uiState.value.routeWeather
        val nearestWeather = weatherList.minByOrNull { it.point.distanceTo(userLoc) }
        val distance = nearestWeather?.point?.distanceTo(userLoc) ?: Double.MAX_VALUE
        if (nearestWeather != null && distance <= 1000.0) {
            _uiState.value = _uiState.value.copy(approachingWeather = nearestWeather)
            if (lastAnnouncedWeatherId != nearestWeather.id) {
                lastAnnouncedWeatherId = nearestWeather.id
                ttsManager.speak("Dikkat. İleride ${nearestWeather.description.lowercase()} koşulları var.")
            }
        } else _uiState.value = _uiState.value.copy(approachingWeather = null)
    }

    private val announcedCameraWarningMilestones = mutableSetOf<String>()
    private var lastOverspeedCameraWarningKey: String? = null

    private fun formatSafetyWarningDistance(distanceBucketMeters: Int): String = when {
        distanceBucketMeters >= 1000 -> {
            val km = distanceBucketMeters / 1000
            if (distanceBucketMeters % 1000 == 0) "$km kilometre"
            else "${distanceBucketMeters / 1000.0} kilometre".replace('.', ',')
        }
        else -> "$distanceBucketMeters metre"
    }

    private fun vibrateSafetyWarning(durationMs: Long) {
        val app = getApplication<Application>()
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    durationMs.coerceIn(80L, 800L),
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs.coerceIn(80L, 800L))
        }
    }

    fun checkSafetyCameraProximity(cameras: List<com.example.haritalar.model.SafetyCamera>) {
        val navState = _uiState.value.navigationState
        val isDriving = navState == NavigationState.NAVIGATING || navState == NavigationState.OFF_ROUTE_REROUTING
        if (!_uiState.value.isSafetyCamerasLayerVisible || !isDriving) {
            _uiState.value = _uiState.value.copy(
                approachingCamera = null,
                approachingCameraWarning = null
            )
            return
        }

        val userLocation = currentRoutingLocation() ?: run {
            _uiState.value = _uiState.value.copy(
                approachingCamera = null,
                approachingCameraWarning = null
            )
            return
        }

        val routeGeometry = _uiState.value.selectedRoute?.geometry.orEmpty()
        val routeScopedCameras = if (navState == NavigationState.NAVIGATING && routeGeometry.size >= 2) {
            com.example.haritalar.navigation.SafetyCameraRouteFilterPolicy.relevantForRoute(
                cameras = cameras,
                route = routeGeometry,
                userPoint = _uiState.value.navigationProgress?.snappedLocation ?: userLocation.point
            )
        } else {
            cameras
        }

        val warning = com.example.haritalar.navigation.SafetyCameraWarningPolicy.nearest(
            cameras = routeScopedCameras,
            point = userLocation.point,
            speedKmh = userLocation.speedKmh
        )

        if (warning == null) {
            _uiState.value = _uiState.value.copy(
                approachingCamera = null,
                approachingCameraWarning = null
            )
            return
        }

        val camera = warning.camera
        _uiState.value = _uiState.value.copy(
            approachingCamera = camera,
            approachingCameraWarning = warning
        )

        val milestone = warning.announcementMilestoneMeters
        if (milestone != null && milestone > 0) {
            val milestoneKey = "${camera.id}:$milestone"
            if (announcedCameraWarningMilestones.add(milestoneKey)) {
                ttsManager.speak(
                    "Erken uyarı. ${formatSafetyWarningDistance(milestone)} ileride sabit hız kamerası noktası var."
                )
                vibrateSafetyWarning(140L)
            }
        }

        val speedLimit = warning.speedLimitKmh
        if (warning.overspeed && speedLimit != null) {
            val overspeedKey = "${camera.id}:$speedLimit"
            if (lastOverspeedCameraWarningKey != overspeedKey) {
                lastOverspeedCameraWarningKey = overspeedKey
                ttsManager.speak(
                    "Dikkat. Doğrulanmış hız sınırı $speedLimit kilometre saat. Güvenli şekilde hızınızı limite düşürün."
                )
                vibrateSafetyWarning(320L)
            }
        } else if (!warning.overspeed) {
            lastOverspeedCameraWarningKey = null
        }
    }

    private val announcedRoadFeatureMilestones = mutableSetOf<String>()

    fun checkRoadFeatureProximity() {
        val navState = _uiState.value.navigationState
        val isDriving = navState == NavigationState.NAVIGATING ||
            navState == NavigationState.OFF_ROUTE_REROUTING
        val route = _uiState.value.selectedRoute
        val location = currentRoutingLocation()

        if (!isDriving || route == null || location == null || route.geometry.size < 2) {
            _uiState.value = _uiState.value.copy(approachingRoadFeatureWarning = null)
            return
        }

        val warning = RoadFeatureRoutePolicy.nearestWarning(
            features = _uiState.value.routeRoadFeatures,
            route = route.geometry,
            userPoint = _uiState.value.navigationProgress?.snappedLocation ?: location.point,
            speedKmh = location.speedKmh
        )

        _uiState.value = _uiState.value.copy(approachingRoadFeatureWarning = warning)
        if (warning == null) return

        val milestone = if (warning.distanceMeters <= 300.0) 300 else warning.warningRadiusMeters
        val key = "${warning.feature.id}:$milestone"
        if (announcedRoadFeatureMilestones.add(key)) {
            ttsManager.speak(RoadFeatureRoutePolicy.voiceText(warning))
            vibrateSafetyWarning(if (milestone <= 300) 220L else 120L)
        }
    }

    fun togglePoiLayer() {
        val newVis = !_uiState.value.isPoiLayerVisible
        if (!newVis) {
            poiGeneration++
            poiLoadJob?.cancel()
            poiViewportRefreshJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isPoiLayerVisible = false,
                poiList = emptyList(),
                isLoadingPois = false,
                statusMessage = null
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isPoiLayerVisible = true,
            poiList = emptyList()
        )
        loadPois(_uiState.value.selectedPoiCategory)
    }

    fun selectPoiCategory(category: PoiCategory?) {
        poiLoadJob?.cancel()
        poiViewportRefreshJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedPoiCategory = category,
            isPoiLayerVisible = true,
            poiList = emptyList(),
            isLoadingPois = false,
            statusMessage = null
        )
        loadPois(category)
    }

    fun loadPois(category: PoiCategory?) {
        val state = _uiState.value
        val viewport = state.currentViewportBbox
        val zoomLevel = state.currentZoomLevel

        if (viewport != null && !PoiViewportPolicy.canQuery(zoomLevel, category)) {
            poiGeneration++
            poiLoadJob?.cancel()
            lastPoiSearchCenter = null
            lastPoiSearchRadiusMeters = 0
            _uiState.value = state.copy(
                poiList = emptyList(),
                isLoadingPois = false,
                statusMessage = category?.let {
                    "${it.displayName} noktalarını görmek için haritayı biraz yakınlaştırın."
                }
            )
            return
        }

        val center = PoiSearchCenterPolicy.resolve(
            trackingMode = state.mapTrackingMode,
            liveLocation = currentRoutingLocation()?.point,
            viewport = viewport
        ) ?: run {
            _uiState.value = state.copy(
                poiList = emptyList(),
                isLoadingPois = false,
                statusMessage = "İlgi noktası aramak için harita görünümü henüz hazır değil."
            )
            return
        }

        val searchRadius = PoiViewportPolicy.searchRadiusMeters(viewport, zoomLevel)
        val generation = ++poiGeneration
        lastPoiSearchCenter = center
        lastPoiSearchRadiusMeters = searchRadius
        poiLoadJob?.cancel()
        poiLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                poiList = emptyList(),
                isLoadingPois = true,
                statusMessage = null
            )
            val pois = repository.fetchPois(center, category, searchRadius)
            if (generation != poiGeneration || _uiState.value.selectedPoiCategory != category) {
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                poiList = pois,
                isLoadingPois = false,
                statusMessage = if (pois.isEmpty()) {
                    "Bu harita alanında ${category?.displayName?.lowercase() ?: "ilgi noktası"} bulunamadı."
                } else null
            )
        }
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        ttsManager.isMuted = newMuted
        if (newMuted) ttsManager.stop()
        _uiState.value = _uiState.value.copy(isMuted = newMuted)
    }

    fun recenterMap() {
        if (currentRoutingLocation() == null) {
            locationManager.requestFreshLocation()
            _uiState.value = _uiState.value.copy(
                statusMessage = "Canlı GPS konumu yenileniyor..."
            )
        }
        _uiState.value = _uiState.value.copy(
            mapTrackingMode = if (_uiState.value.navigationState == NavigationState.NAVIGATING) {
                MapTrackingMode.FOLLOW_BEARING
            } else {
                MapTrackingMode.FOLLOW_USER
            }
        )
    }
    fun setFreeTrackingMode() { _uiState.value = _uiState.value.copy(mapTrackingMode = MapTrackingMode.FREE) }

    fun toggleSimulation() {
        val currentRoute = _uiState.value.selectedRoute ?: return
        val isSim = !_uiState.value.isSimulationActive
        _uiState.value = _uiState.value.copy(isSimulationActive = isSim)
        if (isSim) locationManager.startRouteSimulation(currentRoute.geometry) { _, _ -> } else locationManager.stopSimulation()
    }

    fun toggleLiveSharing() {
        if (liveShareSession != null) {
            val session = liveShareSession
            liveShareSession = null
            liveShareJob?.cancel()
            liveShareJob = null
            _uiState.value = _uiState.value.copy(isLiveSharingActive = false, liveShareUrl = null)
            viewModelScope.launch {
                runCatching { liveSharingClient.revoke(session!!.id, session.token) }
            }
            return
        }

        val location = currentRoutingLocation() ?: run {
            _uiState.value = _uiState.value.copy(statusMessage = "Canlı paylaşım için gerçek GPS konumu gerekli.")
            return
        }

        liveShareJob?.cancel()
        liveShareJob = viewModelScope.launch {
            try {
                val eta = _uiState.value.navigationProgress?.totalRemainingSeconds
                val session = liveSharingClient.createSession(
                    latitude = location.point.latitude,
                    longitude = location.point.longitude,
                    bearing = location.bearing,
                    speedKmh = location.speedKmh,
                    etaSeconds = eta
                )
                liveShareSession = session
                _uiState.value = _uiState.value.copy(
                    isLiveSharingActive = true,
                    liveShareUrl = session.viewerUrl,
                    statusMessage = "Canlı takip paylaşımı açıldı."
                )

                while (liveShareSession?.id == session.id) {
                    val current = currentRoutingLocation() ?: break
                    val currentEta = _uiState.value.navigationProgress?.totalRemainingSeconds
                    runCatching {
                        liveSharingClient.updateLocation(
                            sessionId = session.id,
                            token = session.token,
                            latitude = current.point.latitude,
                            longitude = current.point.longitude,
                            bearing = current.bearing,
                            speedKmh = current.speedKmh,
                            etaSeconds = currentEta
                        )
                    }.onFailure {
                        _uiState.value = _uiState.value.copy(statusMessage = "Canlı takip sunucusuna ulaşılamadı; paylaşım durduruldu.")
                        liveShareSession = null
                        _uiState.value = _uiState.value.copy(isLiveSharingActive = false, liveShareUrl = null)
                        break
                    }
                    delay(15_000L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                liveShareSession = null
                _uiState.value = _uiState.value.copy(
                    isLiveSharingActive = false,
                    liveShareUrl = null,
                    statusMessage = "Canlı paylaşım başlatılamadı: ${e.message ?: "sunucu hatası"}"
                )
            }
        }
    }

    fun saveSelectedPlace(title: String, category: String) {
        val dest = _uiState.value.selectedDestination ?: run {
            _uiState.value = _uiState.value.copy(statusMessage = "Kaydedilecek konum seçilmedi.")
            return
        }
        viewModelScope.launch {
            val result = repository.addFavorite(title, dest.displayName, dest.point, category)
            val message = when (result) {
                com.example.haritalar.data.repository.SavedPlaceResult.SAVED -> "$title kaydedildi."
                com.example.haritalar.data.repository.SavedPlaceResult.REPLACED -> "$title güncellendi."
                com.example.haritalar.data.repository.SavedPlaceResult.DUPLICATE -> "Bu adres zaten kayıtlı."
                com.example.haritalar.data.repository.SavedPlaceResult.CAPACITY_REACHED ->
                    "Kayıt kapasitesi dolu. En fazla 500 adres kaydedilebilir."
                com.example.haritalar.data.repository.SavedPlaceResult.INVALID ->
                    "Adres kaydedilemedi; konum veya başlık geçersiz."
            }
            _uiState.value = _uiState.value.copy(statusMessage = message)
        }
    }

    fun addFavorite(title: String, category: String) = saveSelectedPlace(title, category)

    fun removeFavorite(fav: FavoritePlace) { viewModelScope.launch { repository.deleteFavorite(fav) } }
    fun clearStatusMessage() { _uiState.value = _uiState.value.copy(statusMessage = null) }

    fun downloadOfflineMap() {
        val bbox = _uiState.value.currentViewportBbox ?: return
        val currentZoom = _uiState.value.currentZoomLevel.toDouble()
        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
            .include(org.maplibre.android.geometry.LatLng(bbox.south, bbox.west))
            .include(org.maplibre.android.geometry.LatLng(bbox.north, bbox.east))
            .build()
        val maxZoom = kotlin.math.max(currentZoom, 15.0)
        offlineMapManager.downloadRegion(
            styleUrl = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json",
            bounds = bounds, minZoom = currentZoom, maxZoom = maxZoom, pixelRatio = 1.0f
        )
    }

    fun onViewportChanged(bbox: TrafficSignalBoundingBox, zoomLevel: Float) {
        _uiState.value = _uiState.value.copy(currentViewportBbox = bbox, currentZoomLevel = zoomLevel)

        // Premium POI browsing: visible viewport + zoom determines both query radius and refresh density.
        if (_uiState.value.isPoiLayerVisible) {
            val category = _uiState.value.selectedPoiCategory
            if (!PoiViewportPolicy.canQuery(zoomLevel, category)) {
                poiGeneration++
                poiLoadJob?.cancel()
                poiViewportRefreshJob?.cancel()
                lastPoiSearchCenter = null
                lastPoiSearchRadiusMeters = 0
                _uiState.value = _uiState.value.copy(
                    poiList = emptyList(),
                    isLoadingPois = false
                )
            } else {
                val candidateCenter = PoiSearchCenterPolicy.resolve(
                    trackingMode = _uiState.value.mapTrackingMode,
                    liveLocation = currentRoutingLocation()?.point,
                    viewport = bbox
                )
                val candidateRadius = PoiViewportPolicy.searchRadiusMeters(bbox, zoomLevel)
                val previousCenter = lastPoiSearchCenter
                val refreshDistance = PoiViewportPolicy.refreshDistanceMeters(zoomLevel)
                val radiusChanged = lastPoiSearchRadiusMeters == 0 ||
                    kotlin.math.abs(candidateRadius - lastPoiSearchRadiusMeters) >=
                    kotlin.math.max(1_500, (lastPoiSearchRadiusMeters * 0.30).toInt())

                if (candidateCenter != null &&
                    (previousCenter == null ||
                        previousCenter.distanceTo(candidateCenter) >= refreshDistance ||
                        radiusChanged)
                ) {
                    poiViewportRefreshJob?.cancel()
                    poiViewportRefreshJob = viewModelScope.launch {
                        delay(650L)
                        loadPois(_uiState.value.selectedPoiCategory)
                    }
                }
            }
        }

        val signalGeneration = ++trafficSignalGeneration
        trafficSignalJob?.cancel()

        if (!_uiState.value.isTrafficSignalsLayerVisible ||
            zoomLevel < TrafficSignalRepository.MIN_ZOOM_FOR_SIGNALS
        ) {
            _uiState.value = _uiState.value.copy(
                trafficSignals = emptyList(),
                selectedTrafficSignal = null,
                isLoadingTrafficSignals = false
            )
            return
        }

        trafficSignalJob = viewModelScope.launch {
            delay(500L)
            if (signalGeneration != trafficSignalGeneration) return@launch

            _uiState.value = _uiState.value.copy(isLoadingTrafficSignals = true)
            val result = trafficSignalRepository.getTrafficSignalsForViewport(bbox, zoomLevel)
            if (signalGeneration != trafficSignalGeneration ||
                _uiState.value.currentZoomLevel < TrafficSignalRepository.MIN_ZOOM_FOR_SIGNALS
            ) {
                return@launch
            }

            val visibleSignals = trafficSignalRepository.visibleSignalsForViewport(
                signals = when (result) {
                    is TrafficSignalFetchResult.Success -> result.signals
                    is TrafficSignalFetchResult.Error -> result.fallbackSignals
                },
                bbox = bbox,
                zoomLevel = zoomLevel
            )

            _uiState.value = _uiState.value.copy(
                trafficSignals = visibleSignals,
                isLoadingTrafficSignals = false
            )
        }
    }

    fun toggleTrafficSignalsLayer() {
        val visible = !_uiState.value.isTrafficSignalsLayerVisible
        trafficSignalGeneration++
        trafficSignalJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isTrafficSignalsLayerVisible = visible,
            trafficSignals = if (visible) _uiState.value.trafficSignals else emptyList(),
            selectedTrafficSignal = if (visible) _uiState.value.selectedTrafficSignal else null,
            isLoadingTrafficSignals = false
        )
        if (visible) {
            _uiState.value.currentViewportBbox?.let {
                onViewportChanged(it, _uiState.value.currentZoomLevel)
            }
        }
    }
    fun selectTrafficSignal(signal: TrafficSignal) { _uiState.value = _uiState.value.copy(selectedTrafficSignal = signal) }
    fun dismissTrafficSignalDetail() { _uiState.value = _uiState.value.copy(selectedTrafficSignal = null) }
    fun navigateToTrafficSignal(signal: TrafficSignal) { dismissTrafficSignalDetail(); selectDestinationPoint(signal.point, signal.displayTitle) }

    override fun onCleared() {
        super.onCleared()
        routeCalculationJob?.cancel()
        weatherJob?.cancel()
        poiLoadJob?.cancel()
        poiViewportRefreshJob?.cancel()
        trafficSignalJob?.cancel()
        liveShareJob?.cancel()
        liveShareSession = null
        vehicleHeadingManager.stop()
        locationManager.stopLocationUpdates()
        ttsManager.shutdown()
        navigationEngine.stop()
        NavigationForegroundService.stop(getApplication())
    }
}
