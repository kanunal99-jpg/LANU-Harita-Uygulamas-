package com.example.haritalar.data.search

/**
 * İstanbul's 39 districts grouped by continent.
 *
 * Source: İstanbul Büyükşehir Belediyesi "Yetki Alanı" district-area table.
 * This mapping is static administrative/geographic reference data and is used
 * only for labeling/filtering; coordinates still come from map providers.
 */
object IstanbulAdministrativeDirectory {
    enum class Side(val displayName: String) {
        EUROPE("Avrupa Yakası"),
        ASIA("Anadolu Yakası")
    }

    private val europe = setOf(
        "Arnavutköy", "Avcılar", "Bağcılar", "Bahçelievler", "Bakırköy",
        "Başakşehir", "Bayrampaşa", "Beşiktaş", "Beylikdüzü", "Beyoğlu",
        "Büyükçekmece", "Çatalca", "Esenler", "Esenyurt", "Eyüpsultan",
        "Fatih", "Gaziosmanpaşa", "Güngören", "Kağıthane", "Küçükçekmece",
        "Sarıyer", "Silivri", "Sultangazi", "Şişli", "Zeytinburnu"
    )

    private val asia = setOf(
        "Adalar", "Ataşehir", "Beykoz", "Çekmeköy", "Kadıköy", "Kartal",
        "Maltepe", "Pendik", "Sancaktepe", "Sultanbeyli", "Şile", "Tuzla",
        "Ümraniye", "Üsküdar"
    )

    val allDistricts: Set<String> = europe + asia

    fun sideOf(district: String): Side? {
        val normalized = TurkishAddressHelper.normalizeTurkish(district)
        return when {
            europe.any { TurkishAddressHelper.normalizeTurkish(it) == normalized } -> Side.EUROPE
            asia.any { TurkishAddressHelper.normalizeTurkish(it) == normalized } -> Side.ASIA
            else -> null
        }
    }

    fun districts(side: Side): List<String> =
        (if (side == Side.EUROPE) europe else asia).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
}
