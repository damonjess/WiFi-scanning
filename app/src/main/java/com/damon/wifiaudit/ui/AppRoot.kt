package com.damon.wifiaudit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.damon.wifiaudit.map.SightingMapScreen
import com.damon.wifiaudit.ui.theme.CyanAccent
import com.damon.wifiaudit.ui.theme.DarkBackground
import com.damon.wifiaudit.ui.theme.TextMuted

@Composable
fun AppRoot() {
    var selectedIndex by remember { mutableStateOf(0) }
    var detailTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // (id, "WIFI"|"BLE")

    // FULL-SCREEN DETAIL OVERLAY
    detailTarget?.let { (mac, type) ->
        DeviceDetailScreen(
            macAddress = mac,
            deviceType = type,
            onBack = { detailTarget = null }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkBackground.copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                    icon = { Icon(Icons.Default.WifiTethering, null) },
                    label = { Text("Scan") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text("History") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedIndex == 2,
                    onClick = { selectedIndex = 2 },
                    icon = { Icon(Icons.Default.NetworkCheck, null) },
                    label = { Text("Network") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selectedIndex == 3,
                    onClick = { selectedIndex = 3 },
                    icon = { Icon(Icons.Default.Map, null) },
                    label = { Text("Map") },
                    colors = navColors()
                )
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedIndex) {
                0 -> UnifiedScanScreen(
                    onWifiClick = { bssid -> detailTarget = bssid to "WIFI" },
                    onBleClick = { mac -> detailTarget = mac to "BLE" }
                )
                1 -> HistoryScreen() // your existing or redesigned history
                2 -> NetworkScannerScreen()
                3 -> SightingMapScreen() // your existing map
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = CyanAccent,
    selectedTextColor = CyanAccent,
    unselectedIconColor = TextMuted,
    unselectedTextColor = TextMuted,
    indicatorColor = CyanAccent.copy(alpha = 0.15f)
)
