package com.damon.wifiaudit.map

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.damon.wifiaudit.ui.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
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

    // Create MapView with proper lifecycle
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(51.5074, -0.1278))
            // Dark mode tile overlay
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    }

    // CRITICAL: Proper lifecycle
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Update markers
    LaunchedEffect(wifiPoints, blePoints, trackPoints, showTrack) {
        mapView.overlays.clear()

        val allPoints = mutableListOf<GeoPoint>()
        val random = java.util.Random()

        wifiPoints.forEach { record ->
            val latJitter = (random.nextDouble() - 0.5) * 0.00002
            val lonJitter = (random.nextDouble() - 0.5) * 0.00002
            val geo = GeoPoint(record.latitude + latJitter, record.longitude + lonJitter)
            allPoints.add(geo)

            val marker = Marker(mapView).apply {
                position = geo
                title = record.ssid
                snippet = "${record.bssid} • ${record.rssi} dBm"
                icon = createDotIcon(context, AndroidColor.parseColor("#00E5FF"), 24)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(marker)
        }

        blePoints.forEach { record ->
            val latJitter = (random.nextDouble() - 0.5) * 0.00002
            val lonJitter = (random.nextDouble() - 0.5) * 0.00002
            val geo = GeoPoint(record.latitude + latJitter, record.longitude + lonJitter)
            allPoints.add(geo)

            val marker = Marker(mapView).apply {
                position = geo
                title = record.deviceName ?: record.macAddress
                snippet = "${record.macAddress} • ${record.rssi} dBm"
                icon = createDotIcon(context, AndroidColor.parseColor("#FF00E5"), 24)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(marker)
        }

        if (showTrack && trackPoints.size >= 2) {
            val polyline = Polyline(mapView).apply {
                setPoints(trackPoints.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = AndroidColor.parseColor("#00E5FF")
                outlinePaint.alpha = 100
                outlinePaint.strokeWidth = 5f
            }
            mapView.overlays.add(0, polyline)
        }

        if (allPoints.isNotEmpty()) {
            val box = BoundingBox.fromGeoPoints(allPoints)
            mapView.post { mapView.zoomToBoundingBox(box, true, 120) }
        }

        mapView.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Session selector (top center)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var expanded by remember { mutableStateOf(false) }
            val selected = sessions.find { it.id == selectedSessionId }

            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, DarkSurfaceElevated)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selected?.let { formatSessionLabel(it) } ?: "Select session",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("▼", fontSize = 10.sp, color = TextMuted)
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text(formatSessionLabel(session), color = Color.White, fontSize = 13.sp) },
                        onClick = {
                            viewModel.selectSession(session.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Right controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (trackPoints.size >= 2) {
                FloatingMapButton(
                    icon = Icons.Default.Timeline,
                    isActive = showTrack,
                    onClick = { viewModel.toggleTrack() }
                )
            }
            FloatingMapButton(
                icon = Icons.Default.MyLocation,
                isActive = false,
                onClick = {
                    val myLocOverlay = mapView.overlays.find { it is MyLocationNewOverlay } as? MyLocationNewOverlay
                    val myLoc = myLocOverlay?.myLocation
                    myLoc?.let {
                        mapView.controller.animateTo(it)
                        mapView.controller.setZoom(18.0)
                    }
                }
            )
        }

        // Bottom legend
        if (wifiPoints.isNotEmpty() || blePoints.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, DarkSurfaceElevated)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LegendDot(CyanAccent, "WiFi")
                    LegendDot(MagentaAccent, "BLE")
                }
            }
        }

        if (wifiPoints.isEmpty() && blePoints.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Map,
                    null,
                    modifier = Modifier.size(48.dp).alpha(0.3f),
                    tint = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No sightings", color = Color.White, fontSize = 16.sp)
                Text("Run a scan first", color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FloatingMapButton(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) CyanAccent.copy(alpha = 0.2f) else DarkSurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, if (isActive) CyanAccent.copy(alpha = 0.4f) else DarkSurfaceElevated),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (isActive) CyanAccent else Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}

private fun formatSessionLabel(s: com.damon.wifiaudit.data.SessionSummary): String {
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return "${sdf.format(java.util.Date(s.startTime))} • ${s.wifiCount + s.bleCount} pts"
}

private fun createDotIcon(context: Context, color: Int, size: Int): android.graphics.drawable.BitmapDrawable {
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply {
        this.color = color
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 1f, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}
