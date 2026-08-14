package com.damon.wifiaudit.ui

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.ui.theme.*
import com.damon.wifiaudit.vendor.OuiVendorLookup
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    macAddress: String,
    deviceType: String, // "WIFI" or "BLE"
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: DeviceDetailViewModel = viewModel(
        factory = remember(macAddress, deviceType) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DeviceDetailViewModel(
                        context.applicationContext as Application,
                        macAddress,
                        deviceType
                    ) as T
                }
            }
        }
    )

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            null,
                            tint = if (state.isFavorite) Color(0xFFFFD700) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // MAP CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.latitude != null && state.longitude != null) {
                        val mapView = remember {
                            MapView(context).apply {
                                setMultiTouchControls(false)
                                controller.setZoom(17.0)
                                controller.setCenter(GeoPoint(state.latitude!!, state.longitude!!))
                            }
                        }
                        DisposableEffect(Unit) {
                            mapView.onResume()
                            onDispose { mapView.onPause(); mapView.onDetach() }
                        }
                        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                        // Green glow pin
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .align(Alignment.Center)
                                .background(Color(0xFF76FF03).copy(alpha = 0.3f), CircleShape)
                                .border(2.dp, Color(0xFF76FF03), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bluetooth, null, tint = Color(0xFF76FF03), modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No location data", color = TextMuted)
                        }
                    }
                }
            }

            // RANGE HEATMAP TOGGLE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Range heatmap", color = Color.White, fontSize = 16.sp)
                Switch(
                    checked = false,
                    onCheckedChange = { /* TODO */ },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF8C9EFF),
                        checkedTrackColor = Color(0xFF3D5AFE).copy(alpha = 0.5f)
                    )
                )
            }

            // HISTORY STYLE
            DetailRow("History style", "Markers", subtitleColor = TextMuted)

            // HISTORY PERIOD
            DetailRow(
                "History period: Day",
                "Showing big location history may affect map performance",
                subtitleColor = Color(0xFFFFB74D)
            )

            // ADD TAG
            OutlinedButton(
                onClick = { },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkSurfaceElevated),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("+  Add tag")
            }

            // INFO CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF3D5AFE).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bluetooth, null, tint = Color(0xFF8C9EFF), modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(state.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    DetailField("Name", state.name)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DetailField("Address", state.macAddress, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        StateBadge("RST")
                    }
                    DetailField("Manufacturer", state.vendor ?: "N/A")

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Metadata", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32).copy(alpha = 0.3f),
                                contentColor = Color(0xFF81C784)
                            )
                        ) {
                            Text("Analyse")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Expandable sections (empty for now, won't crash)
                    ExpandableSection("0 services discovered") {}
                    ExpandableSection("0 raw data fragments") {}

                    DetailField("Detect count", state.detectCount.toString())
                    state.firstSeen?.let { DetailField("First detection", formatDate(it)) }
                    state.lastSeen?.let { DetailField("Last detection", formatDate(it)) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, subtitle: String, subtitleColor: Color = TextMuted) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, fontSize = 13.sp, color = subtitleColor)
    }
}

@Composable
private fun DetailField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            value,
            fontSize = 16.sp,
            color = if (value == "N/A") TextMuted else Color.White,
            fontFamily = if (value.contains(":")) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ExpandableSection(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null,
                tint = TextMuted
            )
        }
        if (expanded) content()
    }
}

private fun formatDate(millis: Long): String {
    return SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
