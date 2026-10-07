package com.example.haritalar.data.repository

object SavedPlacePolicy {
    const val MAX_SAVED_PLACES = 500
    private val VALID_CATEGORIES = setOf("HOME", "WORK", "CUSTOM")

    fun normalizeCategory(raw: String): String? =
        raw.trim().uppercase().takeIf { it in VALID_CATEGORIES }

    fun decision(
        currentCount: Int,
        category: String,
        hasExistingSingleCategory: Boolean,
        exactDuplicate: Boolean
    ): SavedPlaceResult {
        if (normalizeCategory(category) == null || currentCount < 0) return SavedPlaceResult.INVALID
        if (exactDuplicate) return SavedPlaceResult.DUPLICATE
        if (currentCount >= MAX_SAVED_PLACES && !hasExistingSingleCategory) {
            return SavedPlaceResult.CAPACITY_REACHED
        }
        return if (hasExistingSingleCategory) SavedPlaceResult.REPLACED else SavedPlaceResult.SAVED
    }
}
