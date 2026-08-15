package com.damon.wifiaudit.ui.map

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.preference.PreferenceManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTabScreen(
    viewModel: MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToDevice: (macAddress: String, type: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val wifiPoints by viewModel.wifiPoints.collectAsState()
    val blePoints by viewModel.blePoints.collectAsState()
    val total by viewModel.totalPoints.collectAsState()
    val showWifi by viewModel.showWifi.collectAsState()
    val showBle by viewModel.showBle.collectAsState()
    val selected by viewModel.selectedPoint.collectAsState()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(selected) { showSheet = selected != null }

    // Init osmdroid once
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
        }
    }

    // Update markers whenever data or filters change
    LaunchedEffect(wifiPoints, blePoints, showWifi, showBle) {
        val map = mapViewRef ?: return@LaunchedEffect

        // CRITICAL FIX: Only remove our overlays, NEVER the TilesOverlay
        val toRemove = map.overlays.filter { it is Marker || it is Polyline }
        toRemove.forEach { map.overlays.remove(it) }

        val visibleWifi = if (showWifi) wifiPoints else emptyList()
        val visibleBle = if (showBle) blePoints else emptyList()
        val allVisible = visibleWifi + visibleBle

        if (allVisible.isEmpty()) {
            map.postInvalidate()
            return@LaunchedEffect
        }

        // --- Add WiFi polylines (connect same-MAC sightings in time order) ---
        visibleWifi.groupBy { it.macAddress }.forEach { (_, pts) ->
            val sorted = pts.sortedBy { it.timestamp }
            if (sorted.size > 1) {
                val line = Polyline(map).apply {
                    outlinePaint.color = Color(0xFF00BCD4).toArgb()
                    outlinePaint.strokeWidth = 5f
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    setPoints(sorted.map { GeoPoint(it.latitude, it.longitude) })
                }
                map.overlays.add(line)
            }
            sorted.forEach { pt ->
                map.overlays.add(createMarker(map, pt, isWifi = true))
            }
        }

        // --- Add BLE polylines ---
        visibleBle.groupBy { it.macAddress }.forEach { (_, pts) ->
            val sorted = pts.sortedBy { it.timestamp }
            if (sorted.size > 1) {
                val line = Polyline(map).apply {
                    outlinePaint.color = Color(0xFFE040FB).toArgb()
                    outlinePaint.strokeWidth = 5f
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    setPoints(sorted.map { GeoPoint(it.latitude, it.longitude) })
                }
                map.overlays.add(line)
            }
            sorted.forEach { pt ->
                map.overlays.add(createMarker(map, pt, isWifi = false))
            }
        }

        // --- Fit map to show all markers ---
        val lats = allVisible.map { it.latitude }
        val lngs = allVisible.map { it.longitude }
        val box = BoundingBox(
            lats.maxOrNull() ?: 0.0,
            lngs.maxOrNull() ?: 0.0,
            lats.minOrNull() ?: 0.0,
            lngs.minOrNull() ?: 0.0
        )
        map.post {
            map.zoomToBoundingBox(box, true, 100, 18.0, 1000L)
        }

        map.postInvalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OSM Map
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    controller.setZoom(15.0)
                    mapViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { /* overlays handled by LaunchedEffect */ }
        )

        // --- TOP BAR ---
        Surface(
            color = Color(0xFF1A1A23).copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showWifi,
                    onClick = { viewModel.toggleWifi() },
                    label = { Text("WiFi ${wifiPoints.size}", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Wifi, null, tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00BCD4).copy(alpha = 0.15f),
                        selectedLabelColor = Color(0xFF00BCD4)
                    ),
                    modifier = Modifier.height(32.dp)
                )

                FilterChip(
                    selected = showBle,
                    onClick = { viewModel.toggleBle() },
                    label = { Text("BLE ${blePoints.size}", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Bluetooth, null, tint = Color(0xFFE040FB), modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE040FB).copy(alpha = 0.15f),
                        selectedLabelColor = Color(0xFFE040FB)
                    ),
                    modifier = Modifier.height(32.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "$total pts",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // --- RIGHT CONTROLS ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, top = 110.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControlButton(Icons.Default.MyLocation) {
                val all = wifiPoints + blePoints
                if (all.isNotEmpty()) {
                    val lats = all.map { it.latitude }
                    val lngs = all.map { it.longitude }
                    val box = BoundingBox(lats.max(), lngs.max(), lats.min(), lngs.min())
                    mapViewRef?.post { mapViewRef?.zoomToBoundingBox(box, true, 120, 19.0, 800L) }
                }
            }
            MapControlButton(Icons.Default.Add) { mapViewRef?.controller?.zoomIn() }
            MapControlButton(Icons.Default.Remove) { mapViewRef?.controller?.zoomOut() }
        }

        // --- COMPACT LEGEND (top-right) ---
        CompactLegend(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 64.dp)
        )

        // --- EMPTY STATE ---
        if (total == 0) {
            Surface(
                color = Color(0xFF0F0F15).copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Map, null, tint = Color(0xFF8C9EFF), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No wardriving data yet", color = Color.White, fontSize = 16.sp)
                    Text(
                        "Scan with GPS enabled to populate the map.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // --- BOTTOM SHEET (Modal, not fixed overlay) ---
    if (showSheet && selected != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                viewModel.selectPoint(null)
            },
            sheetState = sheetState,
            containerColor = Color(0xFF1A1A23),
            scrimColor = Color.Black.copy(alpha = 0.3f)
        ) {
            PointDetailContent(
                point = selected!!,
                onDismiss = {
                    showSheet = false
                    viewModel.selectPoint(null)
                },
                onOpenDevice = { mac, type ->
                    showSheet = false
                    viewModel.selectPoint(null)
                    onNavigateToDevice(mac, type) // <-- NAVIGATION WIRED
                }
            )
        }
    }
}

