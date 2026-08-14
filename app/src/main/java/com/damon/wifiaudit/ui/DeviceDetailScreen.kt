package com.damon.wifiaudit.ui

import android.app.Application
import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
import com.damon.wifiaudit.ble.BleUuidResolver
import com.damon.wifiaudit.ble.GattUuidResolver
import com.damon.wifiaudit.ble.LightGattManager
import com.damon.wifiaudit.data.AppDatabase
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
                        
                        if (deviceType == "BLE") {
                            val isAnalysing = state.gattState is LightGattManager.State.Connecting ||
                                              state.gattState is LightGattManager.State.Discovering
                            val isConnected = state.gattState is LightGattManager.State.Ready

                            Button(
                                onClick = { viewModel.analyseDevice() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        isConnected -> Color(0xFF2E7D32).copy(alpha = 0.3f)
                                        isAnalysing -> Color(0xFFFFB300).copy(alpha = 0.2f)
                                        else -> Color(0xFF2E7D32).copy(alpha = 0.3f)
                                    },
                                    contentColor = when {
                                        isConnected -> Color(0xFF81C784)
                                        isAnalysing -> Color(0xFFFFB300)
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
                                    Text("Analysing...")
                                } else if (isConnected) {
                                    Text("Disconnect")
                                } else {
                                    Text("Analyse")
                                }
                            }

                            // Show "Load saved" if we have historic data but no live connection
                            if (!isConnected && !isAnalysing && state.hasHistoricGatt) {
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.loadHistoricGatt() },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF8C9EFF).copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8C9EFF))
                                ) {
                                    Text("Load saved", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    state.gattError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ $error",
                            fontSize = 12.sp,
                            color = Color(0xFFFFB74D),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val services = state.services
                    val serviceCount = services.size

                    ExpandableSection(
                        title = "$serviceCount service${if (serviceCount != 1) "s" else ""} discovered",
                        initiallyExpanded = services.isNotEmpty()
                    ) {
                        services.forEach { svc ->
                            ServiceCard(svc, viewModel.database)
                        }
                        if (services.isEmpty() && state.gattState is LightGattManager.State.Disconnected) {
                            Text(
                                "Tap Analyse to connect and discover services",
                                color = TextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
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
