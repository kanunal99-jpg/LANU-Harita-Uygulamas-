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

class NavigationRepository(context: Context) {
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

    suspend fun searchPlacesResponse(query: String, focusPoint: GeoPoint?): com.example.haritalar.model.SearchResponse {
        val response = searchProviderChain.executeSearch(query, focusPoint)
        if (response is com.example.haritalar.model.SearchResponse.Success && response.results.isNotEmpty()) {
            val top = response.results.first()
            searchHistoryDao.insertSearch(
                SearchHistoryItem(
                    query = query,
                    displayName = top.displayName,
                    latitude = top.point.latitude,
                    longitude = top.point.longitude
                )
            )
        }
        return response
    }

    suspend fun searchPlaces(query: String, focusPoint: GeoPoint?): List<SearchResult> {
        val response = searchPlacesResponse(query, focusPoint)
        return if (response is com.example.haritalar.model.SearchResponse.Success) response.results else emptyList()
    }

    suspend fun reverseGeocode(point: GeoPoint): String? =
        searchProviderChain.reverseGeocode(point) ?: geocodingService.reverseGeocode(point)

    suspend fun fetchPois(center: GeoPoint, category: PoiCategory?): List<PoiItem> =
        poiService.fetchPoisAround(center, radiusMeters = 3000, selectedCategory = category)

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

    suspend fun addFavorite(title: String, address: String, point: GeoPoint, category: String) {
        favoriteDao.insertFavorite(
            FavoritePlace(
                title = title,
                address = address,
                latitude = point.latitude,
                longitude = point.longitude,
                category = category
            )
        )
    }

    suspend fun deleteFavorite(favorite: FavoritePlace) = favoriteDao.deleteFavorite(favorite)

    suspend fun clearHistory() = searchHistoryDao.clearHistory()
}
