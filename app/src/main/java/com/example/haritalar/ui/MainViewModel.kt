package com.example.haritalar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.haritalar.data.db.FavoritePlace
import com.example.haritalar.data.db.SearchHistoryItem
import com.example.haritalar.data.repository.NavigationRepository
import com.example.haritalar.data.repository.TrafficSignalRepository
import com.example.haritalar.model.CameraMode
import com.example.haritalar.model.DepartureGuidance
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.MapTrackingMode
import com.example.haritalar.model.NavigationState
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.RouteOption
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
import com.example.haritalar.navigation.NavigationEngine
import com.example.haritalar.navigation.NavigationLocationPolicy
import com.example.haritalar.navigation.NavigationProgress
import com.example.haritalar.navigation.UserLocationData
import com.example.haritalar.navigation.VehicleHeadingManager
import com.example.haritalar.navigation.VehicleHeadingState
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
    val currentViewportBbox: com.example.haritalar.model.TrafficSignalBoundingBox? = null,
    val currentZoomLevel: Float = 0f,
    val isWeatherLayerVisible: Boolean = true,
    val routeWeather: List<com.example.haritalar.model.WeatherCondition> = emptyList(),
    val approachingWeather: com.example.haritalar.model.WeatherCondition? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = NavigationRepository(application)
    val locationManager = AppLocationManager(application)
    val offlineMapManager = com.example.haritalar.data.offline.OfflineMapManager(application)
    val weatherRepository = com.example.haritalar.data.weather.WeatherRepository()
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
    private var trafficRefreshJob: Job? = null
    private var trafficSignalJob: Job? = null
    private var generationCounter = 1L

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

    fun onSearchFocusChanged(focused: Boolean) {
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
        _uiState.value = _uiState.value.copy(searchQuery = query)
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
        if (q.isNotBlank()) {
            onSearchQueryChanged(q)
        }
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
        _uiState.value = _uiState.value.copy(
            selectedDestination = result,
            searchQuery = result.name,
            searchResults = emptyList(),
            searchStatus = com.example.haritalar.model.SearchUiStatus.IDLE,
            isDestinationCardVisible = true
        )
        calculateRoutes(result.point)
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
            _uiState.value = _uiState.value.copy(
                selectedDestination = result,
                searchQuery = title,
                searchResults = emptyList(),
                searchStatus = com.example.haritalar.model.SearchUiStatus.IDLE,
                isDestinationCardVisible = true
            )
            calculateRoutes(point)
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
                statusMessage = "Gerçek GPS konumu bekleniyor. Konum alınmadan rota hesaplanamaz."
            )
            return
        }

        val genId = ++generationCounter
        routeCalculationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingRoutes = true,
                activeGenerationId = genId,
                statusMessage = "Rotalar hesaplanıyor..."
            )

            try {
                val (routes, trafficMap) = repository.calculateRouteAlternatives(userLocation.point, destination, genId)
                if (genId != _uiState.value.activeGenerationId) return@launch

                val primaryRoute = routes.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    routeOptions = routes,
                    selectedRoute = primaryRoute,
                    trafficStatusMap = trafficMap,
                    navigationState = if (routes.isNotEmpty()) NavigationState.ROUTE_SELECTION else NavigationState.IDLE,
                    isLoadingRoutes = false,
                    statusMessage = if (routes.isEmpty()) "Rota bulunamadı." else null
                )
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

    /**
     * Destination-card shortcut: calculate the route and start only after a real
     * route result has arrived. This removes the previous calculate/start race.
     */
    fun startNavigationTo(destination: GeoPoint) {
        routeCalculationJob?.cancel()
        val initialLocation = currentRoutingLocation()
        if (initialLocation == null) {
            _uiState.value = _uiState.value.copy(
                routeOptions = emptyList(),
                selectedRoute = null,
                isLoadingRoutes = false,
                statusMessage = "Gerçek GPS konumu bekleniyor. Navigasyon başlatılamaz."
            )
            return
        }

        val genId = ++generationCounter
        routeCalculationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingRoutes = true,
                activeGenerationId = genId,
                routeOptions = emptyList(),
                selectedRoute = null,
                statusMessage = "Rota hesaplanıyor..."
            )

            try {
                val (routes, trafficMap) = repository.calculateRouteAlternatives(initialLocation.point, destination, genId)
                if (genId != _uiState.value.activeGenerationId) return@launch

                val route = routes.firstOrNull()
                if (route == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRoutes = false,
                        routeOptions = emptyList(),
                        selectedRoute = null,
                        navigationState = NavigationState.IDLE,
                        statusMessage = "Rota bulunamadı."
                    )
                    return@launch
                }

                val latestLocation = currentRoutingLocation()
                if (latestLocation == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRoutes = false,
                        routeOptions = routes,
                        selectedRoute = route,
                        navigationState = NavigationState.ROUTE_SELECTION,
                        statusMessage = "GPS konumu güncelliğini kaybetti. Navigasyon başlatılmadı."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    routeOptions = routes,
                    selectedRoute = route,
                    trafficStatusMap = trafficMap,
                    isLoadingRoutes = false,
                    navigationState = NavigationState.ROUTE_SELECTION,
                    statusMessage = null
                )
                startNavigationInternal(route, latestLocation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (genId == _uiState.value.activeGenerationId) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingRoutes = false,
                        navigationState = NavigationState.IDLE,
                        statusMessage = "Rota hesaplama hatası: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectRoute(route: RouteOption) {
        _uiState.value = _uiState.value.copy(selectedRoute = route)
        fetchWeatherForRoute(route)
    }

    private fun fetchWeatherForRoute(route: RouteOption) {
        viewModelScope.launch {
            val weather = weatherRepository.getRouteWeather(route)
            _uiState.value = _uiState.value.copy(routeWeather = weather)
            checkWeatherProximity()
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
            _uiState.value = _uiState.value.copy(statusMessage = "Gerçek GPS konumu alınamıyor. Navigasyon başlatılamadı.")
            return
        }

        startNavigationInternal(route, userLocation)
    }

    private fun startNavigationInternal(route: RouteOption, userLocation: UserLocationData) {
        val currentHeading = _uiState.value.vehicleHeadingState.heading
        val depGuidance = VehicleHeadingManager.buildDepartureGuidance(
            vehicleHeading = currentHeading,
            route = route,
            userPoint = userLocation.point
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
        startPeriodicTrafficRefresh()
    }

    fun stopNavigation() {
        routeCalculationJob?.cancel()
        routeCalculationJob = null
        vehicleHeadingManager.stop()
        vehicleHeadingManager.resetSession()
        navigationEngine.stop()
        locationManager.stopSimulation()
        trafficRefreshJob?.cancel()
        ttsManager.stop()

        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.IDLE,
            navigationProgress = null,
            selectedRoute = null,
            routeOptions = emptyList(),
            selectedDestination = null,
            searchQuery = "",
            cameraMode = CameraMode.TWO_D,
            mapTrackingMode = MapTrackingMode.FOLLOW_USER,
            isSimulationActive = false,
            statusMessage = null,
            isSearchAlongRouteOpen = false,
            alongRoutePois = emptyList(),
            isLoadingAlongRoute = false,
            departureGuidance = null,
            isWrongWay = false,
            isLoadingRoutes = false
        )
    }

    private fun handleOffRoute(currentPoint: GeoPoint) {
        routeCalculationJob?.cancel()
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
                        routeOptions = routes,
                        selectedRoute = newRoute,
                        trafficStatusMap = trafficMap,
                        navigationState = NavigationState.NAVIGATING,
                        statusMessage = null,
                        isLoadingRoutes = false
                    )
                    navigationEngine.updateRoute(newRoute)
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
        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.ARRIVED,
            tripSummary = summary,
            statusMessage = "Hedefinize ulaştınız!"
        )
    }

    fun dismissTripSummary() {
        stopNavigation()
        _uiState.value = _uiState.value.copy(tripSummary = null)
    }

    fun openSearchAlongRoute() {
        _uiState.value = _uiState.value.copy(isSearchAlongRouteOpen = true)
    }

    fun closeSearchAlongRoute() {
        _uiState.value = _uiState.value.copy(
            isSearchAlongRouteOpen = false,
            alongRoutePois = emptyList(),
            isLoadingAlongRoute = false
        )
    }

    fun searchAlongRouteCategory(category: PoiCategory) {
        val focus = currentRoutingLocation()?.point ?: run {
            _uiState.value = _uiState.value.copy(statusMessage = "İlgi noktalarını aramak için gerçek konum gerekli.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAlongRoute = true)
            val pois = repository.fetchPois(focus, category)
            _uiState.value = _uiState.value.copy(
                alongRoutePois = pois,
                isLoadingAlongRoute = false
            )
        }
    }

    fun selectAlongRoutePoi(poi: PoiItem) {
        closeSearchAlongRoute()
        val dest = SearchResult(
            id = poi.id,
            name = poi.name,
            displayName = poi.address ?: poi.name,
            point = poi.point,
            type = "poi"
        )
        selectSearchResult(dest)
    }

    fun openTrafficInspector() {
        _uiState.value = _uiState.value.copy(
            isTrafficInspectorOpen = true,
            customTomTomKey = repository.getEffectiveTomTomKey()
        )
    }

    fun closeTrafficInspector() {
        _uiState.value = _uiState.value.copy(isTrafficInspectorOpen = false)
    }

    fun saveCustomTomTomKey(key: String) {
        repository.setCustomTomTomKey(key)
        _uiState.value = _uiState.value.copy(
            customTomTomKey = key.trim(),
            statusMessage = "TomTom API anahtarı güncellendi."
        )
    }

    fun testTrafficConnection() {
        val testPoint = currentRoutingLocation()?.point
            ?: _uiState.value.selectedDestination?.point
            ?: run {
                _uiState.value = _uiState.value.copy(
                    isTestingTraffic = false,
                    trafficTestResult = null,
                    statusMessage = "Trafik bağlantı testi için gerçek konum veya hedef gerekli."
                )
                return
            }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingTraffic = true, trafficTestResult = null)
            val result = repository.testTomTomTraffic(testPoint)
            _uiState.value = _uiState.value.copy(
                isTestingTraffic = false,
                trafficTestResult = result
            )
        }
    }

    private fun startPeriodicTrafficRefresh() {
        trafficRefreshJob?.cancel()
        trafficRefreshJob = viewModelScope.launch {
            while (_uiState.value.navigationState == NavigationState.NAVIGATING) {
                delay(60_000L)
                val currentRoute = _uiState.value.selectedRoute ?: break
                val genId = _uiState.value.activeGenerationId

                val result = repository.trafficCoordinator.requestRefresh(
                    routes = listOf(currentRoute),
                    generationId = genId
                )

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

    fun set2DMode() {
        _uiState.value = _uiState.value.copy(cameraMode = CameraMode.TWO_D)
    }

    fun set3DMode() {
        _uiState.value = _uiState.value.copy(cameraMode = CameraMode.THREE_D)
    }

    fun toggleTrafficLayer() {
        _uiState.value = _uiState.value.copy(isTrafficLayerVisible = !_uiState.value.isTrafficLayerVisible)
    }

    fun toggleSafetyCamerasLayer() {
        _uiState.value = _uiState.value.copy(isSafetyCamerasLayerVisible = !_uiState.value.isSafetyCamerasLayerVisible)
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
        } else {
            _uiState.value = _uiState.value.copy(approachingWeather = null)
        }
    }

    private var lastAnnouncedCameraId: Long? = null

    fun checkSafetyCameraProximity(cameras: List<com.example.haritalar.model.SafetyCamera>) {
        if (!_uiState.value.isSafetyCamerasLayerVisible) {
            _uiState.value = _uiState.value.copy(approachingCamera = null)
            return
        }
        val userLoc = currentRoutingLocation()?.point ?: return

        val nearestCamera = cameras.minByOrNull { it.point.distanceTo(userLoc) }
        val distance = nearestCamera?.point?.distanceTo(userLoc) ?: Double.MAX_VALUE

        if (nearestCamera != null && distance <= 500.0) {
            _uiState.value = _uiState.value.copy(approachingCamera = nearestCamera)
            if (lastAnnouncedCameraId != nearestCamera.id) {
                lastAnnouncedCameraId = nearestCamera.id
                val speedMsg = nearestCamera.maxSpeed?.takeIf { it.isNotBlank() }?.let { " $it kilometre hız sınırı," } ?: ""
                ttsManager.speak("Dikkat. İleride$speedMsg radar noktası var.")
            }
        } else {
            _uiState.value = _uiState.value.copy(approachingCamera = null)
        }
    }

    fun togglePoiLayer() {
        val newVis = !_uiState.value.isPoiLayerVisible
        _uiState.value = _uiState.value.copy(isPoiLayerVisible = newVis)
        if (newVis && _uiState.value.poiList.isEmpty()) {
            loadPois(_uiState.value.selectedPoiCategory)
        }
    }

    fun selectPoiCategory(category: PoiCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedPoiCategory = category,
            isPoiLayerVisible = true
        )
        loadPois(category)
    }

    fun loadPois(category: PoiCategory?) {
        val center = currentRoutingLocation()?.point ?: run {
            _uiState.value = _uiState.value.copy(
                statusMessage = "İlgi noktalarını yüklemek için gerçek konum gerekli."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusMessage = "İlgi noktaları yükleniyor...")
            val pois = repository.fetchPois(center, category)
            _uiState.value = _uiState.value.copy(
                poiList = pois,
                statusMessage = if (pois.isEmpty()) "Bu bölgede ilgi noktası bulunamadı." else null
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
        _uiState.value = _uiState.value.copy(
            mapTrackingMode = if (_uiState.value.navigationState == NavigationState.NAVIGATING) {
                MapTrackingMode.FOLLOW_BEARING
            } else {
                MapTrackingMode.FOLLOW_USER
            }
        )
    }

    fun setFreeTrackingMode() {
        _uiState.value = _uiState.value.copy(mapTrackingMode = MapTrackingMode.FREE)
    }

    fun toggleSimulation() {
        val currentRoute = _uiState.value.selectedRoute ?: return
        val isSim = !_uiState.value.isSimulationActive
        _uiState.value = _uiState.value.copy(isSimulationActive = isSim)

        if (isSim) {
            locationManager.startRouteSimulation(currentRoute.geometry) { _, _ ->
                // Progress handled by userLocation observer
            }
        } else {
            locationManager.stopSimulation()
        }
    }

    fun toggleLiveSharing() {
        val isSharing = _uiState.value.isLiveSharingActive
        if (isSharing) {
            _uiState.value = _uiState.value.copy(
                isLiveSharingActive = false,
                liveShareUrl = null
            )
        } else {
            val uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8)
            _uiState.value = _uiState.value.copy(
                isLiveSharingActive = true,
                liveShareUrl = "https://haritalar.example.com/share/$uniqueId"
            )
        }
    }

    fun addFavorite(title: String, category: String) {
        val dest = _uiState.value.selectedDestination ?: return
        viewModelScope.launch {
            repository.addFavorite(title, dest.displayName, dest.point, category)
            _uiState.value = _uiState.value.copy(statusMessage = "$title favorilere eklendi.")
        }
    }

    fun removeFavorite(fav: FavoritePlace) {
        viewModelScope.launch {
            repository.deleteFavorite(fav)
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

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
            bounds = bounds,
            minZoom = currentZoom,
            maxZoom = maxZoom,
            pixelRatio = 1.0f
        )
    }

    fun onViewportChanged(bbox: TrafficSignalBoundingBox, zoomLevel: Float) {
        _uiState.value = _uiState.value.copy(
            currentViewportBbox = bbox,
            currentZoomLevel = zoomLevel
        )
        if (!_uiState.value.isTrafficSignalsLayerVisible) return
        if (zoomLevel < TrafficSignalRepository.MIN_ZOOM_FOR_SIGNALS) {
            return
        }

        trafficSignalJob?.cancel()
        trafficSignalJob = viewModelScope.launch {
            delay(500L)
            _uiState.value = _uiState.value.copy(isLoadingTrafficSignals = true)
            val result = trafficSignalRepository.getTrafficSignalsForViewport(bbox, zoomLevel)
            when (result) {
                is TrafficSignalFetchResult.Success -> {
                    val merged = trafficSignalRepository.deduplicateSignals(
                        _uiState.value.trafficSignals + result.signals
                    )
                    _uiState.value = _uiState.value.copy(
                        trafficSignals = merged,
                        isLoadingTrafficSignals = false
                    )
                }
                is TrafficSignalFetchResult.Error -> {
                    if (result.fallbackSignals.isNotEmpty()) {
                        val merged = trafficSignalRepository.deduplicateSignals(
                            _uiState.value.trafficSignals + result.fallbackSignals
                        )
                        _uiState.value = _uiState.value.copy(
                            trafficSignals = merged,
                            isLoadingTrafficSignals = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoadingTrafficSignals = false)
                    }
                }
            }
        }
    }

    fun toggleTrafficSignalsLayer() {
        val next = !_uiState.value.isTrafficSignalsLayerVisible
        _uiState.value = _uiState.value.copy(isTrafficSignalsLayerVisible = next)
    }

    fun selectTrafficSignal(signal: TrafficSignal) {
        _uiState.value = _uiState.value.copy(selectedTrafficSignal = signal)
    }

    fun dismissTrafficSignalDetail() {
        _uiState.value = _uiState.value.copy(selectedTrafficSignal = null)
    }

    fun navigateToTrafficSignal(signal: TrafficSignal) {
        dismissTrafficSignalDetail()
        selectDestinationPoint(signal.point, signal.displayTitle)
    }

    override fun onCleared() {
        super.onCleared()
        routeCalculationJob?.cancel()
        vehicleHeadingManager.stop()
        locationManager.stopLocationUpdates()
        ttsManager.shutdown()
        navigationEngine.stop()
    }
}
