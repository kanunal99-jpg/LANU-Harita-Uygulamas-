package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.SafetyCamera
import com.example.haritalar.model.SafetyCameraType
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
        assertTrue(announcements[0].contains("2 denetim noktası"))
        assertTrue(announcements[1].contains("Rotanın 20 kilometresinde"))
        assertTrue(announcements[1].contains("D100 üzerinde, Örnek Tesis yakınında"))
        assertTrue(announcements[2].contains("Rotanın 30 kilometresinde"))
    }

    @Test
    fun partialRouteScanDoesNotReadOutVerifiedNoCameraStatement() {
        val announcements = SafetyCameraVoicePolicy.preDriveAnnouncements(
            cameras = emptyList(),
            coverageDegraded = true
        )
        assertEquals(1, announcements.size)
        assertTrue(announcements.first().contains("verisi alınamadı"))
        assertTrue(announcements.first().contains("kamera bulunmadığı söylenemez"))
    }

    @Test
    fun partialRouteScanWithKnownCameraExplainsIncompleteCoverage() {
        val camera = SafetyCamera(id = 117L, point = GeoPoint(41.0, 29.0))
        val announcements = SafetyCameraVoicePolicy.preDriveAnnouncements(
            cameras = listOf(SafetyCameraRouteFilterPolicy.RouteCamera(camera, 12_000.0)),
            coverageDegraded = true
        )
        assertEquals(2, announcements.size)
        assertTrue(announcements.first().contains("kısmi"))
        assertTrue(announcements.first().contains("Toplam sayı bilinmiyor"))
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
        assertTrue(text.contains("Kaynakta belirtilen hız sınırı 90"))
    }

    @Test
    fun drivingAnnouncementSpeaksRedLightCameraType() {
        val camera = SafetyCamera(
            id = 99L,
            point = GeoPoint(41.0, 29.0),
            type = SafetyCameraType.RED_LIGHT,
            rawTags = mapOf("lanu:nearby_road" to "Bağdat Caddesi")
        )
        val warning = SafetyCameraWarningPolicy.evaluate(
            camera = camera,
            distanceMeters = 980.0,
            speedKmh = 50f
        )!!

        val text = SafetyCameraVoicePolicy.drivingMilestoneAnnouncement(warning)

        assertTrue(text.contains("kırmızı ışık kamerası"))
        assertTrue(text.contains("1 kilometre sonra"))
    }

    @Test
    fun sourceLimitIsNotAnnouncedAsVerifiedRegulatoryFact() {
        val camera = SafetyCamera(
            id = 521L,
            point = GeoPoint(41.0, 29.0),
            maxSpeed = "90",
            rawTags = mapOf("lanu:nearby_road" to "D100")
        )
        val warning = SafetyCameraWarningPolicy.evaluate(camera, 900.0, 100f)!!
        val spoken = SafetyCameraVoicePolicy.drivingMilestoneAnnouncement(warning)
        assertTrue(spoken.contains("Kaynakta belirtilen hız sınırı 90"))
        assertTrue(!spoken.contains("Doğrulanmış hız sınırı"))
    }

    @Test
    fun conditionalSpeedLimitsAreNeverSpokenAsFixedLimits() {
        val camera = SafetyCamera(
            id = 522L,
            point = GeoPoint(41.0, 29.0),
            maxSpeed = "50 @ (Mo-Fr 07:00-19:00)"
        )
        val warning = SafetyCameraWarningPolicy.evaluate(camera, 900.0, 100f)!!
        val spoken = SafetyCameraVoicePolicy.drivingMilestoneAnnouncement(warning)
        assertTrue(!spoken.contains("kilometre saat"))
        val brief = SafetyCameraVoicePolicy.preDriveAnnouncements(
            listOf(SafetyCameraRouteFilterPolicy.RouteCamera(camera, 20_000.0))
        )
        assertTrue(brief.none { it.contains("kilometre saat") })
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
