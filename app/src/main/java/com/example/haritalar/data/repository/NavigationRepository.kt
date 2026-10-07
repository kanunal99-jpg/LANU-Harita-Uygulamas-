package com.example.haritalar.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.haritalar.data.cache.TrafficSignalCache
import com.example.haritalar.data.db.AppDatabase
import com.example.haritalar.data.db.FavoritePlace
import com.example.haritalar.data.db.SearchHistoryItem
import com.example.haritalar.data.network.NominatimGeocodingService
import com.example.haritalar.data.network.OsrmRoutingProvider
import com.example.haritalar.data.network.PoiNetworkService
import com.example.haritalar.data.network.TrafficSignalService
import com.example.haritalar.data.network.ValhallaRoutingProvider
import com.example.haritalar.data.offline.OfflineRouteCache
import com.example.haritalar.data.search.CacheSearchProvider
import com.example.haritalar.data.search.NominatimSearchProvider
import com.example.haritalar.data.search.PhotonSearchProvider
import com.example.haritalar.data.search.SearchProviderChain
import com.example.haritalar.data.traffic.TomTomTrafficProvider
import com.example.haritalar.data.traffic.TrafficProviderChain
import com.example.haritalar.data.traffic.TrafficRefreshCoordinator
import com.example.haritalar.data.traffic.TrafficRouteRankingService
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.SearchResult
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.TrafficTestResult
import kotlinx.coroutines.flow.Flow

enum class SavedPlaceResult {
    SAVED,
    REPLACED,
    DUPLICATE,
    CAPACITY_REACHED,
    INVALID
}

class NavigationRepository(context: Context) {
    companion object {
        const val MAX_SAVED_PLACES = SavedPlacePolicy.MAX_SAVED_PLACES
    }
    val db = AppDatabase.getInstance(context)
    private val favoriteDao = db.favoriteDao()
    private val searchHistoryDao = db.searchHistoryDao()
    val trafficSignalDao = db.trafficSignalDao()
    private val prefs = context.getSharedPreferences("lanu_navigation_prefs", Context.MODE_PRIVATE)
    private val offlineRouteCache = OfflineRouteCache(context)

    val trafficSignalService = TrafficSignalService()
    val trafficSignalCache = TrafficSignalCache(trafficSignalDao = trafficSignalDao)
    val trafficSignalRepository = TrafficSignalRepository(trafficSignalService, trafficSignalCache)

    val cacheSearchProvider = CacheSearchProvider(searchHistoryDao)
    val searchProviderChain = SearchProviderChain(
        primaryProvider = NominatimSearchProvider(),
        alternativeProvider = PhotonSearchProvider(),
        cacheProvider = cacheSearchProvider
    )

    private val geocodingService = NominatimGeocodingService()
    private val poiService = PoiNetworkService()
    private val valhallaProvider = ValhallaRoutingProvider()
    private val osrmProvider = OsrmRoutingProvider()

    val tomtomProvider = TomTomTrafficProvider(getEffectiveTomTomKey())
    val trafficProviderChain = TrafficProviderChain(primaryProvider = tomtomProvider)
    val trafficRankingService = TrafficRouteRankingService(trafficProviderChain)
    val trafficCoordinator = TrafficRefreshCoordinator(trafficRankingService)

    fun getEffectiveTomTomKey(): String {
        val custom = prefs.getString("custom_tomtom_api_key", null)?.trim()
        if (!custom.isNullOrBlank()) return custom
        val buildKey = BuildConfig.TOMTOM_API_KEY.trim().removeSurrounding("\"")
        if (buildKey == "MY_TOMTOM_API_KEY" || buildKey == "null") return ""
        return buildKey
    }

    fun setCustomTomTomKey(newKey: String) {
        val cleaned = newKey.trim().removeSurrounding("\"")
        prefs.edit().putString("custom_tomtom_api_key", cleaned).apply()
        tomtomProvider.apiKey = cleaned
        trafficProviderChain.clearCache()
    }

    suspend fun testTomTomTraffic(point: GeoPoint): TrafficTestResult = tomtomProvider.testLiveConnection(point)

    val favorites: Flow<List<FavoritePlace>> = favoriteDao.getAllFavorites()
    val recentSearches: Flow<List<SearchHistoryItem>> = searchHistoryDao.getRecentSearches()

    suspend fun searchPlacesResponse(query: String, focusPoint: GeoPoint?): com.example.haritalar.model.SearchResponse =
        searchProviderChain.executeSearch(query, focusPoint)

    suspend fun recordSearchSelection(query: String, result: SearchResult) {
        val normalizedQuery = query.trim().takeIf { it.isNotBlank() } ?: result.name
        searchHistoryDao.insertSearch(
            SearchHistoryItem(
                query = normalizedQuery,
                displayName = result.displayName,
                latitude = result.point.latitude,
                longitude = result.point.longitude
            )
        )
    }

