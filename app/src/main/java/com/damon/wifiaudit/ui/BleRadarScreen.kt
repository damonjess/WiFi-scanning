package com.damon.wifiaudit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.ble.BleDeviceInfo
import com.damon.wifiaudit.ble.BleScanViewModel
import com.damon.wifiaudit.ui.theme.DarkBackground
import com.damon.wifiaudit.ui.theme.DarkSurface
import com.damon.wifiaudit.ui.theme.DarkSurfaceElevated
import com.damon.wifiaudit.ui.theme.TextMuted
import com.damon.wifiaudit.vendor.OuiVendorLookup

@Composable
fun BleRadarScreen(
    viewModel: BleScanViewModel = viewModel(),
    onDeviceClick: (BleDeviceInfo) -> Unit
) {
    val devices by viewModel.deviceList.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var excludeApple by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    val filtered = devices.filter { dev ->
        val matchesSearch = searchQuery.isBlank() 
            || dev.deviceName?.contains(searchQuery, ignoreCase = true) == true
            || dev.macAddress.contains(searchQuery, ignoreCase = true)
        val matchesFav = !showFavoritesOnly || true // TODO: wire from DB/ViewModel
        val matchesApple = !excludeApple || dev.vendorName?.contains("Apple", ignoreCase = true) != true
        matchesSearch && matchesFav && matchesApple
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(DarkBackground)
                    .padding(16.dp)
            ) {
                Text(
                    "BLE Radar",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // Filter chips row
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchChip(query = searchQuery, onQueryChange = { searchQuery = it })
                    FilterChipActive(
                        label = "Not apple",
                        isActive = excludeApple,
                        onClick = { excludeApple = !excludeApple }
                    )
                    FilterChipActive(
                        label = "Favorite",
                        isActive = showFavoritesOnly,
                        onClick = { showFavoritesOnly = !showFavoritesOnly }
                    )
                    AddFilterChip(onClick = { /* show filter dialog */ })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    isScanning = !isScanning
                    if (isScanning) viewModel.startScanning() else viewModel.stopScanning()
                },
                containerColor = Color(0xFF3D5AFE), // indigo like screenshot
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Bluetooth, null) },
                text = { Text(if (isScanning) "Stop" else "Scan") }
            )
        },
        bottomBar = {
            RadarBottomNav(selectedIndex = 0, onSelect = {})
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.macAddress }) { device ->
                BleDeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device) }
                )
            }
        }
    }
}

@Composable
private fun BleDeviceCard(
    device: BleDeviceInfo,
    onClick: () -> Unit
) {
    val vendor = device.vendorName
    val lifetime = remember(device.lastSeenMillis) { formatLifetime(device.lastSeenMillis) }
    val lastUpdate = remember(device.lastSeenMillis) { formatLastUpdate(device.lastSeenMillis) }
    
    // Generate avatar from vendor/name
    val avatarIcon = when {
        vendor?.contains("Apple", ignoreCase = true) == true -> "🍎"
        vendor?.contains("Tile", ignoreCase = true) == true -> "📍"
        device.iBeaconUuid != null -> "📡"
        else -> "?"
    }
    val avatarColor = when {
        vendor?.contains("Apple", ignoreCase = true) == true -> Color(0xFF2E7D32)
        vendor?.contains("Tile", ignoreCase = true) == true -> Color(0xFF1565C0)
        else -> Color(0xFF424242)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.2f))
                    .border(1.dp, avatarColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarIcon == "?") {
                    Text(
                        "?",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatarColor
                    )
                } else {
                    Text(avatarIcon, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName ?: "N/A",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (vendor != null) {
                    Text(
                        text = vendor,
                        fontSize = 14.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.macAddress,
                        fontSize = 14.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // State badge like STPⓘ or RSTⓘ
                    StateBadge(state = "RST") // or derive from connection state
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "lifetime: $lifetime | last update: $lastUpdate",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun StateBadge(state: String) {
    val (bg, text) = when (state) {
        "STP" -> Color(0xFF1B5E20) to Color(0xFF81C784)
        "RST" -> Color(0xFF5D4037) to Color(0xFFFFB74D)
        "CON" -> Color(0xFF0D47A1) to Color(0xFF64B5F6)
        else -> DarkSurfaceElevated to TextMuted
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = text,
                letterSpacing = 1.sp
            )
            Text(
                text = "ⓘ",
                fontSize = 10.sp,
                color = text,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
private fun FilterChipActive(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) Color(0xFF3D5AFE).copy(alpha = 0.2f) else DarkSurface,
        border = BorderStroke(1.dp, if (isActive) Color(0xFF3D5AFE) else DarkSurfaceElevated)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (isActive) Color(0xFF8C9EFF) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SearchChip(
    query: String,
    onQueryChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .width(180.dp)
                .height(44.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp)
        ) { innerTextField ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkSurfaceElevated)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) { innerTextField() }
                }
            }
        }
    } else {
        FilterChipActive(
            label = "Search",
            isActive = query.isNotBlank(),
            onClick = { expanded = true }
        )
    }
}

@Composable
private fun AddFilterChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Text(
            text = "+",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun RadarBottomNav(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val items = listOf(
        "Device list" to Icons.Default.Devices,
        "Radar Alerts" to Icons.Default.Notifications,
        "Journal" to Icons.Default.Article,
        "Settings" to Icons.Default.Settings
    )
    NavigationBar(
        containerColor = DarkBackground.copy(alpha = 0.95f),
        tonalElevation = 0.dp
    ) {
        items.forEachIndexed { index, (label, icon) ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                icon = { Icon(icon, null, modifier = Modifier.size(24.dp)) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF8C9EFF),
                    selectedTextColor = Color(0xFF8C9EFF),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = Color(0xFF3D5AFE).copy(alpha = 0.2f)
                )
            )
        }
    }
}

private fun formatLifetime(lastSeen: Long): String {
    val mins = ((System.currentTimeMillis() - lastSeen) / 60000).toInt()
    return if (mins < 1) "< 1 min" else "$mins min"
}

private fun formatLastUpdate(lastSeen: Long): String {
    val secs = ((System.currentTimeMillis() - lastSeen) / 1000).toInt()
    return when {
        secs < 60 -> "$secs sec ago"
        secs < 3600 -> "${secs / 60} min ago"
        else -> "${secs / 3600} hr ago"
    }
}
