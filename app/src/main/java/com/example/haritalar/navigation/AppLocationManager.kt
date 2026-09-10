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

class AppLocationManager(private val context: Context) {
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val systemLocationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _userLocation = MutableStateFlow<UserLocationData?>(null)
    val userLocation: StateFlow<UserLocationData?> = _userLocation.asStateFlow()

    private var fusedCallback: LocationCallback? = null
    private var sysListener: LocationListener? = null

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(hasFinePermission: Boolean) {
        if (!hasFinePermission) {
            // Default center: Istanbul, Turkey (Taksim / Bosphorus)
            if (_userLocation.value == null) {
                _userLocation.value = UserLocationData(
                    point = GeoPoint(41.0082, 28.9784),
                    bearing = 0f,
                    speedKmh = 0f,
                    accuracyMeters = 20f
                )
            }
            return
        }

        try {
            // 1. Fetch last known location quickly
            fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    onNewAndroidLocation(loc)
                } else {
                    // Try system location manager
                    val lastGps = systemLocationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    val lastNet = systemLocationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    val best = lastGps ?: lastNet
                    if (best != null) onNewAndroidLocation(best)
                }
            }

            // 2. High accuracy continuous requests
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(2.0f)
                .build()

            fusedCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { onNewAndroidLocation(it) }
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
        try {
            sysListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    onNewAndroidLocation(loc)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            systemLocationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1500L,
                2.0f,
                sysListener!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e("AppLocationManager", "System location fallback error: ${e.message}")
        }
    }

    private fun onNewAndroidLocation(loc: Location) {
        val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f) else 0f
        val bearing = if (loc.hasBearing()) loc.bearing else _userLocation.value?.bearing ?: 0f
        val isWeak = loc.hasAccuracy() && loc.accuracy > 45f

        _userLocation.value = UserLocationData(
            point = GeoPoint(loc.latitude, loc.longitude),
            bearing = bearing,
            speedKmh = speedKmh,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 10f,
            timestamp = loc.time,
            isGpsWeak = isWeak,
            isSimulated = false
        )
    }

    /**
     * Updates location manually (e.g. for GPS simulator along the active route).
     */
    fun updateLocationManual(point: GeoPoint, bearing: Float, speedKmh: Float) {
        _userLocation.value = UserLocationData(
            point = point,
            bearing = bearing,
            speedKmh = speedKmh,
            accuracyMeters = 5f,
            timestamp = System.currentTimeMillis(),
            isGpsWeak = false,
            isSimulated = true
        )
    }

    /**
     * Simulates driving along a route for emulator environments.
     */
    fun startRouteSimulation(
        routePoints: List<GeoPoint>,
        onProgress: (index: Int, point: GeoPoint) -> Unit
    ) {
        stopSimulation()
        if (routePoints.size < 2) return

        simulationJob = scope.launch {
            for (i in 0 until routePoints.size) {
                val pt = routePoints[i]
                val nextPt = if (i < routePoints.size - 1) routePoints[i + 1] else pt
                val bearing = calculateBearing(pt, nextPt)
                val speed = if (i == routePoints.size - 1) 0f else (45f + (i % 15))

                updateLocationManual(pt, bearing, speed)
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
        val radians = Math.atan2(y, x)
        return ((Math.toDegrees(radians) + 360) % 360).toFloat()
    }

    fun stopLocationUpdates() {
        stopSimulation()
        fusedCallback?.let {
            fusedClient.removeLocationUpdates(it)
            fusedCallback = null
        }
        sysListener?.let {
            systemLocationManager?.removeUpdates(it)
            sysListener = null
        }
    }
}