    suspend fun searchPlaces(query: String, focusPoint: GeoPoint?): List<SearchResult> {
        val response = searchPlacesResponse(query, focusPoint)
        return if (response is com.example.haritalar.model.SearchResponse.Success) response.results else emptyList()
    }

    suspend fun reverseGeocode(point: GeoPoint): String? =
        searchProviderChain.reverseGeocode(point) ?: geocodingService.reverseGeocode(point)

    suspend fun fetchPois(
        center: GeoPoint,
        category: PoiCategory?,
        radiusMeters: Int = 8_000
    ): List<PoiItem> {
        val safeRadius = radiusMeters.coerceIn(2_500, 20_000)
        val primary = poiService.fetchPoisAround(center, radiusMeters = safeRadius, selectedCategory = category)
        if (primary.isNotEmpty()) return primary

        if (category == null) return emptyList()
        val fallbackQueries = when (category) {
            PoiCategory.RESTAURANT -> listOf("restoran", "restaurant")
            PoiCategory.FUEL -> listOf("benzinlik", "akaryakıt")
            PoiCategory.HOSPITAL -> listOf("hastane", "hospital")
            PoiCategory.PHARMACY -> listOf("eczane", "pharmacy")
            PoiCategory.MARKET -> listOf("market", "süpermarket")
            PoiCategory.PARKING -> listOf("otopark", "parking")
            PoiCategory.ATM -> listOf("ATM", "banka ATM")
            PoiCategory.CAFE -> listOf("kafe", "cafe")
            PoiCategory.CHARGING_STATION -> listOf("şarj istasyonu", "elektrikli araç şarj")
        }

        for (query in fallbackQueries) {
            val response = searchProviderChain.executeSearch(query, center)
            if (response is com.example.haritalar.model.SearchResponse.Success) {
                val fallbackDistance = (safeRadius + 5_000).coerceAtMost(25_000).toDouble()
                val fallback = response.results
                    .filter { it.point.distanceTo(center) <= fallbackDistance }
                    .take(40)
                    .map {
                        PoiItem(
                            id = "search_${it.id}",
                            name = it.name,
                            category = category,
                            point = it.point,
                            address = it.shortAddress.ifBlank { it.displayName }
                        )
                    }
                if (fallback.isNotEmpty()) return fallback
            }
        }
        return emptyList()
    }

    suspend fun calculateRouteAlternatives(
        start: GeoPoint,
        end: GeoPoint,
        generationId: Long
    ): Pair<List<RouteOption>, Map<String, Pair<TrafficStatus, List<TrafficSegment>>>> {
        trafficCoordinator.resetGeneration(generationId)

        val rawRoutes = RouteProviderFallback.resolve(
            primary = { valhallaProvider.calculateRoutes(start, end, generationId) },
            alternative = { osrmProvider.calculateRoutes(start, end, generationId) }
        )

        val sourceRoutes = if (rawRoutes.isNotEmpty()) {
            offlineRouteCache.save(start, end, rawRoutes)
            rawRoutes
        } else {
            offlineRouteCache.load(start, end, generationId)
        }

        if (sourceRoutes.isEmpty()) {
            return emptyList<RouteOption>() to emptyMap()
        }

        val normalizedRoutes = sourceRoutes.map { it.copy(generationId = generationId) }
        return trafficRankingService.rankAndApplyTraffic(normalizedRoutes, generationId)
    }

    suspend fun addFavorite(title: String, address: String, point: GeoPoint, category: String): SavedPlaceResult {
        val cleanedTitle = title.trim().take(80)
        val cleanedCategory = SavedPlacePolicy.normalizeCategory(category) ?: return SavedPlaceResult.INVALID
        if (cleanedTitle.isBlank() || point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0) {
            return SavedPlaceResult.INVALID
        }

        val exactDuplicate = favoriteDao.findExact(cleanedTitle, point.latitude, point.longitude) != null
        val existingCategory = if (cleanedCategory == "HOME" || cleanedCategory == "WORK") {
            favoriteDao.getFavoriteByCategory(cleanedCategory)
        } else null
        val decision = SavedPlacePolicy.decision(
            currentCount = favoriteDao.getFavoriteCount(),
            category = cleanedCategory,
            hasExistingSingleCategory = existingCategory != null,
            exactDuplicate = exactDuplicate
        )
        if (decision == SavedPlaceResult.DUPLICATE ||
            decision == SavedPlaceResult.CAPACITY_REACHED ||
            decision == SavedPlaceResult.INVALID
        ) {
            return decision
        }

        if (existingCategory != null) {
            favoriteDao.deleteByCategory(cleanedCategory)
        }

        favoriteDao.insertFavorite(
            FavoritePlace(
                title = cleanedTitle,
                address = address.trim().take(300),
                latitude = point.latitude,
                longitude = point.longitude,
                category = cleanedCategory
            )
        )
        return decision
    }

    suspend fun deleteFavorite(favorite: FavoritePlace) = favoriteDao.deleteFavorite(favorite)

    suspend fun clearHistory() = searchHistoryDao.clearHistory()
}
