package com.damon.wifiaudit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.damon.wifiaudit.scan.NetworkViewModel
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailBottomSheet(
    device: NetworkViewModel.NetworkDevice,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
        ) {
            // Header
            Text(
                text = device.hostname ?: device.ip,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "IP: ${device.ip}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (device.mac != null) {
                Text(
                    text = "MAC: ${device.mac}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Actions Section
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (device.openPorts.contains(80) || device.openPorts.contains(443)) {
                    Button(
                        onClick = {
                            val protocol = if (device.openPorts.contains(443)) "https" else "http"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$protocol://${device.ip}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Web, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Web Admin")
                    }
                }
                
                if (device.openPorts.contains(554) || device.openPorts.contains(8554)) {
                    OutlinedButton(
                        onClick = {
                            val port = if (device.openPorts.contains(554)) 554 else 8554
                            val url = "rtsp://${device.ip}:$port/live"
                            clipboardManager.setText(AnnotatedString(url))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy RTSP")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Vendor Deep-Dive
            Text(
                text = "Vendor Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            device.vendorInfo?.let { info ->
                DetailItem("Manufacturer", info.name)
                DetailItem("OUI Prefix", info.prefix)
                DetailItem("Block Type", info.blockType ?: "Unknown")
                DetailItem("Last Updated", info.lastUpdate ?: "Unknown")
                if (info.isPrivate) {
                    DetailItem("Visibility", "Private Registration", color = MaterialTheme.colorScheme.error)
                }
            } ?: Text(
                text = device.vendor ?: "Unknown manufacturer",
                style = MaterialTheme.typography.bodyLarge
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Security Assessments
            if (device.securityMatches.isNotEmpty()) {
                Text(
                    text = "Security Assessment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                device.securityMatches.forEach { match ->
                    val color = when (match.severity) {
                        SurveillanceDeviceWatchdog.Severity.CRITICAL -> MaterialTheme.colorScheme.error
                        SurveillanceDeviceWatchdog.Severity.HIGH -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                    Surface(
                        color = color,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = match.category.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = match.matchedOn,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // Technical Details
            Text(
                text = "Technical Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            DetailItem("Discovery Source", device.source ?: "Active Scan")
            if (device.responseTime > 0) {
                DetailItem("Response Time", "${device.responseTime}ms")
            }
            DetailItem("Open Ports", device.openPorts.joinToString(", ").ifEmpty { "None detected" })
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}
