package com.damon.wifiaudit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.damon.wifiaudit.data.BleSightingRecord
import com.damon.wifiaudit.data.WifiSightingRecord
import com.damon.wifiaudit.scan.NetworkViewModel
import com.damon.wifiaudit.ui.theme.*
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header with search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CyanAccent.copy(alpha = 0.06f), Color.Transparent)
                    )
                )
                .padding(16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        if (selectedTab == HistoryTab.WIFI) "Search SSID, BSSID, vendor…"
                        else "Search device name or MAC…",
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = TextMuted)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = CyanAccent.copy(alpha = 0.4f),
                    unfocusedBorderColor = DarkSurfaceElevated,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CyanAccent
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // WiFi / BLE tabs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubTab(
                    label = "Wi-Fi",
                    count = wifiItems.itemCount,
                    icon = Icons.Default.Wifi,
                    isActive = selectedTab == HistoryTab.WIFI,
                    accent = CyanAccent,
                    onClick = { viewModel.selectedTab.value = HistoryTab.WIFI },
                    modifier = Modifier.weight(1f)
                )
                SubTab(
                    label = "BLE",
                    count = bleItems.itemCount,
                    icon = Icons.Default.Bluetooth,
                    isActive = selectedTab == HistoryTab.BLE,
                    accent = MagentaAccent,
                    onClick = { viewModel.selectedTab.value = HistoryTab.BLE },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipStyled(
                label = "Most recent",
                isActive = sortMode == SortMode.RECENT,
                onClick = { viewModel.sortMode.value = SortMode.RECENT }
            )
            FilterChipStyled(
                label = "Strongest",
                isActive = sortMode == SortMode.SIGNAL_STRONGEST,
                onClick = { viewModel.sortMode.value = SortMode.SIGNAL_STRONGEST }
            )
            if (selectedTab == HistoryTab.WIFI) {
                FilterChipStyled(
                    label = "All",
                    isActive = encFilter == null,
                    onClick = { viewModel.encryptionFilter.value = null }
                )
                encTypes.forEach { enc ->
                    FilterChipStyled(
                        label = enc,
                        isActive = encFilter == enc,
                        onClick = { viewModel.encryptionFilter.value = enc }
                    )
                }
            }
        }

        // Records list
        if (selectedTab == HistoryTab.WIFI) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    count = wifiItems.itemCount,
                    key = wifiItems.itemKey { it.id }
                ) { index ->
                    wifiItems[index]?.let { record ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            WifiRecordCard(
                                record = record,
                                onClick = {
                                    selectedDevice = NetworkViewModel.NetworkDevice(
                                        ip = "N/A",
                                        mac = record.bssid,
                                        vendor = record.vendorName ?: OuiVendorLookup.lookup(record.bssid),
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    count = bleItems.itemCount,
                    key = bleItems.itemKey { it.id }
                ) { index ->
                    bleItems[index]?.let { record ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            BleRecordCard(
                                record = record,
                                onClick = {
                                    selectedDevice = NetworkViewModel.NetworkDevice(
                                        ip = "N/A",
                                        mac = record.macAddress,
                                        vendor = record.vendorName ?: OuiVendorLookup.lookup(record.macAddress),
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
        DeviceDetailBottomSheet(device = device, onDismiss = { selectedDevice = null })
    }
}

@Composable
private fun SubTab(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) accent.copy(alpha = 0.12f) else DarkSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) accent.copy(alpha = 0.3f) else DarkSurfaceElevated
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) accent else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label ($count)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) accent else TextMuted
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

@Composable
private fun WifiRecordCard(
    record: WifiSightingRecord,
    onClick: () -> Unit
) {
    val vendor = record.vendorName
    val watchdogMatch = remember(record.ssid, vendor) {
        SurveillanceDeviceWatchdog.classifyWifi(record.ssid, vendor)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = record.ssid,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SignalBadge(rssi = record.rssi)
            }

            Text(
                text = record.bssid,
                fontSize = 12.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            ChipFlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoChip(text = record.encryption, color = LimeAccent)
                if (vendor != null) {
                    InfoChip(text = vendor, color = CyanAccent)
                }
                if (record.deviceModel != null) {
                    InfoChip(
                        text = record.deviceModel,
                        color = CyanAccent,
                        outlined = true
                    )
                }
                if (watchdogMatch != null) {
                    AlertChip(text = "⚠ ${watchdogMatch.category.label}")
                }
            }

            Text(
                text = "${dateFormat.format(Date(record.timestamp))}  •  " +
                        "%.5f".format(record.latitude) + ", " + "%.5f".format(record.longitude),
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun BleRecordCard(
    record: BleSightingRecord,
    onClick: () -> Unit
) {
    val vendor = record.vendorName
    val watchdogMatch = remember(record.deviceName, vendor) {
        SurveillanceDeviceWatchdog.classifyBle(record.deviceName, vendor)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = record.deviceName ?: vendor ?: "Unnamed device",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SignalBadge(rssi = record.rssi)
            }

            Text(
                text = record.macAddress,
                fontSize = 12.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            ChipFlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (record.hasGatt) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF8C9EFF).copy(alpha = 0.15f)
                    ) {
                        Text(
                            record.primaryGattService ?: "GATT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF8C9EFF),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (record.deviceModel != null) {
                    InfoChip(text = record.deviceModel, color = CyanAccent, outlined = true)
                }
                if (watchdogMatch != null) {
                    AlertChip(text = "⚠ ${watchdogMatch.category.label}")
                }
                if (vendor != null && record.deviceName != null) {
                    InfoChip(text = vendor, color = CyanAccent)
                }
                if (record.proximityUuid != null) {
                    InfoChip(text = "iBeacon", color = MagentaAccent)
                }
            }

            Text(
                text = "${dateFormat.format(Date(record.timestamp))}  •  " +
                        "%.5f".format(record.latitude) + ", " + "%.5f".format(record.longitude),
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    color: Color,
    outlined: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (outlined) Color.Transparent else color.copy(alpha = 0.1f),
        border = if (outlined) BorderStroke(1.dp, color.copy(alpha = 0.25f)) else null
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AlertChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFF5252).copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFF5252),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// Simple FlowRow implementation for chip wrapping
@Composable
private fun ChipFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hGapPx = 6.dp.roundToPx()
        val vGapPx = 6.dp.roundToPx()
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentWidth = 0
        var currentHeight = 0

        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            if (currentRow.isNotEmpty() && currentWidth + hGapPx + placeable.width > constraints.maxWidth) {
                rows.add(currentRow)
                rowWidths.add(currentWidth)
                rowHeights.add(currentHeight)
                currentRow = mutableListOf()
                currentWidth = 0
                currentHeight = 0
            }
            currentRow.add(placeable)
            currentWidth += if (currentRow.size == 1) placeable.width else hGapPx + placeable.width
            currentHeight = maxOf(currentHeight, placeable.height)
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowWidths.add(currentWidth)
            rowHeights.add(currentHeight)
        }

        val totalHeight = rowHeights.sum() + (rows.size - 1).coerceAtLeast(0) * vGapPx
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = when (horizontalArrangement) {
                    Arrangement.End -> constraints.maxWidth - rowWidths[rowIndex]
                    Arrangement.Center -> (constraints.maxWidth - rowWidths[rowIndex]) / 2
                    else -> 0
                }
                row.forEachIndexed { index, placeable ->
                    if (index > 0) x += hGapPx
                    placeable.placeRelative(x, y)
                    x += placeable.width
                }
                y += rowHeights[rowIndex] + vGapPx
            }
        }
    }
}
