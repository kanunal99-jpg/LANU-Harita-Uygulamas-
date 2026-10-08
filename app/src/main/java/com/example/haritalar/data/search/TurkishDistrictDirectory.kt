package com.example.haritalar.data.search

import java.util.Locale

/**
 * Türkiye administrative directory. 81 provinces / 973 districts with explicit parent province.
 *
 * Public-domain CC0 source: https://github.com/sabrigunes/turkey-administrative-units-dataset
 * Compiled from T.C. İçişleri Bakanlığı, snapshot October 2025 (NOT live 2026 data).
 * Istanbul side classification cross-checked against IBB:
 * https://ibb.istanbul/ibb/belediye-hakkinda/yetki-alani/
 *
 * Use these names as validation/lookup aids, not proof that an arbitrary address exists.
 */
object TurkishDistrictDirectory {
    private val locale = Locale.forLanguageTag("tr-TR")

    val districtsByProvince: Map<String, List<String>> = linkedMapOf(
        "Adana" to listOf("Aladağ", "Ceyhan", "Çukurova", "Feke", "İmamoğlu", "Karaisalı", "Karataş", "Kozan", "Pozantı", "Saimbeyli", "Sarıçam", "Seyhan", "Tufanbeyli", "Yumurtalık", "Yüreğir"),
        "Adıyaman" to listOf("Besni", "Çelikhan", "Gerger", "Gölbaşı", "Kahta", "Merkez", "Samsat", "Sincik", "Tut"),
        "Afyonkarahisar" to listOf("Başmakçı", "Bayat", "Bolvadin", "Çay", "Çobanlar", "Dazkırı", "Dinar", "Emirdağ", "Evciler", "Hocalar", "İhsaniye", "İscehisar", "Kızılören", "Merkez", "Sandıklı", "Sinanpaşa", "Şuhut", "Sultandağı"),
        "Ağrı" to listOf("Diyadin", "Doğubayazıt", "Eleşkirt", "Hamur", "Merkez", "Patnos", "Taşlıçay", "Tutak"),
        "Amasya" to listOf("Göynücek", "Gümüşhacıköy", "Hamamözü", "Merkez", "Merzifon", "Suluova", "Taşova"),
        "Ankara" to listOf("Akyurt", "Altındağ", "Ayaş", "Bala", "Beypazarı", "Çamlıdere", "Çankaya", "Çubuk", "Elmadağ", "Etimesgut", "Evren", "Gölbaşı", "Güdül", "Haymana", "Kahramankazan", "Kalecik", "Keçiören", "Kızılcahamam", "Mamak", "Nallıhan", "Polatlı", "Pursaklar", "Şereflikoçhisar", "Sincan", "Yenimahalle"),
        "Antalya" to listOf("Akseki", "Aksu", "Alanya", "Demre", "Döşemealtı", "Elmalı", "Finike", "Gazipaşa", "Gündoğmuş", "İbradı", "Kaş", "Kemer", "Kepez", "Konyaaltı", "Korkuteli", "Kumluca", "Manavgat", "Muratpaşa", "Serik"),
        "Artvin" to listOf("Ardanuç", "Arhavi", "Borçka", "Hopa", "Kemalpaşa", "Merkez", "Murgul", "Şavşat", "Yusufeli"),
        "Aydın" to listOf("Bozdoğan", "Buharkent", "Çine", "Didim", "Efeler", "Germencik", "İncirliova", "Karacasu", "Karpuzlu", "Koçarlı", "Köşk", "Kuşadası", "Kuyucak", "Nazilli", "Söke", "Sultanhisar", "Yenipazar"),
        "Balıkesir" to listOf("Altıeylül", "Ayvalık", "Balya", "Bandırma", "Bigadiç", "Burhaniye", "Dursunbey", "Edremit", "Erdek", "Gömeç", "Gönen", "Havran", "İvrindi", "Karesi", "Kepsut", "Manyas", "Marmara", "Savaştepe", "Sındırgı", "Susurluk"),
        "Bilecik" to listOf("Bozüyük", "Gölpazarı", "İnhisar", "Merkez", "Osmaneli", "Pazaryeri", "Söğüt", "Yenipazar"),
        "Bingöl" to listOf("Adaklı", "Genç", "Karlıova", "Kiğı", "Merkez", "Solhan", "Yayladere", "Yedisu"),
        "Bitlis" to listOf("Adilcevaz", "Ahlat", "Güroymak", "Hizan", "Merkez", "Mutki", "Tatvan"),
        "Bolu" to listOf("Dörtdivan", "Gerede", "Göynük", "Kıbrıscık", "Mengen", "Merkez", "Mudurnu", "Seben", "Yeniçağa"),
        "Burdur" to listOf("Ağlasun", "Altınyayla", "Bucak", "Çavdır", "Çeltikçi", "Gölhisar", "Karamanlı", "Kemer", "Merkez", "Tefenni", "Yeşilova"),
        "Bursa" to listOf("Büyükorhan", "Gemlik", "Gürsu", "Harmancık", "İnegöl", "İznik", "Karacabey", "Keles", "Kestel", "Mudanya", "Mustafakemalpaşa", "Nilüfer", "Orhaneli", "Orhangazi", "Osmangazi", "Yenişehir", "Yıldırım"),
        "Çanakkale" to listOf("Ayvacık", "Bayramiç", "Biga", "Bozcaada", "Çan", "Eceabat", "Ezine", "Gelibolu", "Gökçeada", "Lapseki", "Merkez", "Yenice"),
        "Çankırı" to listOf("Atkaracalar", "Bayramören", "Çerkeş", "Eldivan", "Ilgaz", "Kızılırmak", "Korgun", "Kurşunlu", "Merkez", "Orta", "Şabanözü", "Yapraklı"),
        "Çorum" to listOf("Alaca", "Bayat", "Boğazkale", "Dodurga", "İskilip", "Kargı", "Laçin", "Mecitözü", "Merkez", "Oğuzlar", "Ortaköy", "Osmancık", "Sungurlu", "Uğurludağ"),
        "Denizli" to listOf("Acıpayam", "Babadağ", "Baklan", "Bekilli", "Beyağaç", "Bozkurt", "Buldan", "Çal", "Çameli", "Çardak", "Çivril", "Güney", "Honaz", "Kale", "Merkezefendi", "Pamukkale", "Sarayköy", "Serinhisar", "Tavas"),
        "Diyarbakır" to listOf("Bağlar", "Bismil", "Çermik", "Çınar", "Çüngüş", "Dicle", "Eğil", "Ergani", "Hani", "Hazro", "Kayapınar", "Kocaköy", "Kulp", "Lice", "Silvan", "Sur", "Yenişehir"),
        "Edirne" to listOf("Enez", "Havsa", "İpsala", "Keşan", "Lalapaşa", "Meriç", "Merkez", "Süloğlu", "Uzunköprü"),
        "Elazığ" to listOf("Ağın", "Alacakaya", "Arıcak", "Baskil", "Karakoçan", "Keban", "Kovancılar", "Maden", "Merkez", "Palu", "Sivrice"),
        "Erzincan" to listOf("Çayırlı", "İliç", "Kemah", "Kemaliye", "Merkez", "Otlukbeli", "Refahiye", "Tercan", "Üzümlü"),
        "Erzurum" to listOf("Aşkale", "Aziziye", "Çat", "Hınıs", "Horasan", "İspir", "Karaçoban", "Karayazı", "Köprüköy", "Narman", "Oltu", "Olur", "Palandöken", "Pasinler", "Pazaryolu", "Şenkaya", "Tekman", "Tortum", "Uzundere", "Yakutiye"),
        "Eskişehir" to listOf("Alpu", "Beylikova", "Çifteler", "Günyüzü", "Han", "İnönü", "Mahmudiye", "Mihalgazi", "Mihalıççık", "Odunpazarı", "Sarıcakaya", "Seyitgazi", "Sivrihisar", "Tepebaşı"),
        "Gaziantep" to listOf("Araban", "İslahiye", "Karkamış", "Nizip", "Nurdağı", "Oğuzeli", "Şahinbey", "Şehitkamil", "Yavuzeli"),
        "Giresun" to listOf("Alucra", "Bulancak", "Çamoluk", "Çanakçı", "Dereli", "Doğankent", "Espiye", "Eynesil", "Görele", "Güce", "Keşap", "Merkez", "Piraziz", "Şebinkarahisar", "Tirebolu", "Yağlıdere"),
        "Gümüşhane" to listOf("Kelkit", "Köse", "Kürtün", "Merkez", "Şiran", "Torul"),
        "Hakkari" to listOf("Çukurca", "Derecik", "Merkez", "Şemdinli", "Yüksekova"),
        "Hatay" to listOf("Altınözü", "Antakya", "Arsuz", "Belen", "Defne", "Dörtyol", "Erzin", "Hassa", "İskenderun", "Kırıkhan", "Kumlu", "Payas", "Reyhanlı", "Samandağ", "Yayladağı"),
        "Isparta" to listOf("Aksu", "Atabey", "Eğirdir", "Gelendost", "Gönen", "Keçiborlu", "Merkez", "Şarkikaraağaç", "Senirkent", "Sütçüler", "Uluborlu", "Yalvaç", "Yenişarbademli"),
        "Mersin" to listOf("Akdeniz", "Anamur", "Aydıncık", "Bozyazı", "Çamlıyayla", "Erdemli", "Gülnar", "Mezitli", "Mut", "Silifke", "Tarsus", "Toroslar", "Yenişehir"),
        "İstanbul" to listOf("Adalar", "Arnavutköy", "Ataşehir", "Avcılar", "Bağcılar", "Bahçelievler", "Bakırköy", "Başakşehir", "Bayrampaşa", "Beşiktaş", "Beykoz", "Beylikdüzü", "Beyoğlu", "Büyükçekmece", "Çatalca", "Çekmeköy", "Esenler", "Esenyurt", "Eyüpsultan", "Fatih", "Gaziosmanpaşa", "Güngören", "Kadıköy", "Kağıthane", "Kartal", "Küçükçekmece", "Maltepe", "Pendik", "Sancaktepe", "Sarıyer", "Şile", "Silivri", "Şişli", "Sultanbeyli", "Sultangazi", "Tuzla", "Ümraniye", "Üsküdar", "Zeytinburnu"),
        "İzmir" to listOf("Aliağa", "Balçova", "Bayındır", "Bayraklı", "Bergama", "Beydağ", "Bornova", "Buca", "Çeşme", "Çiğli", "Dikili", "Foça", "Gaziemir", "Güzelbahçe", "Karabağlar", "Karaburun", "Karşıyaka", "Kemalpaşa", "Kınık", "Kiraz", "Konak", "Menderes", "Menemen", "Narlıdere", "Ödemiş", "Seferihisar", "Selçuk", "Tire", "Torbalı", "Urla"),
        "Kars" to listOf("Akyaka", "Arpaçay", "Digor", "Kağızman", "Merkez", "Sarıkamış", "Selim", "Susuz"),
        "Kastamonu" to listOf("Abana", "Ağlı", "Araç", "Azdavay", "Bozkurt", "Çatalzeytin", "Cide", "Daday", "Devrekani", "Doğanyurt", "Hanönü", "İhsangazi", "İnebolu", "Küre", "Merkez", "Pınarbaşı", "Şenpazar", "Seydiler", "Taşköprü", "Tosya"),
        "Kayseri" to listOf("Akkışla", "Bünyan", "Develi", "Felahiye", "Hacılar", "İncesu", "Kocasinan", "Melikgazi", "Özvatan", "Pınarbaşı", "Sarıoğlan", "Sarız", "Talas", "Tomarza", "Yahyalı", "Yeşilhisar"),
        "Kırklareli" to listOf("Babaeski", "Demirköy", "Kofçaz", "Lüleburgaz", "Merkez", "Pehlivanköy", "Pınarhisar", "Vize"),
        "Kırşehir" to listOf("Akçakent", "Akpınar", "Boztepe", "Çiçekdağı", "Kaman", "Merkez", "Mucur"),
        "Kocaeli" to listOf("Başiskele", "Çayırova", "Darıca", "Derince", "Dilovası", "Gebze", "Gölcük", "İzmit", "Kandıra", "Karamürsel", "Kartepe", "Körfez"),
        "Konya" to listOf("Ahırlı", "Akören", "Akşehir", "Altınekin", "Beyşehir", "Bozkır", "Çeltik", "Cihanbeyli", "Çumra", "Derbent", "Derebucak", "Doğanhisar", "Emirgazi", "Ereğli", "Güneysınır", "Hadim", "Halkapınar", "Hüyük", "Ilgın", "Kadınhanı", "Karapınar", "Karatay", "Kulu", "Meram", "Sarayönü", "Selçuklu", "Seydişehir", "Taşkent", "Tuzlukçu", "Yalıhüyük", "Yunak"),
        "Kütahya" to listOf("Altıntaş", "Aslanapa", "Çavdarhisar", "Domaniç", "Dumlupınar", "Emet", "Gediz", "Hisarcık", "Merkez", "Pazarlar", "Şaphane", "Simav", "Tavşanlı"),
        "Malatya" to listOf("Akçadağ", "Arapgir", "Arguvan", "Battalgazi", "Darende", "Doğanşehir", "Doğanyol", "Hekimhan", "Kale", "Kuluncak", "Pütürge", "Yazıhan", "Yeşilyurt"),
        "Manisa" to listOf("Ahmetli", "Akhisar", "Alaşehir", "Demirci", "Gölmarmara", "Gördes", "Kırkağaç", "Köprübaşı", "Kula", "Salihli", "Sarıgöl", "Saruhanlı", "Şehzadeler", "Selendi", "Soma", "Turgutlu", "Yunusemre"),
        "Kahramanmaraş" to listOf("Afşin", "Andırın", "Çağlayancerit", "Dulkadiroğlu", "Ekinözü", "Elbistan", "Göksun", "Nurhak", "Onikişubat", "Pazarcık", "Türkoğlu"),
        "Mardin" to listOf("Artuklu", "Dargeçit", "Derik", "Kızıltepe", "Mazıdağı", "Midyat", "Nusaybin", "Ömerli", "Savur", "Yeşilli"),
        "Muğla" to listOf("Bodrum", "Dalaman", "Datça", "Fethiye", "Kavaklıdere", "Köyceğiz", "Marmaris", "Menteşe", "Milas", "Ortaca", "Seydikemer", "Ula", "Yatağan"),
        "Muş" to listOf("Bulanık", "Hasköy", "Korkut", "Malazgirt", "Merkez", "Varto"),
        "Nevşehir" to listOf("Acıgöl", "Avanos", "Derinkuyu", "Gülşehir", "Hacıbektaş", "Kozaklı", "Merkez", "Ürgüp"),
        "Niğde" to listOf("Altunhisar", "Bor", "Çamardı", "Çiftlik", "Merkez", "Ulukışla"),
        "Ordu" to listOf("Akkuş", "Altınordu", "Aybastı", "Çamaş", "Çatalpınar", "Çaybaşı", "Fatsa", "Gölköy", "Gülyalı", "Gürgentepe", "İkizce", "Kabadüz", "Kabataş", "Korgan", "Kumru", "Mesudiye", "Perşembe", "Ulubey", "Ünye"),
        "Rize" to listOf("Ardeşen", "Çamlıhemşin", "Çayeli", "Derepazarı", "Fındıklı", "Güneysu", "Hemşin", "İkizdere", "İyidere", "Kalkandere", "Merkez", "Pazar"),
        "Sakarya" to listOf("Adapazarı", "Akyazı", "Arifiye", "Erenler", "Ferizli", "Geyve", "Hendek", "Karapürçek", "Karasu", "Kaynarca", "Kocaali", "Pamukova", "Sapanca", "Serdivan", "Söğütlü", "Taraklı"),
        "Samsun" to listOf("19 Mayıs", "Alaçam", "Asarcık", "Atakum", "Ayvacık", "Bafra", "Canik", "Çarşamba", "Havza", "İlkadım", "Kavak", "Ladik", "Salıpazarı", "Tekkeköy", "Terme", "Vezirköprü", "Yakakent"),
        "Siirt" to listOf("Baykan", "Eruh", "Kurtalan", "Merkez", "Pervari", "Şirvan", "Tillo"),
        "Sinop" to listOf("Ayancık", "Boyabat", "Dikmen", "Durağan", "Erfelek", "Gerze", "Merkez", "Saraydüzü", "Türkeli"),
        "Sivas" to listOf("Akıncılar", "Altınyayla", "Divriği", "Doğanşar", "Gemerek", "Gölova", "Gürün", "Hafik", "İmranlı", "Kangal", "Koyulhisar", "Merkez", "Şarkışla", "Suşehri", "Ulaş", "Yıldızeli", "Zara"),
        "Tekirdağ" to listOf("Çerkezköy", "Çorlu", "Ergene", "Hayrabolu", "Kapaklı", "Malkara", "Marmaraereğlisi", "Muratlı", "Saray", "Şarköy", "Süleymanpaşa"),
        "Tokat" to listOf("Almus", "Artova", "Başçiftlik", "Erbaa", "Merkez", "Niksar", "Pazar", "Reşadiye", "Sulusaray", "Turhal", "Yeşilyurt", "Zile"),
        "Trabzon" to listOf("Akçaabat", "Araklı", "Arsin", "Beşikdüzü", "Çarşıbaşı", "Çaykara", "Dernekpazarı", "Düzköy", "Hayrat", "Köprübaşı", "Maçka", "Of", "Ortahisar", "Şalpazarı", "Sürmene", "Tonya", "Vakfıkebir", "Yomra"),
        "Tunceli" to listOf("Çemişgezek", "Hozat", "Mazgirt", "Merkez", "Nazımiye", "Ovacık", "Pertek", "Pülümür"),
        "Şanlıurfa" to listOf("Akçakale", "Birecik", "Bozova", "Ceylanpınar", "Eyyübiye", "Halfeti", "Haliliye", "Harran", "Hilvan", "Karaköprü", "Siverek", "Suruç", "Viranşehir"),
        "Uşak" to listOf("Banaz", "Eşme", "Karahallı", "Merkez", "Sivaslı", "Ulubey"),
        "Van" to listOf("Bahçesaray", "Başkale", "Çaldıran", "Çatak", "Edremit", "Erciş", "Gevaş", "Gürpınar", "İpekyolu", "Muradiye", "Özalp", "Saray", "Tuşba"),
        "Yozgat" to listOf("Akdağmadeni", "Aydıncık", "Boğazlıyan", "Çandır", "Çayıralan", "Çekerek", "Kadışehri", "Merkez", "Saraykent", "Sarıkaya", "Şefaatli", "Sorgun", "Yenifakılı", "Yerköy"),
        "Zonguldak" to listOf("Alaplı", "Çaycuma", "Devrek", "Ereğli", "Gökçebey", "Kilimli", "Kozlu", "Merkez"),
        "Aksaray" to listOf("Ağaçören", "Eskil", "Gülağaç", "Güzelyurt", "Merkez", "Ortaköy", "Sarıyahşi", "Sultanhanı"),
        "Bayburt" to listOf("Aydıntepe", "Demirözü", "Merkez"),
        "Karaman" to listOf("Ayrancı", "Başyayla", "Ermenek", "Kazımkarabekir", "Merkez", "Sarıveliler"),
        "Kırıkkale" to listOf("Bahşılı", "Balışeyh", "Çelebi", "Delice", "Karakeçili", "Keskin", "Merkez", "Sulakyurt", "Yahşihan"),
        "Batman" to listOf("Beşiri", "Gercüş", "Hasankeyf", "Kozluk", "Merkez", "Sason"),
        "Şırnak" to listOf("Beytüşşebap", "Cizre", "Güçlükonak", "İdil", "Merkez", "Silopi", "Uludere"),
        "Bartın" to listOf("Amasra", "Kurucaşile", "Merkez", "Ulus"),
        "Ardahan" to listOf("Çıldır", "Damal", "Göle", "Hanak", "Merkez", "Posof"),
        "Iğdır" to listOf("Aralık", "Karakoyunlu", "Merkez", "Tuzluca"),
        "Yalova" to listOf("Altınova", "Armutlu", "Çiftlikköy", "Çınarcık", "Merkez", "Termal"),
        "Karabük" to listOf("Eflani", "Eskipazar", "Merkez", "Ovacık", "Safranbolu", "Yenice"),
        "Kilis" to listOf("Elbeyli", "Merkez", "Musabeyli", "Polateli"),
        "Osmaniye" to listOf("Bahçe", "Düziçi", "Hasanbeyli", "Kadirli", "Merkez", "Sumbas", "Toprakkale"),
        "Düzce" to listOf("Akçakoca", "Çilimli", "Cumayeri", "Gölyaka", "Gümüşova", "Kaynaşlı", "Merkez", "Yığılca"),
    )

