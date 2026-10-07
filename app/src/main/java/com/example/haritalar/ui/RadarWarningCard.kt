package com.example.haritalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.navigation.SafetyCameraWarningPolicy
import java.util.Locale

@Composable
fun RadarWarningCard(
    warning: SafetyCameraWarningPolicy.ProximityWarning?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = warning != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (warning != null) {
            val camera = warning.camera
            val alertColor = if (warning.overspeed) Color(0xFFDC2626) else Color(0xFFD97706)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = alertColor,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Sabit hız kamerası erken uyarısı",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ERKEN UYARI • SABİT HIZ KAMERASI • ${formatRadarDistance(warning.distanceMeters)}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (warning.overspeed) {
                                "OSM kaynağındaki hız sınırının üzerindesin; güvenli şekilde yavaşla."
                            } else {
                                "Aktif rota üzerinde OSM'de kayıtlı sabit kamera noktası."
                            },
                            color = Color.White.copy(alpha = 0.96f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        warning.estimatedSecondsToCamera?.let { seconds ->
                            Text(
                                text = "Mevcut hızla yaklaşık ${formatEta(seconds)} sonra.",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = warning.speedLimitKmh?.let { "OSM'de kayıtlı hız sınırı: $it km/h" }
                                ?: "Hız limiti OSM kaynağında belirtilmemiş.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Kaynak: ${camera.source}",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatRadarDistance(distanceMeters: Double): String {
    return if (distanceMeters >= 1000.0) {
        String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
    } else {
        "${distanceMeters.coerceAtLeast(0.0).toInt()} m"
    }
}

private fun formatEta(seconds: Int): String {
    return if (seconds >= 60) {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        if (remainingSeconds >= 30) "${minutes + 1} dk" else "$minutes dk"
    } else {
        "$seconds sn"
    }
}
