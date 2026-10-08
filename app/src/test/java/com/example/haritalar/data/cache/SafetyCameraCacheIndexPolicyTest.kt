package com.example.haritalar.data.cache

import com.example.haritalar.model.SafetyCameraBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCameraCacheIndexPolicyTest {
    private fun box(lat: Double, lon: Double): SafetyCameraBoundingBox =
        SafetyCameraBoundingBox(lat - 0.1, lon - 0.1, lat + 0.1, lon + 0.1)

    private fun area(lat: Double, lon: Double, savedAt: Long): SafetyCameraCacheIndexPolicy.Area {
        val bbox = box(lat, lon)
        return SafetyCameraCacheIndexPolicy.Area(
            key = SafetyCameraCacheIndexPolicy.keyFor(bbox),
            bbox = bbox,
            savedAtMillis = savedAt
        )
    }

    @Test
    fun geographicallySeparateAreasArePreserved() {
        val istanbul = area(41.0, 29.0, 100L)
        val ankara = area(39.9, 32.8, 200L)
        val retained = SafetyCameraCacheIndexPolicy.retain(listOf(istanbul), ankara)
        assertEquals(2, retained.size)
        assertEquals(listOf(ankara.key, istanbul.key), retained.map { it.key })
        assertEquals(istanbul.key, SafetyCameraCacheIndexPolicy.candidates(
            retained, box(41.0, 29.0), nowMillis = 300L, maxAgeMillis = 1_000L
        ).single().key)
    }

    @Test
    fun writingSameAreaUpdatesInsteadOfDuplicating() {
        val first = area(41.0, 29.0, 100L)
        val updated = area(41.0, 29.0, 200L)
        val retained = SafetyCameraCacheIndexPolicy.retain(listOf(first), updated)
        assertEquals(1, retained.size)
        assertEquals(200L, retained.single().savedAtMillis)
    }

    @Test
    fun evictionKeepsOnlyNewestFortyEightAreas() {
        val candidates = (0..60).map { n -> area(36.0 + n * 0.1, 27.0, n.toLong() + 10L) }
        val retained = candidates.dropLast(1).fold(emptyList<SafetyCameraCacheIndexPolicy.Area>()) {
                acc, item -> SafetyCameraCacheIndexPolicy.retain(acc, item)
            }
        val result = SafetyCameraCacheIndexPolicy.retain(retained, candidates.last())
        assertEquals(48, result.size)
        assertEquals(candidates.last().key, result.first().key)
        assertFalse(result.any { it.key == candidates.first().key })
    }

    @Test
    fun largeAreaPayloadIsRejectedByBudgetWithoutRemovingHealthyCache() {
        val bbox = box(41.0, 29.0)
        val healthy = SafetyCameraCacheIndexPolicy.Area(
            SafetyCameraCacheIndexPolicy.keyFor(bbox), bbox, 100L, 10_000
        )
        val hugeBox = box(39.9, 32.8)
        val huge = SafetyCameraCacheIndexPolicy.Area(
            SafetyCameraCacheIndexPolicy.keyFor(hugeBox), hugeBox, 200L,
            SafetyCameraCacheIndexPolicy.MAX_AREA_BYTES + 1
        )
        val retained = SafetyCameraCacheIndexPolicy.retain(listOf(healthy), huge)
        assertEquals(listOf(healthy.key), retained.map { it.key })
    }

    @Test
    fun totalCacheByteBudgetEvictsOldestPayloads() {
        fun bigArea(lat: Double, timestamp: Long): SafetyCameraCacheIndexPolicy.Area {
            val bbox = box(lat, 29.0)
            return SafetyCameraCacheIndexPolicy.Area(
                SafetyCameraCacheIndexPolicy.keyFor(bbox), bbox, timestamp, 180_000
            )
        }
        var retained = emptyList<SafetyCameraCacheIndexPolicy.Area>()
        (1..25).forEach { n ->
            retained = SafetyCameraCacheIndexPolicy.retain(retained, bigArea(36.0 + n * 0.1, n.toLong()))
        }
        assertTrue(retained.sumOf { it.payloadBytes.toLong() } <= SafetyCameraCacheIndexPolicy.MAX_CACHE_BYTES)
        assertEquals(16, retained.size)
        assertEquals(25L, retained.first().savedAtMillis)
    }

    @Test
    fun expiredOrFutureCacheCannotMasqueradeAsFresh() {
        val expired = area(41.0, 29.0, 100L)
        val future = area(41.0, 29.0, 900L)
        assertTrue(SafetyCameraCacheIndexPolicy.candidates(
            listOf(expired, future), box(41.0, 29.0), nowMillis = 400L, maxAgeMillis = 200L
        ).isEmpty())
    }

    @Test
    fun foreignRegionAndInvalidQueryAreNeverCacheHits() {
        val istanbul = area(41.0, 29.0, 100L)
        assertTrue(SafetyCameraCacheIndexPolicy.candidates(
            listOf(istanbul), box(39.9, 32.8), nowMillis = 150L, maxAgeMillis = 1_000L
        ).isEmpty())
        assertTrue(SafetyCameraCacheIndexPolicy.candidates(
            listOf(istanbul),
            SafetyCameraBoundingBox(50.0, 29.0, 40.0, 29.0),
            nowMillis = 150L,
            maxAgeMillis = 1_000L
        ).isEmpty())
    }
}
