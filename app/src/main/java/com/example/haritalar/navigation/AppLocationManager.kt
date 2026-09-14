package com.example.haritalar.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.example.haritalar.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserLocationData(
    val point: GeoPoint,
    val bearing: Float = 0f,
    val speedKmh: Float = 0f,
    val accuracyMeters: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val isGpsWeak: Boolean = false,
    val isSimulated: Boolean = false
)

class AppLocationManager(private val context: Context) : AutoCloseable {
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val systemLocationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val _userLocation = MutableStateFlow<UserLocationData?>(null)
    val userLocation: StateFlow<UserLocationData?> = _userLocation.asStateFlow()
    private var fusedCallback: LocationCallback? = null
    private var sysListener: LocationListener? = null
    private var simulationJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val qualityFilter = LocationQualityFilter()
    @Volatile private var closed = false

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(hasFinePermission: Boolean) {
        if (closed) return
        if (!hasFinePermission) {
            stopLocationUpdates()
            Log.w("AppLocationManager", "Location permission unavailable; waiting for a real fix.")
            return
        }
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (closed) return@addOnSuccessListener
                if (loc != null) onNewAndroidLocation(loc) else {
                    val lastGps = systemLocationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    val lastNet = systemLocationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    (lastGps ?: lastNet)?.let(::onNewAndroidLocation)
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(2.0f)
                .build()
            fusedCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (!closed) result.lastLocation?.let(::onNewAndroidLocation)
                }
            }
            fusedClient.requestLocationUpdates(request, fusedCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("AppLocationManager", "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("AppLocationManager", "Location update error: ${e.message}")
            startSystemLocationFallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSystemLocationFallback() {
        if (closed) return
        try {
            sysListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) { if (!closed) onNewAndroidLocation(loc) }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            systemLocationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 2.0f, sysListener!!, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e("AppLocationManager", "System location fallback error: ${e.message}")
        }
    }

    private fun onNewAndroidLocation(loc: Location) {
        if (closed || !loc.latitude.isFinite() || !loc.longitude.isFinite()) return
        val candidate = UserLocationData(
            point = GeoPoint(loc.latitude, loc.longitude),
            bearing = if (loc.hasBearing()) loc.bearing else _userLocation.value?.bearing ?: 0f,
            speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).coerceIn(0f, 250f) else 0f,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 10f,
            timestamp = loc.time,
            isGpsWeak = loc.hasAccuracy() && loc.accuracy > 45f,
            isSimulated = false
        )
        qualityFilter.accept(candidate)?.let { _userLocation.value = it }
    }

    fun updateLocationManual(point: GeoPoint, bearing: Float, speedKmh: Float) {
        if (closed) return
        _userLocation.value = UserLocationData(point, bearing, speedKmh, 5f, System.currentTimeMillis(), false, true)
    }

    fun startRouteSimulation(routePoints: List<GeoPoint>, onProgress: (index: Int, point: GeoPoint) -> Unit) {
        stopSimulation()
        if (closed || routePoints.size < 2) return
        simulationJob = scope.launch {
            for (i in routePoints.indices) {
                val pt = routePoints[i]
                val nextPt = routePoints.getOrElse(i + 1) { pt }
                updateLocationManual(pt, calculateBearing(pt, nextPt), if (i == routePoints.lastIndex) 0f else 45f + (i % 15))
                onProgress(i, pt)
                delay(1200L)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    private fun calculateBearing(from: GeoPoint, to: GeoPoint): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)
        val dLon = lon2 - lon1
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
    }

    fun stopLocationUpdates() {
        stopSimulation()
        fusedCallback?.let { fusedClient.removeLocationUpdates(it); fusedCallback = null }
        sysListener?.let { systemLocationManager?.removeUpdates(it); sysListener = null }
        qualityFilter.reset()
    }

    override fun close() {
        if (closed) return
        closed = true
        stopLocationUpdates()
        scope.cancel()
    }
}
