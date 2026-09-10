package com.example.haritalar.navigation

import com.example.haritalar.model.LaneDirection
import com.example.haritalar.model.LaneInfo
import com.example.haritalar.model.ManeuverType

object LaneGuidanceHelper {

    /**
     * Synthesizes or adapts realistic lane layouts for turn maneuvers.
     * Complies with modern navigation standards (Google Maps / Yandex).
     */
    fun generateLanesForManeuver(type: ManeuverType, roadName: String = ""): List<LaneInfo> {
        val isHighway = isHighwayRoad(roadName)

        return when (type) {
            ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT -> {
                if (isHighway) {
                    listOf(
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                        LaneInfo(listOf(LaneDirection.RIGHT, LaneDirection.STRAIGHT), isActive = true),
                        LaneInfo(listOf(LaneDirection.RIGHT), isActive = true)
                    )
                } else {
                    listOf(
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                        LaneInfo(listOf(LaneDirection.RIGHT), isActive = true)
                    )
                }
            }
            ManeuverType.SLIGHT_RIGHT, ManeuverType.RAMP_RIGHT -> {
                listOf(
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                    LaneInfo(listOf(LaneDirection.SLIGHT_RIGHT), isActive = true)
                )
            }
            ManeuverType.LEFT, ManeuverType.SHARP_LEFT -> {
                listOf(
                    LaneInfo(listOf(LaneDirection.LEFT), isActive = true),
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false)
                )
            }
            ManeuverType.SLIGHT_LEFT, ManeuverType.RAMP_LEFT -> {
                listOf(
                    LaneInfo(listOf(LaneDirection.SLIGHT_LEFT), isActive = true),
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false)
                )
            }
            ManeuverType.UTURN -> {
                listOf(
                    LaneInfo(listOf(LaneDirection.UTURN), isActive = true),
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false),
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = false)
                )
            }
            ManeuverType.ENTER_ROUNDABOUT, ManeuverType.EXIT_ROUNDABOUT -> {
                listOf(
                    LaneInfo(listOf(LaneDirection.LEFT, LaneDirection.STRAIGHT), isActive = true),
                    LaneInfo(listOf(LaneDirection.RIGHT, LaneDirection.STRAIGHT), isActive = true)
                )
            }
            ManeuverType.MERGE -> {
                listOf(
                    LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = true),
                    LaneInfo(listOf(LaneDirection.SLIGHT_LEFT), isActive = true)
                )
            }
            ManeuverType.STRAIGHT -> {
                if (isHighway) {
                    listOf(
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = true),
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = true),
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = true),
                        LaneInfo(listOf(LaneDirection.RIGHT), isActive = false)
                    )
                } else {
                    listOf(
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = true),
                        LaneInfo(listOf(LaneDirection.STRAIGHT), isActive = true)
                    )
                }
            }
            else -> emptyList()
        }
    }

    /**
     * Determines legal Turkish speed limits based on road naming conventions.
     */
    fun determineSpeedLimit(roadName: String): Int {
        val lower = roadName.lowercase()
        return when {
            lower.contains("otoyol") || lower.contains("kuzey marmara") || lower.startsWith("o-") -> 130
            lower.contains("tem") || lower.contains("çevre yolu") || lower.contains("e-5") || lower.contains("d-100") -> 100
            lower.contains("bulvar") || lower.contains("caddesi") || lower.contains("cad.") || lower.startsWith("d-") -> 70
            lower.contains("sokak") || lower.contains("sok.") || lower.contains("yolu") -> 50
            else -> 50
        }
    }

    private fun isHighwayRoad(roadName: String): Boolean {
        val lower = roadName.lowercase()
        return lower.contains("otoyol") ||
                lower.contains("kuzey marmara") ||
                lower.contains("tem") ||
                lower.contains("çevre") ||
                lower.startsWith("o-") ||
                lower.startsWith("d-100")
    }

    /**
     * Generates clear spoken lane instruction in Turkish.
     */
    fun buildLaneVoiceHint(lanes: List<LaneInfo>): String? {
        if (lanes.isEmpty()) return null
        val activeIndices = lanes.mapIndexedNotNull { index, lane -> if (lane.isActive) index else null }
        if (activeIndices.isEmpty() || activeIndices.size == lanes.size) return null

        val total = lanes.size
        return when {
            activeIndices.all { it >= total / 2 } -> "Sağ şeritleri kullanın"
            activeIndices.all { it < total / 2 } -> "Sol şeritleri kullanın"
            activeIndices.size == 1 && activeIndices.first() == total - 1 -> "En sağ şeritte kalın"
            activeIndices.size == 1 && activeIndices.first() == 0 -> "En sol şeritte kalın"
            else -> "Uygun şeritte ilerleyin"
        }
    }
}
