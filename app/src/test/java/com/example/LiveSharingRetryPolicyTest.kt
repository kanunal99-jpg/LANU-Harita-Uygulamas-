package com.example

import com.example.haritalar.data.network.LiveSharingRetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSharingRetryPolicyTest {
    @Test
    fun transientHttpCodesRetry() {
        assertTrue(LiveSharingRetryPolicy.shouldRetryHttp(408))
        assertTrue(LiveSharingRetryPolicy.shouldRetryHttp(429))
        assertTrue(LiveSharingRetryPolicy.shouldRetryHttp(502))
        assertFalse(LiveSharingRetryPolicy.shouldRetryHttp(400))
        assertFalse(LiveSharingRetryPolicy.shouldRetryHttp(401))
    }

    @Test
    fun backoffIsBounded() {
        assertEquals(500L, LiveSharingRetryPolicy.backoffMs(1))
        assertEquals(1_500L, LiveSharingRetryPolicy.backoffMs(2))
        assertEquals(0L, LiveSharingRetryPolicy.backoffMs(3))
    }
}
