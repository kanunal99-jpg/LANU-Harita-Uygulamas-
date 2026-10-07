package com.example.haritalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.navigation.LanuBriefItem
import com.example.haritalar.navigation.LanuBriefItemType
import com.example.haritalar.navigation.LanuBriefSeverity
import com.example.haritalar.navigation.LanuBriefStatus
import com.example.haritalar.navigation.LanuDriveBrief
import java.util.Locale

@Composable
fun LanuBriefCard(
    brief: LanuDriveBrief?,
    modifier: Modifier = Modifier
) {
    if (brief == null) return
    var expanded by remember(brief.routeId) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LANU Brief",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = formatRouteSummary(brief),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Kapat" else "Detay")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val headlineItems = brief.items
                .filter { it.type != LanuBriefItemType.DATA_QUALITY }
                .sortedByDescending { severityRank(it.severity) }
                .take(3)

            headlineItems.forEach { item ->
                BriefItemRow(item = item, compact = true)
                Spacer(modifier = Modifier.height(6.dp))
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    brief.items
                        .filterNot { headlineItems.contains(it) }
                        .forEach { item ->
                            BriefItemRow(item = item, compact = false)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                    Text(
                        text = "Kaynak durumu: ${brief.verifiedItemCount} doğrulanmış • ${brief.partialItemCount} kısmi • ${brief.unavailableItemCount} doğrulanamadı",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BriefItemRow(
    item: LanuBriefItem,
    compact: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = iconFor(item.type),
            contentDescription = null,
            tint = colorFor(item.severity, item.status),
            modifier = Modifier
                .size(if (compact) 17.dp else 19.dp)
                .padding(top = 1.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    text = item.detail,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${statusLabel(item.status)} • ${item.source}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                )
            }
        }
    }
}

private fun iconFor(type: LanuBriefItemType) = when (type) {
    LanuBriefItemType.TRAFFIC -> Icons.Default.Traffic
    LanuBriefItemType.CAMERA -> Icons.Default.PhotoCamera
    LanuBriefItemType.WEATHER -> Icons.Default.Cloud
    LanuBriefItemType.TOLL -> Icons.Default.Payments
    LanuBriefItemType.FERRY -> Icons.Default.DirectionsBoat
    LanuBriefItemType.DATA_QUALITY -> Icons.Default.VerifiedUser
}

@Composable
private fun colorFor(
    severity: LanuBriefSeverity,
    status: LanuBriefStatus
): Color {
    if (status == LanuBriefStatus.UNAVAILABLE) {
        return MaterialTheme.colorScheme.onSurfaceVariant
    }
    return when (severity) {
        LanuBriefSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        LanuBriefSeverity.WARNING -> Color(0xFFEA580C)
        LanuBriefSeverity.NOTICE -> Color(0xFFD97706)
        LanuBriefSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
}

private fun statusLabel(status: LanuBriefStatus): String = when (status) {
    LanuBriefStatus.VERIFIED -> "Doğrulanmış"
    LanuBriefStatus.PARTIAL -> "Kısmi kapsama"
    LanuBriefStatus.UNAVAILABLE -> "Doğrulanamadı"
}

private fun severityRank(severity: LanuBriefSeverity): Int = when (severity) {
    LanuBriefSeverity.INFO -> 0
    LanuBriefSeverity.NOTICE -> 1
    LanuBriefSeverity.WARNING -> 2
    LanuBriefSeverity.CRITICAL -> 3
}

private fun formatRouteSummary(brief: LanuDriveBrief): String {
    val km = String.format(Locale.US, "%.1f km", brief.distanceMeters / 1000.0)
    val mins = (brief.totalDurationSeconds / 60L).coerceAtLeast(1L)
    return "$km • $mins dk • sürüş öncesi kontrol"
}
