package com.example.haritalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.navigation.NavigationProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DrivingTopInstructionBanner(
    progress: NavigationProgress?,
    isOffRoute: Boolean,
    modifier: Modifier = Modifier
) {
    if (progress == null) return

    val maneuver = progress.currentManeuver
    val icon = getManeuverIcon(maneuver?.type)
    val distanceText = formatDistance(progress.distanceToManeuverMeters)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isOffRoute) Color(0xFFC62828) else Color(0xFF0F172A),
        contentColor = Color.White,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("driving_top_banner")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (isOffRoute) Color(0xFFB71C1C) else Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Dönüş yönü",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (isOffRoute) {
                    Text(
                        text = "Rotadan Çıkıldı",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F)
                    )
                    Text(
                        text = "Yeni rota hesaplanıyor...",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = distanceText,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF38BDF8)
                        )
                        if (progress.currentRoadName.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = progress.currentRoadName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = maneuver?.instruction ?: "Rotaya devam edin",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DrivingBottomDashboard(
    progress: NavigationProgress?,
    speedKmh: Float,
    trafficStatus: TrafficStatus?,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (progress == null) return

    val etaDate = Date(System.currentTimeMillis() + (progress.totalRemainingSeconds * 1000L))
    val etaString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(etaDate)
    val remainingMins = Math.max(1, Math.round(progress.totalRemainingSeconds / 60.0).toInt())
    val remainingDistanceText = formatDistance(progress.totalRemainingDistanceMeters)

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("driving_bottom_dashboard")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            // Speed and Traffic tag row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed gauge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${Math.round(speedKmh)} km/s",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Traffic status indicator
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (trafficStatus?.verified == true) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (trafficStatus?.verified == true) Color(0xFF2E7D32) else Color.Gray
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = trafficStatus?.message ?: "Temel ETA devrede",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (trafficStatus?.verified == true) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main ETA, Remaining Time, Distance and Stop button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$remainingMins",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF007AFF)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "dk",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF007AFF),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = remainingDistanceText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = "Tahmini Varış: $etaString",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onStopNavigation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("stop_navigation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Navigasyonu Bitir",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bitir",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private fun getManeuverIcon(type: ManeuverType?): ImageVector {
    return when (type) {
        ManeuverType.RIGHT -> Icons.Default.TurnRight
        ManeuverType.SHARP_RIGHT -> Icons.Default.TurnSharpRight
        ManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
        ManeuverType.LEFT -> Icons.Default.TurnLeft
        ManeuverType.SHARP_LEFT -> Icons.Default.TurnSharpLeft
        ManeuverType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
        ManeuverType.UTURN -> Icons.Default.Undo
        ManeuverType.ENTER_ROUNDABOUT, ManeuverType.EXIT_ROUNDABOUT -> Icons.Default.Refresh
        ManeuverType.REACH_DESTINATION -> Icons.Default.LocationOn
        ManeuverType.FERRY -> Icons.Default.DirectionsBoat
        else -> Icons.Default.Straight
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        "${meters.toInt()} m"
    }
}
