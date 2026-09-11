package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.HouseNumberStatus
import com.example.haritalar.model.TurkishAddressDetails
import java.util.Locale

/**
 * Structured breakdown of a user's Turkish address search query.
 */
data class ParsedAddressQuery(
    val rawQuery: String,
    val country: String = "Türkiye",
    val province: String? = null,
    val district: String? = null,
    val neighborhood: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val poiOrKeyword: String? = null,
    val isBuildingLevelRequested: Boolean = false
)

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

    // Complete list of 81 Turkish provinces (official names)
    val TURKISH_PROVINCES = listOf(
        "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Aksaray", "Amasya", "Ankara", "Antalya", "Ardahan",
        "Artvin", "Aydın", "Balıkesir", "Bartın", "Batman", "Bayburt", "Bilecik", "Bingöl", "Bitlis",
        "Bolu", "Burdur", "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli", "Diyarbakır", "Düzce",
        "Edirne", "Elazığ", "Erzincan", "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane",
        "Hakkari", "Hatay", "Iğdır", "Isparta", "İstanbul", "İzmir", "Kahramanmaraş", "Karabük", "Karaman",
        "Kars", "Kastamonu", "Kayseri", "Kilis", "Kırıkkale", "Kırklareli", "Kırşehir", "Kocaeli",
        "Konya", "Kütahya", "Malatya", "Manisa", "Mardin", "Mersin", "Muğla", "Muş", "Nevşehir",
        "Niğde", "Ordu", "Osmaniye", "Rize", "Sakarya", "Samsun", "Şanlıurfa", "Siirt", "Sinop",
        "Şırnak", "Sivas", "Tekirdağ", "Tokat", "Trabzon", "Tunceli", "Uşak", "Van", "Yalova",
        "Yozgat", "Zonguldak"
    )

    // Major / frequent Turkish districts
    val NOTABLE_DISTRICTS = listOf(
        "Kadıköy", "Beşiktaş", "Şişli", "Üsküdar", "Beyoğlu", "Fatih", "Bakırköy", "Sarıyer",
        "Maltepe", "Kartal", "Pendik", "Ataşehir", "Ümraniye", "Beylikdüzü", "Zeytinburnu",
        "Çankaya", "Keçiören", "Yenimahalle", "Mamak", "Etimesgut", "Sincan", "Altındağ", "Gölbaşı",
        "Konak", "Bornova", "Karşıyaka", "Buca", "Çiğli", "Gaziemir", "Balçova", "Narlıdere",
        "Nilüfer", "Osmangazi", "Yıldırım", "Muratpaşa", "Kepez", "Konyaaltı", "Alanya", "Manavgat",
        "Seyhan", "Çukurova", "Yüreğir", "Selçuklu", "Meram", "Karatay", "Şahinbey", "Şehitkamil",
        "İzmit", "Gebze", "Melikgazi", "Kocasinan", "Odunpazarı", "Tepebaşı", "Ortahisar"
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
        query = query.replace(Regex("(?i)\\bno\\s*[:.]?\\s*(\\d+(?:[/-][a-zA-Z0-9]+|[a-zA-Z])?)", RegexOption.IGNORE_CASE), "No:$1")
        query = query.replace(Regex("(?i)\\bnumara\\s+[:.]?\\s*(\\d+(?:[/-][a-zA-Z0-9]+|[a-zA-Z])?)", RegexOption.IGNORE_CASE), "No:$1")

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
     * Parses a free-form Turkish address query into structured components:
     * province, district, neighborhood, street, and house number.
     */
    fun parseAddressQuery(rawQuery: String): ParsedAddressQuery {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return ParsedAddressQuery(rawQuery = "")
        }

        var working = standardizeAddressQuery(trimmed)
        var detectedHouseNumber: String? = null
        var detectedProvince: String? = null
        var detectedDistrict: String? = null
        var detectedNeighborhood: String? = null
        var detectedStreet: String? = null

        // 1. Detect explicit "No:15" or "No:15/A" or "No: 15"
        val explicitNoRegex = Regex("(?i)\\bNo:([0-9]+(?:[/-][a-zA-Z0-9]+|[a-zA-Z])?)\\b")
        val noMatch = explicitNoRegex.find(working)
        if (noMatch != null) {
            detectedHouseNumber = noMatch.groupValues[1]
            working = working.replace(noMatch.value, "").trim()
        }

        // 2. Extract Province (İl)
        for (prov in TURKISH_PROVINCES) {
            val pNorm = normalizeTurkish(prov)
            val wNorm = normalizeTurkish(working)
            val regex = Regex("(?i)\\b$pNorm\\b")
            val match = regex.find(wNorm)
            if (match != null) {
                detectedProvince = prov
                // remove from working
                val start = match.range.first
                val end = match.range.last + 1
                // Replace in working using matched range or word boundary
                working = working.replace(Regex("(?i)\\b$prov\\b"), " ")
                    .replace(Regex("(?i)\\b$pNorm\\b"), " ")
                    .trim()
                break
            }
        }

        // 3. Extract District (İlçe) from known districts
        for (dist in NOTABLE_DISTRICTS) {
            val dNorm = normalizeTurkish(dist)
            val wNorm = normalizeTurkish(working)
            val regex = Regex("(?i)\\b$dNorm\\b")
            if (regex.containsMatchIn(wNorm)) {
                detectedDistrict = dist
                working = working.replace(Regex("(?i)\\b$dist\\b"), " ")
                    .replace(Regex("(?i)\\b$dNorm\\b"), " ")
                    .trim()
                break
            }
        }

        // 4. Extract Neighborhood (Mahalle) if marked with Mahallesi / Mah.
        val mahRegex = Regex("(?i)\\b([a-zA-ZçÇğĞıIİöÖşŞüÜ0-9\\s]+?)\\s+Mahallesi\\b")
        val mahMatch = mahRegex.find(working)
        if (mahMatch != null) {
            val rawMah = mahMatch.groupValues[1].trim().split(" ").takeLast(2).joinToString(" ")
            detectedNeighborhood = "$rawMah Mahallesi"
            working = working.replace(mahMatch.value, " ").trim()
        }

        // 5. Extract Street (Cadde, Bulvar, Sokak) with optional trailing number
        // Examples: "Moda Caddesi", "Atatürk Bulvarı", "123 Sokak", "İstiklal Caddesi"
        // Also checks if house number is immediately following the street name (e.g. "Moda Caddesi 15")
        val streetRegex = Regex("(?i)\\b((?:\\d+[.\\s]+)?[a-zA-ZçÇğĞıIİöÖşŞüÜ0-9\\s]+?)\\s+(Caddesi|Bulvarı|Sokağı|Sokak)(?:\\s+([0-9]+(?:[/-][a-zA-Z0-9]+|[a-zA-Z])?))?\\b")
        val streetMatch = streetRegex.find(working)

        if (streetMatch != null) {
            val roadNamePart = streetMatch.groupValues[1].trim().split(" ").takeLast(3).joinToString(" ")
            val roadTypePart = streetMatch.groupValues[2].trim()
            val trailingNum = streetMatch.groupValues.getOrNull(3)?.trim()

            detectedStreet = "$roadNamePart $roadTypePart"
            if (!trailingNum.isNullOrEmpty() && detectedHouseNumber == null) {
                detectedHouseNumber = trailingNum
            }
            working = working.replace(streetMatch.value, " ").trim()
        }

        // 6. If house number is still not found, check if a standalone trailing number exists at the end of query
        if (detectedHouseNumber == null) {
            val standaloneNumRegex = Regex("(?i)\\b([0-9]+(?:[/-][a-zA-Z0-9]+|[a-zA-Z])?)\\s*$")
            val endNumMatch = standaloneNumRegex.find(trimmed)
            if (endNumMatch != null) {
                val candidateNum = endNumMatch.groupValues[1]
                // Make sure it's not a numbered street like "123 Sokak" where 123 is before Sokak
                if (detectedStreet == null || !detectedStreet.contains(candidateNum)) {
                    detectedHouseNumber = candidateNum
                }
            }
        }

        // 7. Check if there's an unrecognized district or neighborhood in the remaining working string
        val remainingTokens = working.split(Regex("[,;\\s]+")).map { it.trim() }.filter { it.length >= 2 }
        if (detectedDistrict == null && remainingTokens.isNotEmpty()) {
            // First remaining word could be district if province was specified
            if (detectedProvince != null && remainingTokens.isNotEmpty()) {
                detectedDistrict = remainingTokens.first().replaceFirstChar { if (it.isLowerCase()) it.titlecase(TR_LOCALE) else it.toString() }
            }
        }

        // If street was not matched via regex (e.g. "Caferağa Moda Caddesi 15" without prior match)
        if (detectedStreet == null) {
            val altStreetRegex = Regex("(?i)([a-zA-ZçÇğĞıIİöÖşŞüÜ0-9\\s]+?\\b(Caddesi|Bulvarı|Sokağı|Sokak))")
            val altMatch = altStreetRegex.find(trimmed)
            if (altMatch != null) {
                detectedStreet = altMatch.groupValues[1].trim()
            }
        }

        // If neighborhood was not matched via "Mahallesi", but known neighborhood word is present
        if (detectedNeighborhood == null) {
            val knownNeighborhoods = listOf("Kızılay", "Caferağa", "Kazımdirik", "Moda", "Selimiye", "Karaköy", "Bebek", "Nişantaşı", "Alsancak", "Bostanlı", "Ulus")
            for (kn in knownNeighborhoods) {
                if (trimmed.contains(kn, ignoreCase = true)) {
                    detectedNeighborhood = if (kn.equals("Moda", ignoreCase = true)) "Caferağa (Moda)" else "$kn Mahallesi"
                    break
                }
            }
        }

        val hasHouseNum = !detectedHouseNumber.isNullOrBlank()

        return ParsedAddressQuery(
            rawQuery = trimmed,
            province = detectedProvince,
            district = detectedDistrict,
            neighborhood = detectedNeighborhood,
            street = detectedStreet,
            houseNumber = detectedHouseNumber,
            isBuildingLevelRequested = hasHouseNum
        )
    }

    /**
     * Generates a targeted list of query variations based on parsed address details
     * and linguistic normalization.
     */
    fun generateSearchQueries(rawQuery: String): List<String> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return emptyList()

        val parsed = parseAddressQuery(trimmed)
        val results = mutableListOf<String>()

        // 1. If house number and street are parsed, construct specific building queries
        if (parsed.street != null && parsed.houseNumber != null) {
            val streetWithNum = "${parsed.street} ${parsed.houseNumber}"
            val adminParts = listOfNotNull(parsed.neighborhood, parsed.district, parsed.province).joinToString(", ")
            if (adminParts.isNotEmpty()) {
                results.add("$streetWithNum, $adminParts")
            }
            results.add(streetWithNum)
            results.add("${parsed.street} No:${parsed.houseNumber}")
        }

        // 2. Standardized query
        val standardized = standardizeAddressQuery(trimmed)
        if (!results.contains(standardized)) {
            results.add(standardized)
        }

        // 3. Original query if different
        if (standardized != trimmed && !results.contains(trimmed)) {
            results.add(trimmed)
        }

        // 4. If building was requested, also add street-level query as safe fallback
        if (parsed.street != null && parsed.houseNumber != null) {
            val streetFallback = listOfNotNull(parsed.street, parsed.neighborhood, parsed.district, parsed.province).joinToString(", ")
            if (!results.contains(streetFallback)) {
                results.add(streetFallback)
            }
        }

        // 5. Normalized ASCII query (tolerance for keyboard without Turkish diacritics)
        val normalized = normalizeTurkish(trimmed)
        if (!results.contains(normalized) && normalized.isNotEmpty()) {
            results.add(normalized)
        }

        // 6. Commas and extra punctuation stripped
        val cleanedPunctuation = trimmed.replace(Regex("[,;\\-\\.\\/]"), " ").replace(Regex("\\s+"), " ").trim()
        if (!results.contains(cleanedPunctuation) && cleanedPunctuation.isNotEmpty()) {
            results.add(cleanedPunctuation)
        }

        return results.distinct()
    }

    /**
     * Verifies if provider's returned house number matches user's requested house number.
     * Handles sub-numbers like "15/A", "15 A", "15-B".
     */
    fun isHouseNumberMatch(requestedNumber: String?, providerHouseNumber: String?): Boolean {
        if (requestedNumber.isNullOrBlank() || providerHouseNumber.isNullOrBlank()) return false
        val reqClean = requestedNumber.trim().lowercase(TR_LOCALE).replace("no:", "").replace("no", "").trim()
        val provClean = providerHouseNumber.trim().lowercase(TR_LOCALE).replace("no:", "").replace("no", "").trim()

        if (reqClean == provClean) return true

        // Check base number matching (e.g., requested "15", provider "15/A" or requested "15/A", provider "15")
        val reqBase = reqClean.split(Regex("[/\\-\\s]")).firstOrNull() ?: reqClean
        val provBase = provClean.split(Regex("[/\\-\\s]")).firstOrNull() ?: provClean

        return reqBase == provBase && reqBase.isNotEmpty()
    }

    /**
     * Formats structured TurkishAddressDetails into human-readable Title and Subtitle.
     * Title: Primary name (POI name, or Street + House Number, or Neighborhood)
     * Subtitle: Administrative context (Neighborhood, District / City)
     *
     * IMPORTANT: Never synthesizes a house number if it is not present in details.houseNumber.
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

        if (hasProvince && (details.province != null && !subtitleBuilder.contains(details.province, ignoreCase = true))) {
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
