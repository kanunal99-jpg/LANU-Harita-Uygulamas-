package com.example.haritalar.data.repository

import com.example.haritalar.model.TrafficSignal
import com.example.haritalar.model.TrafficSignalBoundingBox

object TrafficSignalViewportPolicy {
    fun visibleSignals(
        bbox: TrafficSignalBoundingBox,
        zoomLevel: Float,
        signals: List<TrafficSignal>
    ): List<TrafficSignal> {
        if (!bbox.isValid() || zoomLevel < TrafficSignalRepository.MIN_ZOOM_FOR_SIGNALS) {
            return emptyList()
        }
        return signals
            .asSequence()
            .filter { bbox.contains(it.point) }
            .distinctBy { it.id }
            .toList()
    }
}
