package com.example.haritalar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.model.TrafficSignal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficSignalDetailSheet(
    signal: TrafficSignal,
    onNavigateTo: (TrafficSignal) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("traffic_signal_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with custom Traffic Signal graphic badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Traffic Signal housing badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.size(48.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = signal.displayTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Doğrulanmış Yol Altyapısı • ${signal.source}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_close_traffic_signal_detail")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Metadata Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow(
                        icon = Icons.Default.Tag,
                        label = "OSM Düğüm (Node) ID",
                        value = "#${signal.id}"
                    )

                    DetailRow(
                        icon = Icons.Default.Place,
                        label = "Koordinatlar",
                        value = String.format(java.util.Locale.US, "%.5f, %.5f", signal.point.latitude, signal.point.longitude)
                    )

                    if (!signal.crossing.isNullOrBlank()) {
                        DetailRow(
                            icon = Icons.Default.DirectionsWalk,
                            label = "Yaya Geçidi Tipi",
                            value = when (signal.crossing.lowercase()) {
                                "traffic_signals" -> "Sinyalize yaya geçidi"
                                "marked" -> "Çizgili yaya geçidi"
                                "unmarked" -> "İşaretsiz yaya geçidi"
                                "zebra" -> "Zebra yaya geçidi"
                                "island" -> "Refüjlü / Yaya adalı geçit"
                                else -> signal.crossing
                            }
                        )
                    }

                    if (signal.hasSound) {
                        DetailRow(
                            icon = Icons.Default.VolumeUp,
                            label = "Sesli Sinyalizasyon",
                            value = "Mevcut (Görme engelli desteği)",
                            valueColor = Color(0xFF15803D)
                        )
                    }

                    if (signal.hasVibration) {
                        DetailRow(
                            icon = Icons.Default.Vibration,
                            label = "Titreşimli / Hissedilebilir Yüzey",
                            value = "Mevcut (Erişilebilirlik donanımı)",
                            valueColor = Color(0xFF15803D)
                        )
                    }

                    if (signal.hasArrow) {
                        DetailRow(
                            icon = Icons.Default.TurnRight,
                            label = "Dönüş / Ok Sinyali",
                            value = "Mevcut (Ayrı dönüş ışığı fazı)",
                            valueColor = Color(0xFF0284C7)
                        )
                    }

                    if (!signal.direction.isNullOrBlank()) {
                        DetailRow(
                            icon = Icons.Default.Navigation,
                            label = "Trafik Yönü",
                            value = signal.direction
                        )
                    }

                    if (!signal.reference.isNullOrBlank()) {
                        DetailRow(
                            icon = Icons.Default.Info,
                            label = "Referans Kodu",
                            value = signal.reference
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Button
            Button(
                onClick = { onNavigateTo(signal) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_navigate_to_traffic_signal")
            ) {
                Icon(Icons.Default.Directions, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bu Işığa Rota Çiz",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = valueColor
            )
        }
    }
}
