package com.example.haritalar.data.traffic

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.TrafficSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TrafficProviderChainTest {
    private val point = GeoPoint(41.0, 29.0)

    private class FakeProvider(
        override val name: String,
        override val isAvailable: Boolean = true,
        private val result: TrafficSegment? = null,
        private val failure: Boolean = false
    ) : TrafficProvider {
        var calls = 0
        override suspend fun fetchSegmentData(point: GeoPoint): TrafficSegment? {
            calls++
            if (failure) error("provider failure")
            return result
        }
    }

    private fun segment(speed: Double) = TrafficSegment(
        coordinates = listOf(point),
        currentSpeed = speed,
        freeFlowSpeed = 60.0,
        delaySeconds = 10,
        confidence = 0.9
    )

    @Test
    fun primaryExceptionFallsBackToAlternative() = runBlocking {
        val primary = FakeProvider("primary", failure = true)
        val alternative = FakeProvider("alternative", result = segment(30.0))
        val chain = TrafficProviderChain(primary, alternative)

        val result = chain.getTrafficSegment(point)

        assertSame(alternative, alternative)
        assertEquals(1, primary.calls)
        assertEquals(1, alternative.calls)
        assertEquals(30.0, result?.currentSpeed ?: 0.0, 0.001)
    }

    @Test
    fun bothProvidersFailReturnNull() = runBlocking {
        val primary = FakeProvider("primary", failure = true)
        val alternative = FakeProvider("alternative", failure = true)
        val chain = TrafficProviderChain(primary, alternative)

        assertNull(chain.getTrafficSegment(point))
        assertEquals(1, primary.calls)
        assertEquals(1, alternative.calls)
    }

    @Test
    fun verifiedCacheWinsWithoutProviderCall() = runBlocking {
        val cached = segment(45.0)
        val primary = FakeProvider("primary", result = segment(20.0))
        val cache = TrafficCache()
        cache.put(point, cached)
        val chain = TrafficProviderChain(primary, null, cache)

        val result = chain.getTrafficSegment(point)

        assertEquals(45.0, result?.currentSpeed ?: 0.0, 0.001)
        assertEquals(0, primary.calls)
    }
}
