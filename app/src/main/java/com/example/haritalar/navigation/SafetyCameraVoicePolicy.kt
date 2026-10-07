package com.example.haritalar.navigation

import com.example.haritalar.model.SafetyCamera
import java.util.Locale

object SafetyCameraVoicePolicy {
    fun locationContext(camera: SafetyCamera): String? {
        val tags = camera.rawTags
        val street = firstNonBlank(
            tags["addr:street"],
            tags["street"],
            tags["road"]
        )
        if (street != null) return "$street üzerinde"

        val placeName = firstNonBlank(
            tags["name"],
            tags["description"],
            tags["location"]
        )
        if (placeName != null) return "$placeName yakınında"

        val operator = camera.operator?.trim()?.takeIf { it.isNotEmpty() }
        if (operator != null) return "$operator noktası yakınında"

        val reference = camera.reference?.trim()?.takeIf { it.isNotEmpty() }
        if (reference != null) return "$reference referanslı noktada"

        return null
    }

    fun milestoneAnnouncement(
        warning: SafetyCameraWarningPolicy.ProximityWarning
    ): String {
        val distance = formatDistance(warning.announcementMilestoneMeters ?: warning.distanceBucketMeters)
        val context = locationContext(warning.camera)
        val limitText = warning.speedLimitKmh?.let { " Hız sınırı $it kilometre saat." }.orEmpty()

        return buildString {
            append("Radar uyarısı. ")
            append("$distance ileride")
            if (context != null) append(", $context")
            append(" sabit hız kamerası var.")
            append(limitText)
        }
    }

    fun preDriveBrief(
        camerasAhead: List<SafetyCameraRouteFilterPolicy.CameraAhead>
    ): String? {
        if (camerasAhead.isEmpty()) return null

        val first = camerasAhead.first()
        val count = camerasAhead.size
        val context = locationContext(first.camera)
        val firstDistance = formatDistance(first.distanceAheadMeters)

        return buildString {
            append("Rota radar özeti. ")
            append("Seçili rota üzerinde $count sabit hız kamerası görünüyor. ")
            append("İlk kamera yaklaşık $firstDistance sonra")
            if (context != null) append(", $context")
            append(".")
            SafetyCameraWarningPolicy.parseSpeedLimitKmh(first.camera.maxSpeed)?.let {
                append(" İlk kamerada doğrulanmış hız sınırı $it kilometre saat.")
            }
        }
    }

    private fun formatDistance(distanceMeters: Int): String =
        formatDistance(distanceMeters.toDouble())

    private fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters >= 1000.0) {
            val km = distanceMeters / 1000.0
            if (kotlin.math.abs(km - km.toInt()) < 0.05) {
                "${km.toInt()} kilometre"
            } else {
                String.format(Locale.US, "%.1f kilometre", km).replace('.', ',')
            }
        } else {
            "${distanceMeters.coerceAtLeast(0.0).toInt()} metre"
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
