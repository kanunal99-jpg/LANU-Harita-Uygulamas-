package com.example.haritalar.data.repository

import android.util.Log
import com.example.haritalar.data.cache.TrafficSignalCache
import com.example.haritalar.data.network.TrafficSignalService
import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox
import com.example.haritalar.model.TrafficSignalFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * High-reliability repository for OpenStreetMap traffic signals.
 * Implements the mandatory fallback chain:
 * Primary (Overpass main) -> Alternative (Overpass mirrors) -> Cache -> Safe Default (empty list).
 */
class TrafficSignalRepository(
    private val service: TrafficSignalService = TrafficSignalService(),
    private val cache: TrafficSignalCache = TrafficSignalCache()
) {
    companion object {
        private const val TAG = "TrafficSignalRepository"
        const val MIN_ZOOM_FOR_SIGNALS = 14.0f
    }

    private val mutex = Mutex()
    private var lastRequestedBbox: TrafficSignalBoundingBox? = null

    /**
     * Loads traffic signals for the given viewport bounding box.
     * Guaranteed to never throw runtime exceptions and always returns a safe, verified result.
     */
    suspend fun getTrafficSignalsForViewport(
        bbox: TrafficSignalBoundingBox,
        zoomLevel: Float,
        forceRefresh: Boolean = false
    ): TrafficSignalFetchResult = withContext(Dispatchers.IO) {
        // Zoom check: traffic signals are high-density intersection infrastructure.
        // Below zoom 14, querying entire provinces would overload servers and clutter the map.
        if (zoomLevel < MIN_ZOOM_FOR_SIGNALS) {
            Log.d(TAG, "Zoom level $zoomLevel < $MIN_ZOOM_FOR_SIGNALS, skipping query.")
            return@withContext TrafficSignalFetchResult.Success(
                signals = emptyList(),
                fromCache = false
            )
        }

        if (!bbox.isValid()) {
            return@withContext TrafficSignalFetchResult.Error(
                message = "Geçersiz arama koordinatları",
                isNetworkError = false
            )
        }

        // 1. Check Cache first unless forceRefresh is true
        if (!forceRefresh) {
            val cachedSignals = cache.getSignalsForBoundingBox(bbox, allowStale = false)
            if (cachedSignals.isNotEmpty()) {
                Log.d(TAG, "Returning ${cachedSignals.size} signals from fresh cache.")
                return@withContext TrafficSignalFetchResult.Success(
                    signals = cachedSignals,
                    fromCache = true
                )
            }
        }

        // 2. Fetch from Network (Primary -> Alternative mirrors)
        val networkResult = try {
            mutex.withLock {
                lastRequestedBbox = bbox
                service.fetchTrafficSignalsInBoundingBox(bbox)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network fetch exception: ${e.message}", e)
            TrafficSignalFetchResult.Error(
                message = e.message ?: "Ağ hatası",
                isNetworkError = true
            )
        }

        when (networkResult) {
            is TrafficSignalFetchResult.Success -> {
                val uniqueSignals = deduplicateSignals(networkResult.signals)
                // Update cache with fresh data
                cache.putSignals(bbox, uniqueSignals)
                TrafficSignalFetchResult.Success(
                    signals = uniqueSignals,
                    fromCache = false,
                    endpointUsed = networkResult.endpointUsed
                )
            }
            is TrafficSignalFetchResult.Error -> {
                Log.w(TAG, "Network failed (${networkResult.message}), checking offline stale cache fallback...")
                // 3. Fallback: Stale Cache for offline capability
                val staleSignals = cache.getSignalsForBoundingBox(bbox, allowStale = true)
                if (staleSignals.isNotEmpty()) {
                    val uniqueStale = deduplicateSignals(staleSignals)
                    Log.i(TAG, "Successfully fell back to ${uniqueStale.size} cached signals while offline.")
                    TrafficSignalFetchResult.Success(
                        signals = uniqueStale,
                        fromCache = true,
                        endpointUsed = "Offline Cache Fallback"
                    )
                } else {
                    // 4. Safe Default: empty list without crash
                    Log.i(TAG, "No cache available, returning safe empty list.")
                    TrafficSignalFetchResult.Error(
                        message = networkResult.message,
                        isNetworkError = networkResult.isNetworkError,
                        fallbackSignals = emptyList()
                    )
                }
            }
        }
    }

    /**
     * Deduplicates a list of traffic signals by OSM node id.
     */
    fun deduplicateSignals(signals: List<TrafficSignal>): List<TrafficSignal> {
        val seen = mutableSetOf<Long>()
        val result = mutableListOf<TrafficSignal>()
        for (s in signals) {
            if (seen.add(s.id)) {
                result.add(s)
            }
        }
        return result
    }
}
