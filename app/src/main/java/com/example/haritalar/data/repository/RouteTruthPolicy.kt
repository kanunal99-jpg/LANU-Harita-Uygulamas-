package com.example.haritalar.data.repository

import com.example.haritalar.model.RouteAttributeStatus
import com.example.haritalar.model.RouteType

/**
 * Prevents provider/request intent from being presented as verified route fact.
 */
object RouteTruthPolicy {
    fun verifiedRouteType(
        requestedType: RouteType,
        tollStatus: RouteAttributeStatus,
        ferryStatus: RouteAttributeStatus
    ): RouteType = when (requestedType) {
        RouteType.TOLL_FREE ->
            if (tollStatus == RouteAttributeStatus.ABSENT) requestedType else RouteType.ALTERNATIVE
        RouteType.NO_FERRY ->
            if (ferryStatus == RouteAttributeStatus.ABSENT) requestedType else RouteType.ALTERNATIVE
        RouteType.TOLL_AND_FERRY_FREE ->
            if (tollStatus == RouteAttributeStatus.ABSENT &&
                ferryStatus == RouteAttributeStatus.ABSENT
            ) requestedType else RouteType.ALTERNATIVE
        else -> requestedType
    }

    fun osrmType(index: Int): RouteType =
        if (index == 0) RouteType.RECOMMENDED else RouteType.ALTERNATIVE

    fun osrmTitle(index: Int): String =
        if (index == 0) "Önerilen Rota" else "Alternatif Rota ${index + 1}"

    fun positiveOnlyStatus(flagPresent: Boolean): RouteAttributeStatus =
        if (flagPresent) RouteAttributeStatus.PRESENT else RouteAttributeStatus.UNKNOWN
}
