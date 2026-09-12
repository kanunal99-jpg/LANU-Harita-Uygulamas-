package com.example.haritalar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haritalar.data.network.SafetyCameraService
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.model.SafetyCameraFetchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Viewport-scoped speed-camera state. Keeps the last successful OSM result on failure. */
class SafetyCameraLayerViewModel(
    private val service: SafetyCameraService = SafetyCameraService()
) : ViewModel() {
    private val _cameras = MutableStateFlow<List<SafetyCamera>>(emptyList())
    val cameras: StateFlow<List<SafetyCamera>> = _cameras.asStateFlow()

    private var requestJob: Job? = null
    private var lastRequest: SafetyCameraBoundingBox? = null

    fun onViewportChanged(bbox: SafetyCameraBoundingBox, zoomLevel: Float) {
        if (!bbox.isValid() || zoomLevel < 12f) return
        if (lastRequest?.let { sameArea(it, bbox) } == true && requestJob?.isActive == true) return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            delay(400)
            lastRequest = bbox
            when (val result = service.fetchSpeedCamerasInBoundingBox(bbox)) {
                is SafetyCameraFetchResult.Success -> _cameras.value = result.cameras
                is SafetyCameraFetchResult.Error -> {
                    if (result.fallbackCameras.isNotEmpty()) _cameras.value = result.fallbackCameras
                }
            }
        }
    }

    private fun sameArea(a: SafetyCameraBoundingBox, b: SafetyCameraBoundingBox): Boolean =
        kotlin.math.abs(a.south - b.south) < 0.002 &&
            kotlin.math.abs(a.west - b.west) < 0.002 &&
            kotlin.math.abs(a.north - b.north) < 0.002 &&
            kotlin.math.abs(a.east - b.east) < 0.002

    override fun onCleared() {
        requestJob?.cancel()
        super.onCleared()
    }
}
