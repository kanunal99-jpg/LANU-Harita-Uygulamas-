package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCriticalPoiPolicyTest {
    private val route = listOf(
        GeoPoint(41.0000, 29.0000),
        GeoPoint(41.0000, 29.1000),
        GeoPoint(41.0000, 29.2000)
    )

    @Test
    fun keepsCriticalServicesInsideCorridorAndSortsByRouteKm() {
        val laterFuel = PoiItem(
            id = "fuel_later",
            name = "İkinci Benzinlik",
            category = PoiCategory.FUEL,
            point = GeoPoint(41.0010, 29.1500)
        )
        val earlyPharmacy = PoiItem(
            id = "pharmacy_early",
            name = "İlk Eczane",
            category = PoiCategory.PHARMACY,
            point = GeoPoint(41.0005, 29.0300)
        )
        val farHospital = PoiItem(
            id = "hospital_far",
            name = "Uzak Hastane",
            category = PoiCategory.HOSPITAL,
            point = GeoPoint(41.0500, 29.0800)
        )
        val restaurant = PoiItem(
            id = "food",
            name = "Restoran",
            category = PoiCategory.RESTAURANT,
            point = GeoPoint(41.0002, 29.0500)
        )

        val matches = RouteCriticalPoiPolicy.matchToRoute(
            listOf(laterFuel, earlyPharmacy, farHospital, restaurant),
            route
        )

        assertEquals(listOf("pharmacy_early", "fuel_later"), matches.map { it.poi.id })
        assertTrue(matches.first().routeDistanceMeters < matches.last().routeDistanceMeters)
        assertTrue(matches.all { it.corridorDistanceMeters <= RouteCriticalPoiPolicy.ROUTE_CORRIDOR_METERS })
    }

    @Test
    fun routeSamplingKeepsStartAndDestinationAcrossLongRoutes() {
        val longRoute = listOf(
            GeoPoint(41.0, 29.0),
            GeoPoint(41.0, 29.5),
            GeoPoint(41.0, 30.0)
        )

        val centers = RouteCriticalPoiPolicy.routeSampleCenters(longRoute)

        assertEquals(longRoute.first(), centers.first())
        assertEquals(longRoute.last(), centers.last())
        assertTrue(centers.size >= 4)
    }

    @Test
    fun duplicateOsmItemsAreNotCountedTwice() {
        val poi = PoiItem(
            id = "node_42",
            name = "Şarj",
            category = PoiCategory.CHARGING_STATION,
            point = GeoPoint(41.0001, 29.0400)
        )

        val matches = RouteCriticalPoiPolicy.matchToRoute(listOf(poi, poi), route)

        assertEquals(1, matches.size)
    }
}
