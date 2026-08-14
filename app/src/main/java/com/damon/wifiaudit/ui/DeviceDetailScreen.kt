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
    viewModel: DeviceDetailViewModel = viewModel(
        key = macAddress,
        factory = DeviceDetailViewModelFactory(
            LocalContext.current.applicationContext as Application,
            macAddress,
            deviceType
        )
    )
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val adv by viewModel.advertisement.collectAsState()
    val heatmapEnabled by viewModel.heatmapEnabled.collectAsState()
    val heatmapPoints by viewModel.heatmapPoints.collectAsState()
    
    var showHeatmapSheet by remember { mutableStateOf(false) }

    if (showHeatmapSheet) {
        HeatmapBottomSheet(
            points = heatmapPoints,
            onDismiss = { showHeatmapSheet = false }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // MAP CARD
            item {
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
            }

            // CORE INFO
            item {
                CoreInfoCard(state)
            }

            // HEATMAP CONTROL
            item {
                HeatmapControlCard(
                    enabled = heatmapEnabled,
                    pointCount = heatmapPoints.size,
                    onToggle = { viewModel.toggleHeatmap() },
                    onViewMap = { showHeatmapSheet = true },
                    onClear = { viewModel.clearHeatmap() }
                )
            }

            // METADATA / ADVERTISEMENT
            if (deviceType == "BLE") {
                item {
                    MetadataSection(
                        state = state,
                        advertisement = adv,
                        onAnalyse = { viewModel.analyseDevice() },
                        isAnalysing = state.gattState is LightGattManager.State.Connecting ||
                                      state.gattState is LightGattManager.State.Discovering,
                        isConnected = state.gattState is LightGattManager.State.Ready,
                        onLoadHistoric = { viewModel.loadHistoricGatt() }
                    )
                }
            }

            // GATT SERVICES
            if (state.services.isNotEmpty()) {
                item {
                    Text(
                        "${state.services.size} service${if (state.services.size != 1) "s" else ""} discovered",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(state.services) { service ->
                    ServiceCard(service, viewModel.database)
                }
            }

            // HISTORY STATS
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    DetailField("Detect count", state.detectCount.toString())
                    state.firstSeen?.let { DetailField("First detection", formatDate(it)) }
                    state.lastSeen?.let { DetailField("Last detection", formatDate(it)) }
                }
            }
        }
    }
}

@Composable
private fun CoreInfoCard(state: DeviceDetailViewModel.UiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DetailField(label = "Name", value = state.name)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataSection(
    state: DeviceDetailViewModel.UiState,
    advertisement: ParsedAdvertisement?,
    onAnalyse: () -> Unit,
    isAnalysing: Boolean,
    isConnected: Boolean,
    onLoadHistoric: () -> Unit
) {
    var showRaw by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Metadata",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Button(
                    onClick = onAnalyse,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isAnalysing -> Color(0xFFFFB300).copy(alpha = 0.2f)
                            isConnected -> Color(0xFFE57373).copy(alpha = 0.2f)
                            else -> Color(0xFF2E7D32).copy(alpha = 0.3f)
                        },
                        contentColor = when {
                            isAnalysing -> Color(0xFFFFB300)
                            isConnected -> Color(0xFFE57373)
                            else -> Color(0xFF81C784)
                        }
                    )
                ) {
                    if (isAnalysing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFFFFB300),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analysing…")
                    } else if (isConnected) {
                        Text("Disconnect")
                    } else {
                        Text("Analyse")
                    }
                }
            }

            if (!isConnected && !isAnalysing && state.hasHistoricGatt) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLoadHistoric,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF8C9EFF).copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8C9EFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load saved GATT snapshot")
                }
            }

            advertisement?.let { adv ->
                Spacer(modifier = Modifier.height(12.dp))

                adv.flags?.let { flags ->
                    val flagNames = AdvertisementParser.formatFlags(flags)
                    if (flagNames.isNotEmpty()) {
                        Text("Flags", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
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
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (adv.serviceUuids.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Advertised Services (${adv.serviceUuids.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    adv.serviceUuids.take(5).forEach { uuid ->
                        Text(uuid.toString(), fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                    }
                }

                if (adv.manufacturerData.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Manufacturer Data", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    adv.manufacturerData.forEach { (id, bytes) ->
                        Text("ID 0x${id.toString(16).uppercase()}: ${bytes.joinToString("") { "%02X".format(it) }}",
                            fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide raw data" else "Show raw advertisement data", fontSize = 12.sp, color = Color(0xFF8C9EFF))
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

@Composable
private fun ServiceCard(service: LightGattManager.BleService, db: AppDatabase) {
    val context = BleUuidResolver.serviceContext(service.uuid)
    val isStandard = remember(service.uuid) { BleUuidResolver.isStandardUuid(service.uuid) }
    val shortForm = remember(service.uuid) { BleUuidResolver.fullShortForm(service.uuid) }
    
    // Database-backed resolution
    var resolvedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(service.uuid) {
        resolvedName = GattUuidResolver.resolveServiceName(service.uuid, db)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkBackground.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Service header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (context?.threatLevel) {
                                BleUuidResolver.ThreatLevel.CRITICAL -> Color(0xFFFF1744)
                                BleUuidResolver.ThreatLevel.HIGH -> Color(0xFFFF5252)
                                BleUuidResolver.ThreatLevel.MEDIUM -> Color(0xFFFFB300)
                                BleUuidResolver.ThreatLevel.LOW -> Color(0xFF76FF03)
                                else -> Color(0xFF8C9EFF)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            if (context != null) {
                                append(context.icon + " ")
                            }
                            // Priority: Resolved Name > Hardcoded Name > Raw UUID
                            append(resolvedName ?: BleUuidResolver.serviceName(service.uuid))
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    // Wardriving context
                    context?.let {
                        Text(
                            it.description,
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isStandard) Color(0xFF8C9EFF).copy(alpha = 0.1f) else Color(0xFF76FF03).copy(alpha = 0.1f)
                ) {
                    Text(
                        shortForm,
                        fontSize = 12.sp,
                        color = if (isStandard) Color(0xFF8C9EFF) else Color(0xFF76FF03),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Characteristics
            if (service.characteristics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                service.characteristics.forEach { char ->
                    CharRow(char, db)
                }
            }
        }
    }
}

@Composable
private fun CharRow(char: LightGattManager.BleCharacteristic, db: AppDatabase) {
    val ctx = BleUuidResolver.characteristicContext(char.uuid)
    val nameFallback = BleUuidResolver.characteristicName(char.uuid)
    val isStandard = BleUuidResolver.isStandardUuid(char.uuid)

    var resolvedName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(char.uuid) {
        resolvedName = GattUuidResolver.resolveCharacteristicName(char.uuid, db)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("├─", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        if (ctx != null) append(ctx.icon + " ")
                        append(resolvedName ?: nameFallback)
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            // Only show UUID if it's non-standard (hides clutter)
            if (!isStandard) {
                Text(
                    char.uuid.toString(),
                    fontSize = 9.sp,
                    color = TextMuted.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Show read value if available
            char.value?.let { valStr ->
                if (valStr.isNotBlank()) {
                    Text(
                        valStr,
                        fontSize = 12.sp,
                        color = Color(0xFF8C9EFF),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                PropertyBadge("R", Color(0xFF81C784))
            }
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                PropertyBadge("W", Color(0xFF64B5F6))
            }
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                PropertyBadge("N", Color(0xFFFFB74D))
            }
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                PropertyBadge("I", Color(0xFFCE93D8))
            }
        }
    }
}

@Composable
private fun PropertyBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
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
