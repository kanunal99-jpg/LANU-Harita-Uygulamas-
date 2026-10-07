package com.example.haritalar.navigation

import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationSnapPolicyTest {
    private val geocoder = GeoPoint(40.88, 29.32)

    @Test
    fun neighborhoodUsesNearbyRoutableEndpoint() {
        val roadEnd = GeoPoint(40.881, 29.321)
        assertEquals(
            roadEnd,
            DestinationSnapPolicy.displayPoint(
                AddressResultType.NEIGHBORHOOD,
                geocoder,
                roadEnd
            )
        )
    }

    @Test
    fun exactPoiIsNeverMoved() {
        val roadEnd = GeoPoint(40.881, 29.321)
        assertEquals(
            geocoder,
            DestinationSnapPolicy.displayPoint(
                AddressResultType.POI,
                geocoder,
                roadEnd
            )
        )
    }

    @Test
    fun implausiblyDistantEndpointIsRejected() {
        val farAway = GeoPoint(41.05, 29.55)
        assertEquals(
            geocoder,
            DestinationSnapPolicy.displayPoint(
                AddressResultType.NEIGHBORHOOD,
                geocoder,
                farAway
            )
        )
    }
}
