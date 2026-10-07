package com.example.haritalar.data.search

import com.example.haritalar.model.SearchResult

/**
 * Conservative cross-provider deduplication.
 *
 * Same business names in different districts must never collapse into one item.
 * Results are merged only when they share an id, or when normalized names match
 * and their coordinates are within a small physical radius.
 */
object SearchResultDeduplicator {
    private const val SAME_PLACE_RADIUS_METERS = 65.0

    fun deduplicate(results: List<SearchResult>): List<SearchResult> {
        val unique = mutableListOf<SearchResult>()
        for (item in results) {
            val duplicate = unique.any { existing -> isSamePlace(existing, item) }
            if (!duplicate) unique += item
        }
        return unique
    }

    fun isSamePlace(a: SearchResult, b: SearchResult): Boolean {
        if (a.id == b.id && a.id.isNotBlank()) return true

        val nameA = normalize(a.name)
        val nameB = normalize(b.name)
        if (nameA.isBlank() || nameB.isBlank() || nameA != nameB) return false

        return a.point.distanceTo(b.point) <= SAME_PLACE_RADIUS_METERS
    }

    private fun normalize(value: String): String =
        TurkishAddressHelper.normalizeTurkish(value)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
