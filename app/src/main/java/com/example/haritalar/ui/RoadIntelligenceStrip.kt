package com.example.haritalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.navigation.RoadIntelligenceEvent
import com.example.haritalar.navigation.RoadIntelligencePriority
import com.example.haritalar.navigation.RoadIntelligenceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoadIntelligenceStrip(
    events: List<RoadIntelligenceEvent>,
    modifier: Modifier = Modifier
) {
    val primary = events.firstOrNull()

    AnimatedVisibility(
        visible = primary != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (primary != null) {
            val accent = priorityColor(primary.priority)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("road_intelligence_strip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = iconFor(primary.type),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = primary.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (events.size > 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "+${events.size - 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = primary.detail,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = buildSourceLine(primary),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun iconFor(type: RoadIntelligenceType): ImageVector = when (type) {
    RoadIntelligenceType.ROAD_CLOSURE -> Icons.Default.Block
    RoadIntelligenceType.CAMERA -> Icons.Default.PhotoCamera
    RoadIntelligenceType.WEATHER -> Icons.Default.Cloud
    RoadIntelligenceType.TRAFFIC -> Icons.Default.Traffic
}

@Composable
private fun priorityColor(priority: RoadIntelligencePriority): Color = when (priority) {
    RoadIntelligencePriority.P0 -> MaterialTheme.colorScheme.error
    RoadIntelligencePriority.P1 -> Color(0xFFD97706)
    RoadIntelligencePriority.P2 -> MaterialTheme.colorScheme.primary
    RoadIntelligencePriority.P3 -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun buildSourceLine(event: RoadIntelligenceEvent): String {
    val freshness = event.updatedAtMillis?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    }
    return if (freshness != null) {
        "Kaynak: ${event.source} • güncellendi $freshness"
    } else {
        "Kaynak: ${event.source}"
    }
}
