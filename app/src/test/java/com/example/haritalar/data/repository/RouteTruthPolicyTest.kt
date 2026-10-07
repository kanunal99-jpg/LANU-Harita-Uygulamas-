package com.example.haritalar.data.repository

import com.example.haritalar.model.RouteAttributeStatus
import com.example.haritalar.model.RouteType
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTruthPolicyTest {
    @Test
    fun tollFreeLabelRequiresVerifiedAbsenceOfTolls() {
        assertEquals(
            RouteType.TOLL_FREE,
            RouteTruthPolicy.verifiedRouteType(
                RouteType.TOLL_FREE,
                RouteAttributeStatus.ABSENT,
                RouteAttributeStatus.UNKNOWN
            )
        )
        assertEquals(
            RouteType.ALTERNATIVE,
            RouteTruthPolicy.verifiedRouteType(
                RouteType.TOLL_FREE,
                RouteAttributeStatus.UNKNOWN,
                RouteAttributeStatus.UNKNOWN
            )
        )
        assertEquals(
            RouteType.ALTERNATIVE,
            RouteTruthPolicy.verifiedRouteType(
                RouteType.TOLL_FREE,
                RouteAttributeStatus.PRESENT,
                RouteAttributeStatus.UNKNOWN
            )
        )
    }

    @Test
    fun combinedFreeLabelRequiresBothFactsVerifiedAbsent() {
        assertEquals(
            RouteType.TOLL_AND_FERRY_FREE,
            RouteTruthPolicy.verifiedRouteType(
                RouteType.TOLL_AND_FERRY_FREE,
                RouteAttributeStatus.ABSENT,
                RouteAttributeStatus.ABSENT
            )
        )
        assertEquals(
            RouteType.ALTERNATIVE,
            RouteTruthPolicy.verifiedRouteType(
                RouteType.TOLL_AND_FERRY_FREE,
                RouteAttributeStatus.ABSENT,
                RouteAttributeStatus.UNKNOWN
            )
        )
    }

    @Test
    fun osrmAlternativesNeverPretendToBeShortestOrFreeByPosition() {
        assertEquals(RouteType.RECOMMENDED, RouteTruthPolicy.osrmType(0))
        assertEquals(RouteType.ALTERNATIVE, RouteTruthPolicy.osrmType(1))
        assertEquals("Önerilen Rota", RouteTruthPolicy.osrmTitle(0))
        assertEquals("Alternatif Rota 2", RouteTruthPolicy.osrmTitle(1))
        assertEquals(RouteAttributeStatus.UNKNOWN, RouteTruthPolicy.positiveOnlyStatus(false))
    }
}
