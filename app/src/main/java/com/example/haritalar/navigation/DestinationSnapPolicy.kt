package com.example.haritalar.navigation

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint

/**
 * Broad geocoder results (neighborhood/district/city) often point to polygon centroids
 * rather than a drivable road. Once a route provider returns a snapped endpoint, this
 * policy lets the UI marker follow that endpoint without moving exact addresses/POIs.
 */
object DestinationSnapPolicy {
    const val MAX_BROAD_PLACE_SNAP_METERS = 2_500.0

    fun displayPoint(
        resultType: AddressResultType,
        geocoderPoint: GeoPoint,
        routeEndPoint: GeoPoint?
    ): GeoPoint {
        val broadResult = resultType == AddressResultType.NEIGHBORHOOD ||
            resultType == AddressResultType.DISTRICT ||
            resultType == AddressResultType.CITY ||
            resultType == AddressResultType.PLACE

        if (!broadResult || routeEndPoint == null) return geocoderPoint
        if (geocoderPoint.distanceTo(routeEndPoint) > MAX_BROAD_PLACE_SNAP_METERS) return geocoderPoint
        return routeEndPoint
    }
}
