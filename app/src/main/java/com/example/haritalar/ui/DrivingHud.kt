package com.example.haritalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.haritalar.model.DepartureGuidance
import com.example.haritalar.model.DepartureTurn
import com.example.haritalar.model.LaneDirection
import com.example.haritalar.model.LaneInfo
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.PoiCategory
import com.example.haritalar.model.PoiItem
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.TripSummary
import com.example.haritalar.navigation.NavigationProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DrivingTopInstructionBanner(
    progress: NavigationProgress?,
    isOffRoute: Boolean,
    isGpsWeak: Boolean = false,
    departureGuidance: DepartureGuidance? = null,
    isWrongWay: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (progress == null) return

    val maneuver = progress.currentManeuver
    val icon = getManeuverIcon(maneuver?.type)
    val distanceText = formatDistance(progress.distanceToManeuverMeters)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 1. Wrong-Way Warning Banner (High Priority Safety)
        if (isWrongWay) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFD32F2F),
                contentColor = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("wrong_way_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Ters Yön",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Yellow
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "TERS YÖN UYARISI",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Lütfen uygun ilk noktadan güvenle geriye dönün",
                            fontSize = 12.sp,
                            color = Color(0xFFFFEBEE)
                        )
                    }
                }
            }
        }

        // 2. Initial Departure Guidance Banner (Route start assistance)
        if (departureGuidance != null && departureGuidance.turnType != DepartureTurn.STRAIGHT) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0284C7),
                contentColor = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("departure_guidance_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (departureGuidance.turnType) {
                            DepartureTurn.SLIGHT_RIGHT, DepartureTurn.RIGHT, DepartureTurn.SHARP_RIGHT ->
                                Icons.AutoMirrored.Filled.ArrowForward
                            DepartureTurn.SLIGHT_LEFT, DepartureTurn.LEFT, DepartureTurn.SHARP_LEFT ->
                                Icons.AutoMirrored.Filled.ArrowBack
                            DepartureTurn.UTURN -> Icons.Default.Undo
                            else -> Icons.Default.Navigation
                        },
                        contentDescription = "Başlangıç Yönü",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Rota Başlangıcı: ${departureGuidance.instruction}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // 3. Weak GPS alert banner if needed
        if (isGpsWeak) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE65100),
                contentColor = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GPS sinyali zayıf • Mevcut rota korunuyor",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Primary Maneuver Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isOffRoute) Color(0xFFC62828) else Color(0xFF0F172A),
            contentColor = Color.White,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("driving_top_banner")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        if (isOffRoute) {
                            Text(
                                text = "Rotadan Çıkıldı",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                            Text(
                                text = "Yeni rota hesaplanıyor...",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = distanceText,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF38BDF8)
                                )
                                if (progress.currentRoadName.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = progress.currentRoadName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = maneuver?.instruction ?: "Rotaya devam edin",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Lane Guidance Bar
                if (!isOffRoute && progress.lanes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LaneGuidanceBar(lanes = progress.lanes)
                }
            }
        }
    }
}

