package com.example.haritalar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.haritalar.data.db.FavoritePlace
import com.example.haritalar.data.db.SearchHistoryItem
import com.example.haritalar.data.repository.NavigationRepository
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
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.TrafficTestResult
import com.example.haritalar.model.TripSummary
import com.example.haritalar.navigation.AppLocationManager
import com.example.haritalar.navigation.CompassHeadingSensor
import com.example.haritalar.navigation.NavigationEngine
import com.example.haritalar.navigation.NavigationProgress
import com.example.haritalar.navigation.UserLocationData
import com.example.haritalar.navigation.VehicleHeadingManager
import com.example.haritalar.navigation.VehicleHeadingState
import com.example.haritalar.voice.TurkishTtsManager
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
    val selectedDestination: SearchResult? = null,
    val routeOptions: List<RouteOption> = emptyList(),
    val selectedRoute: RouteOption? = null,
    val trafficStatusMap: Map<String, Pair<TrafficStatus, List<TrafficSegment>>> = emptyMap(),
    val navigationState: NavigationState = NavigationState.IDLE,
    val navigationProgress: NavigationProgress? = null,
    val cameraMode: CameraMode = CameraMode.TWO_D,
    val mapTrackingMode: MapTrackingMode = MapTrackingMode.FOLLOW_USER,
    val isTrafficLayerVisible: Boolean = true,
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
    val customTomTomKey: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = NavigationRepository(application)
    val locationManager = AppLocationManager(application)
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
    private var trafficRefreshJob: Job? = null
    private var generationCounter = 1L

    init {
        // Observe location updates
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

                        // Clear departure banner once vehicle has proceeded beyond start (~65m)
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

        // Observe compass sensor updates when vehicle is stopped or at low speed (< 4.5 km/h)
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
        locationManager.startLocationUpdates(hasFinePermission)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(350L) // debounce
            _uiState.value = _uiState.value.copy(isSearching = true)
            val focus = _uiState.value.userLocation?.point
            val results = repository.searchPlaces(query, focus)
            _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
        }
    }

    fun selectSearchResult(result: SearchResult) {
        _uiState.value = _uiState.value.copy(
            selectedDestination = result,
            searchQuery = result.name,
            searchResults = emptyList()
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
                point = point,
                type = "point"
            )
            _uiState.value = _uiState.value.copy(
                selectedDestination = result,
                searchQuery = title,
                searchResults = emptyList()
            )
            calculateRoutes(point)
        }
    }

    fun calculateRoutes(destination: GeoPoint) {
        val userLoc = _uiState.value.userLocation?.point ?: GeoPoint(41.0082, 28.9784)
        val genId = ++generationCounter

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingRoutes = true,
                activeGenerationId = genId,
                statusMessage = "Rotalar hesaplanıyor..."
            )

            try {
                val (routes, trafficMap) = repository.calculateRouteAlternatives(userLoc, destination, genId)

                // Stale result guard
                if (genId == _uiState.value.activeGenerationId) {
                    val primaryRoute = routes.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        routeOptions = routes,
                        selectedRoute = primaryRoute,
                        trafficStatusMap = trafficMap,
                        navigationState = if (routes.isNotEmpty()) NavigationState.ROUTE_SELECTION else NavigationState.IDLE,
                        isLoadingRoutes = false,
                        statusMessage = if (routes.isEmpty()) "Rota bulunamadı." else null
                    )
                }
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

    fun selectRoute(route: RouteOption) {
        _uiState.value = _uiState.value.copy(selectedRoute = route)
    }

    fun startNavigation() {
        val route = _uiState.value.selectedRoute ?: return
        val userLoc = _uiState.value.userLocation
        val userPoint = userLoc?.point ?: route.geometry.firstOrNull() ?: GeoPoint(41.0082, 28.9784)
        val currentHeading = _uiState.value.vehicleHeadingState.heading

        val depGuidance = VehicleHeadingManager.buildDepartureGuidance(
            vehicleHeading = currentHeading,
            route = route,
            userPoint = userPoint
        )

        vehicleHeadingManager.start()

        _uiState.value = _uiState.value.copy(
            navigationState = NavigationState.NAVIGATING,
            cameraMode = CameraMode.THREE_D, // 3D perspective during navigation as required!
            mapTrackingMode = MapTrackingMode.FOLLOW_BEARING,
            departureGuidance = depGuidance,
            isWrongWay = depGuidance.isWrongWay
        )

        navigationEngine.startNavigation(route)
        startPeriodicTrafficRefresh()
    }

    fun stopNavigation() {
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
            isWrongWay = false
        )
    }

    private fun handleOffRoute(currentPoint: GeoPoint) {
        vehicleHeadingManager.onReroute()
        val dest = _uiState.value.selectedDestination?.point ?: return
        val genId = ++generationCounter

        viewModelScope.launch {
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
                        statusMessage = null
                    )
                    navigationEngine.updateRoute(newRoute)
                }
            } catch (e: Exception) {
                // Keep navigating on current route if reroute fails
                _uiState.value = _uiState.value.copy(
                    navigationState = NavigationState.NAVIGATING,
                    statusMessage = "Yeniden rota oluşturulamadı, mevcut rotaya dönün."
                )
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
        val focus = _uiState.value.userLocation?.point ?: return
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
        val testPoint = _uiState.value.userLocation?.point
            ?: _uiState.value.selectedDestination?.point
            ?: GeoPoint(41.0082, 28.9784) // Istanbul default coordinate

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
                delay(60_000L) // 60s minimum interval
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
        val center = _uiState.value.userLocation?.point ?: GeoPoint(41.0082, 28.9784)
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

    override fun onCleared() {
        super.onCleared()
        vehicleHeadingManager.stop()
        locationManager.stopLocationUpdates()
        ttsManager.shutdown()
        navigationEngine.stop()
    }
}
