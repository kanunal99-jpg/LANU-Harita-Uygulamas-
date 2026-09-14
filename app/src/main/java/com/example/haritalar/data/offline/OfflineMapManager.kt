package com.example.haritalar.data.offline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

class OfflineMapManager(private val context: Context) {
    // Do not touch MapLibre OfflineManager during ViewModel construction.
    // It is initialized only when an offline operation is actually requested.
    private val offlineManager: OfflineManager by lazy(LazyThreadSafetyMode.NONE) {
        OfflineManager.getInstance(context)
    }

    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage.asStateFlow()

    fun downloadRegion(
        styleUrl: String,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
        pixelRatio: Float
    ) {
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            bounds,
            minZoom,
            maxZoom,
            pixelRatio
        )

        val metadata = "{\"name\":\"OfflineRegion\"}".toByteArray()

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    _downloadMessage.value = "İndirme başlatıldı..."
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)

                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val percentage = if (status.requiredResourceCount > 0) {
                                (100.0 * status.completedResourceCount / status.requiredResourceCount).toFloat()
                            } else {
                                0.0f
                            }

                            if (status.isComplete) {
                                _downloadMessage.value = "İndirme tamamlandı!"
                                _downloadProgress.value = 100f

                                callbackScope.launch {
                                    delay(3000)
                                    _downloadProgress.value = null
                                    _downloadMessage.value = null
                                }
                            } else {
                                _downloadProgress.value = percentage
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            Log.e("OfflineMapManager", "Offline Error: ${error.reason} - ${error.message}")
                            _downloadMessage.value = "İndirme hatası: ${error.reason}"
                            _downloadProgress.value = null
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            Log.e("OfflineMapManager", "Tile count limit exceeded: $limit")
                            _downloadMessage.value = "Karolaj limiti aşıldı."
                        }
                    })
                }

                override fun onError(error: String) {
                    Log.e("OfflineMapManager", "Error creating region: $error")
                    _downloadMessage.value = "Oluşturma hatası: $error"
                }
            }
        )
    }
}
