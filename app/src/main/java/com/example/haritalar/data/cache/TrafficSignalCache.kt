package com.example.haritalar.data.cache

import android.util.Log
import com.example.haritalar.data.db.CachedTrafficSignalEntity
import com.example.haritalar.data.db.TrafficSignalDao
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for OpenStreetMap traffic signals.
 * Implements a 2-tier caching strategy:
 * 1. Fast in-memory spatial grid cache with TTL.
 * 2. Persistent Room DB storage for full offline survival.
 */
class TrafficSignalCache(
    private val trafficSignalDao: TrafficSignalDao? = null,
    private val ttlMillis: Long = 2 * 60 * 60 * 1000L // 2 Hours TTL
) {
    companion object {
        private const val TAG = "TrafficSignalCache"
        // Grid cell step in degrees (~1.1 km grid)
        private const val GRID_STEP = 0.01
    }

    private data class MemoryEntry(
        val signals: List<TrafficSignal>,
        val timestamp: Long
    )

    // Grid-cell key -> MemoryEntry
    private val inMemoryCache = ConcurrentHashMap<String, MemoryEntry>()
    // Set of all cached signal IDs in memory to guarantee 0 duplicates
    private val inMemorySignalIndex = ConcurrentHashMap<Long, TrafficSignal>()

    /**
     * Generates a discrete grid key for a given lat/lon.
     */
    private fun getGridKey(lat: Double, lon: Double): String {
        val latIndex = (lat / GRID_STEP).toInt()
        val lonIndex = (lon / GRID_STEP).toInt()
        return "$latIndex:$lonIndex"
    }

    /**
     * Retrieves cached traffic signals inside the bounding box.
     * @param allowStale If true, returns signals even if TTL has expired (e.g. during offline mode).
     * @return List of cached TrafficSignals, or emptyList() if not cached.
     */
    suspend fun getSignalsForBoundingBox(
        bbox: TrafficSignalBoundingBox,
        allowStale: Boolean = false
    ): List<TrafficSignal> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val results = mutableListOf<TrafficSignal>()
        val seenIds = mutableSetOf<Long>()

        // 1. Check in-memory index first
        for ((_, signal) in inMemorySignalIndex) {
            if (bbox.contains(signal.point)) {
                if (seenIds.add(signal.id)) {
                    results.add(signal)
                }
            }
        }

        if (results.isNotEmpty()) {
            Log.d(TAG, "In-memory cache hit: found ${results.size} signals for bbox")
            return@withContext results
        }

        // 2. Check Room Database fallback
        if (trafficSignalDao != null) {
            try {
                val dbEntities = trafficSignalDao.getSignalsInBoundingBox(
                    minLat = bbox.south,
                    maxLat = bbox.north,
                    minLon = bbox.west,
                    maxLon = bbox.east
                )

                for (entity in dbEntities) {
                    val isExpired = (now - entity.cachedAt) > ttlMillis
                    if (!isExpired || allowStale) {
                        val signal = TrafficSignal(
                            id = entity.id,
                            point = GeoPoint(entity.latitude, entity.longitude),
                            crossing = entity.crossing,
                            direction = entity.direction,
                            hasSound = entity.hasSound,
                            hasVibration = entity.hasVibration,
                            hasArrow = entity.hasArrow,
                            reference = entity.reference,
                            source = "OpenStreetMap (Yerel Önbellek)"
                        )
                        if (seenIds.add(signal.id)) {
                            results.add(signal)
                            inMemorySignalIndex[signal.id] = signal
                        }
                    }
                }

                if (results.isNotEmpty()) {
                    Log.d(TAG, "Room DB cache hit: found ${results.size} signals for bbox")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading from TrafficSignalDao: ${e.message}")
            }
        }

        results
    }

    /**
     * Saves freshly fetched signals into memory and Room DB.
     */
    suspend fun putSignals(
        bbox: TrafficSignalBoundingBox,
        signals: List<TrafficSignal>
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Store into memory index
        for (signal in signals) {
            inMemorySignalIndex[signal.id] = signal
        }

        val gridKey = "${getGridKey(bbox.south, bbox.west)}_${getGridKey(bbox.north, bbox.east)}"
        inMemoryCache[gridKey] = MemoryEntry(signals, now)

        // 2. Persist into Room Database
        if (trafficSignalDao != null && signals.isNotEmpty()) {
            try {
                val entities = signals.map { s ->
                    CachedTrafficSignalEntity(
                        id = s.id,
                        latitude = s.point.latitude,
                        longitude = s.point.longitude,
                        crossing = s.crossing,
                        direction = s.direction,
                        hasSound = s.hasSound,
                        hasVibration = s.hasVibration,
                        hasArrow = s.hasArrow,
                        reference = s.reference,
                        cachedAt = now
                    )
                }
                trafficSignalDao.insertSignals(entities)
                Log.d(TAG, "Persisted ${entities.size} traffic signals to Room DB")
            } catch (e: Exception) {
                Log.w(TAG, "Error inserting signals into Room DB: ${e.message}")
            }
        }
    }

    /**
     * Clears in-memory cache for testing or manual reset.
     */
    fun clearMemory() {
        inMemoryCache.clear()
        inMemorySignalIndex.clear()
    }

    /**
     * Deletes expired records from persistent database.
     */
    suspend fun pruneExpired() = withContext(Dispatchers.IO) {
        if (trafficSignalDao != null) {
            try {
                val expiryThreshold = System.currentTimeMillis() - ttlMillis
                trafficSignalDao.deleteExpired(expiryThreshold)
            } catch (e: Exception) {
                Log.w(TAG, "Error pruning expired traffic signals: ${e.message}")
            }
        }
    }
}
