package com.damon.wifiaudit.map

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.damon.wifiaudit.data.SessionSummary
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun SightingMapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val wifiPoints by viewModel.wifiLocations.collectAsState()
    val blePoints by viewModel.bleLocations.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    val showTrack by viewModel.showTrack.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val selectedSessionId by viewModel.selectedSessionId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(16.0)
            
            // Set a default center if no data yet (e.g. London)
            controller.setCenter(GeoPoint(51.5074, -0.1278))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(wifiPoints, blePoints, trackPoints, showTrack) {
        mapView.overlays.clear()

        val wifiIcon = createCircleMarker(Color.BLUE)
        val bleIcon = createCircleMarker(Color.MAGENTA)

        val wifiClusterer = RadiusMarkerClusterer(context).apply {
            setRadius(80)
            textPaint.textSize = 32f
            textPaint.color = Color.WHITE
        }
        val bleClusterer = RadiusMarkerClusterer(context).apply {
            setRadius(80)
            textPaint.textSize = 32f
            textPaint.color = Color.WHITE
        }

        val allPoints = mutableListOf<GeoPoint>()

        // Breadcrumb trail — drawn first so markers layer on top of the line
        if (showTrack && trackPoints.size >= 2) {
            val trackGeoPoints = trackPoints.map { GeoPoint(it.latitude, it.longitude) }
            val polyline = Polyline().apply {
                setPoints(trackGeoPoints)
                outlinePaint.color = Color.parseColor("#8000838F") // semi-transparent teal
                outlinePaint.strokeWidth = 8f
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(polyline)

            // Start marker (green) and end marker (red) so direction of travel is clear
            val startMarker = Marker(mapView).apply {
                position = trackGeoPoints.first()
                title = "Start"
                snippet = formatTime(trackPoints.first().timestamp)
                icon = android.graphics.drawable.BitmapDrawable(context.resources, createCircleMarker(Color.GREEN))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            val endMarker = Marker(mapView).apply {
                position = trackGeoPoints.last()
                title = "End"
                snippet = formatTime(trackPoints.last().timestamp)
                icon = android.graphics.drawable.BitmapDrawable(context.resources, createCircleMarker(Color.RED))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(startMarker)
            mapView.overlays.add(endMarker)
        }

        wifiPoints.forEach { record ->
            val geo = GeoPoint(record.latitude, record.longitude)
            allPoints.add(geo)
            val marker = Marker(mapView).apply {
                position = geo
                title = "WiFi: ${record.ssid.ifBlank { "<hidden>" }}"
                snippet = "${record.bssid} • ${record.encryption} • ${record.rssi} dBm"
                icon = android.graphics.drawable.BitmapDrawable(context.resources, wifiIcon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            wifiClusterer.add(marker)
        }

        blePoints.forEach { record ->
            val geo = GeoPoint(record.latitude, record.longitude)
            allPoints.add(geo)
            val marker = Marker(mapView).apply {
                position = geo
                title = "BLE: ${record.deviceName ?: record.macAddress}"
                snippet = "${record.macAddress} • ${record.rssi} dBm"
                icon = android.graphics.drawable.BitmapDrawable(context.resources, bleIcon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            bleClusterer.add(marker)
        }

        if (wifiPoints.isNotEmpty()) mapView.overlays.add(wifiClusterer)
        if (blePoints.isNotEmpty()) mapView.overlays.add(bleClusterer)

        if (allPoints.isNotEmpty()) {
            if (allPoints.size == 1) {
                mapView.controller.setZoom(17.0)
                mapView.controller.animateTo(allPoints.first())
            } else {
                val box = BoundingBox.fromGeoPoints(allPoints)
                mapView.post {
                    mapView.zoomToBoundingBox(box, true, 100)
                }
            }
        }

        mapView.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { 
                // MapView state is mostly handled by LaunchedEffect(wifiPoints...)
                // but we could trigger invalidation here if needed
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            var expanded by remember { mutableStateOf(false) }
            val selectedSession = sessions.find { it.id == selectedSessionId }
            val label = selectedSession?.let { formatSessionLabel(it) } ?: "Select Session"

            FilterChip(
                selected = false,
                onClick = { expanded = true },
                label = { Text(label) }
            )

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text(formatSessionLabel(session)) },
                        onClick = {
                            viewModel.selectSession(session.id)
                            expanded = false
                        }
                    )
                }
            }

            if (trackPoints.size >= 2) {
                Spacer(modifier = Modifier.height(8.dp))
                FilterChip(
                    selected = showTrack,
                    onClick = { viewModel.toggleTrack() },
                    label = { Text("Route") },
                    leadingIcon = { Icon(Icons.Default.Timeline, contentDescription = null) }
                )
            }

            val currentSnapshot by viewModel.currentSnapshot.collectAsState()
            if (currentSnapshot.latitude != null && currentSnapshot.longitude != null) {
                Spacer(modifier = Modifier.height(8.dp))
                IconButton(
                    onClick = {
                        val geo = GeoPoint(currentSnapshot.latitude!!, currentSnapshot.longitude!!)
                        mapView.controller.animateTo(geo)
                        mapView.controller.setZoom(18.0)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Center on my location")
                }
            }
        }

        if (wifiPoints.isEmpty() && blePoints.isEmpty()) {
            Text(
                text = "No sightings yet.\nRun a wardriving scan first.",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun formatSessionLabel(s: com.damon.wifiaudit.data.SessionSummary): String {
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return "${sdf.format(java.util.Date(s.startTime))} • ${s.wifiCount + s.bleCount} sightings"
}

private fun formatTime(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

private fun createCircleMarker(color: Int): android.graphics.Bitmap {
    val size = 40
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        this.color = color
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    
    // Add a small white border
    paint.color = Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2f, paint)
    
    return bitmap
}
