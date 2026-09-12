package com.example.haritalar.ui

/**
 * Ordered map-style candidates used by the map renderer.
 *
 * The renderer owns the actual network loading because MapLibre's Style.Builder
 * is callback based. Keeping the order here makes fallback behavior deterministic
 * and unit-testable without performing network calls in tests.
 */
object MapStyleFallbackPolicy {
    const val PRIMARY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val BACKUP_STYLE_URL = "https://demotiles.maplibre.org/style.json"

    val candidates: List<String> = listOf(PRIMARY_STYLE_URL, BACKUP_STYLE_URL)
}
