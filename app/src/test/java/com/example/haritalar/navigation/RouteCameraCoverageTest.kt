package com.example.haritalar.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCameraCoverageTest {
    @Test
    fun onlyEveryLiveSourceResponseCanVerifyWholeRoute() {
        val coverage = RouteCameraCoverage(routeId = "r1", totalCenters = 3)
            .withProviderSuccess(fromCache = false)
            .withProviderSuccess(fromCache = false)
            .withProviderSuccess(fromCache = false)
            .copy(finished = true)
        assertTrue(coverage.fullyVerified)
        assertFalse(coverage.coverageDegraded)
        assertEquals(3, coverage.verifiedCenters)
    }

    @Test
    fun anyProviderFailureKeepsRouteUnverifiedAfterLoopFinishes() {
        val coverage = RouteCameraCoverage(routeId = "r1", totalCenters = 2)
            .withProviderSuccess(fromCache = false)
            .withProviderFailure()
            .copy(finished = true)
        assertTrue(coverage.coverageDegraded)
        assertFalse(coverage.fullyVerified)
        assertEquals(1, coverage.failedCenters)
    }

    @Test
    fun cachedLastKnownGoodDoesNotPretendToBeLiveVerified() {
        val coverage = RouteCameraCoverage(routeId = "r1", totalCenters = 2)
            .withProviderSuccess(fromCache = false)
            .withProviderSuccess(fromCache = true)
            .copy(finished = true)
        assertTrue(coverage.coverageDegraded)
        assertEquals(1, coverage.cachedCenters)
    }

    @Test
    fun incompleteScanNeverClaimsFullRouteCovered() {
        val coverage = RouteCameraCoverage(routeId = "r1", totalCenters = 3)
            .withProviderSuccess(fromCache = false)
        assertFalse(coverage.fullyVerified)
        assertFalse(coverage.coverageDegraded)
        val failedScan = coverage.finishWithRemainingFailures()
        assertEquals(2, failedScan.failedCenters)
        assertTrue(failedScan.coverageDegraded)
        assertEquals(3, failedScan.checkedCenters)
    }
}
