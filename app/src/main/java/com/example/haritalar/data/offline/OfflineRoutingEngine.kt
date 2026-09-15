package com.example.haritalar.data.offline

import android.content.Context
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption

/** On-device routing boundary; implementations must never fabricate routes. */
interface OfflineRoutingEngine {
    suspend fun route(context: Context, start: GeoPoint, destination: GeoPoint, profile: String = "auto"): List<RouteOption>
    fun isReady(context: Context): Boolean
}
