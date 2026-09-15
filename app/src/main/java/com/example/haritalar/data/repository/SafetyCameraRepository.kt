package com.example.haritalar.data.repository

import com.example.haritalar.data.network.SafetyCameraService
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.model.SafetyCameraFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos

class SafetyCameraRepository(private val service: SafetyCameraService = SafetyCameraService(), private val nowMs: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private var cached = emptyList<SafetyCamera>()
    private var cachedCenter: GeoPoint? = null
    private var cachedAt = 0L

    suspend fun getNearbyCameras(point: GeoPoint): List<SafetyCamera> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val now = nowMs()
                val moved = cachedCenter?.distanceTo(point) ?: Double.MAX_VALUE
                if (cached.isNotEmpty() && now - cachedAt <= 120_000L && moved < 900.0) {
                    return@withLock cached
                }
                val radius = 5_500.0
                val latDelta = radius / 111_000.0
                val cosLat = cos(point.latitude * PI / 180.0).coerceAtLeast(0.01)
                val lonDelta = radius / (111_000.0 * cosLat)
                val box = SafetyCameraBoundingBox(
                    south = (point.latitude - latDelta).coerceIn(-90.0, 90.0),
                    west = (point.longitude - lonDelta).coerceIn(-180.0, 180.0),
                    north = (point.latitude + latDelta).coerceIn(-90.0, 90.0),
                    east = (point.longitude + lonDelta).coerceIn(-180.0, 180.0)
                )
                when (val result = service.fetchSpeedCamerasInBoundingBox(box, 250)) {
                    is SafetyCameraFetchResult.Success -> {
                        cached = result.cameras
                        cachedCenter = point
                        cachedAt = now
                        result.cameras
                    }
                    is SafetyCameraFetchResult.Error -> {
                        if (cached.isNotEmpty() && now - cachedAt <= 900_000L) {
                            cached.filter { it.point.distanceTo(point) <= 6_000.0 }
                        } else emptyList()
                    }
                }
            }
        }
    }
}