// --- Marker factory ---
private fun createMarker(map: MapView, pt: MapViewModel.MapPoint, isWifi: Boolean): Marker {
    return Marker(map).apply {
        position = GeoPoint(pt.latitude, pt.longitude)
        icon = createRssiDot(map.context, pt.rssi, isWifi)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = "${pt.name}\n${pt.macAddress}\n${pt.rssi} dBm"
        setOnMarkerClickListener { _, _ ->
            // Access ViewModel through a shared holder or callback
            // Since Marker callback doesn't easily reach Compose, we use a static bridge
            MapMarkerBridge.pendingSelection = pt
            true
        }
    }
}

// Bridge to communicate marker taps back to Compose
object MapMarkerBridge {
    var onSelect: ((MapViewModel.MapPoint) -> Unit)? = null
    var pendingSelection: MapViewModel.MapPoint? = null
        set(value) {
            field = value
            value?.let { onSelect?.invoke(it) }
        }
}

// In your ViewModel init or LaunchedEffect, set the bridge:
// MapMarkerBridge.onSelect = { pt -> viewModel.selectPoint(pt) }

// --- Dot drawable ---
private fun createRssiDot(context: Context, rssi: Int, isWifi: Boolean): BitmapDrawable {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val color = if (isWifi) {
        when {
            rssi >= -50 -> Color(0xFF00E676)
            rssi >= -70 -> Color(0xFF00BCD4)
            else -> Color(0xFF01579B)
        }
    } else {
        when {
            rssi >= -50 -> Color(0xFFEA80FC)
            rssi >= -70 -> Color(0xFFE040FB)
            else -> Color(0xFFAA00FF)
        }
    }

    paint.color = color.copy(alpha = 0.3f).toArgb()
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)

    paint.color = color.toArgb()
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

    paint.color = Color.White.copy(alpha = 0.9f).toArgb()
    canvas.drawCircle(size / 2f, size / 2f, size / 10f, paint)

    return BitmapDrawable(context.resources, bitmap)
}

@Composable
private fun MapControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFF1A1A23).copy(alpha = 0.9f),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CompactLegend(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = Color(0xFF1A1A23).copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Text("Signal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    LegendRow(Color(0xFF00E676), "Strong (-50+)")
                    LegendRow(Color(0xFF00BCD4), "Fair (-50 to -70)")
                    LegendRow(Color(0xFF01579B), "Weak (< -70)")
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF00BCD4), CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WiFi", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFE040FB), CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("BLE", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun PointDetailContent(
    point: MapViewModel.MapPoint,
    onDismiss: () -> Unit,
    onOpenDevice: (mac: String, type: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.width(36.dp).height(4.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp)))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconColor = if (point.type == MapViewModel.PointType.WIFI) Color(0xFF00BCD4) else Color(0xFFE040FB)
                Icon(
                    if (point.type == MapViewModel.PointType.WIFI) Icons.Default.Wifi else Icons.Default.Bluetooth,
                    null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(point.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(point.macAddress, fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatColumn("RSSI", "${point.rssi} dBm", rssiColor(point.rssi))
            StatColumn("Lat", String.format("%.5f", point.latitude), Color.White)
            StatColumn("Lng", String.format("%.5f", point.longitude), Color.White)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val type = if (point.type == MapViewModel.PointType.WIFI) "WIFI" else "BLE"
                onOpenDevice(point.macAddress, type)
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C9EFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Device Details", fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

private fun rssiColor(rssi: Int) = when {
    rssi >= -50 -> Color(0xFF00E676)
    rssi >= -70 -> Color(0xFF00BCD4)
    else -> Color(0xFFFF3D00)
}
