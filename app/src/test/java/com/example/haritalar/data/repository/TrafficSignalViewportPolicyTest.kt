package com.example.haritalar.data.repository

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficSignalViewportPolicyTest {
    private val bbox = TrafficSignalBoundingBox(
        south = 40.8,
        west = 29.1,
        north = 41.0,
        east = 29.3
    )

    @Test
    fun lowZoomAlwaysClearsPreviouslyLoadedSignals() {
        val signals = listOf(
            TrafficSignal(1L, GeoPoint(40.9, 29.2))
        )
        assertTrue(
            TrafficSignalViewportPolicy.visibleSignals(
                bbox,
                TrafficSignalRepository.MIN_ZOOM_FOR_SIGNALS - 0.1f,
                signals
            ).isEmpty()
        )
    }

    @Test
    fun onlyCurrentViewportSignalsSurviveAndDuplicatesAreRemoved() {
        val inside = TrafficSignal(1L, GeoPoint(40.9, 29.2))
        val outside = TrafficSignal(2L, GeoPoint(41.2, 29.5))

        val visible = TrafficSignalViewportPolicy.visibleSignals(
            bbox,
            TrafficSignalRepository.MIN_ZOOM_FOR_SIGNALS,
            listOf(inside, outside, inside)
        )

        assertEquals(listOf(inside), visible)
    }
}
