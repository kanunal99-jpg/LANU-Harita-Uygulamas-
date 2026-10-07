package com.example.haritalar.data.search

/**
 * Small public-source directory for places whose official address is known but
 * may not yet be indexed under the same brand name by OSM geocoders.
 *
 * Coordinates are never invented: SearchProviderChain resolves these addresses
 * through the normal geocoding providers before returning a navigable result.
 */
data class VerifiedPlaceEntry(
    val id: String,
    val name: String,
    val address: String,
    val province: String,
    val district: String,
    val street: String,
    val aliases: Set<String>,
    val sourceUrl: String
)

object VerifiedPlaceDirectory {
    private val entries = listOf(
        VerifiedPlaceEntry(
            id = "verified_global_donuk_gida",
            name = "Global Donuk Gıda",
            address = "Osmangazi Mahallesi, Melikşah Sokak, No:33, Sancaktepe, İstanbul",
            province = "İstanbul",
            district = "Sancaktepe",
            street = "Melikşah Sokak",
            aliases = setOf(
                "global donuk gıda",
                "global donuk gida",
                "global donuk",
                "globaldonukgida"
            ),
            sourceUrl = "https://globaldonukgida.com/"
        )
    )

    fun findMatches(query: String): List<VerifiedPlaceEntry> {
        val normalized = TurkishAddressHelper.normalizeTurkish(query)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.length < 4) return emptyList()

        val queryTokens = normalized.split(" ").filter { it.length >= 3 }.toSet()
        return entries.filter { entry ->
            entry.aliases.any { alias ->
                val aliasNorm = TurkishAddressHelper.normalizeTurkish(alias)
                    .replace(Regex("\\s+"), " ")
                    .trim()
                normalized == aliasNorm ||
                    (queryTokens.size >= 2 && queryTokens.count { aliasNorm.contains(it) } >= 2)
            }
        }
    }
}
