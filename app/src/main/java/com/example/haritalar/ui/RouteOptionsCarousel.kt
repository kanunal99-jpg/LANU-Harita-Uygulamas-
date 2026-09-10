package com.example.haritalar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.model.RouteOption
import com.example.haritalar.model.TrafficSegment
import com.example.haritalar.model.TrafficStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RouteOptionsCarousel(
    routes: List<RouteOption>,
    selectedRoute: RouteOption?,
    trafficMap: Map<String, Pair<TrafficStatus, List<TrafficSegment>>>,
    onSelectRoute: (RouteOption) -> Unit,
    onStartNavigation: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (routes.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("route_options_carousel")
    ) {
        Column(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header: Title & Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Rota Alternatifleri (${routes.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "İstediğiniz rotayı seçip navigasyonu başlatın",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontal Scroll of Route Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                routes.forEach { route ->
                    val isSelected = route.routeId == selectedRoute?.routeId
                    val trafficPair = trafficMap[route.routeId]
                    val trafficStatus = trafficPair?.first

                    RouteCard(
                        route = route,
                        trafficStatus = trafficStatus,
                        isSelected = isSelected,
                        onClick = { onSelectRoute(route) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start Navigation Primary Action Button
            Button(
                onClick = onStartNavigation,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp)
                    .testTag("start_navigation_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Navigasyonu Başlat (${selectedRoute?.title ?: "En Hızlı"})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: RouteOption,
    trafficStatus: TrafficStatus?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val totalSeconds = route.totalDurationSeconds
    val mins = Math.max(1, Math.round(totalSeconds / 60.0).toInt())
    val km = String.format(Locale.US, "%.1f km", route.distanceMeters / 1000.0)

    val etaDate = Date(System.currentTimeMillis() + (totalSeconds * 1000L))
    val etaStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(etaDate)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) Color(0xFFF0F7FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF007AFF) else Color.Transparent
        ),
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() }
            .testTag("route_card_${route.routeId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & Selected check
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = route.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isSelected) Color(0xFF007AFF) else MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Seçili",
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Duration & Distance
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$mins",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " dk",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• $km",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Text(
                text = "Varış: $etaStr",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Summary / Road name
            Text(
                text = route.summary.ifEmpty { "Doğrudan güzergâh" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Feature Badges (Tolls, Ferries, Traffic)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (route.hasTolls) {
                    Badge(text = "Ücretli", bg = Color(0xFFFEF3C7), fg = Color(0xFFB45309))
                } else {
                    Badge(text = "Ücretsiz", bg = Color(0xFFDCFCE7), fg = Color(0xFF15803D))
                }

                if (route.hasFerry) {
                    Badge(text = "Feribot", bg = Color(0xFFE0E7FF), fg = Color(0xFF4338CA))
                }

                if (trafficStatus?.verified == true && trafficStatus.delaySeconds > 60) {
                    val delayMins = Math.round(trafficStatus.delaySeconds / 60.0)
                    Badge(text = "+$delayMins dk", bg = Color(0xFFFFEDD5), fg = Color(0xFFC2410C))
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
