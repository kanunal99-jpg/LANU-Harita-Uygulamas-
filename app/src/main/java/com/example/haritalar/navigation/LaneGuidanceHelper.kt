package com.example.haritalar.navigation

import com.example.haritalar.model.LaneInfo
import com.example.haritalar.model.ManeuverType

/**
 * Navigation guidance must only expose lane/speed-limit facts supplied by a
 * routing provider. The app never invents a lane layout from a turn type.
 */
object LaneGuidanceHelper {

    /**
     * Kept as the provider-facing hook for compatibility. Providers should pass
     * verified lane metadata here when the upstream response contains it.
     * No lane data is synthesized when upstream data is absent.
     */
    fun generateLanesForManeuver(type: ManeuverType, roadName: String = ""): List<LaneInfo> = emptyList()

    /**
     * Speed limits are source data, not something that can be safely inferred
     * from Turkish road-name conventions. Return null until a provider supplies
     * a verified limit.
     */
    fun determineSpeedLimit(roadName: String): Int? = null

    fun buildLaneVoiceHint(lanes: List<LaneInfo>): String? {
        if (lanes.isEmpty()) return null
        val activeIndices = lanes.mapIndexedNotNull { index, lane -> if (lane.isActive) index else null }
        if (activeIndices.isEmpty() || activeIndices.size == lanes.size) return null

        val total = lanes.size
        val center = (total - 1) / 2.0
        val activeCenter = activeIndices.average()
        return when {
            activeCenter > center -> "Sağ şeritleri kullanın"
            activeCenter < center -> "Sol şeritleri kullanın"
            activeIndices.size == 1 && activeIndices.first() == total - 1 -> "En sağ şeritte kalın"
            activeIndices.size == 1 && activeIndices.first() == 0 -> "En sol şeritte kalın"
            else -> "Uygun şeritte ilerleyin"
        }
    }
}
