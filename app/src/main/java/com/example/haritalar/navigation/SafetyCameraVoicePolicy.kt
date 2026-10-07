package com.example.haritalar.navigation

import com.example.haritalar.model.SafetyCamera
import java.util.Locale

object SafetyCameraVoicePolicy {
    fun locationContext(
        camera: SafetyCamera,
        resolvedAddress: String? = null
    ): String {
        val tags = camera.rawTags
        val road = firstNonBlank(
            tags["lanu:nearby_road"],
            tags["addr:street"],
            tags["street"],
            tags["road"]
        )
        val place = firstNonBlank(
            tags["lanu:nearby_place"],
            tags["name"],
            tags["description"],
            tags["location"]
        )

        if (road != null && place != null) return "$road üzerinde, $place yakınında"
        if (road != null) return "$road üzerinde"
        if (place != null) return "$place yakınında"

        val address = resolvedAddress
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(4)
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() }
        if (address != null) return "$address konumunda"

        val operator = camera.operator?.trim()?.takeIf { it.isNotEmpty() }
        if (operator != null) return "$operator noktası yakınında"

        val reference = camera.reference?.trim()?.takeIf { it.isNotEmpty() }
        if (reference != null) return "$reference referanslı noktada"

        return String.format(
            Locale.US,
            "%.5f enlem, %.5f boylam konumunda",
            camera.point.latitude,
            camera.point.longitude
        ).replace('.', ',')
    }

    fun drivingMilestoneAnnouncement(
        warning: SafetyCameraWarningPolicy.ProximityWarning,
        resolvedAddress: String? = null
    ): String {
        val distance = formatDistance(
            warning.announcementMilestoneMeters ?: warning.distanceBucketMeters
        )
        val context = locationContext(warning.camera, resolvedAddress)
        val limitText = warning.speedLimitKmh
            ?.let { " Hız sınırı $it kilometre saat." }
            .orEmpty()

        return buildString {
            append("Radar uyarısı. ")
            append("$distance sonra, $context sabit hız kamerası var.")
            append(limitText)
        }
    }

    fun preDriveAnnouncements(
        cameras: List<SafetyCameraRouteFilterPolicy.RouteCamera>,
        resolvedAddresses: Map<Long, String> = emptyMap()
    ): List<String> {
        if (cameras.isEmpty()) {
            return listOf("Rota radar özeti. Seçili güzergâhta doğrulanmış sabit hız kamerası görünmüyor.")
        }

        val result = mutableListOf<String>()
        result += "Rota radar özeti. Seçili güzergâhta ${cameras.size} sabit hız kamerası görünüyor."

        cameras.forEachIndexed { index, item ->
            val context = locationContext(
                camera = item.camera,
                resolvedAddress = resolvedAddresses[item.camera.id]
            )
            val routeKm = formatRouteKilometer(item.routeDistanceMeters)
            val speedLimit = SafetyCameraWarningPolicy.parseSpeedLimitKmh(item.camera.maxSpeed)

            result += buildString {
                append("${index + 1}. kamera. ")
                append("Rotanın $routeKm kilometresinde, ")
                append("$context sabit hız kamerası var.")
                if (speedLimit != null) {
                    append(" Hız sınırı $speedLimit kilometre saat.")
                }
            }
        }
        return result
    }

    private fun formatRouteKilometer(distanceMeters: Double): String {
        val km = distanceMeters.coerceAtLeast(0.0) / 1000.0
        val nearestWhole = kotlin.math.round(km)
        return if (kotlin.math.abs(km - nearestWhole) < 0.05) {
            nearestWhole.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", km).replace('.', ',')
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
