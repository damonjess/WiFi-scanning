package com.damon.wifiaudit.ui

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.ble.BleScanViewModel
import com.damon.wifiaudit.scan.ScanCycleSnapshot
import com.damon.wifiaudit.scan.WardrivingService
import com.damon.wifiaudit.ui.theme.*
import com.damon.wifiaudit.vendor.OuiVendorLookup
import kotlinx.coroutines.delay

@Composable
fun UnifiedScanScreen(
    viewModel: WardrivingStatusViewModel = viewModel(),
    bleViewModel: BleScanViewModel = viewModel(),
    onWifiClick: (String) -> Unit,
    onBleClick: (String) -> Unit
) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val bleDevices by bleViewModel.deviceList.collectAsState()
    val serviceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()

    var startTime by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var showBleOnly by remember { mutableStateOf(false) }

    LaunchedEffect(serviceRunning) {
        if (serviceRunning && startTime == 0L) {
            startTime = System.currentTimeMillis()
        } else if (!serviceRunning) {
            startTime = 0L
            elapsedSeconds = 0L
        }
        while (serviceRunning) {
            elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // SCAN CONTROL
        ScanControlCard(
            isRunning = serviceRunning,
            elapsedSeconds = elapsedSeconds,
            onToggle = {
                val intent = Intent(context, WardrivingService::class.java)
                if (serviceRunning) {
                    context.stopService(intent)
                    bleViewModel.stopScanning()
                } else {
                    ContextCompat.startForegroundService(context, intent)
                    bleViewModel.startScanning()
                }
            }
        )

        // STATS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GradientStatCard("Wi-Fi", snapshot.wifiResults.size.toString(), CyanAccent, Modifier.weight(1f))
            GradientStatCard("BLE", bleDevices.size.toString(), MagentaAccent, Modifier.weight(1f))
            GradientStatCard("Saved", snapshot.cyclesWritten.toString(), LimeAccent, Modifier.weight(1f))
        }

        // TOGGLE CHIPS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipStyled("Nearby WiFi", !showBleOnly) { showBleOnly = false }
            FilterChipStyled("BLE Radar", showBleOnly) { showBleOnly = true }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // LIST
        if (showBleOnly) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bleDevices, key = { it.macAddress }) { device ->
                    BleDeviceRow(device = device, onClick = { onBleClick(device.macAddress) })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { GpsCard(snapshot) }
                items(snapshot.wifiResults, key = { it.BSSID }) { result ->
                    WifiScanRow(result = result, onClick = { onWifiClick(result.BSSID) })
                }
            }
        }
    }
}

@Composable
private fun BleDeviceRow(
    device: BleDeviceInfo,
    onClick: () -> Unit
) {
    val vendor = remember(device.macAddress) { OuiVendorLookup.lookup(device.macAddress) }
    val lifetime = remember(device.lastSeenMillis) {
        val mins = ((System.currentTimeMillis() - device.lastSeenMillis) / 60000).toInt()
        if (mins < 1) "< 1 min" else "$mins min"
    }
    val lastUpdate = remember(device.lastSeenMillis) {
        val secs = ((System.currentTimeMillis() - device.lastSeenMillis) / 1000).toInt()
        "$secs sec ago"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF2A2A35), CircleShape)
                    .border(1.dp, Color(0xFF3A3A45), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6E6E8A))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.deviceName ?: "Unknown", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                val companyFromAdv = device.manufacturerFromAdv
                if (companyFromAdv != null) {
                    Text(
                        "📡 $companyFromAdv",
                        fontSize = 12.sp,
                        color = Color(0xFF8C9EFF),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (vendor != null) {
                    Text(vendor, fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(top = 2.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.macAddress, fontSize = 14.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(8.dp))
                    StateBadge("RST")
                }
                Text(
                    "lifetime: $lifetime | last update: $lastUpdate",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun WifiScanRow(
    result: android.net.wifi.ScanResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.SSID.ifBlank { "<Hidden>" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    result.BSSID,
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            SignalBadge(result.level)
        }
    }
}

@Composable
private fun ScanControlCard(
    isRunning: Boolean,
    elapsedSeconds: Long,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "fade"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .border(2.dp, CyanAccent, CircleShape)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isRunning) CyanAccent else Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (isRunning) "SCANNING" else "IDLE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isRunning) CyanAccent else Color.White
                )
                if (isRunning) {
                    Text(
                        formatElapsed(elapsedSeconds),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFFF5252) else CyanAccent
                    ),
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        if (isRunning) "STOP SCANNING" else "START SCANNING",
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color.White else DarkBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientStatCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GpsCard(snapshot: ScanCycleSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CURRENT FIX",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
                if (snapshot.latitude != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(LimeAccent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = LimeAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (snapshot.latitude != null)
                    "${"%.5f".format(snapshot.latitude)}, ${"%.5f".format(snapshot.longitude)}"
                else "Waiting for GPS…",
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
