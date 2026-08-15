package com.damon.wifiaudit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.map.MapViewModel
import com.damon.wifiaudit.ui.map.MapTabScreen
import com.damon.wifiaudit.ui.map.OsmMapScreen
import com.damon.wifiaudit.ui.rules.RulesScreen
import com.damon.wifiaudit.util.LockManager
import com.damon.wifiaudit.ui.theme.CyanAccent
import com.damon.wifiaudit.ui.theme.DarkBackground
import com.damon.wifiaudit.ui.theme.TextMuted

@Composable
fun AppRoot() {
    var selectedIndex by remember { mutableStateOf(0) }
    var detailTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // (id, "WIFI"|"BLE")
    var mapTarget by remember { mutableStateOf<String?>(null) } // mac address
    val isLocked by LockManager.isLocked.collectAsState()

    // LOCK SCREEN
    if (isLocked) {
        LockScreen(onUnlock = { LockManager.unlock() })
        return
    }

    // FULL-SCREEN MAP OVERLAY
    mapTarget?.let { mac ->
        val mapVm: MapViewModel = viewModel()
        LaunchedEffect(mac) {
            mapVm.loadPointsForMac(mac)
        }
        OsmMapScreen(
            points = mapVm.osmPoints,
            playbackIndex = mapVm.playbackIndex,
            showWifi = mapVm.showWifi,
            showBle = mapVm.showBle,
            onPointSelected = { /* Optional */ },
            onBack = { mapTarget = null },
            viewModel = mapVm
        )
        return
    }

    // FULL-SCREEN DETAIL OVERLAY
    detailTarget?.let { (mac, type) ->
        DeviceDetailScreen(
            macAddress = mac,
            deviceType = type,
            onBack = { detailTarget = null },
            onViewMap = { mapTarget = it }
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
                NavigationBarItem(
                    selected = selectedIndex == 4,
                    onClick = { selectedIndex = 4 },
                    icon = { Icon(Icons.Default.Security, null) },
                    label = { Text("Rules") },
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
                3 -> {
                    MapTabScreen(
                        onNavigateToDevice = { mac, type ->
                            detailTarget = mac to type
                        }
                    )
                }

                4 -> RulesScreen()
            }
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFFE57373),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "APP LOCKED",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Security key device out of range",
                fontSize = 14.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C9EFF))
            ) {
                Text("Unlock")
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
