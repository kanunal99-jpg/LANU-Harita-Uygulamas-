package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.navigation.NavigationProgress
import com.example.haritalar.ui.formatDistance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Final navigation dashboard. Real speed limits only; no synthetic 50 km/h fallback. */
@Composable
fun DrivingBottomDashboard(
    progress: NavigationProgress?,
    speedKmh: Float,
    trafficStatus: TrafficStatus?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onOpenSearchAlongRoute: () -> Unit,
    onOpenTrafficInspector: () -> Unit = {},
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (progress == null) return

    val etaDate = Date(System.currentTimeMillis() + progress.totalRemainingSeconds * 1000L)
    val etaString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(etaDate)
    val remainingMins = max(1, (progress.totalRemainingSeconds / 60.0).roundToInt())
    val remainingDistanceText = formatDistance(progress.totalRemainingDistanceMeters)
    val speedLimit = progress.speedLimitKmh
    val isOverSpeed = speedLimit != null && speedKmh > (speedLimit + 5)

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
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(3.5.dp, Color(0xFFDC2626)),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = speedLimit?.toString() ?: "—",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isOverSpeed) Color(0xFFDC2626) else Color(0xFF0F172A))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${speedKmh.roundToInt()} km/h",
                            color = if (isOverSpeed) Color.White else Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (trafficStatus?.isLiveApi == true) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenTrafficInspector() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (trafficStatus?.isLiveApi == true) Color(0xFF2E7D32) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (trafficStatus?.isLiveApi == true) trafficStatus.message else "Trafik: doğrulanmadı",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (trafficStatus?.isLiveApi == true) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Trafik kaynağı",
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onOpenSearchAlongRoute, modifier = Modifier.size(36.dp).testTag("search_along_route_button")) {
                        Icon(Icons.Default.Search, contentDescription = "Rota Üzerinde Ara")
                    }
                    IconButton(onClick = onToggleMute, modifier = Modifier.size(36.dp).testTag("driving_mute_button")) {
                        Icon(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Ses"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$remainingMins", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFF007AFF))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("dk", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(remainingDistanceText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Tahmini Varış: $etaString", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = onStopNavigation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(46.dp).testTag("stop_navigation_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Navigasyonu Bitir", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bitir", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
