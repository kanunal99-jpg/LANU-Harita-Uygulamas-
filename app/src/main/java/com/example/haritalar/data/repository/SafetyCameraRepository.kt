package com.example.haritalar.data.repository

import android.content.Context
import android.util.Log
import com.example.haritalar.data.cache.SafetyCameraCache
import com.example.haritalar.data.network.SafetyCameraService
import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.model.SafetyCameraFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resilient fixed-camera chain:
 * Overpass primary -> Overpass mirrors -> persistent last-known-good cache ->
 * explicit error with a safe empty fallback.
 */
class SafetyCameraRepository(
    context: Context,
    private val service: SafetyCameraService = SafetyCameraService(),
    private val cache: SafetyCameraCache = SafetyCameraCache(context)
) {
    companion object {
        private const val TAG = "SafetyCameraRepository"
    }

    suspend fun get(
        bbox: SafetyCameraBoundingBox,
        maxCameras: Int = 500
    ): SafetyCameraFetchResult {
        return when (val result = service.fetchSpeedCamerasInBoundingBox(bbox, maxCameras)) {
            is SafetyCameraFetchResult.Success -> {
                withContext(Dispatchers.IO) {
                    cache.save(bbox, result.cameras)
                }
                result
            }
            is SafetyCameraFetchResult.Error -> {
                val cached = withContext(Dispatchers.IO) {
                    cache.loadFor(bbox)
                }
                if (cached.isNotEmpty()) {
                    Log.w(TAG, "Network mirrors failed; using ${cached.size} cached safety cameras")
                    SafetyCameraFetchResult.Success(
                        cameras = cached,
                        fromCache = true,
                        endpointUsed = "persistent-last-known-good"
                    )
                } else {
                    Log.e(TAG, "Safety-camera chain exhausted without usable cache: ${result.message}")
                    result.copy(fallbackCameras = emptyList())
                }
            }
        }
    }
}
