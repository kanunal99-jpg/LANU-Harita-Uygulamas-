package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.TurkishAddressDetails
import java.util.Locale

/**
 * Helper utility for Turkish address parsing, character normalization,
 * abbreviation standardization, and clean presentation formatting.
 */
object TurkishAddressHelper {

    private val TR_LOCALE = Locale("tr", "TR")

    // Mapping of Turkish special characters to Latin/ASCII equivalents
    private val TURKISH_CHAR_MAP = mapOf(
        'ç' to 'c', 'Ç' to 'c',
        'ğ' to 'g', 'Ğ' to 'g',
        'ı' to 'i', 'I' to 'i', 'İ' to 'i', 'i' to 'i',
        'ö' to 'o', 'Ö' to 'o',
        'ş' to 's', 'Ş' to 's',
        'ü' to 'u', 'Ü' to 'u'
    )

    /**
     * Converts Turkish diacritics to ASCII characters and lowercases cleanly.
     * Example: "Kadıköy, Şişli, Çankaya" -> "kadikoy, sisli, cankaya"
     */
    fun normalizeTurkish(text: String): String {
        if (text.isBlank()) return ""
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val mapped = TURKISH_CHAR_MAP[ch]
            if (mapped != null) {
                sb.append(mapped)
            } else {
                sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Standardizes common Turkish address abbreviations like 'mah.', 'cad.', 'sok.', 'no: 12'
     */
    fun standardizeAddressQuery(rawQuery: String): String {
        var query = rawQuery.trim()
        if (query.isEmpty()) return ""

        // Standardize house numbers: "no: 14", "no : 14", "numara 14" -> "No:14"
        query = query.replace(Regex("(?i)\\bno\\s*[:.]?\\s*(\\d+)", RegexOption.IGNORE_CASE), "No:$1")
        query = query.replace(Regex("(?i)\\bnumara\\s+(\\d+)", RegexOption.IGNORE_CASE), "No:$1")

        // Standardize common road / administrative abbreviations
        query = query.replace(Regex("(?i)(^|\\s)(mah|mh)\\.?(\\s|$)")) { m ->
            "${m.groupValues[1]}Mahallesi${m.groupValues[3]}"
        }
        query = query.replace(Regex("(?i)(^|\\s)(cad|cd)\\.?(\\s|$)")) { m ->
            "${m.groupValues[1]}Caddesi${m.groupValues[3]}"
        }
        query = query.replace(Regex("(?i)(^|\\s)(sok|sk)\\.?(\\s|$)")) { m ->
            "${m.groupValues[1]}Sokağı${m.groupValues[3]}"
        }
        query = query.replace(Regex("(?i)(^|\\s)(blv|bulv)\\.?(\\s|$)")) { m ->
            "${m.groupValues[1]}Bulvarı${m.groupValues[3]}"
        }

        return query.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Generates a ranked list of query variations to maximize geocoding hit rate:
     * 1. Standardized query (with abbreviations expanded)
     * 2. Original query
     * 3. Normalized ASCII query (tolerance for no Turkish keyboard)
     * 4. Simplified query without punctuation
     */
    fun generateSearchQueries(rawQuery: String): List<String> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<String>()
        val standardized = standardizeAddressQuery(trimmed)
        results.add(standardized)

        if (standardized != trimmed) {
            results.add(trimmed)
        }

        val normalized = normalizeTurkish(trimmed)
        if (!results.contains(normalized) && normalized.isNotEmpty()) {
            results.add(normalized)
        }

        // Commas and extra punctuation stripped
        val cleanedPunctuation = trimmed.replace(Regex("[,;\\-\\.\\/]"), " ").replace(Regex("\\s+"), " ").trim()
        if (!results.contains(cleanedPunctuation) && cleanedPunctuation.isNotEmpty()) {
            results.add(cleanedPunctuation)
        }

        return results.distinct()
    }

    /**
     * Formats structured TurkishAddressDetails into human-readable Title and Subtitle.
     * Title: Primary name (POI name, or Street + House Number, or Neighborhood)
     * Subtitle: Administrative context (Neighborhood, District / City)
     */
    fun formatAddressParts(
        fallbackName: String,
        fallbackDisplayName: String,
        details: TurkishAddressDetails?
    ): Pair<String, String> {
        if (details == null) {
            val parts = fallbackDisplayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val title = fallbackName.ifEmpty { parts.firstOrNull() ?: "Konum" }
            val subtitle = if (parts.size > 1) parts.drop(1).take(3).joinToString(", ") else "Türkiye"
            return Pair(title, subtitle)
        }

        val hasPoi = !details.poiName.isNullOrBlank()
        val hasStreet = !details.street.isNullOrBlank()
        val hasHouseNumber = !details.houseNumber.isNullOrBlank()
        val hasNeighborhood = !details.neighborhood.isNullOrBlank()
        val hasDistrict = !details.district.isNullOrBlank()
        val hasProvince = !details.province.isNullOrBlank()

        val title = when {
            hasPoi -> details.poiName!!
            hasStreet && hasHouseNumber -> "${details.street} No:${details.houseNumber}"
            hasStreet -> details.street!!
            hasNeighborhood -> if (details.neighborhood!!.endsWith("Mahallesi", ignoreCase = true)) details.neighborhood!! else "${details.neighborhood} Mah."
            hasDistrict -> details.district!!
            hasProvince -> details.province!!
            else -> fallbackName.ifEmpty { fallbackDisplayName.split(",").firstOrNull() ?: "Konum" }
        }

        val subtitleBuilder = StringBuilder()

        // If title is POI or Street, add Neighborhood first
        if ((hasPoi || hasStreet) && hasNeighborhood) {
            val nh = if (details.neighborhood!!.endsWith("Mahallesi", ignoreCase = true) || details.neighborhood!!.endsWith("Mah.", ignoreCase = true)) {
                details.neighborhood!!
            } else {
                "${details.neighborhood} Mah."
            }
            subtitleBuilder.append(nh)
        }

        // Add District & Province (e.g. "Kadıköy, İstanbul" or "Çankaya / Ankara")
        val isDistrictAlreadyTitle = title.equals(details.district, ignoreCase = true)
        if (hasDistrict && !isDistrictAlreadyTitle) {
            if (subtitleBuilder.isNotEmpty()) subtitleBuilder.append(", ")
            subtitleBuilder.append(details.district)
        }

        if (hasProvince && !subtitleBuilder.contains(details.province!!, ignoreCase = true)) {
            if (hasDistrict && !isDistrictAlreadyTitle) {
                subtitleBuilder.append(" / ")
            } else if (subtitleBuilder.isNotEmpty()) {
                subtitleBuilder.append(", ")
            }
            subtitleBuilder.append(details.province)
        }

        val subtitle = if (subtitleBuilder.isNotEmpty()) {
            subtitleBuilder.toString()
        } else {
            val parts = fallbackDisplayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1) parts.drop(1).take(2).joinToString(", ") else "Türkiye"
        }

        return Pair(title, subtitle)
    }

    /**
     * Determines whether result matches user query taking into account Turkish diacritic variations,
     * token presence, and word ordering.
     */
    fun matchesQuery(query: String, targetText: String): Boolean {
        val normQuery = normalizeTurkish(query)
        val normTarget = normalizeTurkish(targetText)
        if (normTarget.contains(normQuery)) return true

        val queryTokens = normQuery.split(" ").filter { it.length >= 2 }
        if (queryTokens.isEmpty()) return false

        // Check if all major tokens in user query appear in the target text
        return queryTokens.all { normTarget.contains(it) }
    }
}
