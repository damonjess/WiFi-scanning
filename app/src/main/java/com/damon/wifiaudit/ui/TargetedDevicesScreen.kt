package com.damon.wifiaudit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damon.wifiaudit.data.AppDatabase

@Composable
fun TargetedDevicesScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).targetDeviceDao() }
    
    // Added "Cameras" to handle Ring, Wyze, Arlo
    val tabs = listOf(
        "Cameras" to "CAMERA", 
        "Trackers" to "TRACKER", 
        "Smart Home" to "SMART_HOME", 
        "Auto" to "AUTO", 
        "IoT/Dev" to "IOT"
    )
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    val currentCategory = tabs[selectedTabIndex].second
    val devices by dao.getByCategory(currentCategory).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp)) {
        // Changed to ScrollableTabRow to prevent text squishing
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF8C9EFF),
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { index, (title, _) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontSize = 13.sp) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(devices) { device ->
                Surface(
                    color = Color(0xFF1A1A23),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(device.deviceName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(device.macAddress, color = Color.Gray, fontSize = 12.sp)
                        Text("Signal: ${device.signalStrength} dBm", color = Color(0xFFFFEA00), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
