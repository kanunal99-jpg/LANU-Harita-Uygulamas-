package com.example.haritalar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.model.CameraMode

@Composable
fun FloatingMapControls(
    cameraMode: CameraMode,
    isMuted: Boolean,
    isSimulationActive: Boolean,
    onToggle2D3D: () -> Unit,
    onOpenLayers: () -> Unit,
    onToggleMute: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(end = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Simulation badge if active
        if (isSimulationActive) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10B981),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Simülasyon",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2D / 3D Quick Toggle Button
        FloatingControlButton(
            icon = if (cameraMode == CameraMode.TWO_D) Icons.Default.ViewInAr else Icons.Default.Layers,
            label = if (cameraMode == CameraMode.TWO_D) "3D" else "2D",
            contentDescription = "2D veya 3D Kamera Modu",
            onClick = onToggle2D3D,
            testTag = "toggle_2d_3d_button"
        )

        // Layers Button
        FloatingControlButton(
            icon = Icons.Default.Layers,
            contentDescription = "Katmanlar Menüsü",
            onClick = onOpenLayers,
            testTag = "open_layers_button"
        )

        // Audio Mute/Unmute Toggle
        FloatingControlButton(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (isMuted) "Sesi Aç" else "Sesi Kapat",
            tint = if (isMuted) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
            onClick = onToggleMute,
            testTag = "toggle_mute_button"
        )

        // Recenter to Current Location
        FloatingControlButton(
            icon = Icons.Default.MyLocation,
            contentDescription = "Konumuma Git",
            tint = Color(0xFF007AFF),
            onClick = onRecenter,
            testTag = "recenter_location_button"
        )
    }
}

@Composable
private fun FloatingControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    label: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(50.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label != null) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF007AFF)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
