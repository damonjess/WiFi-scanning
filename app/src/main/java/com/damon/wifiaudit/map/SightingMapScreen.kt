package com.damon.wifiaudit.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.toColorInt
import com.damon.wifiaudit.ui.theme.*
import com.damon.wifiaudit.vendor.OuiVendorLookup
import com.damon.wifiaudit.watchdog.SurveillanceDeviceWatchdog
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun SightingMapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val wifiPoints by viewModel.wifiLocations.collectAsState()
    val blePoints by viewModel.bleLocations.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    val showTrack by viewModel.showTrack.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val selectedSessionId by viewModel.selectedSessionId.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), MapView(context)).apply {
            enableMyLocation()
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(51.5074, -0.1278))
            overlays.add(locationOverlay)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        locationOverlay.enableMyLocation()
        onDispose {
            locationOverlay.disableMyLocation()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(wifiPoints, blePoints, trackPoints, showTrack) {
        mapView.overlays.removeAll { it !is MyLocationNewOverlay }

        val wifiIcon = createCircleMarker("#00E5FF".toColorInt(), 50)
        val bleIcon = createCircleMarker("#FF00E5".toColorInt(), 50)
        val warningIcon = createCircleMarker("#FF5252".toColorInt(), 60)

        val wifiClusterer = RadiusMarkerClusterer(context).apply {
            setIcon(createCircleMarker("#00E5FF".toColorInt(), 80, true))
            setRadius(120)
            textPaint.textSize = 32f
            textPaint.color = AndroidColor.WHITE
        }
        val bleClusterer = RadiusMarkerClusterer(context).apply {
            setIcon(createCircleMarker("#FF00E5".toColorInt(), 80, true))
            setRadius(120)
            textPaint.textSize = 32f
            textPaint.color = AndroidColor.WHITE
        }
        val watchdogClusterer = RadiusMarkerClusterer(context).apply {
            setIcon(createCircleMarker("#FF5252".toColorInt(), 90, true))
            setRadius(120)
            textPaint.textSize = 32f
            textPaint.color = AndroidColor.WHITE
        }

        val allPoints = mutableListOf<GeoPoint>()
        val random = java.util.Random()

        wifiPoints.forEach { record ->
            val vendor = OuiVendorLookup.lookup(record.bssid)
            val match = SurveillanceDeviceWatchdog.classifyWifi(record.ssid, vendor)
            val latJitter = (random.nextDouble() - 0.5) * 0.00002
            val lonJitter = (random.nextDouble() - 0.5) * 0.00002
            val geo = GeoPoint(record.latitude + latJitter, record.longitude + lonJitter)
            allPoints.add(geo)

            val marker = Marker(mapView).apply {
                position = geo
                title = if (match != null) "⚠ ${match.category.label}: ${record.ssid}" else "WiFi: ${record.ssid}"
                snippet = "${record.bssid} • ${record.encryption} • ${record.rssi} dBm"
                icon = android.graphics.drawable.BitmapDrawable(context.resources, if (match != null) warningIcon else wifiIcon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            if (match != null) watchdogClusterer.add(marker) else wifiClusterer.add(marker)
        }

        blePoints.forEach { record ->
            val vendor = OuiVendorLookup.lookup(record.macAddress)
            val match = SurveillanceDeviceWatchdog.classifyBle(record.deviceName, vendor)
            val latJitter = (random.nextDouble() - 0.5) * 0.00002
            val lonJitter = (random.nextDouble() - 0.5) * 0.00002
            val geo = GeoPoint(record.latitude + latJitter, record.longitude + lonJitter)
            allPoints.add(geo)

            val marker = Marker(mapView).apply {
                position = geo
                title = if (match != null) "⚠ ${match.category.label}: ${record.deviceName ?: record.macAddress}" else "BLE: ${record.deviceName ?: record.macAddress}"
                snippet = "${record.macAddress} • ${record.rssi} dBm"
                icon = android.graphics.drawable.BitmapDrawable(context.resources, if (match != null) warningIcon else bleIcon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            if (match != null) watchdogClusterer.add(marker) else bleClusterer.add(marker)
        }

        if (showTrack && trackPoints.size >= 2) {
            val polyline = Polyline(mapView).apply {
                setPoints(trackPoints.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = AndroidColor.parseColor("#00E5FF")
                outlinePaint.alpha = 120
                outlinePaint.strokeWidth = 6f
            }
            mapView.overlays.add(0, polyline)
        }

        if (!wifiClusterer.items.isEmpty()) mapView.overlays.add(wifiClusterer)
        if (!bleClusterer.items.isEmpty()) mapView.overlays.add(bleClusterer)
        if (!watchdogClusterer.items.isEmpty()) mapView.overlays.add(watchdogClusterer)

        if (allPoints.isNotEmpty()) {
            if (allPoints.size == 1) {
                mapView.controller.setZoom(17.0)
                mapView.controller.animateTo(allPoints.first())
            } else {
                val box = BoundingBox.fromGeoPoints(allPoints)
                mapView.post { mapView.zoomToBoundingBox(box, true, 100) }
            }
        }

        mapView.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Dark overlay vignette at top for readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        // Top controls
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var expanded by remember { mutableStateOf(false) }
            val selectedSession = sessions.find { it.id == selectedSessionId }

            // Session selector
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, DarkSurfaceElevated),
                modifier = Modifier.wrapContentWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = selectedSession?.let { formatSessionLabel(it) } ?: "Select Session",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "▼",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceElevated, RoundedCornerShape(8.dp))
            ) {
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                formatSessionLabel(session),
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            viewModel.selectSession(session.id)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(textColor = Color.White)
                    )
                }
            }
        }

        // Right-side floating controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (trackPoints.size >= 2) {
                MapControlButton(
                    icon = Icons.Default.Timeline,
                    isActive = showTrack,
                    onClick = { viewModel.toggleTrack() }
                )
            }
            MapControlButton(
                icon = Icons.Default.MyLocation,
                isActive = false,
                onClick = {
                    val myLoc = locationOverlay.myLocation
                    if (myLoc != null) {
                        mapView.controller.animateTo(myLoc)
                        mapView.controller.setZoom(18.0)
                    } else {
                        val currentSnapshot = viewModel.currentSnapshot.value
                        if (currentSnapshot.latitude != null && currentSnapshot.longitude != null) {
                            mapView.controller.animateTo(
                                GeoPoint(currentSnapshot.latitude, currentSnapshot.longitude)
                            )
                            mapView.controller.setZoom(18.0)
                        }
                    }
                }
            )
        }

        // Bottom legend
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, DarkSurfaceElevated)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MAP KEY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(color = CyanAccent, label = "WiFi")
                    LegendItem(color = MagentaAccent, label = "BLE")
                    LegendItem(color = Color(0xFFFF5252), label = "Alert")
                }
            }
        }

        // Empty state
        if (wifiPoints.isEmpty() && blePoints.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .alpha(0.2f),
                    tint = TextMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No sightings yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    "Run a wardriving scan to populate the map",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) CyanAccent.copy(alpha = 0.2f) else DarkSurface.copy(alpha = 0.95f),
        border = BorderStroke(
            1.dp,
            if (isActive) CyanAccent.copy(alpha = 0.4f) else DarkSurfaceElevated
        ),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) CyanAccent else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted
        )
    }
}

private fun formatSessionLabel(s: com.damon.wifiaudit.data.SessionSummary): String {
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return "${sdf.format(java.util.Date(s.startTime))} • ${s.wifiCount + s.bleCount} pts"
}

private fun createCircleMarker(color: Int, size: Int = 40, isCluster: Boolean = false): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        this.color = color
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    
    // Add a border
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = if (isCluster) 6f else 4f
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (paint.strokeWidth / 2f), paint)
    
    if (isCluster) {
        // Draw a shadow/outer ring for clusters to make them pop
        paint.color = color
        paint.alpha = 100
        paint.strokeWidth = 4f
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 1f, paint)
    }
    
    return bitmap
}
