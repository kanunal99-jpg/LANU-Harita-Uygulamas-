package com.example.haritalar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.haritalar.model.CameraMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersBottomSheet(
    cameraMode: CameraMode,
    isTrafficLayerVisible: Boolean,
    isTrafficSignalsLayerVisible: Boolean = true,
    isSafetyCamerasLayerVisible: Boolean = true,
    isPoiLayerVisible: Boolean,
    isSimulationActive: Boolean,
    hasActiveRoute: Boolean,
    offlineDownloadProgress: Float? = null,
    offlineDownloadMessage: String? = null,
    onSet2DMode: () -> Unit,
    onSet3DMode: () -> Unit,
    onToggleTrafficLayer: () -> Unit,
    onToggleTrafficSignalsLayer: () -> Unit = {},
    onToggleSafetyCamerasLayer: () -> Unit = {},
    onTogglePoiLayer: () -> Unit,
    onToggleSimulation: () -> Unit,
    onOpenTrafficInspector: () -> Unit = {},
    onDownloadOfflineMap: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("layers_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Harita Katmanları & Görünüm",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Harita görüntüsünü ve katmanlarını özelleştirin",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2D / 3D Mode Switcher
            Text(
                text = "Kamera Modu",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LayerOptionButton(
                    title = "2D Klasik",
                    subtitle = "Düz üstten görünüm (Pitch: 0°)",
                    icon = Icons.Default.Layers,
                    isSelected = cameraMode == CameraMode.TWO_D,
                    onClick = onSet2DMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("layer_2d_button")
                )
                LayerOptionButton(
                    title = "3D Perspektif",
                    subtitle = "Eğimli sürüş açısı (Pitch: 55°)",
                    icon = Icons.Default.ViewInAr,
                    isSelected = cameraMode == CameraMode.THREE_D,
                    onClick = onSet3DMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("layer_3d_button")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Traffic Layer Toggle
            LayerSwitchRow(
                title = "Trafik Katmanı",
                description = "Doğrulanmış canlı trafik yoğunluğu ve gecikmeler",
                icon = Icons.Default.Traffic,
                iconColor = Color(0xFFF59E0B),
                isChecked = isTrafficLayerVisible,
                onCheckedChange = { onToggleTrafficLayer() },
                testTag = "layer_traffic_switch"
            )

            if (isTrafficLayerVisible) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Trafik Akış Göstergeleri:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 16.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Yeşil (Akıcı)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 16.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFF59E0B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sarı (Yavaş)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 16.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFEF4444))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kırmızı (Sıkışık)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenTrafficInspector()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Trafik Kaynağı & Canlı Paket Denetçisi",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Real Traffic Signals Layer Toggle (Physical Infrastructure)
            LayerSwitchRow(
                title = "Trafik Işıkları (Sinyalizasyon)",
                description = "Gerçek OSM kavşak ve yaya geçidi sinyalizasyon ışıkları",
                icon = Icons.Default.VerticalAlignBottom,
                iconColor = Color(0xFFEF4444),
                isChecked = isTrafficSignalsLayerVisible,
                onCheckedChange = { onToggleTrafficSignalsLayer() },
                testTag = "layer_traffic_signals_switch"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Safety Cameras Layer Toggle
            LayerSwitchRow(
                title = "Radar ve Hız Kameraları",
                description = "Sabit hız kameraları ve radar uyarı sistemi",
                icon = Icons.Default.CameraAlt,
                iconColor = Color(0xFFEAB308),
                isChecked = isSafetyCamerasLayerVisible,
                onCheckedChange = { onToggleSafetyCamerasLayer() },
                testTag = "layer_safety_cameras_switch"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // POI Layer Toggle
            LayerSwitchRow(
                title = "İlgi Noktaları (POI)",
                description = "Benzinlik, eczane, hastane, restoran ve marketler",
                icon = Icons.Default.Storefront,
                iconColor = Color(0xFF6366F1),
                isChecked = isPoiLayerVisible,
                onCheckedChange = { onTogglePoiLayer() },
                testTag = "layer_poi_switch"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // GPS Simulation Mode (Demonstration / Testing for streaming emulator)
            if (hasActiveRoute) {
                LayerSwitchRow(
                    title = "Test Sürüş Simülasyonu",
                    description = "Seçili rota üzerinde sanal GPS hareketi başlatır",
                    icon = Icons.Default.DirectionsCar,
                    iconColor = Color(0xFF10B981),
                    isChecked = isSimulationActive,
                    onCheckedChange = { onToggleSimulation() },
                    testTag = "layer_simulation_switch"
                )
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Offline Download Section
            Text(
                text = "Çevrimdışı Harita",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
            
            if (offlineDownloadProgress != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                    Text(text = offlineDownloadMessage ?: "İndiriliyor...", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { offlineDownloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = onDownloadOfflineMap,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Ekranda Görünen Alanı İndir")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LayerOptionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF007AFF) else Color.Transparent
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF007AFF) else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isSelected) Color(0xFF007AFF) else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun LayerSwitchRow(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
