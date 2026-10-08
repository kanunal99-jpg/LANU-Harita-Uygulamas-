package com.example.haritalar.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCameraRouteProgressPolicyTest {
    @Test
    fun firstCenterPublishesEarlyWhenMoreRouteRemains() {
        assertTrue(SafetyCameraRouteProgressPolicy.shouldPublishPartial(0, 72))
        assertFalse(SafetyCameraRouteProgressPolicy.shouldPublishPartial(1, 72))
    }

    @Test
    fun longRoutePublishesPeriodicBatches() {
        assertTrue(SafetyCameraRouteProgressPolicy.shouldPublishPartial(5, 72))
        assertTrue(SafetyCameraRouteProgressPolicy.shouldPublishPartial(11, 72))
        assertFalse(SafetyCameraRouteProgressPolicy.shouldPublishPartial(6, 72))
    }

    @Test
    fun finalCenterNeverPretendsToBePartial() {
        assertFalse(SafetyCameraRouteProgressPolicy.shouldPublishPartial(0, 1))
        assertFalse(SafetyCameraRouteProgressPolicy.shouldPublishPartial(5, 6))
        assertFalse(SafetyCameraRouteProgressPolicy.shouldPublishPartial(71, 72))
        assertFalse(SafetyCameraRouteProgressPolicy.shouldPublishPartial(-1, 72))
    }
}
