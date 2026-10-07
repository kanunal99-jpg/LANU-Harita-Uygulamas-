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
import com.google.android.gms.tasks.CancellationTokenSource
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
    private var freshFixWatchdogJob: Job? = null
    private var currentFixTokenSource: CancellationTokenSource? = null
    private var systemFallbackStarted = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val qualityFilter = LocationQualityFilter()
    @Volatile private var closed = false
    private var hasFineLocationPermission = false
    private var samplingMode = LocationSamplingPolicy.Mode.IDLE

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(hasFinePermission: Boolean) {
        if (closed) return
        if (!hasFinePermission) {
            stopLocationUpdates()
            Log.w("AppLocationManager", "Location permission unavailable; waiting for a real fix.")
            return
        }

        hasFineLocationPermission = true
        samplingMode = LocationSamplingPolicy.nextMode(
            samplingMode,
            _userLocation.value?.speedKmh ?: 0f
        )
        clearProviderCallbacks()

        try {
            fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (closed) return@addOnSuccessListener
                if (loc != null) onNewAndroidLocation(loc) else {
                    val lastGps = systemLocationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    val lastNet = systemLocationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    (lastGps ?: lastNet)?.let(::onNewAndroidLocation)
                }
            }
            requestLocationProviders()
            requestFreshLocation()
            scheduleFreshFixFallback()
        } catch (e: SecurityException) {
            hasFineLocationPermission = false
            clearProviderCallbacks()
            Log.e("AppLocationManager", "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("AppLocationManager", "Location update error: ${e.message}")
            clearProviderCallbacks()
            startSystemLocationFallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationProviders() {
        if (closed || !hasFineLocationPermission) return
        try {
            val config = LocationSamplingPolicy.config(samplingMode)
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                config.intervalMillis
            )
                .setMinUpdateIntervalMillis(config.minUpdateIntervalMillis)
                .setMinUpdateDistanceMeters(config.minUpdateDistanceMeters)
                .build()

            fusedCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (!closed) result.lastLocation?.let(::onNewAndroidLocation)
                }
            }
            fusedClient.requestLocationUpdates(request, fusedCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            hasFineLocationPermission = false
            clearProviderCallbacks()
            Log.e("AppLocationManager", "Fused location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("AppLocationManager", "Fused location update error: ${e.message}")
            clearProviderCallbacks()
            startSystemLocationFallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSystemLocationFallback() {
        if (closed || !hasFineLocationPermission || systemFallbackStarted) return
        val manager = systemLocationManager ?: return
        val config = LocationSamplingPolicy.config(samplingMode)

        if (sysListener == null) {
            sysListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    if (!closed) onNewAndroidLocation(loc)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
        }

        var requestedAnyProvider = false
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            try {
                if (!manager.isProviderEnabled(provider)) continue
                manager.requestLocationUpdates(
                    provider,
                    config.intervalMillis,
                    config.minUpdateDistanceMeters,
                    sysListener!!,
                    Looper.getMainLooper()
                )
                requestedAnyProvider = true
            } catch (e: SecurityException) {
                Log.w("AppLocationManager", "System provider $provider permission unavailable: ${e.message}")
            } catch (e: Exception) {
                Log.w("AppLocationManager", "System provider $provider failed: ${e.message}")
            }
        }

        systemFallbackStarted = requestedAnyProvider
        if (!requestedAnyProvider) {
            Log.w("AppLocationManager", "No system location provider could be started.")
        }
    }

    @SuppressLint("MissingPermission")
    fun requestFreshLocation() {
        if (closed || !hasFineLocationPermission) return

        currentFixTokenSource?.cancel()
        val tokenSource = CancellationTokenSource()
        currentFixTokenSource = tokenSource

        try {
            fusedClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (!closed && loc != null) {
                        onNewAndroidLocation(loc)
                    }
                }
                .addOnFailureListener { error ->
                    Log.w("AppLocationManager", "Fresh fused fix failed: ${error.message}")
                    startSystemLocationFallback()
                }
        } catch (e: SecurityException) {
            Log.w("AppLocationManager", "Fresh location permission unavailable: ${e.message}")
            startSystemLocationFallback()
        } catch (e: Exception) {
            Log.w("AppLocationManager", "Fresh location request failed: ${e.message}")
            startSystemLocationFallback()
        }
    }

    private fun scheduleFreshFixFallback() {
        freshFixWatchdogJob?.cancel()
        freshFixWatchdogJob = scope.launch {
            delay(8_000L)
            while (!closed && hasFineLocationPermission) {
                val current = _userLocation.value
                if (!NavigationLocationPolicy.isUsableForRouting(current)) {
                    Log.w(
                        "AppLocationManager",
                        "GPS freshness watchdog detected missing/stale/inaccurate fix; requesting recovery."
                    )
                    startSystemLocationFallback()
                    requestFreshLocation()
                }
                delay(15_000L)
            }
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
        val accepted = qualityFilter.accept(candidate) ?: return
        _userLocation.value = accepted
        // Keep the watchdog alive after a good fix. If providers stall later,
        // it can recover freshness without requiring a manual recenter/restart.

        val nextMode = LocationSamplingPolicy.nextMode(samplingMode, accepted.speedKmh)
        if (nextMode != samplingMode && hasFineLocationPermission) {
            samplingMode = nextMode
            restartLocationProviders()
        }
    }

    private fun restartLocationProviders() {
        if (closed || !hasFineLocationPermission) return
        clearProviderCallbacks()
        requestLocationProviders()
    }

    private fun clearProviderCallbacks() {
        fusedCallback?.let {
            fusedClient.removeLocationUpdates(it)
            fusedCallback = null
        }
        sysListener?.let {
            systemLocationManager?.removeUpdates(it)
            sysListener = null
        }
        systemFallbackStarted = false
    }

    fun updateLocationManual(point: GeoPoint, bearing: Float, speedKmh: Float) {
        if (closed) return
        _userLocation.value = UserLocationData(
            point,
            bearing,
            speedKmh,
            5f,
            System.currentTimeMillis(),
            false,
            true
        )
    }

    fun startRouteSimulation(routePoints: List<GeoPoint>, onProgress: (index: Int, point: GeoPoint) -> Unit) {
        stopSimulation()
        if (closed || routePoints.size < 2) return
        simulationJob = scope.launch {
            for (i in routePoints.indices) {
                val pt = routePoints[i]
                val nextPt = routePoints.getOrElse(i + 1) { pt }
                updateLocationManual(
                    pt,
                    calculateBearing(pt, nextPt),
                    if (i == routePoints.lastIndex) 0f else 45f + (i % 15)
                )
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
        freshFixWatchdogJob?.cancel()
        freshFixWatchdogJob = null
        currentFixTokenSource?.cancel()
        currentFixTokenSource = null
        clearProviderCallbacks()
        hasFineLocationPermission = false
        samplingMode = LocationSamplingPolicy.Mode.IDLE
        qualityFilter.reset()
    }

    override fun close() {
        if (closed) return
        closed = true
        stopLocationUpdates()
        scope.cancel()
    }
}
