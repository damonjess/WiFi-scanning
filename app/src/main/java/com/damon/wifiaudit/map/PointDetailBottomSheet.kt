package com.damon.wifiaudit.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damon.wifiaudit.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PointDetailBottomSheet(
    point: MapViewModel.HeatmapPoint,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A23)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (point.type == "BLE") Color(0xFFE040FB).copy(alpha = 0.2f)
                    else Color(0xFF00BCD4).copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (point.type == "BLE") Icons.Default.Bluetooth else Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (point.type == "BLE") Color(0xFFE040FB) else Color(0xFF00BCD4)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = point.deviceName ?: "Unknown ${point.type}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = point.mac,
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox("RSSI", "${point.rssi} dBm", rssiColor(point.rssi))
                StatBox("Type", point.type, Color.White)
                point.ssid?.let { StatBox("SSID", it, Color.White) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time
            Text(
                text = "Recorded: ${formatTime(point.timestamp)}",
                fontSize = 12.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action
            Button(
                onClick = { /* navigate to device detail */ },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C9EFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Device Details")
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

private fun rssiColor(rssi: Int) = when {
    rssi >= -50 -> Color(0xFF00E676)
    rssi >= -70 -> Color(0xFFFFEA00)
    else -> Color(0xFFFF3D00)
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
