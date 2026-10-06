package com.example.haritalar.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationSamplingPolicyTest {
    @Test
    fun idleModeRequiresDrivingSpeedToEnterMoving() {
        assertEquals(
            LocationSamplingPolicy.Mode.IDLE,
            LocationSamplingPolicy.nextMode(LocationSamplingPolicy.Mode.IDLE, 7.9f)
        )
        assertEquals(
            LocationSamplingPolicy.Mode.MOVING,
            LocationSamplingPolicy.nextMode(LocationSamplingPolicy.Mode.IDLE, 8.0f)
        )
    }

    @Test
    fun movingModeDoesNotChatterAtLowSpeedBoundary() {
        assertEquals(
            LocationSamplingPolicy.Mode.MOVING,
            LocationSamplingPolicy.nextMode(LocationSamplingPolicy.Mode.MOVING, 3.1f)
        )
        assertEquals(
            LocationSamplingPolicy.Mode.IDLE,
            LocationSamplingPolicy.nextMode(LocationSamplingPolicy.Mode.MOVING, 3.0f)
        )
    }

    @Test
    fun movingConfigurationIsFasterThanIdleConfiguration() {
        assertEquals(1000L, LocationSamplingPolicy.config(LocationSamplingPolicy.Mode.MOVING).intervalMillis)
        assertEquals(4000L, LocationSamplingPolicy.config(LocationSamplingPolicy.Mode.IDLE).intervalMillis)
        assertEquals(2.0f, LocationSamplingPolicy.config(LocationSamplingPolicy.Mode.MOVING).minUpdateDistanceMeters)
        assertEquals(8.0f, LocationSamplingPolicy.config(LocationSamplingPolicy.Mode.IDLE).minUpdateDistanceMeters)
    }
}
