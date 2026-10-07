package com.example.haritalar.data.network

import com.example.haritalar.model.RouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OsrmRouteMetadataPolicyTest {
    @Test
    fun onlyFirstOsrmRouteIsCalledFastest() {
        assertEquals(RouteType.FASTEST, OsrmRouteMetadataPolicy.routeTypeForIndex(0))
        assertEquals(RouteType.ALTERNATIVE, OsrmRouteMetadataPolicy.routeTypeForIndex(1))
        assertEquals(RouteType.ALTERNATIVE, OsrmRouteMetadataPolicy.routeTypeForIndex(5))
    }

    @Test
    fun osrmDoesNotClaimVerifiedTollMetadata() {
        assertFalse(OsrmRouteMetadataPolicy.TOLL_STATUS_VERIFIED)
    }
}
