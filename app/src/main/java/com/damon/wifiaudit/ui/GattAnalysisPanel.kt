package com.damon.wifiaudit.ui

import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damon.wifiaudit.ble.BleUuidResolver
import com.damon.wifiaudit.ble.GattUuidResolver
import com.damon.wifiaudit.ble.LightGattManager
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.ui.theme.DarkBackground
import com.damon.wifiaudit.ui.theme.DarkSurfaceElevated
import com.damon.wifiaudit.ui.theme.TextMuted

@Composable
fun GattAnalysisPanel(
    state: DeviceDetailViewModel.UiState,
    onAnalyse: () -> Unit,
    onLoadHistoric: () -> Unit,
    db: AppDatabase,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A23)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GATT Services",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                when (state.gattState) {
                    is LightGattManager.State.Connecting,
                    is LightGattManager.State.Discovering -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF81C784),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.gattState is LightGattManager.State.Connecting)
                                    "Connecting…" else "Discovering…",
                                fontSize = 12.sp,
                                color = Color(0xFF81C784)
                            )
                        }
                    }
                    is LightGattManager.State.Ready -> {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF81C784).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${state.services.size} services",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    is LightGattManager.State.Error -> {
                        Text(
                            text = "Failed",
                            fontSize = 12.sp,
                            color = Color(0xFFFFB74D)
                        )
                    }
                    else -> {
                        Button(
                            onClick = onAnalyse,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32).copy(alpha = 0.3f),
                                contentColor = Color(0xFF81C784)
                            )
                        ) {
                            Text("Analyse")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            when (state.gattState) {
                is LightGattManager.State.Connecting -> {
                    Text(
                        "Establishing BLE connection…",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
                is LightGattManager.State.Discovering -> {
                    Text(
                        "Reading service table from device…",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
                is LightGattManager.State.Ready -> {
                    if (state.services.isEmpty()) {
                        Text(
                            "Connected successfully, but the device exposed no services.",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    } else {
                        state.services.forEach { service ->
                            GattServiceCard(
                                service = service,
                                db = db,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
                is LightGattManager.State.Error -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFB74D).copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ ${state.gattError ?: "Connection failed"}",
                            fontSize = 13.sp,
                            color = Color(0xFFFFB74D),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    // Retry button
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAnalyse,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81C784)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry")
                    }
                }
                else -> {
                    // Idle / Disconnected
                    if (state.hasHistoricGatt) {
                        OutlinedButton(
                            onClick = onLoadHistoric,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF8C9EFF).copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8C9EFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load saved snapshot")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Or press Analyse to connect live.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    } else {
                        Text(
                            "Press Analyse to perform a live GATT discovery.",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GattServiceCard(
    service: LightGattManager.BleService,
    db: AppDatabase,
    modifier: Modifier = Modifier
) {
    val context = BleUuidResolver.serviceContext(service.uuid)
    val isStandard = remember(service.uuid) { BleUuidResolver.isStandardUuid(service.uuid) }
    val shortForm = remember(service.uuid) { BleUuidResolver.fullShortForm(service.uuid) }
    
    // Database-backed resolution
    var resolvedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(service.uuid) {
        resolvedName = GattUuidResolver.resolveServiceName(service.uuid, db)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBackground.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Service header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (context?.threatLevel) {
                                BleUuidResolver.ThreatLevel.CRITICAL -> Color(0xFFFF1744)
                                BleUuidResolver.ThreatLevel.HIGH -> Color(0xFFFF5252)
                                BleUuidResolver.ThreatLevel.MEDIUM -> Color(0xFFFFB300)
                                BleUuidResolver.ThreatLevel.LOW -> Color(0xFF76FF03)
                                else -> Color(0xFF8C9EFF)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            if (context != null) {
                                append(context.icon + " ")
                            }
                            // Priority: Resolved Name > Hardcoded Name > Raw UUID
                            append(resolvedName ?: BleUuidResolver.serviceName(service.uuid))
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    // Wardriving context
                    context?.let {
                        Text(
                            it.description,
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isStandard) Color(0xFF8C9EFF).copy(alpha = 0.1f) else Color(0xFF76FF03).copy(alpha = 0.1f)
                ) {
                    Text(
                        shortForm,
                        fontSize = 12.sp,
                        color = if (isStandard) Color(0xFF8C9EFF) else Color(0xFF76FF03),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Characteristics
            if (service.characteristics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                service.characteristics.forEach { char ->
                    CharRow(char, db)
                }
            }
        }
    }
}

@Composable
private fun CharRow(char: LightGattManager.BleCharacteristic, db: AppDatabase) {
    val ctx = BleUuidResolver.characteristicContext(char.uuid)
    val nameFallback = BleUuidResolver.characteristicName(char.uuid)
    val isStandard = BleUuidResolver.isStandardUuid(char.uuid)

    var resolvedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(char.uuid) {
        resolvedName = GattUuidResolver.resolveCharacteristicName(char.uuid, db)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("├─", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        if (ctx != null) append(ctx.icon + " ")
                        append(resolvedName ?: nameFallback)
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            // Only show UUID if it's non-standard (hides clutter)
            if (!isStandard) {
                Text(
                    char.uuid.toString(),
                    fontSize = 9.sp,
                    color = TextMuted.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Show read value if available
            char.value?.let { valStr ->
                if (valStr.isNotBlank()) {
                    Text(
                        valStr,
                        fontSize = 12.sp,
                        color = Color(0xFF8C9EFF),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                PropertyBadge("R", Color(0xFF81C784))
            }
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                PropertyBadge("W", Color(0xFF64B5F6))
            }
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                PropertyBadge("N", Color(0xFFFFB74D))
            }
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                PropertyBadge("I", Color(0xFFCE93D8))
            }
        }
    }
}

@Composable
private fun PropertyBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