@Composable
fun LaneGuidanceBar(lanes: List<LaneInfo>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        lanes.forEach { lane ->
            LaneIndicator(lane = lane)
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@Composable
private fun LaneIndicator(lane: LaneInfo) {
    val dir = lane.directions.firstOrNull() ?: LaneDirection.STRAIGHT
    val icon = when (dir) {
        LaneDirection.LEFT -> Icons.Default.TurnLeft
        LaneDirection.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
        LaneDirection.SHARP_LEFT -> Icons.Default.TurnSharpLeft
        LaneDirection.RIGHT -> Icons.Default.TurnRight
        LaneDirection.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
        LaneDirection.SHARP_RIGHT -> Icons.Default.TurnSharpRight
        LaneDirection.UTURN -> Icons.Default.Undo
        LaneDirection.STRAIGHT -> Icons.Default.Straight
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (lane.isActive) Color(0xFF10B981) else Color.White.copy(alpha = 0.15f),
        contentColor = if (lane.isActive) Color.White else Color.White.copy(alpha = 0.4f),
        modifier = Modifier.size(width = 34.dp, height = 34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

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

    val etaDate = Date(System.currentTimeMillis() + (progress.totalRemainingSeconds * 1000L))
    val etaString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(etaDate)
    val remainingMins = Math.max(1, Math.round(progress.totalRemainingSeconds / 60.0).toInt())
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
            // Speedometer, Speed Limit, and Action Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed Limit & Current Speed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // European / Turkish circular speed limit sign
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(3.5.dp, Color(0xFFDC2626)),
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

                    // Digital Speedometer
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isOverSpeed) Color(0xFFDC2626) else Color(0xFF0F172A))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${Math.round(speedKmh)} km/s",
                            color = if (isOverSpeed) Color.White else Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Quick Controls: Search Along Route, Mute & Traffic Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Traffic status pill (clickable to inspect live source and verify packets)
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
                                text = if (trafficStatus?.isLiveApi == true)
                                    trafficStatus.message
                                else
                                    "Trafik: Statik Profil (Doğrula)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (trafficStatus?.isLiveApi == true) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Trafik Kaynağını Doğrula",
                                tint = if (trafficStatus?.isLiveApi == true) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Search along route icon
                    IconButton(
                        onClick = onOpenSearchAlongRoute,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("search_along_route_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Rota Üzerinde Ara",
                            tint = Color(0xFF007AFF)
                        )
                    }

                    // Sound Toggle
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("driving_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Ses",
                            tint = if (isMuted) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = remainingDistanceText,
                            fontSize = 17.sp,
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
                        .height(46.dp)
                        .testTag("stop_navigation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Navigasyonu Bitir",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bitir",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SearchAlongRouteDialog(
    alongRoutePois: List<PoiItem>,
    isLoading: Boolean,
    onSelectCategory: (PoiCategory) -> Unit,
    onSelectPoi: (PoiItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rota Üzerinde Ara",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick categories
                val categories = listOf(
                    PoiCategory.FUEL,
                    PoiCategory.RESTAURANT,
                    PoiCategory.PHARMACY,
                    PoiCategory.PARKING,
                    PoiCategory.CHARGING_STATION
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onSelectCategory(cat) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (cat) {
                                        PoiCategory.FUEL -> Icons.Default.LocalGasStation
                                        PoiCategory.RESTAURANT -> Icons.Default.Restaurant
                                        PoiCategory.PHARMACY -> Icons.Default.LocalPharmacy
                                        PoiCategory.PARKING -> Icons.Default.LocalParking
                                        PoiCategory.CHARGING_STATION -> Icons.Default.EvStation
                                        else -> Icons.Default.Place
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF007AFF)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF007AFF))
                    }
                } else if (alongRoutePois.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Kategori seçin veya arama yapın",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alongRoutePois) { poi ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPoi(poi) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = poi.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = poi.address ?: poi.category.displayName,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = { onSelectPoi(poi) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Ekle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripSummaryDialog(
    summary: TripSummary,
    onDismiss: () -> Unit
) {
    val totalKm = String.format(Locale.US, "%.1f km", summary.totalDistanceMeters / 1000.0)
    val mins = Math.max(1, Math.round(summary.totalDurationSeconds / 60.0).toInt())
    val avgSpeed = String.format(Locale.US, "%.0f km/s", summary.averageSpeedKmh)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("trip_summary_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Celebration Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Varış",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Hedefe Ulaştınız!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Lanu Harita ile güvenli bir yolculuk tamamlandı.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TripStatCard(title = "Mesafe", value = totalKm, icon = Icons.Default.Route)
                    TripStatCard(title = "Süre", value = "$mins dk", icon = Icons.Default.Timer)
                    TripStatCard(title = "Ort. Hız", value = avgSpeed, icon = Icons.Default.Speed)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Destination info
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Varış Noktası",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = summary.destinationAddress,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("finish_trip_button")
                ) {
                    Text(
                        text = "Yolculuğu Tamamla",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TripStatCard(
    title: String,
    value: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF007AFF),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
