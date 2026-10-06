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
 * 2) navigation prefetch area, for warnings up to 5 km even when the map is tightly zoomed
 *
 * Last successful data is retained if a provider/mirror fails.
 */
class SafetyCameraLayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SafetyCameraRepository(application)
    private val _cameras = MutableStateFlow<List<SafetyCamera>>(emptyList())
    val cameras: StateFlow<List<SafetyCamera>> = _cameras.asStateFlow()

    private var viewportCameras: List<SafetyCamera> = emptyList()
    private var navigationCameras: List<SafetyCamera> = emptyList()

    private var viewportJob: Job? = null
    private var navigationJob: Job? = null
    private var lastViewportRequest: SafetyCameraBoundingBox? = null
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

    fun prefetchForNavigation(
        center: GeoPoint,
        radiusMeters: Double = SafetyCameraAreaPolicy.NAVIGATION_PREFETCH_RADIUS_METERS
    ) {
        if (!SafetyCameraAreaPolicy.shouldRefresh(lastNavigationCenter, center) && navigationCameras.isNotEmpty()) {
            return
        }

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
        _cameras.value = (navigationCameras + viewportCameras)
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
        super.onCleared()
    }
}
