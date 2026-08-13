package com.damon.wifiaudit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.damon.wifiaudit.map.OsmConfig
import com.damon.wifiaudit.map.SightingMapScreen
import com.damon.wifiaudit.ui.HistoryScreen
import com.damon.wifiaudit.ui.MainScanScreen
import com.damon.wifiaudit.ui.NetworkScannerScreen
import com.damon.wifiaudit.ui.PermissionGateScreen
import com.damon.wifiaudit.vendor.OuiVendorLookup
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.initialize(applicationContext)
        lifecycleScope.launch {
            OuiVendorLookup.initialize(applicationContext)
        }
        setContent {
            MaterialTheme {
                PermissionGateScreen {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var selectedIndex by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                    icon = { Icon(Icons.Default.Radar, contentDescription = null) },
                    label = { Text("Scan") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 2,
                    onClick = { selectedIndex = 2 },
                    icon = { Icon(Icons.Default.Lan, contentDescription = null) },
                    label = { Text("Network") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 3,
                    onClick = { selectedIndex = 3 },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("Map") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedIndex) {
                0 -> MainScanScreen()
                1 -> HistoryScreen()
                2 -> NetworkScannerScreen()
                3 -> SightingMapScreen()
            }
        }
    }
}
