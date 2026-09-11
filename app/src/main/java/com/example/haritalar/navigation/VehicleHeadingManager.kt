package com.example.haritalar.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.haritalar.model.DepartureGuidance
import com.example.haritalar.model.DepartureTurn
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HeadingSource {
    GPS_BEARING,
    SENSOR,
    LAST_KNOWN,
    UNAVAILABLE;

    companion object {
        val GPS = GPS_BEARING
        val FALLBACK_NORTH = UNAVAILABLE
    }
}

enum class HeadingConfidence {
    HIGH,
    MEDIUM,
    LOW
}

interface HeadingSensorProvider {
    val sensorHeading: StateFlow<Float>
    fun isFresh(maxAgeMs: Long = 4000L): Boolean
    fun start()
    fun stop()
}

data class VehicleHeadingState(
    val heading: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val source: HeadingSource = HeadingSource.UNAVAILABLE,
    val confidence: HeadingConfidence = HeadingConfidence.LOW,
    val speedKmh: Float = 0f
)

/**
 * Manages device orientation sensors (Rotation Vector / Accelerometer + Magnetometer)
 * with low-pass angular filtering to provide steady compass heading even when vehicle is stationary.
 */
class CompassHeadingSensor(context: Context) : SensorEventListener, HeadingSensorProvider {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = if (rotationVectorSensor == null) sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
    private val magneticSensor: Sensor? = if (rotationVectorSensor == null) sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) else null

    private val _sensorHeading = MutableStateFlow(0f)
    override val sensorHeading: StateFlow<Float> = _sensorHeading.asStateFlow()

    private var currentFilteredHeading: Float = 0f
    private var hasFirstHeading = false
    private var lastSensorUpdateTime: Long = 0L

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false

    private var isListening = false

    override fun start() {
        if (isListening || sensorManager == null) return
        isListening = true

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magneticSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    override fun stop() {
        if (!isListening || sensorManager == null) return
        isListening = false
        sensorManager.unregisterListener(this)
    }

    override fun isFresh(maxAgeMs: Long): Boolean {
        return hasFirstHeading && (System.currentTimeMillis() - lastSensorUpdateTime <= maxAgeMs)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var rawDeg: Float? = null

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // orientationAngles[0] is azimuth in radians [-pi, pi]
            val azimuthRad = orientationAngles[0]
            rawDeg = ((Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
            hasAccelerometer = true
            if (hasAccelerometer && hasMagnetometer) {
                rawDeg = computeOrientationFromAccMag()
            }
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
            hasMagnetometer = true
            if (hasAccelerometer && hasMagnetometer) {
                rawDeg = computeOrientationFromAccMag()
            }
        }

        if (rawDeg != null) {
            lastSensorUpdateTime = System.currentTimeMillis()
            if (!hasFirstHeading) {
                currentFilteredHeading = rawDeg
                hasFirstHeading = true
                _sensorHeading.value = currentFilteredHeading
            } else {
                val diff = shortestAngleDelta(currentFilteredHeading, rawDeg)
                // Filter out tiny noise (< 0.4 deg)
                if (Math.abs(diff) > 0.4f) {
                    val filterFactor = 0.25f // Responsive smoothing
                    currentFilteredHeading = (currentFilteredHeading + diff * filterFactor + 360f) % 360f
                    _sensorHeading.value = currentFilteredHeading
                }
            }
        }
    }

    private fun computeOrientationFromAccMag(): Float? {
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        return if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            ((Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f)
        } else null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /**
         * Computes shortest signed delta between two angles in degrees [-180, 180].
         * E.g. from 359 to 1 returns +2, from 1 to 359 returns -2.
         */
        fun shortestAngleDelta(from: Float, to: Float): Float {
            var diff = (to - from) % 360f
            if (diff < -180f) diff += 360f
            if (diff > 180f) diff -= 360f
            return diff
        }

        fun normalizeAngle(deg: Float): Float {
            var a = deg % 360f
            if (a < 0f) a += 360f
            return a
        }
    }
}

/**
 * High-level Vehicle Heading Orchestrator.
 * Combines GPS bearing (primary when moving) and CompassHeadingSensor (when stationary or low-speed),
 * applies shortest-path angular smoothing, handles fallback tiers, wrong-way detection and departure guidance.
 */
class VehicleHeadingManager(
    private val sensor: HeadingSensorProvider? = null
) {
    private val _headingState = MutableStateFlow(VehicleHeadingState())
    val headingState: StateFlow<VehicleHeadingState> = _headingState.asStateFlow()

    private var currentSmoothHeading: Float = 0f
    private var hasInitialHeading: Boolean = false
    private var lastGpsBearing: Float? = null
    private var lastGpsTimestamp: Long = 0L

    // Wrong-way hysteresis
    private var wrongWayConsecutiveTicks: Int = 0

    fun start() {
        sensor?.start()
    }

    fun stop() {
        sensor?.stop()
    }

    fun resetSession() {
        hasInitialHeading = false
        currentSmoothHeading = 0f
        lastGpsBearing = null
        lastGpsTimestamp = 0L
        wrongWayConsecutiveTicks = 0
        _headingState.value = VehicleHeadingState()
    }

    fun onReroute() {
        wrongWayConsecutiveTicks = 0
    }

    /**
     * Updates heading based on new GPS location data.
     */
    fun processLocation(location: UserLocationData): VehicleHeadingState {
        val now = System.currentTimeMillis()
        val speedKmh = location.speedKmh
        val rawGpsBearing = location.bearing

        val (targetHeading, source, confidence) = determineTargetHeading(
            speedKmh = speedKmh,
            rawGpsBearing = rawGpsBearing,
            now = now
        )

        // Apply smooth shortest-path rotation
        val smoothed = updateSmoothedHeading(targetHeading)

        val newState = VehicleHeadingState(
            heading = smoothed,
            timestamp = now,
            source = source,
            confidence = confidence,
            speedKmh = speedKmh
        )
        _headingState.value = newState
        return newState
    }

    /**
     * Pure selection logic: decides whether to use GPS, Sensor, Last Known or Unavailable.
     */
    fun determineTargetHeading(
        speedKmh: Float,
        rawGpsBearing: Float,
        now: Long = System.currentTimeMillis()
    ): Triple<Float, HeadingSource, HeadingConfidence> {
        val isMovingWithGps = speedKmh >= 5.0f && rawGpsBearing > 0.001f

        if (isMovingWithGps) {
            lastGpsBearing = rawGpsBearing
            lastGpsTimestamp = now
            return Triple(rawGpsBearing, HeadingSource.GPS_BEARING, HeadingConfidence.HIGH)
        }

        // Low speed or stopped (< 4.5 km/h) -> Try Sensor
        if (sensor != null && sensor.isFresh(maxAgeMs = 4000L)) {
            val sensorDeg = sensor.sensorHeading.value
            return Triple(sensorDeg, HeadingSource.SENSOR, HeadingConfidence.MEDIUM)
        }

        // Fallback: Last known GPS if not too old (< 10 seconds)
        if (lastGpsBearing != null && (now - lastGpsTimestamp <= 10_000L)) {
            return Triple(lastGpsBearing!!, HeadingSource.LAST_KNOWN, HeadingConfidence.LOW)
        }

        // Fallback: Current smoothed heading if available
        if (hasInitialHeading) {
            return Triple(currentSmoothHeading, HeadingSource.LAST_KNOWN, HeadingConfidence.LOW)
        }

        return Triple(0f, HeadingSource.UNAVAILABLE, HeadingConfidence.LOW)
    }

    /**
     * Interpolates smoothly towards target angle via shortest angular path.
     * Large sudden turns (U-turns, sharp turns > 75 deg) adaptively increase responsiveness.
     */
    fun updateSmoothedHeading(targetDeg: Float): Float {
        val normalizedTarget = CompassHeadingSensor.normalizeAngle(targetDeg)
        if (!hasInitialHeading) {
            currentSmoothHeading = normalizedTarget
            hasInitialHeading = true
            return currentSmoothHeading
        }

        val delta = CompassHeadingSensor.shortestAngleDelta(currentSmoothHeading, normalizedTarget)

        // Adaptive smoothing factor: responsive during sharp maneuvers, smooth during slight drifts
        val absDelta = Math.abs(delta)
        val factor = when {
            absDelta > 90f -> 0.70f // Very fast response on U-turns / roundabouts
            absDelta > 45f -> 0.45f
            absDelta > 15f -> 0.30f
            absDelta > 0.5f -> 0.20f
            else -> 0.0f // eliminate tiny sub-degree jitter
        }

        currentSmoothHeading = CompassHeadingSensor.normalizeAngle(currentSmoothHeading + delta * factor)
        return currentSmoothHeading
    }

    /**
     * Evaluates if the vehicle is driving in the reverse direction of the active route segment.
     * Uses hysteresis (requires consecutive confirmations) to prevent false alarms during normal U-turns or stops.
     */
    fun evaluateWrongWay(
        currentHeading: Float,
        routeSegmentBearing: Float?,
        speedKmh: Float
    ): Boolean {
        if (routeSegmentBearing == null || speedKmh < 7.0f) {
            wrongWayConsecutiveTicks = 0
            return false
        }

        val angleDiff = Math.abs(CompassHeadingSensor.shortestAngleDelta(currentHeading, routeSegmentBearing))
        if (angleDiff >= 135f) {
            wrongWayConsecutiveTicks++
        } else if (angleDiff < 90f) {
            wrongWayConsecutiveTicks = 0
        }

        return wrongWayConsecutiveTicks >= 2
    }

    companion object {
        /**
         * Calculates initial bearing of the route from the starting point along the first meaningful segment
         * (at least 15 meters lookahead) to prevent GPS noise on micro-segments.
         */
        fun calculateRouteInitialBearing(geometry: List<GeoPoint>, lookaheadMeters: Double = 20.0): Float {
            if (geometry.size < 2) return 0f

            val origin = geometry.first()
            var targetPoint = geometry[1]
            var accumulated = 0.0

            for (i in 1 until geometry.size) {
                val segDist = geometry[i - 1].distanceTo(geometry[i])
                accumulated += segDist
                targetPoint = geometry[i]
                if (accumulated >= lookaheadMeters) break
            }

            return calculateBearingBetween(origin, targetPoint)
        }

        fun calculateBearingBetween(from: GeoPoint, to: GeoPoint): Float {
            val lat1 = Math.toRadians(from.latitude)
            val lon1 = Math.toRadians(from.longitude)
            val lat2 = Math.toRadians(to.latitude)
            val lon2 = Math.toRadians(to.longitude)

            val dLon = lon2 - lon1
            val y = Math.sin(dLon) * Math.cos(lat2)
            val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
            val radians = Math.atan2(y, x)
            return CompassHeadingSensor.normalizeAngle(Math.toDegrees(radians).toFloat())
        }

        fun shortestAngleDelta(from: Float, to: Float): Float = CompassHeadingSensor.shortestAngleDelta(from, to)
        fun normalizeAngle(deg: Float): Float = CompassHeadingSensor.normalizeAngle(deg)

        fun classifyDepartureTurn(vehicleHeading: Float, routeBearing: Float): DepartureTurn {
            val delta = shortestAngleDelta(vehicleHeading, routeBearing)
            return classifyDepartureTurn(delta)
        }

        /**
         * Classifies the relative angle between vehicle heading and route initial bearing into a DepartureTurn.
         */
        fun classifyDepartureTurn(relativeAngle: Float): DepartureTurn {
            return when {
                Math.abs(relativeAngle) <= 22.5f -> DepartureTurn.STRAIGHT
                relativeAngle in 22.5f..67.5f -> DepartureTurn.SLIGHT_RIGHT
                relativeAngle in 67.5f..120.0f -> DepartureTurn.RIGHT
                relativeAngle in 120.0f..155.0f -> DepartureTurn.SHARP_RIGHT
                relativeAngle in -67.5f..-22.5f -> DepartureTurn.SLIGHT_LEFT
                relativeAngle in -120.0f..-67.5f -> DepartureTurn.LEFT
                relativeAngle in -155.0f..-120.0f -> DepartureTurn.SHARP_LEFT
                else -> DepartureTurn.UTURN
            }
        }

        /**
         * Generates high-level DepartureGuidance at navigation start.
         */
        fun buildDepartureGuidance(
            vehicleHeading: Float,
            route: RouteOption,
            userPoint: GeoPoint
        ): DepartureGuidance {
            val initialBearing = calculateRouteInitialBearing(route.geometry)
            val relativeAngle = CompassHeadingSensor.shortestAngleDelta(vehicleHeading, initialBearing)
            val turn = classifyDepartureTurn(relativeAngle)

            val instruction = when (turn) {
                DepartureTurn.STRAIGHT -> "Rotaya doğru düz ilerleyin"
                DepartureTurn.SLIGHT_RIGHT -> "Rotaya katılmak için hafif sağa yönelin"
                DepartureTurn.RIGHT -> "Rotaya katılmak için sağa yönelin"
                DepartureTurn.SHARP_RIGHT -> "Rotaya katılmak için keskin sağa yönelin"
                DepartureTurn.SLIGHT_LEFT -> "Rotaya katılmak için hafif sola yönelin"
                DepartureTurn.LEFT -> "Rotaya katılmak için sola yönelin"
                DepartureTurn.SHARP_LEFT -> "Rotaya katılmak için keskin sola yönelin"
                DepartureTurn.UTURN -> "Rotaya ters yöndesiniz! Uygun yerden geriye dönün"
            }

            val guidePoints = if (route.geometry.isNotEmpty()) {
                listOf(userPoint, route.geometry.first())
            } else emptyList()

            return DepartureGuidance(
                relativeAngle = relativeAngle,
                turnType = turn,
                instruction = instruction,
                isWrongWay = (turn == DepartureTurn.UTURN),
                routeInitialBearing = initialBearing,
                vehicleHeading = vehicleHeading,
                departureGuidePoints = guidePoints
            )
        }
    }
}

