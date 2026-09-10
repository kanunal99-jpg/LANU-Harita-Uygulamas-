package com.example.haritalar.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages device orientation sensors (Rotation Vector / Accelerometer + Magnetometer)
 * with low-pass angular filtering to provide steady compass heading even when vehicle is stationary.
 */
class CompassHeadingSensor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = if (rotationVectorSensor == null) sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
    private val magneticSensor: Sensor? = if (rotationVectorSensor == null) sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) else null

    private val _sensorHeading = MutableStateFlow(0f)
    val sensorHeading: StateFlow<Float> = _sensorHeading.asStateFlow()

    private var currentFilteredHeading: Float = 0f
    private var hasFirstHeading = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false

    private var isListening = false

    fun start() {
        if (isListening || sensorManager == null) return
        isListening = true

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magneticSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        isListening = false
        sensorManager.unregisterListener(this)
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
            if (!hasFirstHeading) {
                currentFilteredHeading = rawDeg
                hasFirstHeading = true
                _sensorHeading.value = currentFilteredHeading
            } else {
                val diff = shortestAngleDelta(currentFilteredHeading, rawDeg)
                // Filter out small jitter (< 0.5 deg)
                if (Math.abs(diff) > 0.4f) {
                    val filterFactor = 0.22f // Smooth response
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
        fun shortestAngleDelta(from: Float, to: Float): Float {
            var diff = (to - from) % 360f
            if (diff < -180f) diff += 360f
            if (diff > 180f) diff -= 360f
            return diff
        }
    }
}
