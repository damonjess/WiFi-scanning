package com.damon.wifiaudit.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.damon.wifiaudit.data.BleSightingRecord
import com.damon.wifiaudit.data.WifiSightingRecord
import com.damon.wifiaudit.scan.NetworkViewModel
import com.damon.wifiaudit.vendor.OuiVendorLookup
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val encFilter by viewModel.encryptionFilter.collectAsState()
    val encTypes by viewModel.availableEncryptionTypes.collectAsState()

    val wifiItems = viewModel.wifiPagingFlow.collectAsLazyPagingItems()
    val bleItems = viewModel.blePagingFlow.collectAsLazyPagingItems()

    var selectedDevice by remember { mutableStateOf<NetworkViewModel.NetworkDevice?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == HistoryTab.WIFI,
                onClick = { viewModel.selectedTab.value = HistoryTab.WIFI },
                text = { Text("Wi-Fi") }
            )
            Tab(
                selected = selectedTab == HistoryTab.BLE,
                onClick = { viewModel.selectedTab.value = HistoryTab.BLE },
                text = { Text("BLE") }
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.searchQuery.value = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text(if (selectedTab == HistoryTab.WIFI) "Search SSID or BSSID" else "Search name or MAC") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = sortMode == SortMode.RECENT, onClick = { viewModel.sortMode.value = SortMode.RECENT }, label = { Text("Most recent") })
            FilterChip(selected = sortMode == SortMode.SIGNAL_STRONGEST, onClick = { viewModel.sortMode.value = SortMode.SIGNAL_STRONGEST }, label = { Text("Strongest signal") })
            if (selectedTab == HistoryTab.WIFI) {
                FilterChip(selected = encFilter == null, onClick = { viewModel.encryptionFilter.value = null }, label = { Text("All encryption") })
                encTypes.forEach { enc ->
                    FilterChip(selected = encFilter == enc, onClick = { viewModel.encryptionFilter.value = enc }, label = { Text(enc) })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTab == HistoryTab.WIFI) {
            if (wifiItems.itemCount == 0) {
                EmptyHistoryState(
                    title = "No Wi-Fi sightings",
                    description = if (query.isNotEmpty()) "No results match your search" else "Start scanning to collect Wi-Fi data"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(wifiItems.itemCount, key = wifiItems.itemKey { it.id }) { index ->
                        wifiItems[index]?.let { record ->
                            WifiRecordCard(
                                record = record,
                                onClick = {
                                    selectedDevice = NetworkViewModel.NetworkDevice(
                                        ip = "N/A",
                                        mac = record.bssid,
                                        vendor = OuiVendorLookup.lookup(record.bssid),
                                        vendorInfo = OuiVendorLookup.lookupInfo(record.bssid),
                                        hostname = record.ssid,
                                        source = "History (WiFi)",
                                        openPorts = emptyList(),
                                        securityMatches = emptyList()
                                    )
                                }
                            )
                        }
                    }
                }
            }
        } else {
            if (bleItems.itemCount == 0) {
                EmptyHistoryState(
                    title = "No BLE sightings",
                    description = if (query.isNotEmpty()) "No results match your search" else "Start scanning to collect BLE data"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bleItems.itemCount, key = bleItems.itemKey { it.id }) { index ->
                        bleItems[index]?.let { record ->
                            BleRecordCard(
                                record = record,
                                onClick = {
                                    selectedDevice = NetworkViewModel.NetworkDevice(
                                        ip = "N/A",
                                        mac = record.macAddress,
                                        vendor = OuiVendorLookup.lookup(record.macAddress),
                                        vendorInfo = OuiVendorLookup.lookupInfo(record.macAddress),
                                        hostname = record.deviceName,
                                        source = "History (BLE)",
                                        openPorts = emptyList(),
                                        securityMatches = emptyList()
                                    )
                                }
                            )
                        }
                    }
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

private val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

@Composable
private fun WifiRecordCard(
    record: WifiSightingRecord,
    onClick: () -> Unit
) {
    val vendor = remember(record.bssid) { OuiVendorLookup.lookup(record.bssid) }
    val watchdogMatch = remember(record.ssid, vendor) {
        SurveillanceDeviceWatchdog.classifyWifi(record.ssid, vendor)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(record.ssid, style = MaterialTheme.typography.titleSmall)
                SignalBadge(record.rssi)
            }
            Text(record.bssid, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(record.encryption) })
                if (vendor != null) {
                    AssistChip(onClick = {}, label = { Text(vendor) })
                }
                if (record.deviceModel != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text(record.deviceModel) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                if (watchdogMatch != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text("⚠ ${watchdogMatch.category.label}") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
            }
            Text(
                "${dateFormat.format(Date(record.timestamp))}  •  ${"%.5f".format(record.latitude)}, ${"%.5f".format(record.longitude)}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun BleRecordCard(
    record: BleSightingRecord,
    onClick: () -> Unit
) {
    val vendor = remember(record.macAddress) {
        OuiVendorLookup.lookup(record.macAddress)
    }
    val watchdogMatch = remember(record.deviceName, vendor) {
        SurveillanceDeviceWatchdog.classifyBle(record.deviceName, vendor)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(record.deviceName ?: "Unnamed device", style = MaterialTheme.typography.titleSmall)
                SignalBadge(record.rssi)
            }
            Text(record.macAddress, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.deviceModel != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text(record.deviceModel) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                if (watchdogMatch != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text("⚠ ${watchdogMatch.category.label}") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
                if (vendor != null) {
                    AssistChip(onClick = {}, label = { Text(vendor) })
                }
                if (record.proximityUuid != null) {
                    AssistChip(onClick = {}, label = { Text("iBeacon") })
                }
            }

            Text(
                "${dateFormat.format(Date(record.timestamp))}  •  ${"%.5f".format(record.latitude)}, ${"%.5f".format(record.longitude)}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun EmptyHistoryState(title: String, description: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Radar,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SignalBadge(rssi: Int) {
    val color = when {
        rssi > -60 -> MaterialTheme.colorScheme.primary
        rssi > -80 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Text("$rssi dBm", color = color, style = MaterialTheme.typography.labelMedium)
}
