package com.example.haritalar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.haritalar.data.network.SafetyCameraAreaPolicy
import com.example.haritalar.data.repository.SafetyCameraRepository
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.model.SafetyCameraFetchResult
import com.example.haritalar.model.RouteOption
import com.example.haritalar.navigation.RouteDataCoverage
import com.example.haritalar.navigation.RouteSafetyCameraCoverage
import com.example.haritalar.navigation.RouteSafetyCameraCoveragePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Safety-camera state with two independent scopes:
 * 1) visible viewport, for map rendering
 * 2) navigation prefetch area, for speed-adaptive warnings up to 10 km even when the map is tightly zoomed
 *
 * Last successful data is retained if a provider/mirror fails.
 */
class SafetyCameraLayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SafetyCameraRepository(application)
    private val _cameras = MutableStateFlow<List<SafetyCamera>>(emptyList())
    val cameras: StateFlow<List<SafetyCamera>> = _cameras.asStateFlow()
    private val _routeCoverage = MutableStateFlow<RouteSafetyCameraCoverage?>(null)
    val routeCoverage: StateFlow<RouteSafetyCameraCoverage?> = _routeCoverage.asStateFlow()

    private var viewportCameras: List<SafetyCamera> = emptyList()
    private var navigationCameras: List<SafetyCamera> = emptyList()
    private var routeCameras: List<SafetyCamera> = emptyList()

    private var viewportJob: Job? = null
    private var navigationJob: Job? = null
    private var routeJob: Job? = null
    private var lastViewportRequest: SafetyCameraBoundingBox? = null
    private var lastNavigationCenter: GeoPoint? = null
    private var lastRouteId: String? = null

    fun onViewportChanged(bbox: SafetyCameraBoundingBox, zoomLevel: Float) {
        if (!bbox.isValid() || zoomLevel < 12f) return
        if (lastViewportRequest?.let { sameArea(it, bbox) } == true) return

        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            delay(400)
            when (val result = repository.get(bbox, maxCameras = 500)) {
                is SafetyCameraFetchResult.Success -> {
                    lastViewportRequest = bbox
                    viewportCameras = result.cameras
                    publishMerged()
                }
                is SafetyCameraFetchResult.Error -> {
                    if (result.fallbackCameras.isNotEmpty()) {
                        viewportCameras = result.fallbackCameras
                        publishMerged()
                    }
                }
            }
        }
    }

    fun prefetchForNavigation(
        center: GeoPoint,
        radiusMeters: Double = SafetyCameraAreaPolicy.NAVIGATION_PREFETCH_RADIUS_METERS
    ) {
        if (!SafetyCameraAreaPolicy.shouldRefresh(lastNavigationCenter, center) && navigationCameras.isNotEmpty()) {
            return
        }

        lastNavigationCenter = center
        navigationJob?.cancel()
        navigationJob = viewModelScope.launch {
            val bbox = SafetyCameraAreaPolicy.boundingBoxAround(center, radiusMeters)
            when (val result = repository.get(bbox, maxCameras = 500)) {
                is SafetyCameraFetchResult.Success -> {
                    lastNavigationCenter = center
                    navigationCameras = result.cameras
                    publishMerged()
                }
                is SafetyCameraFetchResult.Error -> {
                    lastNavigationCenter = null
                    if (result.fallbackCameras.isNotEmpty()) {
                        navigationCameras = result.fallbackCameras
                        publishMerged()
                    }
                }
            }
        }
    }

    fun prefetchForRoute(route: RouteOption) {
        if (route.geometry.size < 2) {
            clearRoutePrefetch()
            _routeCoverage.value = RouteSafetyCameraCoverage(
                status = RouteDataCoverage.UNAVAILABLE,
                cameras = emptyList(),
                source = "Rota geometrisi",
                fetchedAtMillis = System.currentTimeMillis(),
                sampleCount = 0,
                successfulSampleCount = 0,
                maxSampleGapMeters = 0.0,
                note = "Rota geometrisi kamera kapsaması için yetersiz."
            )
            return
        }
        if (lastRouteId == route.routeId && _routeCoverage.value != null) return

        routeJob?.cancel()
        lastRouteId = route.routeId
        routeCameras = emptyList()
        _routeCoverage.value = null
        publishMerged()

        routeJob = viewModelScope.launch {
            val plan = RouteSafetyCameraCoveragePolicy.plan(route.geometry)
            if (plan == null) {
                _routeCoverage.value = RouteSafetyCameraCoverage(
                    status = RouteDataCoverage.UNAVAILABLE,
                    cameras = emptyList(),
                    source = "Rota geometrisi",
                    fetchedAtMillis = System.currentTimeMillis(),
                    sampleCount = 0,
                    successfulSampleCount = 0,
                    maxSampleGapMeters = 0.0,
                    note = "Rota kamera örnekleme planı oluşturulamadı."
                )
                return@launch
            }

            val collected = mutableListOf<SafetyCamera>()
            var successfulSamples = 0
            var usedCache = false

            for (point in plan.points) {
                when (
                    val result = repository.get(
                        RouteSafetyCameraCoveragePolicy.boundingBox(point),
                        maxCameras = 500
                    )
                ) {
                    is SafetyCameraFetchResult.Success -> {
                        successfulSamples += 1
                        usedCache = usedCache || result.fromCache
                        collected += result.cameras
                    }
                    is SafetyCameraFetchResult.Error -> {
                        collected += result.fallbackCameras
                    }
                }
            }

            if (lastRouteId != route.routeId) return@launch

            val filtered = RouteSafetyCameraCoveragePolicy.filterToRoute(
                route = route.geometry,
                candidates = collected.distinctBy { it.id }
            )
            val status = when {
                successfulSamples == 0 -> RouteDataCoverage.UNAVAILABLE
                successfulSamples < plan.points.size || !plan.fullCoverage -> RouteDataCoverage.PARTIAL
                else -> RouteDataCoverage.VERIFIED
            }
            val source = when {
                successfulSamples == 0 -> "OSM kamera zinciri"
                usedCache -> "OpenStreetMap + kalıcı LKG cache"
                else -> "OpenStreetMap / Overpass"
            }
            val note = when (status) {
                RouteDataCoverage.VERIFIED ->
                    "Rota kamera örneklerinin tamamı doğrulandı."
                RouteDataCoverage.PARTIAL ->
                    "$successfulSamples/${plan.points.size} kamera örneği doğrulandı veya uzun rota örnekleme sınırına ulaştı."
                RouteDataCoverage.UNAVAILABLE ->
                    "Rota kamera kaynakları doğrulanamadı; kamera sayısı uydurulmuyor."
            }

            routeCameras = filtered
            _routeCoverage.value = RouteSafetyCameraCoverage(
                status = status,
                cameras = filtered,
                source = source,
                fetchedAtMillis = System.currentTimeMillis(),
                sampleCount = plan.points.size,
                successfulSampleCount = successfulSamples,
                maxSampleGapMeters = plan.maxSampleGapMeters,
                note = note
            )
            publishMerged()
        }
    }

    fun clearRoutePrefetch() {
        routeJob?.cancel()
        routeJob = null
        lastRouteId = null
        routeCameras = emptyList()
        _routeCoverage.value = null
        publishMerged()
    }

    fun clearNavigationPrefetch() {
        navigationJob?.cancel()
        navigationJob = null
        lastNavigationCenter = null
        navigationCameras = emptyList()
        publishMerged()
    }

    private fun publishMerged() {
        _cameras.value = (routeCameras + navigationCameras + viewportCameras)
            .distinctBy { it.id }
    }

    private fun sameArea(a: SafetyCameraBoundingBox, b: SafetyCameraBoundingBox): Boolean =
        kotlin.math.abs(a.south - b.south) < 0.002 &&
            kotlin.math.abs(a.west - b.west) < 0.002 &&
            kotlin.math.abs(a.north - b.north) < 0.002 &&
            kotlin.math.abs(a.east - b.east) < 0.002

    override fun onCleared() {
        viewportJob?.cancel()
        navigationJob?.cancel()
        routeJob?.cancel()
        super.onCleared()
    }
}
