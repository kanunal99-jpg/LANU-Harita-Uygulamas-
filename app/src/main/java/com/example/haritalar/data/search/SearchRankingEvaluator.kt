package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.HouseNumberStatus
import com.example.haritalar.model.SearchResult
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Intelligent relevance and ranking layer for Turkish address search results.
 * Prioritizes:
 * 1. Exact verified house number match (when requested)
 * 2. Exact street/avenue match
 * 3. Neighborhood match
 * 4. District match
 * 5. Province / City match
 * 6. Turkish character normalized match
 * 7. Geographic proximity to user location
 */
object SearchRankingEvaluator {

    fun rankAndEvaluateResults(
        rawResults: List<SearchResult>,
        parsedQuery: ParsedAddressQuery,
        focusPoint: GeoPoint? = null
    ): List<SearchResult> {
        if (rawResults.isEmpty()) return emptyList()

        val evaluated = rawResults.map { result ->
            var score = (result.confidence * 400).toInt()

            val details = result.addressDetails
            val hasRequestedHouseNumber = parsedQuery.isBuildingLevelRequested && !parsedQuery.houseNumber.isNullOrBlank()
            val providerHouseNumber = details?.houseNumber

            val houseNumberStatus: HouseNumberStatus
            if (hasRequestedHouseNumber) {
                if (!providerHouseNumber.isNullOrBlank() &&
                    TurkishAddressHelper.isHouseNumberMatch(parsedQuery.houseNumber, providerHouseNumber)
                ) {
                    houseNumberStatus = HouseNumberStatus.VERIFIED
                    // Highest priority: exact verified house number
                    score += 10000
                } else {
                    houseNumberStatus = HouseNumberStatus.UNVERIFIED
                    // Street found, but building number not confirmed in OSM data
                    score += 2000
                }
            } else {
                houseNumberStatus = HouseNumberStatus.NONE
                if (!providerHouseNumber.isNullOrBlank()) {
                    score += 1000
                }
            }

            // Street match
            if (!parsedQuery.street.isNullOrBlank()) {
                val queryStreetNorm = TurkishAddressHelper.normalizeTurkish(parsedQuery.street)
                val resStreetNorm = details?.street?.let { TurkishAddressHelper.normalizeTurkish(it) } ?: ""
                val resNameNorm = TurkishAddressHelper.normalizeTurkish(result.name)

                if (resStreetNorm.contains(queryStreetNorm) || resNameNorm.contains(queryStreetNorm)) {
                    score += 4000
                } else if (TurkishAddressHelper.matchesQuery(parsedQuery.street, resStreetNorm.ifBlank { resNameNorm })) {
                    score += 2500
                }
            }

            // Neighborhood match
            if (!parsedQuery.neighborhood.isNullOrBlank()) {
                val queryMahNorm = TurkishAddressHelper.normalizeTurkish(parsedQuery.neighborhood)
                val resMahNorm = details?.neighborhood?.let { TurkishAddressHelper.normalizeTurkish(it) } ?: ""
                if (resMahNorm.contains(queryMahNorm) || TurkishAddressHelper.normalizeTurkish(result.displayName).contains(queryMahNorm)) {
                    score += 2500
                }
            }

            // District match
            if (!parsedQuery.district.isNullOrBlank()) {
                val queryDistNorm = TurkishAddressHelper.normalizeTurkish(parsedQuery.district)
                val resDistNorm = details?.district?.let { TurkishAddressHelper.normalizeTurkish(it) } ?: ""
                if (resDistNorm.contains(queryDistNorm) || TurkishAddressHelper.normalizeTurkish(result.displayName).contains(queryDistNorm)) {
                    score += 2000
                }
            }

            // Province match
            if (!parsedQuery.province.isNullOrBlank()) {
                val queryProvNorm = TurkishAddressHelper.normalizeTurkish(parsedQuery.province)
                val resProvNorm = details?.province?.let { TurkishAddressHelper.normalizeTurkish(it) } ?: ""
                if (resProvNorm.contains(queryProvNorm) || TurkishAddressHelper.normalizeTurkish(result.displayName).contains(queryProvNorm)) {
                    score += 1000
                }
            }

            // Result type bonus
            when (result.resultType) {
                AddressResultType.ADDRESS -> score += 1500
                AddressResultType.STREET -> score += 1000
                AddressResultType.POI -> score += 1200
                AddressResultType.NEIGHBORHOOD -> score += 600
                AddressResultType.DISTRICT -> score += 400
                AddressResultType.CITY -> score += 200
                AddressResultType.PLACE -> score += 100
            }

            // Penalize overly generic results if specific street/building was requested
            if (hasRequestedHouseNumber &&
                (result.resultType == AddressResultType.CITY || result.resultType == AddressResultType.DISTRICT)
            ) {
                score -= 3000
            }

            // Proximity bonus
            if (focusPoint != null) {
                val distanceKm = calculateHaversineDistanceKm(
                    focusPoint.latitude, focusPoint.longitude,
                    result.point.latitude, result.point.longitude
                )
                when {
                    distanceKm < 10.0 -> score += 500
                    distanceKm < 50.0 -> score += 300
                    distanceKm < 150.0 -> score += 100
                }
            }

            // Ensure title NEVER fakes a house number when provider did not verify it
            val finalName = if (houseNumberStatus == HouseNumberStatus.UNVERIFIED && result.name.contains("No:")) {
                // If by any chance a title had No: without verified details, strip it
                result.name.replace(Regex("(?i)\\s*No:\\s*\\S+"), "").trim()
            } else {
                result.name
            }

            result.copy(
                name = finalName,
                requestedHouseNumber = if (hasRequestedHouseNumber) parsedQuery.houseNumber else null,
                houseNumberStatus = houseNumberStatus,
                relevanceScore = score
            )
        }

        return evaluated.sortedByDescending { it.relevanceScore }
    }

    private fun calculateHaversineDistanceKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371.0 // Earth radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
