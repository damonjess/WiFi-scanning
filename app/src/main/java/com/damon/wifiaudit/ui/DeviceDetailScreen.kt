package com.damon.wifiaudit.ui

import android.app.Application
import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.ble.AdvertisementParser
import com.damon.wifiaudit.ble.BleUuidResolver
import com.damon.wifiaudit.ble.GattUuidResolver
import com.damon.wifiaudit.ble.LightGattManager
import com.damon.wifiaudit.ble.ParsedAdvertisement
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.ui.detail.CreateRuleBottomSheet
import com.damon.wifiaudit.ui.theme.*
import com.damon.wifiaudit.vendor.OuiVendorLookup
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailScreen(
    macAddress: String,
    deviceType: String, // "WIFI" or "BLE"
    onBack: () -> Unit,
    onViewMap: (String) -> Unit = {},
    viewModel: DeviceDetailViewModel = viewModel(
        key = macAddress,
        factory = DeviceDetailViewModelFactory(
            LocalContext.current.applicationContext as Application,
            macAddress,
            deviceType
        )
    )
) {
    val state by viewModel.state.collectAsState()
    val adv by viewModel.advertisement.collectAsState()
    val heatmapEnabled by viewModel.heatmapEnabled.collectAsState()
    val heatmapPoints by viewModel.heatmapPoints.collectAsState()
    val classification by viewModel.classification.collectAsState()

    var showHeatmapSheet by remember { mutableStateOf(false) }
    var showCreateRuleSheet by remember { mutableStateOf(false) }

    if (showHeatmapSheet) {
        HeatmapBottomSheet(
            points = heatmapPoints,
            onDismiss = { showHeatmapSheet = false }
        )
    }

    if (showCreateRuleSheet) {
        CreateRuleBottomSheet(
            mac = macAddress,
            deviceName = state.name,
            onDismiss = { showCreateRuleSheet = false },
            viewModel = viewModel
        )
    }

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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF0F0F15))
                .padding(16.dp)
        ) {
            // ── Header (Map & Classification) ──
            item {
                DeviceHeader(state = state, classification = classification)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ── Core Info (Name, MAC, Vendor, RSSI chips) ──
            item {
                CoreInfoCard(state = state, onCreateRule = { showCreateRuleSheet = true })
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ── Heatmap Toggle ──
            item {
                HeatmapControlCard(
                    enabled = heatmapEnabled,
                    pointCount = heatmapPoints.size,
                    onToggle = { viewModel.toggleHeatmap() },
                    onViewMap = { onViewMap(macAddress) },
                    onClear = { viewModel.clearHeatmap() }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ── Advertisement Metadata (always visible, passive data) ──
            if (deviceType == "BLE") {
                item {
                    AdvertisementMetadataCard(
                        advertisement = adv,
                        onToggleRaw = { /* handled internally */ }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // ── GATT Analysis (the missing piece) ──
                item {
                    GattAnalysisPanel(
                        state = state,
                        onAnalyse = { viewModel.analyseDevice() },
                        onLoadHistoric = { viewModel.loadHistoricGatt() },
                        db = viewModel.db,
                        gattManager = viewModel.gattManager
                    )
                }
            }

            // ── History Stats ──
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    DetailField("Detect count", state.detectCount.toString())
                    state.firstSeen?.let { DetailField("First detection", formatDate(it)) }
                    state.lastSeen?.let { DetailField("Last detection", formatDate(it)) }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun CoreInfoCard(state: DeviceDetailViewModel.UiState, onCreateRule: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    DetailField(label = "Name", value = state.name)
                }
                if (state.macAddress.isNotEmpty()) {
                    IconButton(onClick = onCreateRule) {
                        Icon(Icons.Default.AddModerator, "Create Rule", tint = Color(0xFF8C9EFF))
                    }
                }
            }
            DetailField(label = "Address", value = state.macAddress)
            DetailField(label = "Manufacturer", value = state.vendor ?: "Unknown")
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip(
                    label = "RSSI",
                    value = "${state.rssi} dBm",
                    color = when {
                        state.rssi > -60 -> Color(0xFF81C784)
                        state.rssi > -80 -> Color(0xFFFFB74D)
                        else -> Color(0xFFE57373)
                    }
                )
                StatChip(
                    label = "Detections",
                    value = state.detectCount.toString()
                )
                StatChip(
                    label = "Connectable",
                    value = if (state.isConnectable) "Yes" else "No",
                    color = if (state.isConnectable) Color(0xFF81C784) else Color(0xFF9E9E9E)
                )
            }

            state.txPower?.let { tx ->
                Text(
                    text = "TX Power: $tx dBm",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color = Color(0xFF8C9EFF)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun HeatmapControlCard(
    enabled: Boolean,
    pointCount: Int,
    onToggle: () -> Unit,
    onViewMap: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Range heatmap",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = if (enabled) "Collecting… ($pointCount points)" else "$pointCount points stored",
                    fontSize = 12.sp,
                    color = if (enabled) Color(0xFF81C784) else TextMuted
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF8C9EFF),
                    checkedTrackColor = Color(0xFF8C9EFF).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color(0xFF5C5C6D),
                    uncheckedTrackColor = Color(0xFF2A2A35)
                )
            )
        }

        if (pointCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewMap,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF8C9EFF).copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8C9EFF)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View Heatmap")
                }
                OutlinedButton(
                    onClick = onClear,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun DeviceHeader(
    state: DeviceDetailViewModel.UiState,
    classification: String?
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth()) {
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
                        onDispose {
                            mapView.onPause()
                            mapView.onDetach()
                        }
                    }
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                    // Green dot pin
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center)
                            .background(Color(0xFF76FF03).copy(alpha = 0.25f), CircleShape)
                            .border(1.5.dp, Color(0xFF76FF03), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF76FF03), CircleShape)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No location data", color = TextMuted)
                    }
                }
            }
        }

        classification?.let {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF81C784).copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvertisementMetadataCard(
    advertisement: ParsedAdvertisement?,
    onToggleRaw: () -> Unit
) {
    var showRaw by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Metadata",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            advertisement?.let { adv ->
                Spacer(modifier = Modifier.height(12.dp))

                adv.flags?.let { flags ->
                    val flagNames = AdvertisementParser.formatFlags(flags)
                    if (flagNames.isNotEmpty()) {
                        Text(
                            "Flags",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                            flagNames.forEach { flag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF8C9EFF).copy(alpha = 0.12f),
                                    modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = flag,
                                        fontSize = 10.sp,
                                        color = Color(0xFF8C9EFF),
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 4.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (adv.serviceUuids.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Advertised Services (${adv.serviceUuids.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    adv.serviceUuids.take(5).forEach { uuid ->
                        Text(
                            uuid.toString(),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (adv.manufacturerData.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Manufacturer Data",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    adv.manufacturerData.forEach { (id, bytes) ->
                        Text(
                            "ID 0x${id.toString(16).uppercase()}: ${
                                bytes.joinToString("") {
                                    "%02X".format(
                                        it
                                    )
                                }
                            }",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    showRaw = !showRaw
                    onToggleRaw()
                }) {
                    Text(
                        if (showRaw) "Hide raw data" else "Show raw advertisement data",
                        fontSize = 12.sp,
                        color = Color(0xFF8C9EFF)
                    )
                }
                AnimatedVisibility(visible = showRaw) {
                    SelectionContainer {
                        Text(
                            text = adv.rawHex,
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
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
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable { expanded = !expanded },
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

class DeviceDetailViewModelFactory(
    private val app: Application,
    private val mac: String,
    private val type: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DeviceDetailViewModel(app, mac, type) as T
    }
}
