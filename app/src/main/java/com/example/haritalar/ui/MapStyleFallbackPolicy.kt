package com.example.haritalar.ui

/**
 * Ordered map-style candidates used by the map renderer.
 *
 * Network styles are attempted first. The final candidate is bundled in the APK,
 * so a total network/style outage still leaves MapLibre in a valid safe state.
 */
object MapStyleFallbackPolicy {
    const val PRIMARY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val BACKUP_STYLE_URL = "https://demotiles.maplibre.org/style.json"
    const val SAFE_DEFAULT_STYLE_URI = "asset://map_style_safe_default.json"

    val candidates: List<String> = listOf(
        PRIMARY_STYLE_URL,
        BACKUP_STYLE_URL,
        SAFE_DEFAULT_STYLE_URI
    )
}
