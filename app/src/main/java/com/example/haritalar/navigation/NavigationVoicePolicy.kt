package com.example.haritalar.navigation

import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.TurnManeuver
import java.util.Locale

/** Deterministic Turkish navigation wording; no network voice dependency. */
object NavigationVoicePolicy {
    const val START_MESSAGE = "LANU Güvenli ve iyi yolculuklar dileriz."
    const val SEAT_BELT_MESSAGE = "Lütfen emniyet kemerinizi takınız. Aynalarınızı ve lastiklerinizi kontrol ediniz."
    const val ARRIVAL_MESSAGE = "Vardınız, LANU sağlıklı günler diler."

    fun safetyCamera(distanceBucketMeters: Int, overspeed: Boolean): String {
        val distance = when {
            distanceBucketMeters >= 1000 -> "${distanceBucketMeters / 1000} kilometre"
            distanceBucketMeters > 0 -> "$distanceBucketMeters metre"
            else -> "hemen ileride"
        }
        val base = "Dikkat, $distance ileride radar noktası."
        return if (overspeed) "$base Dikkat, hız sınırını aştınız." else base
    }

    fun maneuver(maneuver: TurnManeuver): String {
        val action = when (maneuver.type) {
            ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT -> "sağa dönün"
            ManeuverType.LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT -> "sola dönün"
            ManeuverType.UTURN -> "U dönüşü yapın"
            ManeuverType.ENTER_ROUNDABOUT -> "döner kavşağa girin"
            ManeuverType.EXIT_ROUNDABOUT -> "döner kavşaktan çıkın"
            ManeuverType.MERGE -> "şeride katılın"
            ManeuverType.RAMP_RIGHT -> "sağ rampaya girin"
            ManeuverType.RAMP_LEFT -> "sol rampaya girin"
            ManeuverType.FERRY -> "feribota yönelin"
            ManeuverType.REACH_DESTINATION -> ARRIVAL_MESSAGE
            ManeuverType.START, ManeuverType.STRAIGHT -> "düz devam edin"
        }
        if (maneuver.type == ManeuverType.REACH_DESTINATION) return action
        val road = maneuver.roadName.trim().takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ""
        return "${action.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("tr", "TR")) else it.toString() }}$road."
    }
}
