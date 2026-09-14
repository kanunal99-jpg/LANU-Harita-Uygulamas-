package com.example.haritalar.data.search

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SearchResult
import com.example.haritalar.model.TurkishAddressDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Primary Geocoding Provider using OpenStreetMap Nominatim.
 * Strictly free, open, and policy compliant with a stable app User-Agent and TR bias.
 */
class NominatimSearchProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : SearchProvider {

    override val name: String = "Nominatim (OSM)"

    override suspend fun search(query: String, focusPoint: GeoPoint?): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val urlBuilder = StringBuilder("https://nominatim.openstreetmap.org/search?")
            .append("q=").append(encodedQuery)
            .append("&format=json")
            .append("&addressdetails=1")
            .append("&limit=15")
            .append("&accept-language=tr,en")
            .append("&countrycodes=tr")

        if (focusPoint != null) {
            val minLon = focusPoint.longitude - 0.8
            val maxLon = focusPoint.longitude + 0.8
            val minLat = focusPoint.latitude - 0.8
            val maxLat = focusPoint.latitude + 0.8
            urlBuilder.append("&viewbox=").append("$minLon,$maxLat,$maxLon,$minLat")
            urlBuilder.append("&bounded=0")
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("User-Agent", "LANUHaritaAndroidNav/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Nominatim HTTP Error: ${response.code} ${response.message}")
            }

            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(bodyString)
            val results = mutableListOf<SearchResult>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val displayName = item.optString("display_name", "")
                val rawName = item.optString("name", "")
                val rawType = item.optString("type", "place")
                val rawClass = item.optString("class", "")
                val osmId = item.optString("osm_id", "osm_$i")
                val importance = item.optDouble("importance", 0.5).toFloat()

                val addrObj = item.optJSONObject("address")
                val addressDetails = parseAddressDetails(addrObj, rawName, displayName)
                val resultType = determineResultType(rawClass, rawType, addressDetails)

                val (formattedTitle, formattedSubtitle) = TurkishAddressHelper.formatAddressParts(
                    fallbackName = rawName,
                    fallbackDisplayName = displayName,
                    details = addressDetails
                )

                results.add(
                    SearchResult(
                        id = osmId,
                        name = formattedTitle,
                        displayName = displayName,
                        shortAddress = formattedSubtitle,
                        point = GeoPoint(lat, lon),
                        type = rawType,
                        resultType = resultType,
                        provider = name,
                        confidence = importance.coerceIn(0.1f, 1.0f),
                        addressDetails = addressDetails
                    )
                )
            }
            results
        }
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=${point.latitude}&lon=${point.longitude}&addressdetails=1&accept-language=tr"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LANUHaritaAndroidNav/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val bodyString = response.body?.string() ?: return@withContext null
                val obj = JSONObject(bodyString)
                val displayName = obj.optString("display_name", "")
                val addrObj = obj.optJSONObject("address")
                val details = parseAddressDetails(addrObj, "", displayName)

                val (title, subtitle) = TurkishAddressHelper.formatAddressParts("", displayName, details)
                if (subtitle.isNotBlank() && title != subtitle) {
                    "$title, $subtitle"
                } else {
                    displayName.ifBlank { null }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAddressDetails(addrObj: JSONObject?, rawName: String, displayName: String): TurkishAddressDetails {
        if (addrObj == null) {
            return TurkishAddressDetails(country = "Türkiye", poiName = rawName.ifBlank { null })
        }
        val country = addrObj.optString("country", "Türkiye")
        val province = addrObj.optString("province").ifBlank {
            addrObj.optString("state").ifBlank { addrObj.optString("city").ifBlank { null } }
        }
        val district = addrObj.optString("town").ifBlank {
            addrObj.optString("county").ifBlank {
                addrObj.optString("district").ifBlank { addrObj.optString("city_district").ifBlank { null } }
            }
        }
        val neighborhood = addrObj.optString("suburb").ifBlank {
            addrObj.optString("neighbourhood").ifBlank { addrObj.optString("quarter").ifBlank { null } }
        }
        val street = addrObj.optString("road").ifBlank {
            addrObj.optString("street").ifBlank {
                addrObj.optString("pedestrian").ifBlank { addrObj.optString("footway").ifBlank { null } }
            }
        }
        val houseNumber = addrObj.optString("house_number").ifBlank {
            addrObj.optString("housenumber").ifBlank {
                addrObj.optString("street_number").ifBlank { addrObj.optString("conscriptionnumber").ifBlank { null } }
            }
        }
        val postalCode = addrObj.optString("postcode").ifBlank { null }
        val poiName = rawName.ifBlank {
            addrObj.optString("amenity").ifBlank {
                addrObj.optString("shop").ifBlank {
                    addrObj.optString("tourism").ifBlank { addrObj.optString("building").ifBlank { null } }
                }
            }
        }
        return TurkishAddressDetails(
            country = country,
            province = province,
            district = district,
            neighborhood = neighborhood,
            street = street,
            houseNumber = houseNumber,
            postalCode = postalCode,
            poiName = poiName
        )
    }

    private fun determineResultType(rawClass: String, rawType: String, details: TurkishAddressDetails): AddressResultType {
        return when {
            details.houseNumber != null -> AddressResultType.ADDRESS
            rawClass == "amenity" || rawClass == "shop" || rawClass == "tourism" || rawClass == "leisure" -> AddressResultType.POI
            rawClass == "highway" || details.street != null -> AddressResultType.STREET
            rawType == "suburb" || rawType == "neighbourhood" || rawType == "quarter" -> AddressResultType.NEIGHBORHOOD
            rawType == "town" || rawType == "county" || rawType == "city_district" -> AddressResultType.DISTRICT
            rawType == "city" || rawType == "province" || rawType == "state" -> AddressResultType.CITY
            else -> AddressResultType.PLACE
        }
    }
}
