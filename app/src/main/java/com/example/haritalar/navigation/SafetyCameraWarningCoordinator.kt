package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera

/** Session-scoped warning coordinator. Emits each 500 m bucket once per camera. */
class SafetyCameraWarningCoordinator {
    private val emittedBucketsByCamera = mutableMapOf<Long, MutableSet<Int>>()

    fun reset() = emittedBucketsByCamera.clear()

    fun evaluate(
        cameras: List<SafetyCamera>,
        point: GeoPoint,
        speedKmh: Float
    ): SafetyCameraWarningPolicy.ProximityWarning? {
        val warning = SafetyCameraWarningPolicy.nearest(cameras, point, speedKmh) ?: return null
        val emitted = emittedBucketsByCamera.getOrPut(warning.camera.id) { mutableSetOf() }
        if (!emitted.add(warning.distanceBucketMeters)) return null
        return warning
    }
}
