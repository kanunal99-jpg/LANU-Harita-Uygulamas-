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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Safety-camera state with two independent scopes:
 * 1) visible viewport, for map rendering
 * 2) complete route prefetch, for pre-drive route-kilometer briefing
 * 3) near-vehicle navigation prefetch, for the final 5 km voice cascade
 *
 * Last successful data is retained if a provider/mirror fails.
 */
class SafetyCameraLayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SafetyCameraRepository(application)
    private val _cameras = MutableStateFlow<List<SafetyCamera>>(emptyList())
    val cameras: StateFlow<List<SafetyCamera>> = _cameras.asStateFlow()

    private val _routeCameras = MutableStateFlow<List<SafetyCamera>>(emptyList())
    val routeCameras: StateFlow<List<SafetyCamera>> = _routeCameras.asStateFlow()

    private val _isRoutePrefetching = MutableStateFlow(false)
    val isRoutePrefetching: StateFlow<Boolean> = _isRoutePrefetching.asStateFlow()

    private val _completedRoutePrefetchRouteId = MutableStateFlow<String?>(null)
    val completedRoutePrefetchRouteId: StateFlow<String?> = _completedRoutePrefetchRouteId.asStateFlow()

    private var viewportCameras: List<SafetyCamera> = emptyList()
    private var navigationCameras: List<SafetyCamera> = emptyList()

    private var viewportJob: Job? = null
    private var routeJob: Job? = null
    private var navigationJob: Job? = null
    private var lastViewportRequest: SafetyCameraBoundingBox? = null
    private var lastRouteFingerprint: String? = null
    private var routePrefetchGeneration: Long = 0L
    private var lastNavigationCenter: GeoPoint? = null

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

    fun prefetchForRoute(routeId: String, route: List<GeoPoint>) {
        if (route.size < 2) {
            clearRoutePrefetch()
            return
        }

        val fingerprint = buildString {
            append(routeId)
            append(':')
            append(route.size)
            append(':')
            append(route.first().latitude)
            append(',')
            append(route.first().longitude)
            append(':')
            append(route.last().latitude)
            append(',')
            append(route.last().longitude)
        }
        if (
            fingerprint == lastRouteFingerprint &&
            _completedRoutePrefetchRouteId.value == routeId
        ) {
            return
        }

        routeJob?.cancel()
        val generation = ++routePrefetchGeneration
        lastRouteFingerprint = fingerprint
        _completedRoutePrefetchRouteId.value = null
        _routeCameras.value = emptyList()
        _isRoutePrefetching.value = true
        publishMerged()

        routeJob = viewModelScope.launch {
            val merged = linkedMapOf<Long, SafetyCamera>()
            try {
                val centers = SafetyCameraAreaPolicy.routePrefetchCenters(route)
                for (center in centers) {
                    val bbox = SafetyCameraAreaPolicy.boundingBoxAround(
                        center = center,
                        radiusMeters = SafetyCameraAreaPolicy.ROUTE_PREFETCH_RADIUS_METERS
                    )
                    when (val result = repository.get(bbox, maxCameras = 500)) {
                        is SafetyCameraFetchResult.Success -> {
                            result.cameras.forEach { merged[it.id] = it }
                        }
                        is SafetyCameraFetchResult.Error -> {
                            result.fallbackCameras.forEach { merged[it.id] = it }
                        }
                    }
                }
                if (generation != routePrefetchGeneration) return@launch
                _routeCameras.value = merged.values.toList()
                _completedRoutePrefetchRouteId.value = routeId
                publishMerged()
            } finally {
                if (generation == routePrefetchGeneration) {
                    _isRoutePrefetching.value = false
                }
            }
        }
    }

    fun clearRoutePrefetch() {
        routeJob?.cancel()
        routeJob = null
        routePrefetchGeneration++
        lastRouteFingerprint = null
        _completedRoutePrefetchRouteId.value = null
        _routeCameras.value = emptyList()
        _isRoutePrefetching.value = false
        publishMerged()
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

    fun clearNavigationPrefetch() {
        navigationJob?.cancel()
        navigationJob = null
        lastNavigationCenter = null
        navigationCameras = emptyList()
        publishMerged()
    }

    private fun publishMerged() {
        _cameras.value = (_routeCameras.value + navigationCameras + viewportCameras)
            .distinctBy { it.id }
    }

    private fun sameArea(a: SafetyCameraBoundingBox, b: SafetyCameraBoundingBox): Boolean =
        kotlin.math.abs(a.south - b.south) < 0.002 &&
            kotlin.math.abs(a.west - b.west) < 0.002 &&
            kotlin.math.abs(a.north - b.north) < 0.002 &&
            kotlin.math.abs(a.east - b.east) < 0.002

    override fun onCleared() {
        viewportJob?.cancel()
        routeJob?.cancel()
        navigationJob?.cancel()
        super.onCleared()
    }
}
