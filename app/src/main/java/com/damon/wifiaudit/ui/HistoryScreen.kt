package com.damon.wifiaudit.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.damon.wifiaudit.data.BleSightingRecord
import com.damon.wifiaudit.data.WifiSightingRecord
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(wifiItems.itemCount, key = wifiItems.itemKey { it.id }) { index ->
                    wifiItems[index]?.let { WifiRecordCard(it) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bleItems.itemCount, key = bleItems.itemKey { it.id }) { index ->
                    bleItems[index]?.let { BleRecordCard(it) }
                }
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

@Composable
private fun WifiRecordCard(record: WifiSightingRecord) {
    val vendor = remember(record.bssid) { OuiVendorLookup.lookup(record.bssid) }
    val watchdogMatch = remember(record.ssid, vendor) {
        SurveillanceDeviceWatchdog.classifyWifi(record.ssid, vendor)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(record.ssid, style = MaterialTheme.typography.titleSmall)
                SignalBadge(record.rssi)
            }
            Text(record.bssid, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(record.encryption) })
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
private fun BleRecordCard(record: BleSightingRecord) {
    val vendor = remember(record.macAddress) {
        OuiVendorLookup.lookup(record.macAddress)
    }
    val watchdogMatch = remember(record.deviceName, vendor) {
        SurveillanceDeviceWatchdog.classifyBle(record.deviceName, vendor)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(record.deviceName ?: "Unnamed device", style = MaterialTheme.typography.titleSmall)
                SignalBadge(record.rssi)
            }
            Text(record.macAddress, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun SignalBadge(rssi: Int) {
    val color = when {
        rssi > -60 -> MaterialTheme.colorScheme.primary
        rssi > -80 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Text("$rssi dBm", color = color, style = MaterialTheme.typography.labelMedium)
}
