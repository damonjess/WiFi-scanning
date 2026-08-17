package com.damon.wifiaudit.ui

import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
import java.util.UUID

@Composable
fun GattAnalysisPanel(
    state: DeviceDetailViewModel.UiState,
    onAnalyse: () -> Unit,
    onLoadHistoric: () -> Unit,
    db: AppDatabase,
    gattManager: LightGattManager?,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (state.isGattLive) {
                                    Color(0xFF81C784).copy(alpha = 0.15f)
                                } else {
                                    Color(0xFF8C9EFF).copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = if (state.isGattLive) {
                                        "${state.services.size} services · Live"
                                    } else {
                                        "${state.services.size} services · Saved"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isGattLive) Color(0xFF81C784) else Color(0xFF8C9EFF),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(onClick = onAnalyse, modifier = Modifier.height(32.dp)) {
                                Text("Refresh", fontSize = 12.sp)
                            }
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
                        if (!state.isGattLive) {
                            Text(
                                "Saved service table. Refresh to reconnect before reading, writing, or enabling notifications.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        state.services.forEach { service ->
                            GattServiceCard(
                                service = service,
                                db = db,
                                gattManager = gattManager,
                                isLive = state.isGattLive,
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
    gattManager: LightGattManager?,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    var resolvedName by remember(service.uuid) {
        mutableStateOf(service.name ?: GattUuidResolver.serviceFallbackName(service.uuid))
    }

    LaunchedEffect(service.uuid) {
        resolvedName = GattUuidResolver.resolveServiceName(service.uuid, db)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A23)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Service header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF8C9EFF), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = resolvedName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF81C784).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = GattUuidResolver.displayId(service.uuid),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF81C784),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = service.uuid.toString(),
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 18.dp, top = 4.dp)
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Characteristics
            service.characteristics.forEach { char ->
                InteractiveCharacteristicRow(
                    characteristic = char,
                    db = db,
                    gattManager = gattManager,
                    serviceUuid = service.uuid,
                    isLive = isLive
                )
            }
        }
    }
}

@Composable
private fun InteractiveCharacteristicRow(
    characteristic: LightGattManager.BleCharacteristic,
    db: AppDatabase,
    gattManager: LightGattManager?,
    serviceUuid: UUID,
    isLive: Boolean
) {
    var expanded by remember(characteristic.uuid) { mutableStateOf(false) }
    var resolvedName by remember(characteristic.uuid) {
        mutableStateOf(characteristic.name ?: GattUuidResolver.characteristicFallbackName(characteristic.uuid))
    }
    var showWriteDialog by remember(characteristic.uuid) { mutableStateOf(false) }

    LaunchedEffect(characteristic.uuid) {
        resolvedName = GattUuidResolver.resolveCharacteristicName(characteristic.uuid, db)
    }

    val canRead = characteristic.properties and 0x02 != 0
    val canWrite = characteristic.properties and 0x08 != 0 || characteristic.properties and 0x04 != 0
    val canNotify = characteristic.properties and 0x10 != 0 || characteristic.properties and 0x20 != 0

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "├─",
                fontSize = 14.sp,
                color = TextMuted,
                modifier = Modifier.padding(end = 8.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resolvedName,
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = characteristic.uuid.toString(),
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            // Property badges
            Row(modifier = Modifier.padding(end = 8.dp)) {
                if (canRead) PropertyBadge("R", Color(0xFF81C784))
                if (canWrite) PropertyBadge("W", Color(0xFF8C9EFF))
                if (canNotify) PropertyBadge("N", Color(0xFFFFB74D))
            }
        }

        // Expanded value + actions
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, bottom = 8.dp)
            ) {
                // Value display
                characteristic.value?.let { bytes ->
                    ValueDisplay(bytes = bytes)
                } ?: Text(
                    text = "No data read yet",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                characteristic.lastError?.let { err ->
                    Text(
                        text = "⚠️ $err",
                        fontSize = 11.sp,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canRead) {
                        ActionButton(
                            label = "Read",
                            color = Color(0xFF81C784),
                            enabled = isLive,
                            onClick = {
                                gattManager?.readCharacteristic(serviceUuid, characteristic.uuid)
                            }
                        )
                    }
                    if (canWrite) {
                        ActionButton(
                            label = "Write",
                            color = Color(0xFF8C9EFF),
                            enabled = isLive,
                            onClick = { showWriteDialog = true }
                        )
                    }
                    if (canNotify) {
                        ActionButton(
                            label = if (characteristic.isNotifying) "Stop Notify" else "Notify",
                            color = if (characteristic.isNotifying) Color(0xFFFFB74D) else Color(0xFFFFB74D).copy(alpha = 0.6f),
                            enabled = isLive,
                            onClick = {
                                gattManager?.setNotify(
                                    serviceUuid,
                                    characteristic.uuid,
                                    !characteristic.isNotifying
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showWriteDialog) {
        WriteValueDialog(
            onDismiss = { showWriteDialog = false },
            onSubmit = { hexString ->
                val bytes = hexString.replace(" ", "").chunked(2)
                    .mapNotNull { it.toIntOrNull(16)?.toByte() }
                    .toByteArray()
                if (bytes.isNotEmpty()) {
                    gattManager?.writeCharacteristic(serviceUuid, characteristic.uuid, bytes)
                }
            }
        )
    }
}

@Composable
private fun PropertyBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ActionButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        modifier = Modifier.height(32.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun ValueDisplay(bytes: ByteArray) {
    val hex = bytes.asSequence().joinToString(" ") { "%02X".format(it) }
    val ascii = bytes.asSequence()
        .map { if (it.toInt() in 32..126) it.toInt().toChar() else '.' }
        .joinToString("")
    val utf8 = try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { null }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "HEX",
                fontSize = 9.sp,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
            SelectionContainer {
                Text(
                    text = hex,
                    fontSize = 12.sp,
                    color = Color(0xFF8C9EFF),
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }

            if (utf8 != null && utf8.all { it.isPrintable() } && utf8.length > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "UTF-8",
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = utf8,
                    fontSize = 12.sp,
                    color = Color(0xFF81C784),
                    fontFamily = FontFamily.Monospace
                )
            }

            if (bytes.size <= 16) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ASCII",
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = ascii,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "${bytes.size} bytes",
                fontSize = 10.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun WriteValueDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A23),
        title = { Text("Write Hex Value", color = Color.White) },
        text = {
            Column {
                Text(
                    "Enter hex bytes (e.g. 01 FF A4)",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F0F15),
                        unfocusedContainerColor = Color(0xFF0F0F15),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text); onDismiss() }) {
                Text("Write", color = Color(0xFF8C9EFF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

private fun Char.isPrintable(): Boolean = this.code in 32..126 || this.code == 10 || this.code == 13
