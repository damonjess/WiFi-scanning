package com.damon.wifiaudit.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.ble.AdvDataParser
import com.damon.wifiaudit.ble.BleUuidResolver
import com.damon.wifiaudit.ble.GattConnectionManager
import com.damon.wifiaudit.data.BleRawFragment
import com.damon.wifiaudit.ui.theme.DarkBackground
import com.damon.wifiaudit.ui.theme.DarkSurface
import com.damon.wifiaudit.ui.theme.DarkSurfaceElevated
import com.damon.wifiaudit.ui.theme.TextMuted
import com.damon.wifiaudit.vendor.OuiVendorLookup
import org.osmdroid.views.MapView

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailScreen(
    macAddress: String,
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel = viewModel(
        factory = DeviceDetailViewModelFactory(LocalContext.current.applicationContext as Application, macAddress)
    )
) {
    val device by viewModel.deviceInfo.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val showHeatmap by viewModel.showHeatmap.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val services by viewModel.services.collectAsState()
    val rawFragments by viewModel.rawFragments.collectAsState()

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
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            null,
                            tint = if (isFavorite) Color(0xFFFFD700) else Color.White
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
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Map preview
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { context -> MapView(context) },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .align(Alignment.Center)
                                .background(Color(0xFF76FF03).copy(alpha = 0.3f), CircleShape)
                                .border(2.dp, Color(0xFF76FF03), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                null,
                                tint = Color(0xFF76FF03),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // Connection Button
            item {
                ConnectionButton(
                    state = connectionState,
                    onConnect = { viewModel.connectToDevice() },
                    onDisconnect = { viewModel.disconnect() }
                )
            }

            // Services expandable
            item {
                ExpandableSection(
                    title = "${services.size} service${if (services.size != 1) "s" else ""} discovered",
                    badge = when (connectionState) {
                        is GattConnectionManager.ConnectionState.Discovering -> "🔍"
                        is GattConnectionManager.ConnectionState.Ready -> "✓"
                        else -> null
                    }
                ) {
                    Column {
                        services.forEach { service ->
                            ServiceCard(service)
                        }
                        if (services.isEmpty() && connectionState is GattConnectionManager.ConnectionState.Disconnected) {
                            Text(
                                "Connect to discover services",
                                color = TextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Raw data fragments
            item {
                ExpandableSection(
                    title = "${rawFragments.size} raw data fragment${if (rawFragments.size != 1) "s" else ""}",
                    initiallyExpanded = rawFragments.isNotEmpty()
                ) {
                    Column {
                        rawFragments.forEach { fragment ->
                            RawFragmentCard(fragment)
                        }
                    }
                }
            }

            // Device info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3D5AFE).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Bluetooth,
                                    null,
                                    tint = Color(0xFF8C9EFF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                device?.deviceName ?: "Unknown",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        DetailField("Name", device?.deviceName ?: "N/A")

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DetailField("Address", macAddress, Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            StateBadge(state = when (connectionState) {
                                is GattConnectionManager.ConnectionState.Connected,
                                is GattConnectionManager.ConnectionState.Ready -> "CON"
                                is GattConnectionManager.ConnectionState.Connecting -> "..."
                                else -> "RST"
                            })
                        }

                        val vendor = remember { OuiVendorLookup.lookup(macAddress) }
                        DetailField("Manufacturer", vendor ?: "N/A")

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Metadata",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.analyseDevice() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32).copy(alpha = 0.3f),
                                    contentColor = Color(0xFF81C784)
                                ),
                                enabled = connectionState is GattConnectionManager.ConnectionState.Ready ||
                                         connectionState is GattConnectionManager.ConnectionState.Disconnected
                            ) {
                                if (connectionState is GattConnectionManager.ConnectionState.Discovering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF81C784),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Analyse")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        DetailField("Detect count", rawFragments.size.toString())

                        rawFragments.firstOrNull()?.let { first ->
                            DetailField("First detection", formatTimestamp(first.timestamp))
                        }
                        rawFragments.lastOrNull()?.let { last ->
                            DetailField("Last detection", formatTimestamp(last.timestamp))
                        }
                    }
                }
            }
        }
    }
}

data class ConnectionButtonInfo(
    val text: String,
    val color: Color,
    val action: () -> Unit
)

@Composable
private fun ConnectionButton(
    state: GattConnectionManager.ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val info = when (state) {
        is GattConnectionManager.ConnectionState.Disconnected,
        is GattConnectionManager.ConnectionState.Error ->
            ConnectionButtonInfo("Connect", Color(0xFF3D5AFE), onConnect)
        is GattConnectionManager.ConnectionState.Connecting ->
            ConnectionButtonInfo("Connecting...", Color(0xFFFFB300), {})
        is GattConnectionManager.ConnectionState.Connected,
        is GattConnectionManager.ConnectionState.Discovering,
        is GattConnectionManager.ConnectionState.Ready ->
            ConnectionButtonInfo("Disconnect", Color(0xFFFF5252), onDisconnect)
    }

    Button(
        onClick = info.action,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = info.color.copy(alpha = 0.2f),
            contentColor = info.color
        ),
        border = BorderStroke(1.dp, info.color.copy(alpha = 0.4f))
    ) {
        if (state is GattConnectionManager.ConnectionState.Connecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = info.color,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(info.text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ServiceCard(service: GattConnectionManager.DiscoveredService) {
    val name = remember(service.uuid) { BleUuidResolver.serviceName(service.uuid) }
    val isStandard = remember(service.uuid) { BleUuidResolver.isStandardUuid(service.uuid) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkBackground.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isStandard) Color(0xFF8C9EFF) else Color(0xFF76FF03)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        service.uuid.toString(),
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    "0x${BleUuidResolver.shortUuid(service.uuid)}",
                    fontSize = 12.sp,
                    color = if (isStandard) Color(0xFF8C9EFF) else Color(0xFF76FF03),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            (if (isStandard) Color(0xFF8C9EFF) else Color(0xFF76FF03)).copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (service.characteristics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                service.characteristics.forEach { char ->
                    val charName = BleUuidResolver.characteristicName(char.uuid)
                    Row(
                        modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("├─", color = TextMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                charName,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                char.uuid.toString(),
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                                PropertyBadge("R", Color(0xFF81C784))
                            }
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                                PropertyBadge("W", Color(0xFF64B5F6))
                            }
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                PropertyBadge("N", Color(0xFFFFB74D))
                            }
                        }
                    }
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RawFragmentCard(fragment: BleRawFragment) {
    val bytes = remember(fragment.hexData) {
        fragment.hexData.split(" ").mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
    }
    val parsed = remember(bytes) { AdvDataParser.parse(bytes) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkBackground.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Fragment #${fragment.id}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    "${fragment.rssi} dBm",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val hexText = remember(bytes, parsed) {
                AdvDataParser.formatHexDump(bytes, parsed)
            }

            SelectionContainer {
                Text(
                    text = hexText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFB0B0C0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF050508), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }

            if (parsed.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    parsed.forEach { struct ->
                        val color = when (struct.type) {
                            0x01 -> Color(0xFF64B5F6) // Flags
                            0x09 -> Color(0xFF81C784) // Name
                            0xFF -> Color(0xFFFFB74D) // Manufacturer
                            else -> TextMuted
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = color.copy(alpha = 0.12f)
                        ) {
                            Text(
                                struct.typeName,
                                fontSize = 10.sp,
                                color = color,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
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
    badge: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(badge, fontSize = 14.sp)
                }
            }
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null,
                tint = TextMuted
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            content()
        }
    }
}

@SuppressLint("SimpleDateFormat")
private fun formatTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("d MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
