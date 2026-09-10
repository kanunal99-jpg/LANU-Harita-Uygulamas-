package com.example.haritalar.data.network

import com.example.haritalar.model.GeoPoint

object PolylineDecoder {
    /**
     * Decodes a 6-digit precision polyline (as used by Valhalla).
     */
    fun decodePolyline6(encoded: String): List<GeoPoint> {
        return decode(encoded, 1e6)
    }

    /**
     * Decodes a 5-digit precision polyline (standard Google / OSRM).
     */
    fun decodePolyline5(encoded: String): List<GeoPoint> {
        return decode(encoded, 1e5)
    }

    private fun decode(encoded: String, precision: Double): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val pLat = lat / precision
            val pLng = lng / precision
            poly.add(GeoPoint(pLat, pLng))
        }

        return poly
    }
}
