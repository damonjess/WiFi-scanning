package com.damon.wifiaudit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp)
    ) {
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

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No ${tabs[selectedTabIndex].first} detected yet.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
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
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = device.deviceName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "MAC: ${device.macAddress}",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Signal: ${device.signalStrength} dBm",
                                color = Color(0xFFFFEA00),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
