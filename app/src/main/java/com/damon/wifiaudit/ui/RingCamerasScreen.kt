package com.damon.wifiaudit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.data.RingCamera
import com.damon.wifiaudit.ui.theme.CyanAccent
import com.damon.wifiaudit.ui.theme.DarkBackground
import com.damon.wifiaudit.ui.theme.TextMuted
import java.time.format.DateTimeFormatter

@Composable
fun RingCamerasScreen(
    viewModel: RingCamerasViewModel = viewModel()
) {
    val ringCameras by viewModel.ringCameras.collectAsState(initial = emptyList())

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Ring Cameras", color = Color.White) },
                containerColor = DarkBackground,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground.copy(alpha = 0.95f),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (ringCameras.isEmpty()) {
                EmptyRingCamerasState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ringCameras) { camera ->
                        RingCameraCard(
                            camera = camera,
                            onDelete = { viewModel.deleteCamera(camera) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RingCameraCard(
    camera: RingCamera,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = camera.deviceName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = camera.macAddress,
                            fontSize = 12.sp,
                            color = TextMuted,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SSID and Signal Strength
            if (camera.ssid.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Network:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = camera.ssid,
                        fontSize = 12.sp,
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Signal Strength
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SignalCellularAlt,
                    contentDescription = null,
                    tint = getSignalColor(camera.signalStrength),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Signal: ${camera.signalStrength} dBm",
                    fontSize = 12.sp,
                    color = getSignalColor(camera.signalStrength),
                    fontWeight = FontWeight.SemiBold
                )
                if (camera.frequency > 0) {
                    Text(
                        text = "• ${camera.frequency} MHz",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // Last Seen
            Text(
                text = "Last seen: ${formatDateTime(camera.lastSeen)}",
                fontSize = 11.sp,
                color = TextMuted
            )

            // Location (if available)
            if (camera.latitude != null && camera.longitude != null) {
                Text(
                    text = "Location: ${String.format("%.4f, %.4f", camera.latitude, camera.longitude)}",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Ring Camera") },
            text = { Text("Remove ${camera.deviceName} from your captured list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFE57373))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White
        )
    }
}

@Composable
private fun EmptyRingCamerasState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Videocam,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Ring Cameras Found",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Walk around to scan and capture nearby Ring cameras",
            fontSize = 14.sp,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun getSignalColor(signalStrength: Int): Color {
    return when {
        signalStrength >= -50 -> Color(0xFF4CAF50) // Green - Excellent
        signalStrength >= -60 -> Color(0xFF8BC34A) // Light Green - Very Good
        signalStrength >= -70 -> Color(0xFFFFC107) // Yellow - Good
        signalStrength >= -80 -> Color(0xFFFF9800) // Orange - Fair
        else -> Color(0xFFE57373) // Red - Weak
    }
}

private fun formatDateTime(dateTime: java.time.LocalDateTime): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    return dateTime.format(formatter)
}