    val istanbulEuropeanDistricts: Set<String> = setOf("Arnavutköy", "Avcılar", "Bağcılar", "Bahçelievler", "Bakırköy", "Başakşehir", "Bayrampaşa", "Beşiktaş", "Beylikdüzü", "Beyoğlu", "Büyükçekmece", "Çatalca", "Esenler", "Esenyurt", "Eyüpsultan", "Fatih", "Gaziosmanpaşa", "Güngören", "Kağıthane", "Küçükçekmece", "Sarıyer", "Silivri", "Sultangazi", "Şişli", "Zeytinburnu")
    val istanbulAnatolianDistricts: Set<String> = setOf("Adalar", "Ataşehir", "Beykoz", "Çekmeköy", "Kadıköy", "Kartal", "Maltepe", "Pendik", "Sancaktepe", "Sultanbeyli", "Şile", "Tuzla", "Ümraniye", "Üsküdar")

    enum class IstanbulSide { EUROPEAN, ANATOLIAN }

    fun istanbulSideOf(district: String): IstanbulSide? = when (normalize(district)) {
        in istanbulEuropeanNormalized -> IstanbulSide.EUROPEAN
        in istanbulAnatolianNormalized -> IstanbulSide.ANATOLIAN
        else -> null
    }

    fun isDistrictOfProvince(district: String, province: String): Boolean =
        districtsByProvince.entries.firstOrNull { normalize(it.key) == normalize(province) }
            ?.value?.any { normalize(it) == normalize(district) } == true

    /**
     * Match only full district tokens. Without province, ambiguous names like 'Merkez'
     * are rejected instead of assigning them to the wrong province.
     */
    fun findDistrict(query: String, province: String?): String? {
        val normalizedQuery = " " + normalize(query).replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ").trim() + " "
        val options = if (province != null) {
            districtsByProvince.entries.firstOrNull { normalize(it.key) == normalize(province) }?.value.orEmpty()
        } else {
            unambiguousDistrictNames
        }
        return options.asSequence()
            .filter { normalizedQuery.contains(" " + normalize(it).replace(Regex("[^a-z0-9]+"), " ") + " ") }
            .maxByOrNull { normalize(it).length }
    }

    private fun normalize(text: String): String = TurkishAddressHelper.normalizeTurkish(text)
    private val istanbulEuropeanNormalized by lazy { istanbulEuropeanDistricts.map(::normalize).toSet() }
    private val istanbulAnatolianNormalized by lazy { istanbulAnatolianDistricts.map(::normalize).toSet() }
    private val unambiguousDistrictNames: List<String> by lazy {
        districtsByProvince.values.flatten().groupBy(::normalize)
            .filterValues { it.size == 1 }
            .values.map { it.first() }
    }
}
