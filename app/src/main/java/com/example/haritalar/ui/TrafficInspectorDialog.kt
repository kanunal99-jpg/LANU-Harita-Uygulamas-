package com.example.haritalar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haritalar.model.TrafficStatus
import com.example.haritalar.model.TrafficTestResult
import com.example.haritalar.data.traffic.TrafficTrendPredictor
import com.example.haritalar.model.TrafficLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficInspectorDialog(
    trafficStatus: TrafficStatus?,
    currentApiKey: String,
    testResult: TrafficTestResult?,
    isTesting: Boolean,
    onSaveApiKey: (String) -> Unit,
    onTestConnection: () -> Unit,
    onDismiss: () -> Unit
) {
    var inputKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("traffic_inspector_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Trafik Veri Kaynağı & Denetçi",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Gerçek cihaz canlı veri şeffaflığı",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 1: Active Source Status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (trafficStatus?.isLiveApi == true)
                        Color(0xFFE8F5E9)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aktif Navigasyon Kaynağı",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (trafficStatus?.isLiveApi == true) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (trafficStatus?.isLiveApi == true) Color(0xFF2E7D32) else Color(0xFF546E7A)
                        ) {
                            Text(
                                text = if (trafficStatus?.isLiveApi == true) "CANLI AKIŞ AKTİF" else "STATİK YOL PROFİLİ",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = trafficStatus?.sourceName ?: "OSRM / Valhalla Statik Yol Profili",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = trafficStatus?.message ?: "Canlı trafik durumu bekleniyor",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (trafficStatus?.isLiveApi == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Doğrulanan Segment: ${trafficStatus.segmentCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1B5E20)
                            )
                            if (trafficStatus.averageSpeedKmh != null) {
                                Text(
                                    text = "Ort. Hız: ${trafficStatus.averageSpeedKmh.toInt()} km/s",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: TomTom API Key Configuration
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TomTom Traffic API Anahtarı",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Canlı trafik akış verisi TomTom FlowSegment API üzerinden çekilir. Ücretsiz API anahtarınızı developer.tomtom.com adresinden alarak buraya girebilirsiniz.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tomtom_api_key_input"),
                        label = { Text("TomTom API Key") },
                        placeholder = { Text("Örn: abcd1234efgh5678...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Anahtarı Göster/Gizle"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { onSaveApiKey(inputKey) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_traffic_key_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Anahtarı Kaydet ve Uygula")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Tarihsel Trafik Trend Tahminleri & Gecikme Analizi
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("traffic_trend_prediction_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Trafik Yoğunluk & Zaman Trendi",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "YAPAY ZEKA TAHMİNİ",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Günün belirli saatleri için geçmiş trafik örüntülerini analiz edebilir ve olası gecikmeleri önceden tahmin edebilirsiniz.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hour display and slider
                    val prediction = TrafficTrendPredictor.predictTrafficForHour(selectedHour, trafficStatus)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tahmin Saati: ${selectedHour.toString().padStart(2, '0')}:00",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = prediction.periodName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Slider(
                        value = selectedHour.toFloat(),
                        onValueChange = { selectedHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("traffic_trend_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Prediction result visualization
                    val levelColor = when (prediction.predictedTrafficLevel) {
                        TrafficLevel.LOW -> Color(0xFF2E7D32) // Green
                        TrafficLevel.MODERATE -> Color(0xFFEF6C00) // Orange
                        TrafficLevel.HEAVY -> Color(0xFFD84315) // Red-Orange
                        TrafficLevel.SEVERE -> Color(0xFFC62828) // Severe Red
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    val levelBg = when (prediction.predictedTrafficLevel) {
                        TrafficLevel.LOW -> Color(0xFFE8F5E9)
                        TrafficLevel.MODERATE -> Color(0xFFFFF3E0)
                        TrafficLevel.HEAVY -> Color(0xFFFBE9E7)
                        TrafficLevel.SEVERE -> Color(0xFFFFEBEE)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    val levelLabel = when (prediction.predictedTrafficLevel) {
                        TrafficLevel.LOW -> "Akıcı / Açık Yol"
                        TrafficLevel.MODERATE -> "Hafif Yoğun"
                        TrafficLevel.HEAVY -> "Yoğun Trafik"
                        TrafficLevel.SEVERE -> "Çok Yoğun / Dur-Kalk"
                        else -> "Bilinmiyor"
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = levelBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, levelColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = levelColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = levelLabel,
                                        color = levelColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = String.format("Yoğunluk: %.1fx", prediction.congestionMultiplier),
                                    color = levelColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.HourglassEmpty,
                                    contentDescription = null,
                                    tint = levelColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (prediction.predictedDelaySeconds > 0) {
                                        "Tahmini Gecikme: +${prediction.predictedDelaySeconds / 60} dakika"
                                    } else {
                                        "Tahmini Gecikme: Gecikme beklenmiyor"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = levelColor
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = levelColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = prediction.advice,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Horizontal quick-compare cards for Peak Periods
                    Text(
                        text = "Günün Önemli Zaman Dilimleri:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Morning Rush
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Sabah Yoğunluğu", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("07:30 - 09:30", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("🔴 Yoğun", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD84315))
                        }

                        // Midday
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Gün Ortası", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("09:30 - 17:00", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("🟡 Orta", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                        }

                        // Evening Rush
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Akşam Yoğunluğu", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("17:00 - 19:30", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("🛑 Çok Yoğun", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Live Verification & Proof of Network Packets
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Canlı Ağ Paketi Doğrulama Testi",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Aşağıdaki butona dokunduğunuzda cihazınız canlı TomTom FlowSegment HTTPS servisine anlık istek atar ve gelen gerçek HTTP yanıt kodunu, gecikme süresini (ms) ve dönen JSON verilerini görüntüler.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onTestConnection,
                        enabled = !isTesting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_traffic_connection_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TomTom Sunucusu Sorgulanıyor...")
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Canlı TomTom Paketini Şimdi Test Et")
                        }
                    }

                    // Test Results Panel
                    AnimatedVisibility(visible = testResult != null) {
                        testResult?.let { res ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (res.isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (res.isSuccess) "HTTP ${res.httpStatusCode} OK (Paket Alındı)" else "HATA: HTTP ${res.httpStatusCode}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (res.isSuccess) Color(0xFF1B5E20) else Color(0xFFC62828)
                                        )
                                        Text(
                                            text = "${res.latencyMs} ms",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (res.isSuccess) {
                                        Text(
                                            text = "• Anlık Akış Hızı: ${res.currentSpeedKmh.toInt()} km/s\n" +
                                                    "• Normal Serbest Hız: ${res.freeFlowSpeedKmh.toInt()} km/s\n" +
                                                    "• Güvenilirlik Skoru: %${(res.confidence * 100).toInt()}\n" +
                                                    "• Gecikme: ${res.delaySeconds} sn\n" +
                                                    "• Segment Koordinat Sayısı: ${res.coordinateCount}",
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = Color(0xFF2E7D32)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Gelen Ham JSON snippet:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        SelectionContainer {
                                            Text(
                                                text = res.rawJsonSnippet,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.White.copy(alpha = 0.7f))
                                                    .padding(6.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = res.errorMessage ?: "Bilinmeyen ağ hatası",
                                            fontSize = 12.sp,
                                            color = Color(0xFFB71C1C),
                                            lineHeight = 16.sp
                                        )
                                        if (res.rawJsonSnippet.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            SelectionContainer {
                                                Text(
                                                    text = res.rawJsonSnippet,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF5D4037),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.White.copy(alpha = 0.7f))
                                                        .padding(6.dp)
                                                )
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
    }
}
