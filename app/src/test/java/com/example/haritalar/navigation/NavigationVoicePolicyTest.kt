package com.example.haritalar.navigation

import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.TurnManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationVoicePolicyTest {
    @Test
    fun startAndArrivalUseSingleWordLanu() {
        assertTrue(NavigationVoicePolicy.START_MESSAGE.contains("LANU"))
        assertTrue(NavigationVoicePolicy.ARRIVAL_MESSAGE.contains("LANU"))
        assertTrue(!NavigationVoicePolicy.START_MESSAGE.contains("L A N U"))
    }

    @Test
    fun cameraWarningContainsDistanceAndOverspeed() {
        val message = NavigationVoicePolicy.safetyCamera(5000, overspeed = true)
        assertEquals("Dikkat, 5 kilometre ileride radar noktası. Dikkat, hız sınırını aştınız.", message)
    }

    @Test
    fun maneuverUsesVerifiedRoadName() {
        val maneuver = TurnManeuver(
            instruction = "",
            distanceMeters = 120.0,
            type = ManeuverType.RIGHT,
            point = GeoPoint(41.0, 29.0),
            roadName = "Bağdat Caddesi"
        )
        assertEquals("Sağa dönün, Bağdat Caddesi.", NavigationVoicePolicy.maneuver(maneuver))
    }
}
