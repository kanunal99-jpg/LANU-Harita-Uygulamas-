package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResult
import com.example.haritalar.model.TurkishAddressDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Alternative/Fallback Geocoding Provider using Komoot Photon. */
class PhotonSearchProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : SearchProvider {
    override val name: String = "Photon (OSM)"

    override suspend fun search(query: String, focusPoint: GeoPoint?): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val urlBuilder = StringBuilder("https://photon.komoot.io/api/?q=").append(encodedQuery).append("&limit=15")
        if (focusPoint != null) {
            urlBuilder.append("&lat=").append(focusPoint.latitude)
            urlBuilder.append("&lon=").append(focusPoint.longitude)
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("User-Agent", "LANUHaritaAndroidNav/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Photon HTTP Error: ${response.code} ${response.message}")

            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(bodyString)
            val features = root.optJSONArray("features") ?: return@withContext emptyList()
            val results = mutableListOf<SearchResult>()

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val geometry = feature.optJSONObject("geometry") ?: continue
                val coords = geometry.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue

                val lon = coords.optDouble(0, Double.NaN)
                val lat = coords.optDouble(1, Double.NaN)
                if (lon.isNaN() || lat.isNaN()) continue

                val properties = feature.optJSONObject("properties") ?: continue
                val countryCode = properties.optString("countrycode", "").uppercase()
                if (countryCode.isNotEmpty() && countryCode != "TR") continue

                val osmId = properties.optString("osm_id", "photon_$i")
                val osmKey = properties.optString("osm_key", "")
                val osmValue = properties.optString("osm_value", "")
                val rawName = properties.optString("name", "")
                val country = properties.optString("country", "Türkiye")
                val state = properties.optString("state").ifBlank { null }
                val city = properties.optString("city").ifBlank { null }
                val district = properties.optString("district").ifBlank { null }
                val locality = properties.optString("locality").ifBlank { null }
                val street = properties.optString("street").ifBlank { null }
                val houseNumber = properties.optString("housenumber").ifBlank { null }
                val postcode = properties.optString("postcode").ifBlank { null }
                val province = state ?: city
                val effectiveDistrict = if (state != null && city != null) city else district
                val effectiveNeighborhood = locality ?: if (state != null && city != null) district else null

                val details = TurkishAddressDetails(
                    country = country,
                    province = province,
                    district = effectiveDistrict,
                    neighborhood = effectiveNeighborhood,
                    street = street,
                    houseNumber = houseNumber,
                    postalCode = postcode,
                    poiName = if (street != null && rawName != street) rawName else null
                )

                val (title, subtitle) = TurkishAddressHelper.formatAddressParts(
                    fallbackName = rawName,
                    fallbackDisplayName = listOfNotNull(rawName, street, district, province).joinToString(", "),
                    details = details
                )

                results.add(
                    SearchResult(
                        id = "photon_$osmId",
                        name = title,
                        displayName = listOfNotNull(title, subtitle, country).joinToString(", "),
                        shortAddress = subtitle,
                        point = GeoPoint(lat, lon),
                        type = osmValue.ifBlank { "place" },
                        resultType = determineResultType(osmKey, osmValue, details),
                        provider = name,
                        confidence = 0.85f,
                        addressDetails = details
                    )
                )
            }
            results
        }
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://photon.komoot.io/reverse?lat=${point.latitude}&lon=${point.longitude}"
            val request = Request.Builder().url(url).header("User-Agent", "LANUHaritaAndroidNav/1.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                val root = JSONObject(bodyString)
                val features = root.optJSONArray("features") ?: return@withContext null
                if (features.length() == 0) return@withContext null
                val props = features.getJSONObject(0).optJSONObject("properties") ?: return@withContext null
                val name = props.optString("name", "")
                val street = props.optString("street", "")
                val district = props.optString("district", props.optString("city", ""))
                val state = props.optString("state", "")
                listOfNotNull(name.ifBlank { null }, street.ifBlank { null }, district.ifBlank { null }, state.ifBlank { null })
                    .distinct().joinToString(", ").ifBlank { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun determineResultType(osmKey: String, osmValue: String, details: TurkishAddressDetails): AddressResultType {
        return when {
            details.houseNumber != null -> AddressResultType.ADDRESS
            osmKey in listOf("amenity", "shop", "tourism", "leisure", "office", "historic") -> AddressResultType.POI
            osmKey == "highway" || details.street != null -> AddressResultType.STREET
            osmValue in listOf("suburb", "neighbourhood", "quarter") || details.neighborhood != null -> AddressResultType.NEIGHBORHOOD
            osmValue in listOf("district", "county", "town") || details.district != null -> AddressResultType.DISTRICT
            osmValue in listOf("city", "province") || details.province != null -> AddressResultType.CITY
            else -> AddressResultType.PLACE
        }
    }
}
