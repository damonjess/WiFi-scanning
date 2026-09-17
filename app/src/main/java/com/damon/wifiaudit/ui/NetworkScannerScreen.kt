package com.damon.wifiaudit.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.scan.ArpCacheReader
import com.damon.wifiaudit.scan.NetworkViewModel
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog

@Composable
fun NetworkScannerScreen(viewModel: NetworkViewModel = viewModel()) {
    val isScanning by viewModel.isScanning.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    var selectedDevice by remember { mutableStateOf<NetworkViewModel.NetworkDevice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Local Network",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${devices.size} devices discovered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "scanning")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Scanning",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = { viewModel.startScan() }) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty() && !isScanning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Lan,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No devices found. Run a scan to see what's on your network.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(devices) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { selectedDevice = device }
                    )
                }
            }
        }
    }

    selectedDevice?.let { device ->
        DeviceDetailBottomSheet(
            device = device,
            onDismiss = { selectedDevice = null }
        )
    }
}

@Composable
fun DeviceCard(
    device: NetworkViewModel.NetworkDevice,
    onClick: () -> Unit
) {
    val titleText = device.deviceName.ifBlank { device.hostname ?: device.ip }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "IP: ${device.ip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (device.responseTime > 0) {
                    Text(
                        text = "${device.responseTime}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (device.vendor != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "Vendor: ${device.vendor}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            if (device.securityMatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                device.securityMatches.forEach { match ->
                    val containerColor = when (match.severity) {
                        SurveillanceDeviceWatchdog.Severity.CRITICAL -> MaterialTheme.colorScheme.error
                        SurveillanceDeviceWatchdog.Severity.HIGH -> MaterialTheme.colorScheme.errorContainer
                        SurveillanceDeviceWatchdog.Severity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                    val contentColor = when (match.severity) {
                        SurveillanceDeviceWatchdog.Severity.CRITICAL -> MaterialTheme.colorScheme.onError
                        SurveillanceDeviceWatchdog.Severity.HIGH -> MaterialTheme.colorScheme.onErrorContainer
                        SurveillanceDeviceWatchdog.Severity.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }

                    Surface(
                        color = containerColor,
                        contentColor = contentColor,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚠ ${match.category.label}: ${match.matchedOn}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val macDisplay = when {
                    device.mac != null -> "MAC: ${device.mac}"
                    !ArpCacheReader.isArpSupported() -> "MAC: Restricted (Android 10+)"
                    !ArpCacheReader.isArpUsable() -> "MAC: ARP cache empty (Android 10+)"
                    else -> "MAC: Resolving..."
                }
                Text(
                    text = macDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (device.mac != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Bold
                )
                
                if (device.source != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = device.source.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (device.openPorts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Open Ports: ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = device.openPorts.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
