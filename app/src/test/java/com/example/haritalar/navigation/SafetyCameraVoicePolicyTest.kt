package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCameraVoicePolicyTest {
    @Test
    fun preDriveBriefListsEveryCameraByRouteKilometerAndLocation() {
        val first = SafetyCamera(
            id = 1L,
            point = GeoPoint(41.0, 29.0),
            maxSpeed = "80",
            rawTags = mapOf(
                "lanu:nearby_road" to "D100",
                "lanu:nearby_place" to "Örnek Tesis"
            )
        )
        val second = SafetyCamera(
            id = 2L,
            point = GeoPoint(41.1, 29.0),
            rawTags = mapOf("lanu:nearby_road" to "Kuzey Marmara Otoyolu")
        )

        val announcements = SafetyCameraVoicePolicy.preDriveAnnouncements(
            cameras = listOf(
                SafetyCameraRouteFilterPolicy.RouteCamera(first, 20_000.0),
                SafetyCameraRouteFilterPolicy.RouteCamera(second, 30_000.0)
            )
        )

        assertEquals(3, announcements.size)
        assertTrue(announcements[0].contains("2 sabit hız kamerası"))
        assertTrue(announcements[1].contains("Rotanın 20 kilometresinde"))
        assertTrue(announcements[1].contains("D100 üzerinde, Örnek Tesis yakınında"))
        assertTrue(announcements[2].contains("Rotanın 30 kilometresinde"))
    }

    @Test
    fun drivingAnnouncementUsesOnlyMilestoneDistanceAndKnownLocation() {
        val camera = SafetyCamera(
            id = 3L,
            point = GeoPoint(41.0, 29.0),
            maxSpeed = "90",
            rawTags = mapOf("lanu:nearby_road" to "D100")
        )
        val warning = SafetyCameraWarningPolicy.evaluate(
            camera = camera,
            distanceMeters = 4_620.0,
            speedKmh = 100f
        )!!

        val text = SafetyCameraVoicePolicy.drivingMilestoneAnnouncement(warning)

        assertTrue(text.contains("5 kilometre sonra"))
        assertTrue(text.contains("D100 üzerinde"))
        assertTrue(text.contains("Hız sınırı 90"))
    }

    @Test
    fun resolvedAddressIsUsedWhenOsmContextIsMissing() {
        val camera = SafetyCamera(
            id = 4L,
            point = GeoPoint(41.0, 29.0)
        )

        val context = SafetyCameraVoicePolicy.locationContext(
            camera = camera,
            resolvedAddress = "D100, Fatih Mahallesi, Gebze, Kocaeli, Türkiye"
        )

        assertEquals("D100, Fatih Mahallesi, Gebze, Kocaeli konumunda", context)
    }
}
